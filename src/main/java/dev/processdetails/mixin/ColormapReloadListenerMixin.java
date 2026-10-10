package dev.processdetails.mixin;

import net.minecraft.client.resources.DryFoliageColorReloadListener;
import net.minecraft.client.resources.FoliageColorReloadListener;
import net.minecraft.client.resources.GrassColorReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.processdetails.reload.ColormapPhaseTracker;

/**
 * Stage timestamps for the three vanilla colormap listeners: prepare runs on
 * a reload worker thread, apply on the main thread. Both are recorded so the
 * detail lines can show the per-colormap pipeline as it happens.
 */
@Mixin({GrassColorReloadListener.class, FoliageColorReloadListener.class, DryFoliageColorReloadListener.class})
public abstract class ColormapReloadListenerMixin {
	@Inject(method = "prepare", at = @At("HEAD"))
	private void processdetails$onPrepareStart(ResourceManager resourceManager, ProfilerFiller profilerFiller, CallbackInfoReturnable<int[]> cir) {
		ColormapPhaseTracker.onPrepareStart(this);
	}

	@Inject(method = "prepare", at = @At("RETURN"))
	private void processdetails$onPrepareEnd(ResourceManager resourceManager, ProfilerFiller profilerFiller, CallbackInfoReturnable<int[]> cir) {
		ColormapPhaseTracker.onPrepareEnd(this);
	}

	@Inject(method = "apply", at = @At("HEAD"))
	private void processdetails$onApplyStart(int[] pixels, ResourceManager resourceManager, ProfilerFiller profilerFiller, CallbackInfo ci) {
		ColormapPhaseTracker.onApplyStart(this);
	}

	@Inject(method = "apply", at = @At("RETURN"))
	private void processdetails$onApplyEnd(int[] pixels, ResourceManager resourceManager, ProfilerFiller profilerFiller, CallbackInfo ci) {
		ColormapPhaseTracker.onApplyEnd(this);
	}
}
