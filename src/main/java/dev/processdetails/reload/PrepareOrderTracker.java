package dev.processdetails.reload;

import java.util.List;
import java.util.Set;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import dev.processdetails.ProcessDetails;

/**
 * Registration order of the reloaders of the most recent resource reload,
 * captured once when vanilla dispatches them ({@code prepareTasks} HEAD, on
 * the thread that starts the reload). Vanilla enqueues every prepare in list
 * order and the background pool drains its submission queue in FIFO order,
 * so the first entry that has not finished reading is the task currently at
 * the front of the drain — the one queued reloaders are actually waiting on.
 *
 * <p>Unlike a live "which prepare is running" probe this works for every
 * listener kind: the big front-runners (fonts, textures, models) implement
 * {@link PreparableReloadListener} directly and never pass through
 * {@code SimplePreparableReloadListener}'s prepare lambda. Reads come from
 * the render thread; the snapshot is swapped as a whole, and listener
 * references are only held until the next capture.</p>
 */
public final class PrepareOrderTracker {
	private static volatile List<Entry> order = List.of();

	private record Entry(Object listener, String name) {
	}

	private PrepareOrderTracker() {
	}

	public static void captureOrder(List<?> listeners) {
		try {
			List<Entry> snapshot = listeners.stream()
					.map(listener -> new Entry(listener, ((PreparableReloadListener) listener).getName()))
					.toList();
			order = snapshot;
		} catch (Throwable failure) {
			ProcessDetails.LOGGER.debug("Failed to capture reloader order", failure);
		}
	}

	/**
	 * Name of the first reloader in registration order that is still inside
	 * vanilla's {@code preparingListeners} set, or null when nothing is left
	 * reading (or no order was captured).
	 */
	public static String frontName(Set<?> preparing) {
		for (Entry entry : order) {
			if (preparing.contains(entry.listener())) {
				return entry.name();
			}
		}
		return null;
	}
}
