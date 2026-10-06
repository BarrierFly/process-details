package dev.processdetails.mixin;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the internals of {@link SimpleReloadInstance} so the detailed
 * progress line can report task counts and pending reloaders.
 */
@Mixin(SimpleReloadInstance.class)
public interface SimpleReloadInstanceAccessor {
	@Accessor("startedTasks")
	AtomicInteger processdetails$getToPrepareCount();

	@Accessor("finishedTasks")
	AtomicInteger processdetails$getPreparedCount();

	@Accessor("startedReloads")
	AtomicInteger processdetails$getToApplyCount();

	@Accessor("finishedReloads")
	AtomicInteger processdetails$getAppliedCount();

	@Accessor("preparingListeners")
	Set<PreparableReloadListener> processdetails$getWaitingReloaders();

	@Accessor("allPreparations")
	CompletableFuture<Unit> processdetails$getPrepareStageFuture();
}
