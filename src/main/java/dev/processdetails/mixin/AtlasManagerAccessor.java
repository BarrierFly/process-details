package dev.processdetails.mixin;

import java.util.Map;

import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the atlas id set of {@link AtlasManager} so the detail lines can
 * enumerate the atlases of the live reload. The value type
 * ({@code AtlasManager.AtlasEntry}) is package private, so only the map keys
 * are ever used and the accessor keeps the wildcard out of the mixin source.
 */
@Mixin(AtlasManager.class)
public interface AtlasManagerAccessor {
	@Accessor("atlasById")
	Map<Identifier, ?> processdetails$getAtlasById();
}