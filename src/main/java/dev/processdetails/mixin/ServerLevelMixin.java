package dev.processdetails.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.processdetails.worldsave.WorldSaveTracker;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
	@Inject(method = "save", at = @At("HEAD"))
	private void processdetails$beginDimension(ProgressListener progressListener, boolean flush, boolean suppressLog, CallbackInfo ci) {
		String dimension = ((ServerLevel) (Object) this).dimension().identifier().toString();
		WorldSaveTracker.beginDimension(dimension);
	}

	@Inject(method = "save", at = @At("RETURN"))
	private void processdetails$endDimension(ProgressListener progressListener, boolean flush, boolean suppressLog, CallbackInfo ci) {
		WorldSaveTracker.endDimension();
	}
}
