package de.baspla.crouchgrow;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import de.baspla.crouchgrow.commands.CrouchGrowInfo;
import de.baspla.crouchgrow.config.CrouchGrowConfig;
import de.baspla.crouchgrow.systems.CrouchDetectionSystem;

public class CrouchGrowPlugin extends JavaPlugin {

    private static Config<CrouchGrowConfig> config = null;

    public CrouchGrowPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        config = this.withConfig("crouchgrow_config", CrouchGrowConfig.CODEC);
    }

    @Override
    protected void setup() {
        config.save();
        getCommandRegistry().registerCommand(new CrouchGrowInfo("crouchgrowinfo", "Toggle CrouchGrow crop analysis messages for yourself."));
        CrouchGrowConfig crouchGrowConfig = getConfig().get();
        if (crouchGrowConfig.isEnabledCrouchGrowth()) {
            getEntityStoreRegistry().registerSystem(new CrouchDetectionSystem(
                    crouchGrowConfig.getGrowthRadius(),
                    crouchGrowConfig.getSecondsAddedPerCrouch(),
                    crouchGrowConfig.getMaxCropsAffectedPerCrouch(),
                    crouchGrowConfig.isShowPlayerParticle(),
                    crouchGrowConfig.isShowPlantParticle()
            ));
        }
    }

    @Override
    protected void shutdown() {
        // Called before disable (in reverse load order)
        // Cleanup resources
        getLogger().atInfo().log("MyPlugin shutting down!");
    }

    public static Config<CrouchGrowConfig> getConfig() {
        return config;
    }
}