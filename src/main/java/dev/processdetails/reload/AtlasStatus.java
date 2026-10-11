package dev.processdetails.reload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import dev.processdetails.ProcessDetails;
import dev.processdetails.TextLayout;
import dev.processdetails.mixin.AtlasManagerAccessor;

/**
 * Per-atlas detail for the vanilla texture atlas reloader (Fabric's
 * {@code minecraft:atlas}), the client reload listener that reads and stitches
 * every sprite texture into a handful of large atlases.
 *
 * <p>What vanilla does per reload, verified against 1.21.11
 * {@code AtlasManager}: {@code prepareSharedState} creates one
 * {@code CompletableFuture<SpriteLoader.Preparations>} per atlas and stores it
 * in the shared state; {@code reload} then submits <em>all</em> of them at once
 * to the shared background pool, so every atlas starts at the same moment and
 * per-atlas "time so far" says nothing. What is worth showing is therefore
 * <em>how many</em> are still stitching (what the pool is chewing on right
 * now) and, for the finished ones, how big they turned out to be — the atlas
 * size and sprite count only exist once {@code SpriteLoader} produced them,
 * and they are what actually predicts the cost.</p>
 *
 * <p>Every field below is written and read by the render thread only (the
 * futures themselves are vanilla's and complete on the background pool); the
 * snapshot is swapped as a whole when a reload schedules its stitches.</p>
 */
public final class AtlasStatus {
	public static final long NONE = -1L;

	private static final int MAX_ATLAS_ROWS = 2;
	private static final int MAX_PENDING_NAMES = 3;
	private static final long FAILURE_LOG_INTERVAL_MS = 2000L;

	/** Failed atlases first, then the heaviest, then by name so rows stay stable. */
	private static final Comparator<Entry> ROW_ORDER = Comparator
			.<Entry>comparingInt(entry -> entry.failed ? 0 : 1)
			.thenComparing(Comparator.comparingInt((Entry entry) -> entry.sprites).reversed())
			.thenComparing(entry -> entry.id.getPath());

	private static final Map<String, Long> LAST_FAILURE_LOG = new HashMap<>();
	private static volatile Snapshot snapshot;

	private AtlasStatus() {
	}

	/**
	 * One atlas of the live reload. {@code finishedAt} and the stitch results
	 * are sampled by the render thread on the first frame the future completes,
	 * so a completion is timestamped to within one frame.
	 */
	private static final class Entry {
		private final Identifier id;
		private final CompletableFuture<SpriteLoader.Preparations> future;
		private long finishedAt = NONE;
		private int width;
		private int height;
		private int sprites;
		private boolean failed;

		private Entry(Identifier id, CompletableFuture<SpriteLoader.Preparations> future) {
			this.id = id;
			this.future = future;
		}
	}

	private static final class Snapshot {
		private final Map<Identifier, Entry> entries;
		private final long startedAt = Util.getMillis();
		private volatile long uploadStartedAt = NONE;
		private volatile long uploadEndedAt = NONE;

		private Snapshot(Map<Identifier, Entry> entries) {
			this.entries = entries;
		}
	}

	/**
	 * Records the atlas futures vanilla just parked in the shared state.
	 * {@code atlasManager} is the mixin itself, passed as {@code Object} so the
	 * accessor cast is the only link between the two.
	 */
	public static void onStitchScheduled(Object atlasManager, AtlasManager.PendingStitchResults results) {
		if (results == null || !(atlasManager instanceof AtlasManagerAccessor accessor)) {
			return;
		}

		Map<Identifier, ?> atlasIds = accessor.processdetails$getAtlasById();
		if (atlasIds == null) {
			return;
		}

		Map<Identifier, Entry> entries = new LinkedHashMap<>();
		for (Identifier id : atlasIds.keySet()) {
			try {
				entries.put(id, new Entry(id, results.get(id)));
			} catch (Throwable failure) {
				// An id without a future means vanilla changed its atlas set
				// between the two calls; the other atlases stay reportable.
				logFailure("schedule", failure);
			}
		}
		snapshot = new Snapshot(entries);
	}

	public static void onUploadStart() {
		Snapshot current = snapshot;
		if (current != null) {
			current.uploadStartedAt = Util.getMillis();
		}
	}

