package dev.processdetails;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

/**
 * Shared text layout for the detail overlays: wraps lines that would cross the
 * screen edges and shifts a whole block up when it would cross the bottom.
 *
 * <p>The vanilla screens this mod augments only ever grow downwards, so a
 * single upward shift that keeps the block's bottom on screen is enough;
 * when even that cannot fit, the block is clamped to the top edge.</p>
 *
 * <p>A line carrying a scrolling detail ({@link Line#scrollingDetail(Component)})
 * is always drawn left-aligned from the block's left edge: its head component
 * stays fixed, and when the detail does not fit between the head's end and the
 * right screen edge, it scrolls leftward through that band on a continuous
 * loop instead of wrapping.</p>
 */
public final class TextLayout {
	/** Gap kept between text and the screen edge. */
	public static final int EDGE_MARGIN = 4;

	private static final String LATIN_ALPHABET = "abcdefghijklmnopqrstuvwxyz";
	private static final double SCROLL_CHARS_PER_SECOND = 10.0;

	private TextLayout() {
	}

	public record Line(Component text, int color, int gapBefore, Component scrollingDetail) {
		public Line(Component text, int color) {
			this(text, color, 0, null);
		}

		public Line(String text, int color) {
			this(Component.literal(text), color, 0, null);
		}

		public Line gapBefore(int gap) {
			return new Line(this.text, this.color, gap, this.scrollingDetail);
		}

		public Line scrollingDetail(Component detail) {
			return detail == null ? this : new Line(this.text, this.color, this.gapBefore, detail);
		}
	}

	/**
	 * Layout of a line carrying a scrolling detail: {@code detail == null}
	 * means the whole line fits (or there is no room to scroll anything) and
	 * is drawn statically from the left edge, so {@code head} then holds the
	 * combined text; otherwise {@code detail} loops leftward inside
	 * {@code [bandLeft, bandRight]}.
	 */
	private record Marquee(FormattedCharSequence head, FormattedCharSequence detail, int detailWidth, int bandLeft, int bandRight) {
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

	public static int drawCenteredBlock(GuiGraphics graphics, Font font, int y, int lineSpacing, List<Line> lines) {
		return drawBlock(graphics, font, graphics.guiWidth() / 2, y, lineSpacing, lines, true);
	}

	/** Same layout rules as {@link #drawCenteredBlock}, but left-aligned at x. */
	public static int drawLeftBlock(GuiGraphics graphics, Font font, int x, int y, int lineSpacing, List<Line> lines) {
		return drawBlock(graphics, font, x, y, lineSpacing, lines, false);
	}

	private static int drawBlock(GuiGraphics graphics, Font font, int x, int y, int lineSpacing, List<Line> lines, boolean centered) {
		TextShaderGuard.ensureTextPipeline();
		int guiWidth = graphics.guiWidth();
		int maxWidth = Math.max(1, centered ? guiWidth - 2 * EDGE_MARGIN : guiWidth - x - EDGE_MARGIN);
		int leftEdge = centered ? EDGE_MARGIN : x;
		int bandRight = guiWidth - EDGE_MARGIN;

		List<List<FormattedCharSequence>> visual = new ArrayList<>(lines.size());
		List<Marquee> marquees = new ArrayList<>(lines.size());
		int height = 0;
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				height += lineSpacing + lines.get(i).gapBefore();
			}
			Line line = lines.get(i);
			if (line.scrollingDetail() != null) {
				FormattedCharSequence head = line.text().getVisualOrderText();
				FormattedCharSequence detail = line.scrollingDetail().getVisualOrderText();
				int headWidth = font.width(head);
				int detailWidth = font.width(detail);
				int bandLeft = leftEdge + headWidth;
				if (detailWidth <= 0 || bandRight - bandLeft <= 0 || headWidth + detailWidth <= bandRight - leftEdge) {
					marquees.add(new Marquee(line.text().copy().append(line.scrollingDetail()).getVisualOrderText(), null, 0, 0, 0));
				} else {
					marquees.add(new Marquee(head, detail, detailWidth, bandLeft, bandRight));
				}
				visual.add(null);
				height += font.lineHeight;
				continue;
			}
			List<FormattedCharSequence> wrapped = font.split(line.text(), maxWidth);
			height += wrapped.size() * font.lineHeight;
			visual.add(wrapped);
			marquees.add(null);
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
			Marquee marquee = marquees.get(i);
			if (marquee != null) {
				graphics.drawString(font, marquee.head(), leftEdge, lineY, lines.get(i).color());
				if (marquee.detail() != null) {
					drawScrollingDetail(graphics, font, marquee, lineY, lines.get(i).color());
				}
				lineY += font.lineHeight;
				continue;
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

	private static void drawScrollingDetail(GuiGraphics graphics, Font font, Marquee marquee, int y, int color) {
		// Fixed pixel speed: the spec says "10 English characters per second",
		// measured as the lowercase alphabet's average glyph width, so details
		// in other scripts scroll at the same speed instead of wider glyphs
		// speeding up.
		double pixelsPerMillisecond = SCROLL_CHARS_PER_SECOND * font.width(LATIN_ALPHABET) / LATIN_ALPHABET.length() / 1000.0;
		double cycle = marquee.detailWidth() + (marquee.bandRight() - marquee.bandLeft());
		int offset = (int) (Util.getMillis() * pixelsPerMillisecond % cycle);
		int firstX = marquee.bandLeft() - offset;
		graphics.enableScissor(marquee.bandLeft(), y, marquee.bandRight(), y + font.lineHeight);
		graphics.drawString(font, marquee.detail(), firstX, y, color);
		graphics.drawString(font, marquee.detail(), firstX + (int) cycle, y, color);
		graphics.disableScissor();
	}
}
