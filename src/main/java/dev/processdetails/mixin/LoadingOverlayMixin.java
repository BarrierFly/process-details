package dev.processdetails.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.processdetails.reload.ReloadDetailsRenderer;

/**
 * Draws detailed reload progress below the vanilla progress bar.
 *
 * <p>Injecting at the tail of {@code drawProgressBar} (instead of wrapping
 * or redirecting vanilla calls) keeps this mod compatible with RRLS, which
 * wraps calls inside the same method to recolor its progress bar.</p>
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Shadow
	@Final
	private ReloadInstance reload;

	@Shadow
	private float currentProgress;

	@Inject(method = "drawProgressBar", at = @At("TAIL"))
	private void processdetails$renderDetails(GuiGraphics graphics, int minX, int minY, int maxX, int maxY, float opacity, CallbackInfo ci) {
		ReloadDetailsRenderer.render(graphics, this.reload, this.currentProgress, maxY, opacity);
	}
}
