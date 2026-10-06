package dev.processdetails.mixin.xaero;

import dev.processdetails.xaero.LoadingScreenRenderer;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;

@Mixin(GuiMap.class)
public class GuiMapMixin {
	@Inject(method = "renderLoadingScreen", at = @At("HEAD"), cancellable = true)
	private void pd$renderLoadingScreen(GuiGraphics graphics, CallbackInfo ci) {
		MapProcessor processor = ((GuiMap) (Object) this).getMapProcessor();
		LoadingScreenRenderer.render(graphics, processor);
		ci.cancel();
	}
}
