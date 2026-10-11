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
import dev.processdetails.reload.PrepareOrderTracker;

/**
 * Load-chain and stage details for the three vanilla biome color maps
 * (grass, foliage, dry foliage) behind their Fabric reloader ids.
 *
 * <p>Two views are combined per colormap: the pack chain behind the texture
 * file ({@code ResourceManager.getResourceStack}, effective pack first —
 * "A ← B" means B provided it but A wins), and the live stage pipeline read
 * from {@link ColormapPhaseTracker}. While the listener is queued, the line
 * shows the wait reason instead of the pack chain.</p>
 *
 * <p>"What am I waiting for" semantics, verified against vanilla 1.21.11
 * {@code SimpleReloadInstance} / {@code SimplePreparableReloadListener}:</p>
 * <ul>
 *   <li><b>Queued</b> — the listener's {@code reload(...)} ran and submitted
 *       its prepare with {@code supplyAsync}, but no worker has started it.
 *       Vanilla enqueues every prepare up front and the pool drains its
 *       submission queue in FIFO order — an idle worker would pick the head
 *       task at once, so a sustained wait happens while every worker grinds
 *       through earlier prepare tasks. The line therefore reports how many
 *       submitted tasks are still pending, which task is currently at the
 *       front of the drain (the next one likely to free a worker), and the
 *       live worker load; all of it is per-frame snapshots (a task can start
 *       between two frames, which reads as a clear queue).</li>
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

	/**
	 * The rendered state of one colormap; {@code queuedOnly} marks the queued
	 * state, whose line replaces the texture path and pack chain with the
	 * wait reason.
	 */
	private record ColormapState(Component component, boolean queuedOnly) {
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
				ColormapState state = stateComponent(reload, waiting, i);
				if (state.queuedOnly()) {
					line = Component.translatable("process-details.colormap.line.queued",
							Component.translatable("process-details.colormap." + colormap.key()),
							state.component());
				} else {
					line = Component.translatable("process-details.colormap.line",
							Component.translatable("process-details.colormap." + colormap.key()),
							state.component(),
							Component.literal(colormap.location().getPath()),
							Component.literal(chainDisplay(stack)));
				}
			}
			lines.add(new TextLayout.Line(line, color).gapBefore(i == 0 ? 4 : 0));
		}
	}

	private static ColormapState stateComponent(ReloadInstance reload, Set<?> waiting, int index) {
		if (reload == null || reload.isDone()) {
			return new ColormapState(Component.translatable("process-details.colormap.state.ready"), false);
		}
		ColormapPhaseTracker.Phase phase = ColormapPhaseTracker.phase(index);
		long now = Util.getMillis();
		if (phase.applyEnd() != ColormapPhaseTracker.NONE) {
			return new ColormapState(Component.translatable("process-details.colormap.phase.done", duration(phase.queuedAt(), phase.applyEnd())), false);
		}
		if (phase.applyStart() != ColormapPhaseTracker.NONE) {
			return new ColormapState(Component.translatable("process-details.colormap.phase.applying", duration(phase.applyStart(), now)), false);
		}
		if (phase.prepareEnd() != ColormapPhaseTracker.NONE) {
			// The listener left preparingListeners when its prepare ended, but
			// its apply still sits behind the remaining reloaders and the gates.
			Component base = Component.translatable("process-details.colormap.phase.read_done", duration(phase.prepareEnd(), now));
			return new ColormapState(joinState(base, gateWaiters(waiting.isEmpty())), false);
		}
		if (phase.prepareStart() != ColormapPhaseTracker.NONE) {
			return new ColormapState(Component.translatable("process-details.colormap.phase.reading", duration(phase.prepareStart(), now)), false);
		}
		if (phase.queuedAt() != ColormapPhaseTracker.NONE) {
			Component base = Component.translatable("process-details.colormap.phase.queued", duration(phase.queuedAt(), now));
			return new ColormapState(joinState(base, backgroundExecutorState(waiting)), true);
		}
		// reload() HEAD was missed (e.g. a wrapper dispatched only its own
		// reload); we never saw which stage the listener reached.
		return new ColormapState(Component.translatable("process-details.colormap.state.unknown"), false);
	}

	/**
	 * Live state of vanilla's shared background executor (the pool that runs
	 * every reloader's prepare step), read per frame without blocking. For
	 * ForkJoinPool — vanilla's actual pool — the pending submission count is
	 * the direct reason a queued colormap still waits; the worker load is
	 * context. All values are estimates; if the backing service cannot report
	 * them, the wait line degrades gracefully instead of failing the whole
	 * detail section.
	 */
	private static List<Component> backgroundExecutorState(Set<?> preparing) {
		try {
			ExecutorService service = ((TracingExecutor) Util.backgroundExecutor()).service();
			int busy;
			int max;
			Integer pending = null;
			if (service instanceof ForkJoinPool pool) {
				busy = pool.getActiveThreadCount();
				max = pool.getParallelism();
				pending = Math.max(0, pool.getQueuedSubmissionCount());
			} else if (service instanceof ThreadPoolExecutor pool) {
				busy = pool.getActiveCount();
				max = pool.getMaximumPoolSize();
			} else {
				return List.of(Component.translatable("process-details.colormap.workers.none"));
			}
			if (pending != null && pending > 0) {
				List<Component> waits = new ArrayList<>(2);
				waits.add(Component.translatable("process-details.colormap.workers.queue.pending", pending, busy, max));
				String front = PrepareOrderTracker.frontName(preparing);
				if (front != null && !front.isBlank()) {
					waits.add(Component.translatable("process-details.colormap.workers.front", front));
				}
				return List.copyOf(waits);
			}
			if (pending != null) {
				// Nothing left in the submission queue: our task was picked up
				// between frames and prepare starts on the next worker scan.
				return List.of(Component.translatable("process-details.colormap.workers.queue.clear", busy, max));
			}
			// Pools without a queue-depth probe: fall back to worker load.
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
