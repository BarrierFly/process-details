package dev.processdetails.mixin.xaero;

import dev.processdetails.xaero.LoadingStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.MapProcessor;

@Mixin(MapProcessor.class)
public class MapProcessorMixin {
	@Inject(method = "changeWorld", at = @At("HEAD"))
	private void pd$changeWorldHead(CallbackInfo ci) {
		LoadingStatus.enter("changeWorld: new world/dimension requested");
	}

	@Inject(method = "changeWorld", at = @At("RETURN"))
	private void pd$changeWorldReturn(CallbackInfo ci) {
		LoadingStatus.exit();
	}

	@Inject(method = "waitForLoadingToFinish", at = @At("HEAD"))
	private void pd$waitForLoadingHead(CallbackInfo ci) {
		LoadingStatus.enter("waitForLoadingToFinish: waiting for pending region file IO");
	}

	@Inject(method = "waitForLoadingToFinish", at = @At("RETURN"))
	private void pd$waitForLoadingReturn(CallbackInfo ci) {
		LoadingStatus.exit();
	}

	@Inject(method = "checkForWorldUpdate", at = @At("HEAD"))
	private void pd$checkForWorldUpdateHead(CallbackInfo ci) {
		LoadingStatus.enter("checkForWorldUpdate: detecting a world/dimension change");
	}

	@Inject(method = "checkForWorldUpdate", at = @At("RETURN"))
	private void pd$checkForWorldUpdateReturn(CallbackInfo ci) {
		LoadingStatus.exit();
	}

	@Inject(method = "updateWorld", at = @At("HEAD"))
	private void pd$updateWorldHead(CallbackInfo ci) {
		LoadingStatus.enterRoot("updateWorld: applying pending world/dimension change");
	}

	@Inject(method = "updateWorld", at = @At("RETURN"))
	private void pd$updateWorldReturn(CallbackInfo ci) {
		LoadingStatus.exit();
	}

	@Inject(method = "updateWorldSynced", at = @At("HEAD"))
	private void pd$updateWorldSyncedHead(CallbackInfo ci) {
		LoadingStatus.enter("updateWorldSynced: saving old dimension, clearing regions, locking map folder");
	}

	@Inject(method = "updateWorldSynced", at = @At("RETURN"))
	private void pd$updateWorldSyncedReturn(CallbackInfo ci) {
		LoadingStatus.exit();
	}
}
