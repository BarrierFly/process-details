package dev.processdetails.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DryFoliageColorReloadListener;
import net.minecraft.client.resources.FoliageColorReloadListener;
import net.minecraft.client.resources.GrassColorReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import dev.processdetails.TextLayout;
import dev.processdetails.mixin.SimpleReloadInstanceAccessor;

/**
 * Load-chain and stage details for the three vanilla biome color maps
 * (grass, foliage, dry foliage) behind their Fabric reloader ids.
 *
 * <p>Two views are combined per colormap: the pack chain behind the texture
 * file ({@code ResourceManager.getResourceStack}, effective pack first —
 * "A ← B" means B provided it but A wins), and the live stage pipeline read
 * from {@link ColormapPhaseTracker} while the listener is still preparing
 * (queued → reading → waiting to apply → applying → done).</p>
 */
public final class ColormapStatus {
	public static final int MISSING_COLOR = 0xFFFF7070;
	private static final int MAX_LAYERS_SHOWN = 3;

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
						processComponent(waiting, colormap.listener(), i),
						Component.literal(colormap.location().getPath()),
						Component.literal(chainDisplay(stack)));
			}
			lines.add(new TextLayout.Line(line, color).gapBefore(i == 0 ? 4 : 0));
		}
	}

	private static Component processComponent(Set<?> waiting, Class<?> listener, int index) {
		if (!isPreparing(waiting, listener)) {
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
			return Component.translatable("process-details.colormap.phase.read_done",
					duration(phase.prepareEnd(), now));
		}
		if (phase.prepareStart() != ColormapPhaseTracker.NONE) {
			return Component.translatable("process-details.colormap.phase.reading", duration(phase.prepareStart(), now));
		}
		if (phase.queuedAt() != ColormapPhaseTracker.NONE) {
			return Component.translatable("process-details.colormap.phase.queued", duration(phase.queuedAt(), now));
		}
		return Component.translatable("process-details.colormap.state.loading");
	}

	private static String duration(long start, long end) {
		if (start == ColormapPhaseTracker.NONE || end < start) {
			return "-";
		}
		return String.format(Locale.ROOT, "%.1fs", (end - start) / 1000.0);
	}

	private static boolean isPreparing(Set<?> waiting, Class<?> listener) {
		for (Object reloader : waiting) {
			if (listener.isInstance(reloader)) {
				return true;
			}
		}
		return false;
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
