package dev.processdetails.mixin;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.processdetails.reload.PrepareRunTracker;

/**
 * Tracks which SimplePreparableReloadListener prepare tasks are running right
 * now, so a queued colormap line can name the task currently at the front of
 * the background pool's FIFO drain. The private synthetic lambda that vanilla
 * binds into supplyAsync (prepare body + Profiler.get() wrapper) is the only
 * funnel every simple listener's prepare passes through; its name is a
 * compile-time constant, identical in dev and production.
 */
@Mixin(SimplePreparableReloadListener.class)
public abstract class PrepareRunMixin {
	@Inject(method = "method_18791", at = @At("HEAD"))
	private void processdetails$onPrepareTaskStart(ResourceManager resourceManager, CallbackInfoReturnable<Object> cir) {
		PrepareRunTracker.onPrepareStart(this);
	}

	@Inject(method = "method_18791", at = @At("RETURN"))
	private void processdetails$onPrepareTaskEnd(ResourceManager resourceManager, CallbackInfoReturnable<Object> cir) {
		PrepareRunTracker.onPrepareEnd(this);
	}
}
