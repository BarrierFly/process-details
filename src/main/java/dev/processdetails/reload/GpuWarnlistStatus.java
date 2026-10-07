package dev.processdetails.reload;

import java.util.List;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GpuWarnlistManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadInstance;

/**
 * Detail lines for the vanilla GPU warnlist reloader (Fabric's
 * "minecraft:private/class_5407"): preparation state, active warnings, and
 * whether the warning screen will appear.
 *
 * <p>The warnlist is only evaluated on the OpenGL backend, which is the most
 * common reason for "why is this always empty".</p>
 */
public final class GpuWarnlistStatus {
	private GpuWarnlistStatus() {
	}

	public static void appendLines(List<Component> lines, ReloadInstance reload) {
		GpuWarnlistManager manager = Minecraft.getInstance().getGpuWarnlistManager();
		GpuDevice device = RenderSystem.tryGetDevice();
		String backend = device != null ? device.getBackendName() : "?";

		if (!"OpenGL".equals(backend)) {
			lines.add(Component.translatable("process-details.gpu.line.inactive", backend));
			return;
		}

		int count = activeWarnings(manager);
		Component countText = count == 0
				? Component.translatable("process-details.gpu.count.none")
				: Component.translatable(count == 1 ? "process-details.gpu.count.one" : "process-details.gpu.count.many", count);
		lines.add(Component.translatable(
				reload.isDone() ? "process-details.gpu.line.main.done" : "process-details.gpu.line.main.reloading",
				countText
		));

		addWarningLine(lines, "renderer", manager.getRendererWarnings());
		addWarningLine(lines, "version", manager.getVersionWarnings());
		addWarningLine(lines, "vendor", manager.getVendorWarnings());

		if (manager.willShowWarning()) {
			lines.add(Component.translatable("process-details.gpu.line.willshow"));
		}
	}

	private static int activeWarnings(GpuWarnlistManager manager) {
		int count = 0;
		if (manager.getRendererWarnings() != null) {
			count++;
		}
		if (manager.getVersionWarnings() != null) {
			count++;
		}
		if (manager.getVendorWarnings() != null) {
			count++;
		}
		return count;
	}

	private static void addWarningLine(List<Component> lines, String kind, String matched) {
		if (matched != null) {
			lines.add(Component.translatable("process-details.gpu.line.warning", Component.translatable("process-details.gpu.kind." + kind), matched));
		}
	}
}
