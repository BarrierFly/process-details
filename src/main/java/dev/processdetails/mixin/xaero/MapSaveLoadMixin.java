package dev.processdetails.mixin.xaero;

import dev.processdetails.xaero.LoadingStatus;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.biome.BiomeGetter;
import xaero.map.file.MapSaveLoad;
import xaero.map.file.RegionDetection;
import xaero.map.region.MapRegion;
import xaero.map.world.MapDimension;

@Mixin(MapSaveLoad.class)
public class MapSaveLoadMixin {
	@Inject(method = "detectRegions", at = @At("HEAD"))
	private void pd$detectRegionsHead(int attempts, CallbackInfo ci) {
		LoadingStatus.enter("detectRegions: scanning the map folder for region files");
	}

	@Inject(method = "detectRegions", at = @At("RETURN"))
	private void pd$detectRegionsReturn(int attempts, CallbackInfo ci) {
		LoadingStatus.exit();
	}

	@Inject(method = "detectRegionsFromFiles", at = @At("HEAD"))
	private void pd$detectRegionsFromFilesHead(
			MapDimension mapDimension, String worldId, String dimId, String mwId, Path folder, String regex,
			int xIndex, int zIndex, int emptySize, int attempts, Consumer<RegionDetection> detectionConsumer, CallbackInfo ci) {
		LoadingStatus.enter("detectRegionsFromFiles: listing files", folder == null ? "<null>" : folder.toString());
	}

	@Inject(method = "detectRegionsFromFiles", at = @At("RETURN"))
	private void pd$detectRegionsFromFilesReturn(
			MapDimension mapDimension, String worldId, String dimId, String mwId, Path folder, String regex,
			int xIndex, int zIndex, int emptySize, int attempts, Consumer<RegionDetection> detectionConsumer, CallbackInfo ci) {
		LoadingStatus.exit();
	}

	@Inject(method = "loadRegion", at = @At("HEAD"))
	private void pd$loadRegionHead(
			MapRegion region, HolderLookup<Block> blockLookup, Registry<Block> blockRegistry, Registry<Fluid> fluidRegistry,
			BiomeGetter biomeGetter, boolean debugConfig, int extraAttempts, CallbackInfoReturnable<Boolean> cir) {
		LoadingStatus.enter("loadRegion: reading map data", LoadingStatus.regionDetail(region));
	}

	@Inject(method = "loadRegion", at = @At("RETURN"))
	private void pd$loadRegionReturn(
			MapRegion region, HolderLookup<Block> blockLookup, Registry<Block> blockRegistry, Registry<Fluid> fluidRegistry,
			BiomeGetter biomeGetter, boolean debugConfig, int extraAttempts, CallbackInfoReturnable<Boolean> cir) {
		LoadingStatus.exit();
	}

	@Inject(method = "saveRegion", at = @At("HEAD"))
	private void pd$saveRegionHead(MapRegion region, boolean debugConfig, int extraAttempts, CallbackInfoReturnable<Boolean> cir) {
		LoadingStatus.enter("saveRegion: writing map data", LoadingStatus.regionDetail(region));
	}

	@Inject(method = "saveRegion", at = @At("RETURN"))
	private void pd$saveRegionReturn(MapRegion region, boolean debugConfig, int extraAttempts, CallbackInfoReturnable<Boolean> cir) {
		LoadingStatus.exit();
	}
}
