package com.smd.toomanytinkers;

import com.smd.toomanytinkers.proxy.IProxy;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION)
public class TooManyTinkers {

    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @SidedProxy(modId = Reference.MOD_ID,
                clientSide = "proxy.com.smd.toomanytinkers.ClientProxy",
                serverSide = "proxy.com.smd.toomanytinkers.CommonProxy")
    public static IProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Hello From {}!", Reference.MOD_NAME);
        LOGGER.info("Proxy is {}", proxy);
        LOGGER.info("Language: {}", Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage());
    }

}
