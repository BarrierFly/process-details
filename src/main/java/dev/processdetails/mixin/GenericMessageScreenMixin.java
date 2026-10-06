package dev.processdetails.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.processdetails.worldsave.WorldSaveDetailsRenderer;

/**
 * The vanilla "Saving world" screen is a plain centered message; draw our
 * save-progress details below it. The renderer checks the tracker first, so
 * other uses of {@link GenericMessageScreen} are left untouched.
 */
@Mixin(GenericMessageScreen.class)
public class GenericMessageScreenMixin {
	@Inject(method = "renderBackground", at = @At("TAIL"))
	private void processdetails$renderSaveDetails(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		WorldSaveDetailsRenderer.render(graphics);
	}
}
