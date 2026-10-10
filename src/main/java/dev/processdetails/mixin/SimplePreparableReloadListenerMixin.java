package dev.processdetails.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.processdetails.reload.ColormapPhaseTracker;

/**
 * Marks when each simple listener is queued into a reload, so the colormap
 * detail lines can show queue time before the prepare task actually runs.
 */
@Mixin(SimplePreparableReloadListener.class)
public abstract class SimplePreparableReloadListenerMixin {
	@Inject(method = "reload", at = @At("HEAD"))
	private void processdetails$onReloadQueued(PreparableReloadListener.SharedState sharedState, Executor prepareExecutor, PreparableReloadListener.PreparationBarrier preparationBarrier, Executor applyExecutor, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
		ColormapPhaseTracker.onQueued(this);
	}
}
