package dev.processdetails.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;

import net.minecraft.TracingExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DryFoliageColorReloadListener;
import net.minecraft.client.resources.FoliageColorReloadListener;
import net.minecraft.client.resources.GrassColorReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import dev.processdetails.ProcessDetails;
import dev.processdetails.TextLayout;
import dev.processdetails.mixin.SimpleReloadInstanceAccessor;

/**
 * Load-chain and stage details for the three vanilla biome color maps
 * (grass, foliage, dry foliage) behind their Fabric reloader ids.
 *
 * <p>Two views are combined per colormap: the pack chain behind the texture
 * file ({@code ResourceManager.getResourceStack}, effective pack first —
 * "A ← B" means B provided it but A wins), and the live stage pipeline read
 * from {@link ColormapPhaseTracker} while the listener is still queued or
 * preparing (queued → reading → waiting to apply → applying → done).</p>
 *
 * <p>"What am I waiting for" semantics, verified against vanilla 1.21.11
 * {@code SimpleReloadInstance} / {@code SimplePreparableReloadListener}:</p>
 * <ul>
 *   <li><b>Queued</b> — the listener's {@code reload(...)} ran, its prepare
 *       was submitted with {@code supplyAsync}, but has not started: it is
 *       waiting for an idle worker of the shared background prepare pool.</li>
 *   <li><b>Read done</b> — prepare finished; the listener leaves vanilla's
 *       {@code preparingListeners} at that point (the set tracks unfinished
 *       prepares, not pending applies), but its apply still waits for
 *       {@code allPreparations} (every reloader's prepare done) <em>and</em>
 *       the previous listener's apply chain — vanilla applies one reloader
 *       after another on the main thread.</li>
 * </ul>
 */
public final class ColormapStatus {
	public static final int MISSING_COLOR = 0xFFFF7070;
	private static final int MAX_LAYERS_SHOWN = 3;

	private static final Component WAIT_READING_ALL = Component.translatable("process-details.colormap.wait.reading_all");
	private static final Component WAIT_APPLY_TURN = Component.translatable("process-details.colormap.wait.apply_turn");

	private record Colormap(String key, Identifier location, Class<?> listener) {
	}

	private static final List<Colormap> COLORMAPS = List.of(
			new Colormap("grass", Identifier.withDefaultNamespace("textures/colormap/grass.png"), GrassColorReloadListener.class),
			new Colormap("foliage", Identifier.withDefaultNamespace("textures/colormap/foliage.png"), FoliageColorReloadListener.class),
			new Colormap("dry_foliage", Identifier.withDefaultNamespace("textures/colormap/dry_foliage.png"), DryFoliageColorReloadListener.class)
	);

	private ColormapStatus() {
	}

	public static void appendLines(List<TextLayout.Line> lines, ReloadInstance reload, int detailColor, int missingColor) {
		Set<?> waiting = reload instanceof SimpleReloadInstanceAccessor accessor
				? accessor.processdetails$getWaitingReloaders()
				: Set.of();
		ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

		for (int i = 0; i < COLORMAPS.size(); i++) {
			Colormap colormap = COLORMAPS.get(i);
			int color = detailColor;
			Component line;
			List<Resource> stack = resourceManager.getResourceStack(colormap.location());
			if (stack.isEmpty()) {
				color = missingColor;
				line = Component.translatable("process-details.colormap.line.missing",
						Component.translatable("process-details.colormap." + colormap.key()),
						colormap.location());
			} else {
				line = Component.translatable("process-details.colormap.line",
						Component.translatable("process-details.colormap." + colormap.key()),
						stateComponent(reload, waiting, i),
						Component.literal(colormap.location().getPath()),
						Component.literal(chainDisplay(stack)));
			}
			lines.add(new TextLayout.Line(line, color).gapBefore(i == 0 ? 4 : 0));
		}
	}

