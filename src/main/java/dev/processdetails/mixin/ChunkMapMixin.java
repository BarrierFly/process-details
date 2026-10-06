package dev.processdetails.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.processdetails.worldsave.WorldSaveTracker;

@Mixin(ChunkMap.class)
public class ChunkMapMixin {
	@Shadow
	@Final
	private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

	@Inject(method = "saveAllChunks", at = @At("HEAD"))
	private void processdetails$setChunkTotal(boolean flush, CallbackInfo ci) {
		// The flush path (quit save) saves every loaded chunk, so its holder
		// count is the total we can measure per-chunk writes against.
		if (flush) {
			WorldSaveTracker.setChunkTotal(this.visibleChunkMap.size());
		}
	}

	@Inject(method = "save", at = @At("RETURN"))
	private void processdetails$countChunkSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
		WorldSaveTracker.incrementChunk();
	}
}
