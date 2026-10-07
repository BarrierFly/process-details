package dev.processdetails.worldsave;

import java.util.Locale;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import dev.processdetails.TextLayout;

/**
 * Draws world-save progress details under the vanilla "Saving world" message
 * shown while the integrated server shuts down.
 */
public final class WorldSaveDetailsRenderer {
	private static final int MESSAGE_TEXT_GAP = 20;
	private static final int LINE_SPACING = 1;
	private static final int COLOR = 0xFFFFFFFF;

	private WorldSaveDetailsRenderer() {
	}

	public static void render(GuiGraphics graphics) {
		if (!WorldSaveTracker.isActive()) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		int y = graphics.guiHeight() / 2 + MESSAGE_TEXT_GAP;

		String elapsed = String.format(Locale.ROOT, "%.1fs", WorldSaveTracker.getElapsedMillis() / 1000.0F);
		Component mainLine = Component.translatable(
				"process-details.save.line.main",
				WorldSaveTracker.getPercent(),
				elapsed
		);

		String dimension = WorldSaveTracker.getCurrentDimension();
		if (dimension == null) {
			dimension = "-";
		}
		int chunksTotal = WorldSaveTracker.getChunksTotal();
		String chunksLine = WorldSaveTracker.getChunksSaved() + (chunksTotal >= 0 ? "/" + chunksTotal : "");
		Component detailLine = Component.translatable(
				"process-details.save.line.detail",
				dimension,
				chunksLine,
				WorldSaveTracker.getCurrentDimensionIndex(),
				WorldSaveTracker.getDimensionsTotal()
		);
		TextLayout.drawCenteredBlock(graphics, font, y, LINE_SPACING, COLOR, mainLine, detailLine);
	}
}
