package dev.processdetails.xaero;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import xaero.map.region.LeveledRegion;

/**
 * Global, thread-safe tracker of what the Xaero World Map is currently doing.
 *
 * <p>Each thread keeps its own phase stack (the map processor runs on the MapRunner
 * thread, while world-change detection happens on the render thread). The renderer asks
 * for the phase stack of whichever thread is currently the most active, so the display
 * naturally follows the work that is actually running.</p>
 */
public final class LoadingStatus {
	private static final int MAX_DEPTH = 32;
	private static final Object LOCK = new Object();
	private static final Map<String, Deque<Phase>> STACKS = new HashMap<>();
	private static volatile Phase lastPhase = null;

	private LoadingStatus() {
	}

	public static final class Phase {
		public final String name;
		public final String detail;
		private final long startNanos;

		Phase(String name, String detail) {
			this.name = name;
			this.detail = detail == null ? "" : detail;
			this.startNanos = System.nanoTime();
		}

		public long elapsedMillis() {
			return (System.nanoTime() - this.startNanos) / 1_000_000L;
		}

		/** Higher = more recently started. */
		long startNanos() {
			return this.startNanos;
		}
	}

	private static Deque<Phase> stackForCurrentThread() {
		String name = Thread.currentThread().getName();
		return STACKS.computeIfAbsent(name, key -> new ArrayDeque<>());
	}

	public static void enter(String name) {
		enter(name, null);
	}

	public static void enter(String name, String detail) {
		synchronized (LOCK) {
			Deque<Phase> stack = stackForCurrentThread();
			if (stack.size() >= MAX_DEPTH) {
				stack.clear();
			}
			Phase phase = new Phase(name, detail);
			stack.push(phase);
			lastPhase = phase;
		}
	}

	/** Starts a new top-level phase for the current thread, discarding anything stale. */
	public static void enterRoot(String name) {
		enterRoot(name, null);
	}

	/** Starts a new top-level phase for the current thread, discarding anything stale. */
	public static void enterRoot(String name, String detail) {
		synchronized (LOCK) {
			Deque<Phase> stack = stackForCurrentThread();
			stack.clear();
			Phase phase = new Phase(name, detail);
			stack.push(phase);
			lastPhase = phase;
		}
	}

	public static void exit() {
		synchronized (LOCK) {
			Deque<Phase> stack = stackForCurrentThread();
			if (!stack.isEmpty()) {
				stack.pop();
			}
		}
	}

	public static void reset() {
		synchronized (LOCK) {
			STACKS.clear();
			lastPhase = null;
		}
	}

	/**
	 * Returns a snapshot of the phase stack of the most recently active thread,
	 * top (most recent) first.
	 */
	public static List<Phase> snapshot() {
		synchronized (LOCK) {
			Deque<Phase> best = null;
			long bestStart = Long.MIN_VALUE;
			for (Deque<Phase> stack : STACKS.values()) {
				if (stack.isEmpty()) {
					continue;
				}
				long start = stack.peek().startNanos();
				if (start > bestStart) {
					bestStart = start;
					best = stack;
				}
			}
			return best == null ? new ArrayList<>() : new ArrayList<>(best);
		}
	}

	public static Phase getLastPhase() {
		return lastPhase;
	}

	public static String regionDetail(LeveledRegion<?> region) {
		if (region == null) {
			return "<unknown region>";
		}
		try {
			return "region " + region.getRegionX() + ", " + region.getRegionZ()
					+ " (cave layer " + region.getCaveLayer() + ")";
		} catch (Throwable t) {
			return "<region " + region + ">";
		}
	}

	public static String formatDuration(long millis) {
		if (millis < 1000L) {
			return millis + " ms";
		}
		return String.format(java.util.Locale.ROOT, "%.2f s", millis / 1000.0);
	}
}
