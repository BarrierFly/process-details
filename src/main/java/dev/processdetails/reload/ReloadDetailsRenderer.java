package dev.processdetails.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import dev.processdetails.TextLayout;
import dev.processdetails.mixin.SimpleReloadInstanceAccessor;

/**
 * Renders two detail lines right below the vanilla reload progress bar:
 * stage/percentage/elapsed time, and task counts plus pending reloaders.
 */
public final class ReloadDetailsRenderer {
	private static final int MAX_PENDING_NAMES = 3;
	private static final int LINE_SPACING = 1;
	private static final int BAR_TEXT_GAP = 5;

	private static ReloadInstance trackedReload;
	private static long reloadStartTime = -1L;

	private ReloadDetailsRenderer() {
	}

	public static void render(GuiGraphics graphics, ReloadInstance reload, float smoothProgress, int barBottom, float opacity) {
		int alpha = Mth.clamp((int) (opacity * 255.0F), 0, 255);
		if (alpha <= 0) {
			return;
		}

		// Vanilla's font renderer treats an alpha channel below 4 as opaque,
		// which would make the text pop in fully bright during fades.
		int color = ARGB.color(Math.max(alpha, 4), 255, 255, 255);

		Font font = Minecraft.getInstance().font;

		long now = Util.getMillis();
		if (trackedReload != reload) {
			trackedReload = reload;
			reloadStartTime = now;
		}
		float elapsedSeconds = (now - reloadStartTime) / 1000.0F;

		Component stageLine = Component.translatable(
				"process-details.reload.stage",
				Component.translatable(stageTranslationKey(reload)),
				Math.round(smoothProgress * 100.0F),
				String.format(Locale.ROOT, "%.1fs", elapsedSeconds)
		);

		int y = barBottom + BAR_TEXT_GAP;
		Component taskLine = buildTaskLine(reload);
		int lineCount = taskLine != null ? 2 : 1;
		Component[] lines = new Component[lineCount];
		lines[0] = stageLine;
		if (taskLine != null) {
			lines[1] = taskLine;
		}
		TextLayout.drawCenteredBlock(graphics, font, y, LINE_SPACING, color, lines);
	}

	private static String stageTranslationKey(ReloadInstance reload) {
		if (reload.isDone()) {
			return "process-details.reload.stage.done";
		}
		if (reload instanceof SimpleReloadInstanceAccessor accessor && accessor.processdetails$getPrepareStageFuture().isDone()) {
			return "process-details.reload.stage.applying";
		}
		return "process-details.reload.stage.preparing";
	}

	private static Component buildTaskLine(ReloadInstance reload) {
		if (!(reload instanceof SimpleReloadInstanceAccessor accessor)) {
			return null;
		}

		MutableComponent taskLine = Component.translatable(
				"process-details.reload.tasks",
				accessor.processdetails$getPreparedCount().get(),
				accessor.processdetails$getToPrepareCount().get(),
				accessor.processdetails$getAppliedCount().get(),
				accessor.processdetails$getToApplyCount().get()
		);

		Component pending = buildPendingLine(accessor);
		if (pending != null) {
			taskLine = taskLine.append(" · ").append(pending);
		}
		return taskLine;
	}

	private static Component buildPendingLine(SimpleReloadInstanceAccessor accessor) {
		List<String> names = new ArrayList<>();
		int extra = 0;
		for (PreparableReloadListener reloader : accessor.processdetails$getWaitingReloaders()) {
			String name = shortenName(reloader.getName());
			if (names.size() < MAX_PENDING_NAMES) {
				names.add(name);
			} else {
				extra++;
			}
		}
		if (names.isEmpty()) {
			return null;
		}

		String joined = String.join(", ", names);
		if (extra > 0) {
			joined += " +" + extra;
		}
		return Component.translatable("process-details.reload.waiting", joined);
	}

	private static String shortenName(String name) {
		// Fabric API names reloaders like "id (ClassName)"; keep the id part only.
		int paren = name.indexOf(" (");
		return paren > 0 ? name.substring(0, paren) : name;
	}
}
