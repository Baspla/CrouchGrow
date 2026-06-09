package de.baspla.crouchgrow.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class CrouchGrowConfig {

    public static final BuilderCodec<CrouchGrowConfig> CODEC = BuilderCodec.builder(CrouchGrowConfig.class, CrouchGrowConfig::new)
            .append(
                new KeyedCodec<>("EnableCrouchGrowth", Codec.BOOLEAN),
                (exConfig, aBoolean, extraInfo) -> exConfig.enabledCrouchGrowth = aBoolean,
                (exConfig, extraInfo) -> exConfig.enabledCrouchGrowth
            )
            .add()
            .append(
                new KeyedCodec<>("GrowthRadius", Codec.INTEGER),
                (exConfig, growthRadius, extraInfo) -> exConfig.growthRadius = growthRadius,
                (exConfig, extraInfo) -> exConfig.growthRadius
            )
            .add()
            .append(
                new KeyedCodec<>("SecondsAddedPerCrouch", Codec.INTEGER),
                (exConfig, secondsAddedPerCrouch, extraInfo) -> exConfig.secondsAddedPerCrouch = secondsAddedPerCrouch,
                (exConfig, extraInfo) -> exConfig.secondsAddedPerCrouch
            )
            .add()
            .append(
                new KeyedCodec<>("MaxCropsAffectedPerCrouch", Codec.INTEGER),
                (exConfig, maxCropsAffectedPerCrouch, extraInfo) -> exConfig.maxCropsAffectedPerCrouch = maxCropsAffectedPerCrouch,
                (exConfig, extraInfo) -> exConfig.maxCropsAffectedPerCrouch
            )
            .add()
            .append(
                new KeyedCodec<>("ShowPlayerParticle", Codec.BOOLEAN),
                (exConfig, showPlayerParticle, extraInfo) -> exConfig.showPlayerParticle = showPlayerParticle,
                (exConfig, extraInfo) -> exConfig.showPlayerParticle
            )
            .add()
            .append(
                new KeyedCodec<>("ShowPlantParticle", Codec.BOOLEAN),
                (exConfig, showPlantParticle, extraInfo) -> exConfig.showPlantParticle = showPlantParticle,
                (exConfig, extraInfo) -> exConfig.showPlantParticle
            )
            .add()
            .build();

    private boolean enabledCrouchGrowth = true;
    private int growthRadius = 5;
    private int secondsAddedPerCrouch = 30;
    private int maxCropsAffectedPerCrouch = 16;
    private boolean showPlayerParticle = true;
    private boolean showPlantParticle = true;

    private CrouchGrowConfig() {}

    public boolean isEnabledCrouchGrowth() {
        return enabledCrouchGrowth;
    }

    public int getGrowthRadius() {
        return growthRadius;
    }

    public int getSecondsAddedPerCrouch() {
        return secondsAddedPerCrouch;
    }

    public int getMaxCropsAffectedPerCrouch() {
        return maxCropsAffectedPerCrouch;
    }

    public boolean isShowPlayerParticle() {
        return showPlayerParticle;
    }

    public boolean isShowPlantParticle() {
        return showPlantParticle;
    }
}
