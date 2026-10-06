package dev.processdetails.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Enables the Xaero mixins only when Xaero's World Map is installed, so the rest
 * of the mod (resource reload details, world save details) keeps working without it.
 */
public class ProcessDetailsMixinPlugin implements IMixinConfigPlugin {
	private static final String XAERO_MIXIN_PACKAGE = "dev.processdetails.mixin.xaero.";

	private boolean xaeroPresent;

	@Override
	public void onLoad(String mixinPackage) {
		this.xaeroPresent = FabricLoader.getInstance().isModLoaded("xaeroworldmap");
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.startsWith(XAERO_MIXIN_PACKAGE)) {
			return this.xaeroPresent;
		}
		return true;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
