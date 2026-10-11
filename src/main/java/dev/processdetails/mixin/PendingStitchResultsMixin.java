package dev.processdetails.mixin;

import java.util.Map;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.client.resources.model.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.processdetails.reload.AtlasStatus;

/**
 * Times the upload step of {@code AtlasManager}: once every atlas is stitched
 * the main thread still has to push all of them to the GPU, which the
 * "Applying resources" stage line alone does not attribute to any reloader.
 */
@Mixin(AtlasManager.PendingStitchResults.class)
public abstract class PendingStitchResultsMixin {
	@Inject(method = "joinAndUpload", at = @At("HEAD"))
	private void processdetails$onUploadStart(CallbackInfoReturnable<Map<Material, TextureAtlasSprite>> cir) {
		AtlasStatus.onUploadStart();
	}

	@Inject(method = "joinAndUpload", at = @At("RETURN"))
	private void processdetails$onUploadEnd(CallbackInfoReturnable<Map<Material, TextureAtlasSprite>> cir) {
		AtlasStatus.onUploadEnd();
	}
}