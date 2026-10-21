package dev.processdetails.xaero;

import dev.processdetails.ProcessDetails;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import xaero.map.MapProcessor;
import xaero.map.file.MapSaveLoad;
import xaero.map.region.LeveledRegion;

/**
 * Logs full diagnostics when the current Xaero map-loading step of a thread
 * stays unchanged for 30 seconds.
 *
 * <p>A watchdog thread is used instead of a timer on the phase tracker because
 * the stuck thread may itself be the render thread, and its stack is the most
 * useful part of the report.</p>
 */
public final class LoadingWatchdog {
	private static final long CHECK_INTERVAL_MILLIS = 500L;
	private static final long STUCK_THRESHOLD_MILLIS = 30_000L;
	private static final long STUCK_THRESHOLD_NANOS = STUCK_THRESHOLD_MILLIS * 1_000_000L;
	private static final int MAX_STACK_FRAMES = 30;

	private static final Map<Thread, TopStep> TOP_STEPS = new HashMap<>();
	private static volatile MapProcessor processor;
	private static boolean started;

	private LoadingWatchdog() {
	}

	public static synchronized void start() {
		if (started) {
			return;
		}
		started = true;
		Thread thread = new Thread(LoadingWatchdog::run, "process-details-xaero-watchdog");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	public static void attach(MapProcessor mapProcessor) {
		processor = mapProcessor;
	}

	private static void run() {
		while (true) {
			try {
				Thread.sleep(CHECK_INTERVAL_MILLIS);
			} catch (InterruptedException e) {
				return;
			}
			try {
				check();
			} catch (Throwable t) {
				ProcessDetails.LOGGER.debug("Xaero loading watchdog check failed", t);
			}
		}
	}

	private static void check() {
		List<LoadingStatus.ThreadSnapshot> snapshots = LoadingStatus.snapshotAll();
		long now = System.nanoTime();
		Set<Thread> active = Collections.newSetFromMap(new IdentityHashMap<>());
		for (LoadingStatus.ThreadSnapshot snapshot : snapshots) {
			if (snapshot.phases.isEmpty() || !snapshot.thread.isAlive()) {
				continue;
			}
			active.add(snapshot.thread);
			LoadingStatus.Phase top = snapshot.phases.get(0);
			TopStep step = TOP_STEPS.get(snapshot.thread);
			if (step == null || step.phase != top) {
				step = new TopStep(top, now);
				TOP_STEPS.put(snapshot.thread, step);
			}
			if (!step.alerted && now - step.sinceNanos >= STUCK_THRESHOLD_NANOS) {
				step.alerted = true;
				logStuck(snapshot, top);
			}
		}
		TOP_STEPS.keySet().retainAll(active);
	}

	private static void logStuck(LoadingStatus.ThreadSnapshot snapshot, LoadingStatus.Phase current) {
		StringBuilder message = new StringBuilder(2048);
		message.append("Xaero's World Map loading step \"").append(current.name)
				.append("\" has been stuck for ").append(LoadingStatus.formatDuration(current.elapsedMillis()))
				.append(" on thread \"").append(snapshot.thread.getName())
				.append("\" (").append(snapshot.thread.getState()).append(')');

		message.append("\n  step stack (top = current, elapsed since the step started):");
		for (int i = 0; i < snapshot.phases.size(); i++) {
			LoadingStatus.Phase phase = snapshot.phases.get(i);
			message.append("\n    ").append(i == 0 ? "> " : "- ").append(phase.name);
			if (!phase.detail.isEmpty()) {
				message.append(" [").append(phase.detail).append(']');
			}
			message.append(" (").append(LoadingStatus.formatDuration(phase.elapsedMillis())).append(')');
		}

		appendProcessorState(message);
		appendThreadStack(message, snapshot.thread);

		ProcessDetails.LOGGER.warn(message.toString());
	}

	private static void appendProcessorState(StringBuilder message) {
		MapProcessor map = processor;
		if (map == null) {
			message.append("\n  processor state: not captured yet");
			return;
		}
		message.append("\n  processor state:");
		try {
			appendValue(message, "world id", map.getCurrentWorldId());
			appendValue(message, "dimension", map.getCurrentDimId());
			appendValue(message, "multiworld", map.getCurrentMWId());
			appendValue(message, "cave layer", map.getCurrentCaveLayer());
			appendValue(message, "waiting for world update", map.isWaitingForWorldUpdate());
			appendValue(message, "rendering paused", map.isRenderingPaused());
			appendValue(message, "uploading paused", map.isUploadingPaused());
			appendValue(message, "writing paused", map.isWritingPaused());
			appendValue(message, "map world usable", map.isMapWorldUsable());
			appendValue(message, "current map locked", map.isCurrentMapLocked());

			MapSaveLoad saveLoad = map.getMapSaveLoad();
			if (saveLoad == null) {
				appendValue(message, "map save/load", "not available");
			} else {
				appendValue(message, "region detection complete", saveLoad.isRegionDetectionComplete());
				appendValue(message, "loading files", saveLoad.loadingFiles);
				appendValue(message, "load queue", saveLoad.getSizeOfToLoad());
				appendValue(message, "save queue", saveLoad.getToSave().size());
				appendValue(message, "branch cache queue", saveLoad.getSizeOfToLoadBranchCache());
				LeveledRegion<?> next = saveLoad.getNextToLoadByViewing();
				appendValue(message, "region in view", next == null ? "-" : LoadingStatus.regionDetail(next));
			}

			appendValue(message, "regions processing", map.getProcessedCount());
		} catch (Throwable t) {
			message.append("\n    <failed to read processor state: ").append(t).append('>');
		}
	}

	private static void appendValue(StringBuilder message, String key, Object value) {
		message.append("\n    ").append(key).append(": ").append(value == null ? "null" : value);
	}

	private static void appendThreadStack(StringBuilder message, Thread thread) {
		StackTraceElement[] frames;
		try {
			frames = thread.getStackTrace();
		} catch (Throwable t) {
			message.append("\n  thread stack: unavailable (").append(t).append(')');
			return;
		}
		if (frames.length == 0) {
			message.append("\n  thread stack: unavailable");
			return;
		}
		message.append("\n  thread stack:");
		int shown = Math.min(frames.length, MAX_STACK_FRAMES);
		for (int i = 0; i < shown; i++) {
			message.append("\n    at ").append(frames[i]);
		}
		if (frames.length > shown) {
			message.append("\n    ... ").append(frames.length - shown).append(" more frame(s)");
		}
	}

	private static final class TopStep {
		private final LoadingStatus.Phase phase;
		private final long sinceNanos;
		private boolean alerted;

		private TopStep(LoadingStatus.Phase phase, long sinceNanos) {
			this.phase = phase;
			this.sinceNanos = sinceNanos;
		}
	}
}