	private static Component stateComponent(ReloadInstance reload, Set<?> waiting, int index) {
		if (reload == null || reload.isDone()) {
			return Component.translatable("process-details.colormap.state.ready");
		}
		ColormapPhaseTracker.Phase phase = ColormapPhaseTracker.phase(index);
		long now = Util.getMillis();
		if (phase.applyEnd() != ColormapPhaseTracker.NONE) {
			return Component.translatable("process-details.colormap.phase.done", duration(phase.queuedAt(), phase.applyEnd()));
		}
		if (phase.applyStart() != ColormapPhaseTracker.NONE) {
			return Component.translatable("process-details.colormap.phase.applying", duration(phase.applyStart(), now));
		}
		if (phase.prepareEnd() != ColormapPhaseTracker.NONE) {
			// The listener left preparingListeners when its prepare ended, but
			// its apply still sits behind the remaining reloaders and the gates.
			Component base = Component.translatable("process-details.colormap.phase.read_done", duration(phase.prepareEnd(), now));
			return joinState(base, gateWaiters(waiting.isEmpty()));
		}
		if (phase.prepareStart() != ColormapPhaseTracker.NONE) {
			return Component.translatable("process-details.colormap.phase.reading", duration(phase.prepareStart(), now));
		}
		if (phase.queuedAt() != ColormapPhaseTracker.NONE) {
			return joinState(Component.translatable("process-details.colormap.phase.queued", duration(phase.queuedAt(), now)),
					backgroundExecutorState());
		}
		// reload() HEAD was missed (e.g. a wrapper dispatched only its own
		// reload); we never saw which stage the listener reached.
		return Component.translatable("process-details.colormap.state.unknown");
	}

	/**
	 * Live load of vanilla's shared background executor (the pool that runs
	 * every reloader's prepare step), read per frame without blocking.
	 * Worker counts are the pool's own estimates; if the backing service
	 * cannot report its load, the wait line degrades gracefully instead of
	 * failing the whole detail section.
	 */
	private static List<Component> backgroundExecutorState() {
		try {
			ExecutorService service = ((TracingExecutor) Util.backgroundExecutor()).service();
			int busy;
			int max;
			if (service instanceof ForkJoinPool pool) {
				busy = pool.getActiveThreadCount();
				max = pool.getParallelism();
			} else if (service instanceof ThreadPoolExecutor pool) {
				busy = pool.getActiveCount();
				max = pool.getMaximumPoolSize();
			} else {
				return List.of(Component.translatable("process-details.colormap.workers.none"));
			}
			if (busy <= 0) {
				return List.of(Component.translatable("process-details.colormap.workers.idle"));
			}
			if (max <= 1) {
				return List.of(Component.translatable("process-details.colormap.workers.sole"));
			}
			return List.of(Component.translatable("process-details.colormap.workers.busy", busy, max));
		} catch (Throwable failure) {
			ProcessDetails.LOGGER.debug("Colormap background executor state unavailable", failure);
			return List.of(Component.translatable("process-details.colormap.workers.none"));
		}
	}

	/**
	 * What a colormap that finished reading still waits for before apply:
	 * while any reloader is preparing, allPreparations is still incomplete and
	 * no apply can run; once nobody is preparing, only the earlier reloaders'
	 * apply chain (vanilla runs applies sequentially) can delay its turn.
	 */
	private static List<Component> gateWaiters(boolean allRead) {
		return allRead ? List.of(WAIT_APPLY_TURN) : List.of(WAIT_READING_ALL, WAIT_APPLY_TURN);
	}

	private static Component joinState(Component base, List<Component> waits) {
		MutableComponent joined = base.copy();
		for (Component wait : waits) {
			joined.append(" · ").append(wait);
		}
		return joined;
	}

	private static String duration(long start, long end) {
		if (start == ColormapPhaseTracker.NONE || end < start) {
			return "-";
		}
		return String.format(Locale.ROOT, "%.1fs", (end - start) / 1000.0);
	}

	private static String chainDisplay(List<Resource> stack) {
		List<String> names = new ArrayList<>();
		int extra = 0;
		for (Resource resource : stack) {
			if (names.size() < MAX_LAYERS_SHOWN) {
				names.add(resource.sourcePackId());
			} else {
				extra++;
			}
		}
		String joined = String.join(" ← ", names);
		return extra > 0 ? joined + " +" + extra : joined;
	}
}