	public static void onUploadEnd() {
		Snapshot current = snapshot;
		if (current != null) {
			current.uploadEndedAt = Util.getMillis();
		}
	}

	public static void appendLines(List<TextLayout.Line> lines, int detailColor) {
		Snapshot current = snapshot;
		if (current == null || current.entries.isEmpty()) {
			return;
		}

		long now = Util.getMillis();
		sample(current, now);

		List<Entry> stitching = new ArrayList<>();
		List<Entry> finished = new ArrayList<>();
		for (Entry entry : current.entries.values()) {
			(entry.finishedAt == NONE ? stitching : finished).add(entry);
		}
		finished.sort(ROW_ORDER);

		List<Component> block = new ArrayList<>();
		block.add(header(current, finished.size(), current.entries.size(), now));
		for (int i = 0; i < Math.min(MAX_ATLAS_ROWS, finished.size()); i++) {
			block.add(atlasLine(finished.get(i), current));
		}
		if (!stitching.isEmpty()) {
			block.add(Component.translatable("process-details.atlas.line.pending", pendingNames(stitching)));
		}

		for (int i = 0; i < block.size(); i++) {
			lines.add(new TextLayout.Line(block.get(i), detailColor).gapBefore(i == 0 ? 4 : 0));
		}
	}

	/**
	 * Timestamps every atlas whose stitch finished since the last frame and
	 * reads its result. {@code getNow} cannot block for an incomplete future,
	 * and the try/catch covers the exceptional case where a broken pack fails
	 * an atlas outright.
	 */
	private static void sample(Snapshot current, long now) {
		for (Entry entry : current.entries.values()) {
			if (entry.finishedAt != NONE || !entry.future.isDone()) {
				continue;
			}
			entry.finishedAt = now;
			try {
				SpriteLoader.Preparations preparations = entry.future.getNow(null);
				if (preparations != null) {
					entry.width = preparations.width();
					entry.height = preparations.height();
					entry.sprites = preparations.regions().size();
				}
			} catch (Throwable failure) {
				entry.failed = true;
				logFailure("stitch", failure);
			}
		}
	}

	private static Component header(Snapshot current, int done, int total, long now) {
		Component state;
		if (current.uploadEndedAt != NONE) {
			state = Component.translatable("process-details.atlas.line.ready", done, total, duration(current.startedAt, current.uploadEndedAt));
		} else if (current.uploadStartedAt != NONE) {
			// Everything is stitched; the main thread is only pushing the
			// results to the GPU now.
			state = Component.translatable("process-details.atlas.line.uploading", total, duration(current.uploadStartedAt, now));
		} else {
			state = Component.translatable("process-details.atlas.line.stitching", done, total, total - done);
		}
		return Component.translatable("process-details.atlas.line.main", state);
	}

	private static Component atlasLine(Entry entry, Snapshot current) {
		if (entry.failed) {
			return Component.translatable("process-details.atlas.line.failed", entry.id.getPath());
		}
		return Component.translatable(
				"process-details.atlas.line.atlas",
				entry.id.getPath(),
				entry.width,
				entry.height,
				entry.sprites,
				duration(current.startedAt, entry.finishedAt)
		);
	}

	private static Component pendingNames(List<Entry> stitching) {
		MutableComponent joined = Component.empty();
		int shown = Math.min(MAX_PENDING_NAMES, stitching.size());
		int extra = stitching.size() - shown;
		for (int i = 0; i < shown; i++) {
			if (i > 0) {
				joined.append(", ");
			}
			joined.append(stitching.get(i).id.getPath());
		}
		if (extra > 0) {
			joined.append(" +" + extra);
		}
		return joined;
	}

	private static String duration(long start, long end) {
		if (start == NONE || end == NONE || end < start) {
			return "-";
		}
		return String.format(Locale.ROOT, "%.1fs", (end - start) / 1000.0);
	}

	private static void logFailure(String stage, Throwable failure) {
		long now = Util.getMillis();
		Long last = LAST_FAILURE_LOG.get(stage);
		if (last == null || now - last >= FAILURE_LOG_INTERVAL_MS) {
			LAST_FAILURE_LOG.put(stage, now);
			ProcessDetails.LOGGER.debug("Atlas detail stage '{}' failed", stage, failure);
		}
	}
}