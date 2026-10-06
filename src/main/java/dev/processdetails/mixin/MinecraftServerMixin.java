package dev.processdetails.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.processdetails.worldsave.WorldSaveTracker;

/**
 * Marks the save session that overlaps with the client's "Saving world" screen:
 * {@code stopServer} only runs when the integrated server is being shut down
 * ( quitting a singleplayer world), not on autosaves.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Shadow
	public abstract Iterable<ServerLevel> getAllLevels();

	@Inject(method = "stopServer", at = @At("HEAD"))
	private void processdetails$beginSaveSession(CallbackInfo ci) {
		int dimensions = 0;
		for (ServerLevel level : this.getAllLevels()) {
			dimensions++;
		}
		WorldSaveTracker.begin(dimensions);
	}

	@Inject(method = "stopServer", at = @At("RETURN"))
	private void processdetails$finishSaveSession(CallbackInfo ci) {
		WorldSaveTracker.finish();
	}
}
