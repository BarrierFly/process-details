package dev.processdetails;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shared text layout for the detail overlays: wraps lines that would cross the
 * screen edges and shifts a whole block up when it would cross the bottom.
 *
 * <p>The vanilla screens this mod augments only ever grow downwards, so a
 * single upward shift that keeps the block's bottom on screen is enough;
 * when even that cannot fit, the block is clamped to the top edge.</p>
 */
public final class TextLayout {
	/** Gap kept between text and the screen edge. */
	public static final int EDGE_MARGIN = 4;

	private TextLayout() {
	}

	public record Line(Component text, int color, int gapBefore) {
		public Line(Component text, int color) {
			this(text, color, 0);
		}

		public Line(String text, int color) {
			this(Component.literal(text), color, 0);
		}

		public Line gapBefore(int gap) {
			return new Line(this.text, this.color, gap);
		}
	}

	/**
	 * Draws the given logical lines centered horizontally, wrapping each one so
	 * it stays inside the screen width. Returns the y right below the block.
	 */
	public static int drawCenteredBlock(GuiGraphics graphics, Font font, int y, int lineSpacing, int color, Component... lines) {
		List<Line> block = new ArrayList<>(lines.length);
		for (Component line : lines) {
			block.add(new Line(line, color));
		}
		return drawBlock(graphics, font, graphics.guiWidth() / 2, y, lineSpacing, block, true);
	}

	/** Same layout rules as {@link #drawCenteredBlock}, but left-aligned at x. */
	public static int drawLeftBlock(GuiGraphics graphics, Font font, int x, int y, int lineSpacing, List<Line> lines) {
		return drawBlock(graphics, font, x, y, lineSpacing, lines, false);
	}

	private static int drawBlock(GuiGraphics graphics, Font font, int x, int y, int lineSpacing, List<Line> lines, boolean centered) {
		int maxWidth = Math.max(1, centered ? graphics.guiWidth() - 2 * EDGE_MARGIN : graphics.guiWidth() - x - EDGE_MARGIN);

		List<List<FormattedCharSequence>> visual = new ArrayList<>(lines.size());
		int height = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				height += lineSpacing + lines.get(i).gapBefore();
			}
			List<FormattedCharSequence> wrapped = font.split(lines.get(i).text(), maxWidth);
			height += wrapped.size() * font.lineHeight;
			visual.add(wrapped);
		}

		int drawY = y;
		int overflow = y + height - (graphics.guiHeight() - EDGE_MARGIN);
		if (overflow > 0) {
			drawY = Math.max(0, y - overflow);
		}

		int lineY = drawY;
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				lineY += lineSpacing + lines.get(i).gapBefore();
			}
			for (FormattedCharSequence sequence : visual.get(i)) {
				if (centered) {
					graphics.drawCenteredString(font, sequence, x, lineY, lines.get(i).color());
				} else {
					graphics.drawString(font, sequence, x, lineY, lines.get(i).color());
				}
				lineY += font.lineHeight;
			}
		}
		return lineY;
	}
}
