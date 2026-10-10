package dev.processdetails.reload;

import net.minecraft.client.resources.DryFoliageColorReloadListener;
import net.minecraft.client.resources.FoliageColorReloadListener;
import net.minecraft.client.resources.GrassColorReloadListener;
import net.minecraft.util.Util;

/**
 * Phase timestamps for the three vanilla colormap reloaders, written from
 * whatever thread runs each stage and read from the render thread. Every
 * field is an independent volatile: readers only need a plausible snapshot
 * of a single stage, never a consistent view across stages.
 */
public final class ColormapPhaseTracker {
	public static final long NONE = -1L;
	private static final Stamp[] STAMPS = {new Stamp(), new Stamp(), new Stamp()};

	private static final class Stamp {
		volatile long queuedAt = NONE;
		volatile long prepareStart = NONE;
		volatile long prepareEnd = NONE;
		volatile long applyStart = NONE;
		volatile long applyEnd = NONE;
	}

	/** Immutable view of one listener's stamps; {@link #NONE} means not reached yet. */
	public record Phase(long queuedAt, long prepareStart, long prepareEnd, long applyStart, long applyEnd) {
	}

	private ColormapPhaseTracker() {
	}

	public static void onQueued(Object listener) {
		Stamp stamp = stamp(listener);
		if (stamp != null) {
			stamp.queuedAt = Util.getMillis();
			stamp.prepareStart = NONE;
			stamp.prepareEnd = NONE;
			stamp.applyStart = NONE;
			stamp.applyEnd = NONE;
		}
	}

	public static void onPrepareStart(Object listener) {
		Stamp stamp = stamp(listener);
		if (stamp != null) {
			long now = Util.getMillis();
			if (stamp.queuedAt == NONE) {
				// reload() HEAD was missed (e.g. a wrapper dispatched only its own reload);
				// keep the timeline anchored at the first event we did see.
				stamp.queuedAt = now;
			}
			stamp.prepareStart = now;
		}
	}

	public static void onPrepareEnd(Object listener) {
		Stamp stamp = stamp(listener);
		if (stamp != null) {
			stamp.prepareEnd = Util.getMillis();
		}
	}

	public static void onApplyStart(Object listener) {
		Stamp stamp = stamp(listener);
		if (stamp != null) {
			stamp.applyStart = Util.getMillis();
		}
	}

	public static void onApplyEnd(Object listener) {
		Stamp stamp = stamp(listener);
		if (stamp != null) {
			stamp.applyEnd = Util.getMillis();
		}
	}

	public static Phase phase(int index) {
		Stamp stamp = STAMPS[index];
		return new Phase(stamp.queuedAt, stamp.prepareStart, stamp.prepareEnd, stamp.applyStart, stamp.applyEnd);
	}

	private static Stamp stamp(Object listener) {
		if (listener instanceof GrassColorReloadListener) {
			return STAMPS[0];
		}
		if (listener instanceof FoliageColorReloadListener) {
			return STAMPS[1];
		}
		if (listener instanceof DryFoliageColorReloadListener) {
			return STAMPS[2];
		}
		return null;
	}
}
