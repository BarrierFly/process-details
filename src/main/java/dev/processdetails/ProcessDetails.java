package dev.processdetails;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDetails implements ClientModInitializer {
	public static final String MOD_ID = "process-details";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		boolean xaero = FabricLoader.getInstance().isModLoaded("xaeroworldmap");
		LOGGER.info("Process Details initialized (Xaero's World Map: {})",
				xaero ? "present, detailed map loading screen enabled" : "absent, map loading features disabled");
	}
}
