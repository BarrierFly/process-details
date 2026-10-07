package dev.processdetails.reload;

import java.util.Locale;

import net.minecraft.network.chat.Component;

/**
 * Readable display names for the three vanilla reloaders that Fabric marks as
 * private (the "minecraft:private/..." ids). In production their simple class
 * names are intermediary names like class_5407; in dev they are lowercase
 * mojmap names like gpuwarnlistmanager.
 */
public final class PrivateReloaderNames {
	private PrivateReloaderNames() {
	}

	public static Component displayName(String idPath) {
		if (idPath == null) {
			return Component.literal("?");
		}
		String path = idPath.toLowerCase(Locale.ROOT);
		if (path.equals("private/class_5407") || path.equals("private/gpuwarnlistmanager")) {
			return Component.translatable("process-details.reloader.gpuwarnlist");
		}
		if (path.equals("private/class_761") || path.equals("private/levelrenderer")) {
			return Component.translatable("process-details.reloader.levelrenderer");
		}
		if (path.equals("private/class_6877") || path.equals("private/periodicnotificationmanager")) {
			return Component.translatable("process-details.reloader.regionalcompliancies");
		}
		return Component.literal(idPath);
	}

	/** Splits Fabric's "namespace/path (SimpleClassName)" getName() form. */
	public static Component shorten(Component name) {
		String text = name.getString();
		int paren = text.indexOf(" (");
		String id = paren > 0 ? text.substring(0, paren) : text;
		int slash = id.indexOf('/');
		String path = slash >= 0 ? id.substring(slash + 1) : id;
		String namespace = slash >= 0 ? id.substring(0, slash) : "minecraft";
		if (namespace.equals("minecraft") && path.startsWith("private/")) {
			return displayName(path);
		}
		return Component.literal(id);
	}
}
