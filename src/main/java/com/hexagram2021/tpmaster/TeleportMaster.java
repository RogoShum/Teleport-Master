package com.hexagram2021.tpmaster;

import com.hexagram2021.tpmaster.server.TPMContent;
import com.hexagram2021.tpmaster.server.config.TPMServerConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(TeleportMaster.MODID)
public class TeleportMaster {
    public static final String MODID = "tpmaster";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TeleportMaster(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, TPMServerConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(TPMContent::registerCommands);
    }
}
