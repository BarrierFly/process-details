package dev.processdetails.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.List;

import net.minecraft.server.packs.resources.ProfiledReloadInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.processdetails.reload.PrepareOrderTracker;

/**
 * Captures the reloader registration order once per reload, at the dispatch
 * source. {@code prepareTasks} is overridden by ProfiledReloadInstance with
 * the same intermediary name (method_67571), so one method string covers both
 * dispatch paths: SimpleReloadInstance in production and ProfiledReloadInstance
 * for profiled debug reloads.
 */
@Mixin({SimpleReloadInstance.class, ProfiledReloadInstance.class})
public abstract class ReloadDispatchMixin {
	@Inject(method = "prepareTasks", at = @At("HEAD"))
	private void processdetails$onDispatchReloaders(Executor prepareExecutor, Executor applyExecutor, ResourceManager resourceManager, List<?> listeners, CallbackInfoReturnable<CompletableFuture<?>> cir) {
		PrepareOrderTracker.captureOrder(listeners);
	}
}
