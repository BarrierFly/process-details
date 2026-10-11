package dev.processdetails.mixin;

import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.processdetails.reload.AtlasStatus;

/**
 * Hands the live reload's per-atlas stitch futures to {@link AtlasStatus}.
 *
 * <p>{@code prepareSharedState} is where vanilla parks one
 * {@code CompletableFuture<SpriteLoader.Preparations>} per atlas; they are
 * only completed much later by the background pool, and after that
 * {@code reload} submits all of them at once. Injecting at the tail therefore
 * captures exactly the set that belongs to the reload in progress.</p>
 */
@Mixin(AtlasManager.class)
public abstract class AtlasManagerMixin {
	@Inject(method = "prepareSharedState", at = @At("RETURN"))
	private void processdetails$capturePendingStitches(PreparableReloadListener.SharedState sharedState, CallbackInfo ci) {
		AtlasStatus.onStitchScheduled(this, sharedState.get(AtlasManager.PENDING_STITCH));
	}
}