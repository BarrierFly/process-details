package dev.processdetails.worldsave;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.util.Util;

/**
 * Tracks world-save progress while the integrated server is shutting down
 * (i.e. while the client shows the "Saving world" screen).
 *
 * <p>Written from the server thread by the save-path mixins, read from the
 * render thread by the renderer; every field is volatile/atomic.</p>
 */
public final class WorldSaveTracker {
	private static volatile boolean active;
	private static volatile boolean done;
	private static volatile long startMillis = -1L;
	private static volatile int dimensionsTotal;
	private static final AtomicInteger dimensionsSaved = new AtomicInteger();
	private static volatile String currentDimension;
	private static final AtomicInteger chunksSaved = new AtomicInteger();
	private static volatile int chunksTotal = -1;

	private WorldSaveTracker() {
	}

	/** Starts a new session; called when {@code MinecraftServer#stopServer} begins. */
	public static void begin(int totalDimensions) {
		active = true;
		done = false;
		startMillis = Util.getMillis();
		dimensionsTotal = totalDimensions;
		dimensionsSaved.set(0);
		currentDimension = null;
		chunksSaved.set(0);
		chunksTotal = -1;
	}

	/** Called when {@code ServerLevel#save} begins. */
	public static void beginDimension(String name) {
		if (!active) {
			return;
		}
		currentDimension = name;
	}

	/** Called when {@code ServerLevel#save} returns. */
	public static void endDimension() {
		if (!active) {
			return;
		}
		currentDimension = null;
		dimensionsSaved.incrementAndGet();
	}

	/** Called with the number of loaded chunks when {@code ChunkMap#saveAllChunks(true)} begins. */
	public static void setChunkTotal(int total) {
		if (!active) {
			return;
		}
		if (total > chunksTotal) {
			chunksTotal = total;
		}
	}

	/** Called every time a single chunk has been written. */
	public static void incrementChunk() {
		if (!active) {
			return;
		}
		chunksSaved.incrementAndGet();
	}

	/** Called when {@code MinecraftServer#stopServer} returns. */
	public static void finish() {
		done = true;
	}

	public static boolean isActive() {
		return active;
	}

	public static boolean isDone() {
		return done;
	}

	public static long getElapsedMillis() {
		long start = startMillis;
		return start < 0 ? 0 : Util.getMillis() - start;
	}

	public static int getDimensionsTotal() {
		return dimensionsTotal;
	}

	/** 1-based index of the dimension currently being saved, 0 when between dimensions. */
	public static int getCurrentDimensionIndex() {
		int index = dimensionsSaved.get();
		return currentDimension != null ? index + 1 : index;
	}

	public static String getCurrentDimension() {
		return currentDimension;
	}

	public static int getChunksSaved() {
		return chunksSaved.get();
	}

	public static int getChunksTotal() {
		return chunksTotal;
	}

	/** Overall percentage, clamped to 99 until the save session is complete. */
	public static int getPercent() {
		if (done) {
			return 100;
		}
		int total = dimensionsTotal;
		if (total <= 0) {
			return 0;
		}
		float chunkFraction = chunksTotal > 0 ? Math.min(1.0F, chunksSaved.get() / (float) chunksTotal) : 0.0F;
		float fraction = (dimensionsSaved.get() + (currentDimension != null ? chunkFraction : 0.0F)) / total;
		return Math.min(99, Math.round(fraction * 100.0F));
	}
}
