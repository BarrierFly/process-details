package dev.processdetails.mixin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.processdetails.reload.PrepareOrderTracker;

/**
 * Captures the reloader registration order once per reload, at the dispatch
 * funnel. {@code SimpleReloadInstance.create} is the single entry point every
 * vanilla reload passes through on the thread that starts the reload: the
 * production simple path runs inline and the profiled debug path is what it
 * forwards to. Injecting {@code prepareTasks} directly is not possible from
 * source: its extra parameters include the protected nested
 * {@code StateFactory}, which cannot be named outside the target class, and a
 * mixin handler signature must list every target parameter.
 */
@Mixin(SimpleReloadInstance.class)
public abstract class ReloadDispatchMixin {
	@Inject(method = "create", at = @At("HEAD"))
	private static void processdetails$onDispatchReloaders(ResourceManager resourceManager, List<?> listeners, Executor prepareExecutor, Executor applyExecutor, CompletableFuture<?> waitingFor, boolean profiled, CallbackInfo ci) {
		PrepareOrderTracker.captureOrder(listeners);
	}
}
