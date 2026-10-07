package dev.processdetails.xaero;

import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import xaero.map.MapProcessor;
import xaero.map.file.MapSaveLoad;
import xaero.map.region.LeveledRegion;
import dev.processdetails.TextLayout;

/**
 * Draws the replacement for Xaero's hard-coded "Preparing World Map..." screen.
 */
public final class LoadingScreenRenderer {
	private static final int BACKGROUND = 0xF00A0A12;
	private static final int TITLE_COLOR = 0xFFFFD24A;
	private static final int CURRENT_COLOR = 0xFFFFFFFF;
	private static final int STEP_COLOR = 0xFF9FB4D0;
	private static final int DETAIL_COLOR = 0xFF8A8A99;
	private static final int LABEL_COLOR = 0xFF7FB0E8;
	private static final int VALUE_COLOR = 0xFFE0E0E0;
	private static final int TRUE_COLOR = 0xFF6FD36F;
	private static final int FALSE_COLOR = 0xFFE06C6C;
	private static final int LINE_SPACING = 1;

	private LoadingScreenRenderer() {
	}

	public static void render(GuiGraphics graphics, MapProcessor processor) {
		Minecraft minecraft = Minecraft.getInstance();
		Window window = minecraft.getWindow();
		int width = window.getGuiScaledWidth();
		int height = window.getGuiScaledHeight();
		Font font = minecraft.font;

		graphics.fill(0, 0, width, height, BACKGROUND);

		int x = 8;
		int y = 8;
		List<TextLayout.Line> lines = new ArrayList<>();

		lines.add(new TextLayout.Line("Xaero's World Map - Preparing (detailed)", TITLE_COLOR));

		List<LoadingStatus.Phase> stack = LoadingStatus.snapshot();
		LoadingStatus.Phase current = stack.isEmpty() ? null : stack.get(0);

		String elapsed = current != null ? LoadingStatus.formatDuration(current.elapsedMillis()) : "-";
		lines.add(new TextLayout.Line("current step elapsed: " + elapsed, DETAIL_COLOR).gapBefore(6));

		lines.add(new TextLayout.Line("Phase stack (top = now):", LABEL_COLOR).gapBefore(4));

		if (stack.isEmpty()) {
			LoadingStatus.Phase last = LoadingStatus.getLastPhase();
			if (last != null) {
				lines.add(new TextLayout.Line("  (idle) last: " + last.name, DETAIL_COLOR));
			} else {
				lines.add(new TextLayout.Line("  (idle - waiting for the map processor)", DETAIL_COLOR));
			}
		} else {
			for (int i = 0; i < stack.size(); i++) {
				LoadingStatus.Phase phase = stack.get(i);
				String indent = "  ".repeat(i);
				StringBuilder line = new StringBuilder(indent);
				line.append(i == 0 ? "> " : "- ").append(phase.name);
				if (!phase.detail.isEmpty()) {
					line.append("  [").append(phase.detail).append(']');
				}
				lines.add(new TextLayout.Line(line.toString(), i == 0 ? CURRENT_COLOR : STEP_COLOR));
			}
		}

		lines.add(new TextLayout.Line("Live state:", LABEL_COLOR).gapBefore(6));

		if (processor == null) {
			lines.add(new TextLayout.Line("  map processor not available yet", DETAIL_COLOR));
		} else {
			kv(lines, "world id", processor.getCurrentWorldId());
			kv(lines, "dimension", processor.getCurrentDimId());
			kv(lines, "multiworld", processor.getCurrentMWId());
			kv(lines, "cave layer", processor.getCurrentCaveLayer());
			flag(lines, "waiting for world update", processor.isWaitingForWorldUpdate(), 3);
			flag(lines, "rendering paused", processor.isRenderingPaused());
			flag(lines, "uploading paused", processor.isUploadingPaused());
			flag(lines, "writing paused", processor.isWritingPaused());
			flag(lines, "map world usable", processor.isMapWorldUsable());
			flag(lines, "current map locked", processor.isCurrentMapLocked());

			MapSaveLoad saveLoad = processor.getMapSaveLoad();
			if (saveLoad != null) {
				flag(lines, "region detection complete", saveLoad.isRegionDetectionComplete(), 3);
				flag(lines, "loading files", saveLoad.loadingFiles);
				kv(lines, "load queue", saveLoad.getSizeOfToLoad());
				kv(lines, "save queue", saveLoad.getToSave().size());
				kv(lines, "branch cache queue", saveLoad.getSizeOfToLoadBranchCache());

				LeveledRegion<?> next = saveLoad.getNextToLoadByViewing();
				kv(lines, "region in view", next == null ? "-" : LoadingStatus.regionDetail(next));
			}

			kv(lines, "regions processing", processor.getProcessedCount(), 3);
			kv(lines, "loading requests", processor.getAffectingLoadingFrequencyCount());
		}

		TextLayout.drawLeftBlock(graphics, font, x, y, LINE_SPACING, lines);
	}

	private static void kv(List<TextLayout.Line> lines, String key, Object value) {
		kv(lines, key, value, 0);
	}

	private static void kv(List<TextLayout.Line> lines, String key, Object value, int gapBefore) {
		lines.add(new TextLayout.Line(key + ": " + (value == null ? "null" : value), VALUE_COLOR).gapBefore(gapBefore));
	}

	private static void flag(List<TextLayout.Line> lines, String key, boolean value) {
		flag(lines, key, value, 0);
	}

	private static void flag(List<TextLayout.Line> lines, String key, boolean value, int gapBefore) {
		lines.add(new TextLayout.Line(key + ": " + value, value ? TRUE_COLOR : FALSE_COLOR).gapBefore(gapBefore));
	}
}
