package dev.processdetails;

import java.io.IOException;
import java.io.Reader;
import java.util.stream.Collectors;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;
import dev.processdetails.ProcessDetails;

/**
 * Keeps the GUI text render pipeline usable even while ShaderManager's shader
 * source cache is still empty.
 *
 * <p>Vanilla preloads only {@code pipeline/gui} and {@code pipeline/gui_textured}
 * from the vanilla pack ({@code GameRenderer#preloadUiShader}); the text
 * pipeline {@code pipeline/gui_text} compiles lazily on the first text draw.
 * Before {@link ShaderManager}'s first apply of a session its source cache is
 * empty, so that lazy compile fails with "Couldn't find source for ... shader",
 * the invalid pipeline stays cached in the device until the next pipeline-cache
 * clear, and every font draw silently no-ops afterwards ({@code
 * GlCommandEncoder#trySetup} returns false outside the IDE instead of throwing).
 * Precompiling gui_text here — mirroring preloadUiShader, but from the current
 * pack stack with a vanilla-pack fallback — keeps the pipeline valid from the
 * first overlay frame on, so the detail lines render as soon as fonts are
 * available instead of disappearing for the rest of the load.</p>
 */
public final class TextShaderGuard {
	private static boolean episodeLogged;
	private static boolean precompileSucceeded;

	private TextShaderGuard() {
	}

	public static void ensureTextPipeline() {
		GpuDevice device = RenderSystem.tryGetDevice();
		if (device == null) {
			return;
		}

		RenderPipeline guiText = RenderPipelines.GUI_TEXT;
		ShaderManager shaderManager = Minecraft.getInstance().getShaderManager();
		if (shaderManager.getShader(guiText.getVertexShader(), ShaderType.VERTEX) != null) {
			episodeLogged = false;
			precompileSucceeded = false;
			return;
		}

		if (precompileSucceeded) {
			return;
		}

		CompiledRenderPipeline compiled = device.precompilePipeline(guiText, shaderSource(Minecraft.getInstance()));
		if (compiled.isValid()) {
			precompileSucceeded = true;
			ProcessDetails.LOGGER.info(
					"Precompiled {} from the current pack stack because ShaderManager has no shader sources yet; overlay text will render once fonts are applied",
					guiText.getLocation());
			return;
		}

		if (!episodeLogged) {
			episodeLogged = true;
			ProcessDetails.LOGGER.warn(
					"{} is still invalid after precompiling; overlay text cannot render until the next shader reload. Shader diagnostics follow",
					guiText.getLocation());
			logShaderDiagnostics(Minecraft.getInstance());
		}
	}

	private static ShaderSource shaderSource(Minecraft minecraft) {
		ResourceManager resources = minecraft.getResourceManager();
		ResourceProvider vanilla = minecraft.getVanillaPackResources().asProvider();
		return (identifier, shaderType) -> {
			String source = read(resources, identifier, shaderType);
			return source != null ? source : read(vanilla, identifier, shaderType);
		};
	}

	private static String read(ResourceProvider provider, Identifier shaderId, ShaderType shaderType) {
		Resource resource = provider.getResource(shaderType.idConverter().idToFile(shaderId)).orElse(null);
		if (resource == null) {
			return null;
		}
		try (Reader reader = resource.openAsReader()) {
			return IOUtils.toString(reader);
		} catch (IOException failure) {
			ProcessDetails.LOGGER.warn("Failed to read shader source {} for precompilation", shaderId, failure);
			return null;
		}
	}

	private static void logShaderDiagnostics(Minecraft minecraft) {
		try {
			ResourceManager resources = minecraft.getResourceManager();
			String packs = resources.listPacks().map(PackResources::packId).collect(Collectors.joining(" > "));
			ProcessDetails.LOGGER.info("Shader diagnostics: effective packs, first has highest priority: {}", packs);
			logShaderFile(resources, "core/rendertype_text.vsh");
			logShaderFile(resources, "core/rendertype_text.fsh");
		} catch (Throwable failure) {
			ProcessDetails.LOGGER.error("Shader diagnostics failed", failure);
		}
	}

	private static void logShaderFile(ResourceManager resources, String path) {
		Resource resource = resources.getResource(Identifier.withDefaultNamespace("shaders/" + path)).orElse(null);
		if (resource == null) {
			ProcessDetails.LOGGER.info("Shader diagnostics: shaders/{} is not present in the current pack stack", path);
		} else {
			ProcessDetails.LOGGER.info("Shader diagnostics: shaders/{} is served by pack '{}'", path, resource.sourcePackId());
		}
	}
}
