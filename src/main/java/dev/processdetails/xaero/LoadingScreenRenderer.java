package dev.processdetails.xaero;

import com.mojang.blaze3d.platform.Window;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import xaero.map.MapProcessor;
import xaero.map.file.MapSaveLoad;
import xaero.map.region.LeveledRegion;

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
	private static final int LINE = 10;

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

		graphics.drawString(font, "Xaero's World Map - Preparing (detailed)", x, y, TITLE_COLOR);
		y += LINE + 6;

		List<LoadingStatus.Phase> stack = LoadingStatus.snapshot();
		LoadingStatus.Phase current = stack.isEmpty() ? null : stack.get(0);

		String elapsed = current != null ? LoadingStatus.formatDuration(current.elapsedMillis()) : "-";
		graphics.drawString(font, "current step elapsed: " + elapsed, x, y, DETAIL_COLOR);
		y += LINE + 4;

		graphics.drawString(font, "Phase stack (top = now):", x, y, LABEL_COLOR);
		y += LINE;

		if (stack.isEmpty()) {
			LoadingStatus.Phase last = LoadingStatus.getLastPhase();
			if (last != null) {
				graphics.drawString(font, "  (idle) last: " + last.name, x, y, DETAIL_COLOR);
			} else {
				graphics.drawString(font, "  (idle - waiting for the map processor)", x, y, DETAIL_COLOR);
			}
			y += LINE;
		} else {
			for (int i = 0; i < stack.size(); i++) {
				LoadingStatus.Phase phase = stack.get(i);
				String indent = "  ".repeat(i);
				StringBuilder line = new StringBuilder(indent);
				line.append(i == 0 ? "> " : "- ").append(phase.name);
				if (!phase.detail.isEmpty()) {
					line.append("  [").append(phase.detail).append(']');
				}
				graphics.drawString(font, line.toString(), x, y, i == 0 ? CURRENT_COLOR : STEP_COLOR);
				y += LINE;
			}
		}

		y += 6;
		graphics.drawString(font, "Live state:", x, y, LABEL_COLOR);
		y += LINE;

		if (processor == null) {
			graphics.drawString(font, "  map processor not available yet", x, y, DETAIL_COLOR);
			return;
		}

		y = kv(graphics, font, x, y, "world id", processor.getCurrentWorldId());
		y = kv(graphics, font, x, y, "dimension", processor.getCurrentDimId());
		y = kv(graphics, font, x, y, "multiworld", processor.getCurrentMWId());
		y = kv(graphics, font, x, y, "cave layer", processor.getCurrentCaveLayer());
		y += 3;

		y = flag(graphics, font, x, y, "waiting for world update", processor.isWaitingForWorldUpdate());
		y = flag(graphics, font, x, y, "rendering paused", processor.isRenderingPaused());
		y = flag(graphics, font, x, y, "uploading paused", processor.isUploadingPaused());
		y = flag(graphics, font, x, y, "writing paused", processor.isWritingPaused());
		y = flag(graphics, font, x, y, "map world usable", processor.isMapWorldUsable());
		y = flag(graphics, font, x, y, "current map locked", processor.isCurrentMapLocked());

		MapSaveLoad saveLoad = processor.getMapSaveLoad();
		if (saveLoad != null) {
			y += 3;
			y = flag(graphics, font, x, y, "region detection complete", saveLoad.isRegionDetectionComplete());
			y = flag(graphics, font, x, y, "loading files", saveLoad.loadingFiles);
			y = kv(graphics, font, x, y, "load queue", saveLoad.getSizeOfToLoad());
			y = kv(graphics, font, x, y, "save queue", saveLoad.getToSave().size());
			y = kv(graphics, font, x, y, "branch cache queue", saveLoad.getSizeOfToLoadBranchCache());

			LeveledRegion<?> next = saveLoad.getNextToLoadByViewing();
			y = kv(graphics, font, x, y, "region in view", next == null ? "-" : LoadingStatus.regionDetail(next));
		}

		y += 3;
		y = kv(graphics, font, x, y, "regions processing", processor.getProcessedCount());
		y = kv(graphics, font, x, y, "loading requests", processor.getAffectingLoadingFrequencyCount());
	}

	private static int kv(GuiGraphics graphics, Font font, int x, int y, String key, Object value) {
		String text = key + ": " + (value == null ? "null" : value);
		graphics.drawString(font, text, x, y, VALUE_COLOR);
		return y + LINE;
	}

	private static int flag(GuiGraphics graphics, Font font, int x, int y, String key, boolean value) {
		graphics.drawString(font, key + ": " + value, x, y, value ? TRUE_COLOR : FALSE_COLOR);
		return y + LINE;
	}
}
