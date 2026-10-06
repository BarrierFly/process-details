package dev.processdetails.mixin.xaero;

import dev.processdetails.xaero.LoadingStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.file.worldsave.WorldDataHandler;

@Mixin(WorldDataHandler.class)
public class WorldDataHandlerMixin {
	@Inject(method = "prepareSingleplayer", at = @At("HEAD"))
	private void pd$prepareSingleplayerHead(CallbackInfo ci) {
		LoadingStatus.enter("prepareSingleplayer: locating the world-save region folder");
	}

	@Inject(method = "prepareSingleplayer", at = @At("RETURN"))
	private void pd$prepareSingleplayerReturn(CallbackInfo ci) {
		LoadingStatus.exit();
	}
}
