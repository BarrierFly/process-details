package dev.processdetails.reload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.Util;

/**
 * Reloaders whose prepare step is running right now, keyed per listener
 * instance. Written from the background prepare threads and read from the
 * render thread; only {@link SimplePreparableReloadListener}-style reloaders
 * funnel through the hooked prepare lambda, so custom
 * {@link PreparableReloadListener} implementations are invisible here and the
 * front-runner display degrades to nothing rather than lying.
 */
public final class PrepareRunTracker {
	private static final long STALE_MS = 600_000L;
	private static final Map<Object, Run> RUNNING = new ConcurrentHashMap<>();

	private record Run(String name, long startedAt) {
	}

	private PrepareRunTracker() {
	}

	public static void onPrepareStart(Object listener) {
		if (listener instanceof PreparableReloadListener reloader) {
			RUNNING.put(listener, new Run(reloader.getName(), Util.getMillis()));
		}
	}

	public static void onPrepareEnd(Object listener) {
		RUNNING.remove(listener);
	}

	/**
	 * Name of the earliest-started prepare still running — the front of the
	 * FIFO drain, i.e. the queued task most likely to free a worker next.
	 * Returns null when nothing is visibly running; entries from a prepare
	 * that threw (RETURN never fires) are pruned by age.
	 */
	public static String frontmostName() {
		Run front = null;
		long now = Util.getMillis();
		for (Map.Entry<Object, Run> entry : RUNNING.entrySet()) {
			Run run = entry.getValue();
			if (now - run.startedAt() > STALE_MS) {
				RUNNING.remove(entry.getKey());
				continue;
			}
			if (front == null || run.startedAt() < front.startedAt()) {
				front = run;
			}
		}
		return front == null ? null : front.name();
	}
}
