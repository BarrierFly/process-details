package dev.processdetails.mixin.xaero;

import dev.processdetails.xaero.LoadingStatus;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.executor.Executor;
import xaero.map.file.worldsave.WorldDataReader;
import xaero.map.region.MapRegion;

@Mixin(WorldDataReader.class)
public class WorldDataReaderMixin {
	@Inject(method = "buildRegion", at = @At("HEAD"))
	private void pd$buildRegionHead(
			MapRegion region, ServerLevel serverWorld, HolderLookup<Block> blockLookup, Registry<Block> blockRegistry,
			Registry<Fluid> fluidRegistry, boolean loading, int[] chunkCountDest, Executor renderExecutor,
			CallbackInfoReturnable<Boolean> cir) {
		LoadingStatus.enter("buildRegion: reading world-save data", LoadingStatus.regionDetail(region));
	}

	@Inject(method = "buildRegion", at = @At("RETURN"))
	private void pd$buildRegionReturn(
			MapRegion region, ServerLevel serverWorld, HolderLookup<Block> blockLookup, Registry<Block> blockRegistry,
			Registry<Fluid> fluidRegistry, boolean loading, int[] chunkCountDest, Executor renderExecutor,
			CallbackInfoReturnable<Boolean> cir) {
		LoadingStatus.exit();
	}
}
