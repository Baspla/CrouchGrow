package de.baspla.crouchgrow.systems;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.builtin.adventure.farming.states.FarmingBlock;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.HashUtil;
import com.hypixel.hytale.protocol.Rangef;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.FarmingStageData;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.farming.GrowthModifierAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import de.baspla.crouchgrow.commands.CrouchGrowInfo;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;

public class CrouchDetectionSystem extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, MovementStatesComponent> movementComponentType = MovementStatesComponent.getComponentType();
    private final ComponentType<EntityStore, TransformComponent> transformComponentType = TransformComponent.getComponentType();

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final int growthRadius;
    private final int secondsAddedPerCrouch;
    private final int maxCropsAffectedPerCrouch;
    private final boolean showPlayerParticle;
    private final boolean showPlantParticle;

    private final Int2BooleanMap wasCrouching = new Int2BooleanOpenHashMap();

    private static volatile Constructor<CommandBuffer<?>> chunkCommandBufferConstructor;

    public CrouchDetectionSystem(int growthRadius, int secondsAddedPerCrouch, int maxCropsAffectedPerCrouch, boolean showPlayerParticle, boolean showPlantParticle) {
        this.growthRadius = Math.max(0, growthRadius);
        this.secondsAddedPerCrouch = Math.max(0, secondsAddedPerCrouch);
        this.maxCropsAffectedPerCrouch = Math.max(0, maxCropsAffectedPerCrouch);
        this.showPlayerParticle = showPlayerParticle;
        this.showPlantParticle = showPlantParticle;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        MovementStatesComponent movement = chunk.getComponent(index, movementComponentType);
        TransformComponent transform = chunk.getComponent(index, transformComponentType);
        UUIDComponent uuidComponent = chunk.getComponent(index, UUIDComponent.getComponentType());

        assert movement != null;
        assert transform != null;
        assert uuidComponent != null;

        World world = store.getExternalData().getWorld();

        if (!world.isAlive()) {
            LOGGER.atWarning().log("World is null or not alive for player UUID: " + uuidComponent.getUuid());
            return;
        }

        boolean isCrouching = movement.getMovementStates().crouching;
        boolean previousState = wasCrouching.getOrDefault(index, false);

        if (isCrouching && !previousState) {
            handlePlayerCrouch(uuidComponent.getUuid(), transform, store, commandBuffer);
        }
        wasCrouching.put(index, isCrouching);
    }

    private void handlePlayerCrouch(@Nonnull UUID playerUuid, @Nonnull TransformComponent transformComponent, @Nonnull Store<EntityStore> entityStore, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        World world = entityStore.getExternalData().getWorld();
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        WorldTimeResource timeResource = entityStore.getResource(WorldTimeResource.getResourceType());
        CommandBuffer<ChunkStore> chunkCommandBuffer = createChunkCommandBuffer(chunkStore);

        Instant now = timeResource.getGameTime();
        Vector3d playerPos = transformComponent.getPosition();

        double growthRadiusSquared = (double) growthRadius * growthRadius;
        if (maxCropsAffectedPerCrouch <= 0) {
            return;
        }

        List<CropCandidate> candidates = new ArrayList<>();

        int minChunkX = ChunkUtil.chunkCoordinate(playerPos.x - growthRadius);
        int maxChunkX = ChunkUtil.chunkCoordinate(playerPos.x + growthRadius);
        int minChunkZ = ChunkUtil.chunkCoordinate(playerPos.z - growthRadius);
        int maxChunkZ = ChunkUtil.chunkCoordinate(playerPos.z + growthRadius);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
                Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);

                if (chunkRef == null || !chunkRef.isValid()) {
                    continue;
                }

                BlockComponentChunk blockCompChunk = chunkStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
                if (blockCompChunk == null) {
                    continue;
                }

                Int2ObjectMap<BlockSection> sectionCache = new Int2ObjectOpenHashMap<>();
                Int2ObjectMap<Ref<ChunkStore>> sectionRefCache = new Int2ObjectOpenHashMap<>();

                for (Int2ReferenceMap.Entry<Ref<ChunkStore>> entityEntry : blockCompChunk.getEntityReferences().int2ReferenceEntrySet()) {
                    Ref<ChunkStore> blockEntityRef = entityEntry.getValue();
                    if (blockEntityRef == null || !blockEntityRef.isValid()) {
                        continue;
                    }

                    FarmingBlock farmingBlock = chunkStore.getComponent(blockEntityRef, FarmingBlock.getComponentType());
                    if (farmingBlock == null) {
                        continue;
                    }

                    int blockIndex = entityEntry.getIntKey();
                    int localX = ChunkUtil.xFromBlockInColumn(blockIndex);
                    int bY = ChunkUtil.yFromBlockInColumn(blockIndex);
                    int localZ = ChunkUtil.zFromBlockInColumn(blockIndex);

                    int bX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
                    int bZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);

                    double dx = (bX + 0.5) - playerPos.x;
                    double dy = (bY + 0.5) - playerPos.y;
                    double dz = (bZ + 0.5) - playerPos.z;
                    if (dx * dx + dy * dy + dz * dz > growthRadiusSquared) {
                        continue;
                    }

                    int sectionY = ChunkUtil.chunkCoordinate(bY);
                    BlockSection blockSection = sectionCache.get(sectionY);

                    if (blockSection == null) {
                        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReference(chunkX, sectionY, chunkZ);
                        if (sectionRef == null || !sectionRef.isValid()) {
                            continue;
                        }

                        blockSection = chunkStore.getComponent(sectionRef, BlockSection.getComponentType());
                        if (blockSection == null) {
                            continue;
                        }

                        sectionCache.put(sectionY, blockSection);
                        sectionRefCache.put(sectionY, sectionRef);
                    }

                    Ref<ChunkStore> sectionRef = sectionRefCache.get(sectionY);
                    if (sectionRef == null || !sectionRef.isValid()) {
                        continue;
                    }

                    int blockId = blockSection.get(bX, bY, bZ);
                    BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
                    if (blockType == null || blockType.getFarming() == null) {
                        continue;
                    }

                    candidates.add(new CropCandidate(playerUuid, farmingBlock, blockType.getFarming(), blockSection, sectionRef, blockEntityRef, bX, bY, bZ));
                }
            }
        }

        Collections.shuffle(candidates);

        int affectedCrops = Math.min(maxCropsAffectedPerCrouch, candidates.size());
        boolean boostedAnyCrop = false;
        for (int i = 0; i < affectedCrops; i++) {
            CropCandidate candidate = candidates.get(i);
            boolean boostedCrop = applyCalculatedGrowth(
                candidate.playerUuid,
                candidate.farmingBlock,
                candidate.farmingData,
                candidate.sectionRef,
                candidate.blockRef,
                candidate.x,
                candidate.y,
                candidate.z,
                now,
                commandBuffer,
                chunkCommandBuffer
            );
            if (boostedCrop) {
                boostedAnyCrop = true;
                candidate.blockSection.scheduleTick(ChunkUtil.indexBlock(candidate.x, candidate.y, candidate.z), now);
            }
        }

        if (boostedAnyCrop && showPlayerParticle) {
            ParticleUtil.spawnParticleEffect("Player_Crouched_Grow", new Vector3d(playerPos.x, playerPos.y+0.1, playerPos.z), commandBuffer);
        }
    }

    private boolean applyCalculatedGrowth(
        @Nonnull UUID playerUuid,
        FarmingBlock farmingBlock,
        FarmingData farmingConfig,
        Ref<ChunkStore> sectionRef,
        Ref<ChunkStore> blockRef,
        int x,
        int y,
        int z,
        Instant now,
        CommandBuffer<EntityStore> commandBuffer,
        CommandBuffer<ChunkStore> chunkCommandBuffer
    ) {
        float currentProgress = farmingBlock.getGrowthProgress();
        int currentStageIndex = (int) currentProgress;
        String stageSet = farmingBlock.getCurrentStageSet();

        Map<String, FarmingStageData[]> stagesBySet = farmingConfig.getStages();
        if (stagesBySet == null) return false;

        FarmingStageData[] stages = stagesBySet.get(stageSet);
        if (stages == null || currentStageIndex >= stages.length) return false; 
        if (currentStageIndex < 0) return false; 
        
        FarmingStageData currentStage = stages[currentStageIndex];
        Rangef durationRange = currentStage.getDuration();

        if (durationRange == null) return false;

        double rand = HashUtil.random(farmingBlock.getGeneration(), x, y, z);
        double totalDurationSeconds = durationRange.min + (durationRange.max - durationRange.min) * rand;

        if (totalDurationSeconds <= 0.1) totalDurationSeconds = 1.0;

        double growthMultiplier = calculateGrowthMultiplier(farmingConfig, chunkCommandBuffer, sectionRef, blockRef, x, y, z);
        if (growthMultiplier <= 0.0) {
            growthMultiplier = 1.0;
        }

        double effectiveDurationSeconds = totalDurationSeconds / growthMultiplier;
        if (effectiveDurationSeconds <= 0.1) {
            effectiveDurationSeconds = 1.0;
        }

        double progressDelta = secondsAddedPerCrouch / effectiveDurationSeconds;

        float newProgress = (float) (currentProgress + progressDelta);
        int oldStageIndex = (int) currentProgress;
        int newStageIndex = (int) newProgress;
        
        if (newStageIndex == oldStageIndex) {
            // Safe addition: progress remains within the current stage bounds
            farmingBlock.setGrowthProgress(newProgress);

            // Calculate the time elapsed in the current stage based on the stage fraction
            double stageFraction = currentProgress - oldStageIndex;
            double baseElapsedTimeSeconds = stageFraction * totalDurationSeconds;
            double effectiveElapsedTimeSeconds = stageFraction * effectiveDurationSeconds;
            double addedBaseTimeSeconds = secondsAddedPerCrouch * growthMultiplier;

            emitAnalysisMessage(
                    playerUuid,
                    String.format(
                            "Crop Analysis [%d, %d, %d] | Base: %s / %s | Multiplier of %.2fx: %s / %s | Added %s Multiplied so %s Base time | Thats a change of +%.2f%% from %.2f%% to %.2f%%",
                            x, y, z,
                            formatDuration(baseElapsedTimeSeconds),
                            formatDuration(totalDurationSeconds),
                            growthMultiplier,
                            formatDuration(effectiveElapsedTimeSeconds),
                            formatDuration(effectiveDurationSeconds),
                            formatDuration(secondsAddedPerCrouch),
                            formatDuration(addedBaseTimeSeconds),
                            progressDelta * 100,
                            currentProgress * 100,
                            newProgress * 100
                    )
            );

            // Trigger the visual effect
            if (showPlantParticle) {
                ParticleUtil.spawnParticleEffect("Plant_Crouched", new Vector3d(x + 0.5, y, z + 0.5), commandBuffer);
            }
            farmingBlock.setLastTickGameTime(now);
            return true;
            
        } else {
            // Boundary overflow: clamp progress right at the edge (99% of current stage)
            // This allows the next natural game tick to smoothly transition the crop
            float clampedProgress = (float) oldStageIndex + 0.99f;
            farmingBlock.setGrowthProgress(clampedProgress);

                emitAnalysisMessage(
                    playerUuid,
                    String.format(
                        "Crop at [%d, %d, %d] reached stage boundary. Clamped to %.2f%% to preserve native transition.",
                        x, y, z,
                        clampedProgress * 100
                    )
                );
                farmingBlock.setLastTickGameTime(now);
                return true;
        }

    }

    private double calculateGrowthMultiplier(
        FarmingData farmingConfig,
        CommandBuffer<ChunkStore> chunkCommandBuffer,
        Ref<ChunkStore> sectionRef,
        Ref<ChunkStore> blockRef,
        int x,
        int y,
        int z
    ) {
        double growthMultiplier = 1.0;
        if (farmingConfig.getGrowthModifiers() == null || farmingConfig.getGrowthModifiers().length == 0) {
            return growthMultiplier;
        }

        for (String modifierName : farmingConfig.getGrowthModifiers()) {
            GrowthModifierAsset modifierAsset = GrowthModifierAsset.getAssetMap().getAsset(modifierName);
            if (modifierAsset == null) {
                continue;
            }

            if (chunkCommandBuffer != null) {
                growthMultiplier *= modifierAsset.getCurrentGrowthMultiplier(chunkCommandBuffer, sectionRef, blockRef, x, y, z, false);
            } else {
                // Fallback to static modifier if a chunk command buffer cannot be created.
                growthMultiplier *= modifierAsset.getModifier();
            }
        }

        return growthMultiplier;
    }

    @SuppressWarnings("unchecked")
    private CommandBuffer<ChunkStore> createChunkCommandBuffer(Store<ChunkStore> chunkStore) {
        try {
            Constructor<CommandBuffer<?>> ctor = chunkCommandBufferConstructor;
            if (ctor == null) {
                ctor = (Constructor<CommandBuffer<?>>) (Constructor<?>) CommandBuffer.class.getDeclaredConstructor(Store.class);
                ctor.setAccessible(true);
                chunkCommandBufferConstructor = ctor;
            }

            return (CommandBuffer<ChunkStore>) ctor.newInstance(chunkStore);
        } catch (ReflectiveOperationException | SecurityException ex) {
            LOGGER.atWarning().log("Failed to create chunk CommandBuffer for growth modifiers: " + ex.getMessage());
            return null;
        }
    }

    private void emitAnalysisMessage(@Nonnull UUID playerUuid, String message) {
        if (CrouchGrowInfo.isInfoEnabled(playerUuid)) {
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef != null) {
                playerRef.sendMessage(Message.raw(message));
            }
        }
    }

    private String formatDuration(double totalSeconds) {
        // Formats into a human-readable string, e.g. "05h 30m 45s"
        int hours = (int) (totalSeconds / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int seconds = (int) (totalSeconds % 60);
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(String.format("%02dh ", hours));
        if (minutes > 0 || hours > 0) sb.append(String.format("%02dm ", minutes));
        sb.append(String.format("%02ds", seconds));
        return sb.toString().trim();

    }

    private static final class CropCandidate {
        private final UUID playerUuid;
        private final FarmingBlock farmingBlock;
        private final FarmingData farmingData;
        private final BlockSection blockSection;
        private final Ref<ChunkStore> sectionRef;
        private final Ref<ChunkStore> blockRef;
        private final int x;
        private final int y;
        private final int z;

        private CropCandidate(
            UUID playerUuid,
            FarmingBlock farmingBlock,
            FarmingData farmingData,
            BlockSection blockSection,
            Ref<ChunkStore> sectionRef,
            Ref<ChunkStore> blockRef,
            int x,
            int y,
            int z
        ) {
            this.playerUuid = playerUuid;
            this.farmingBlock = farmingBlock;
            this.farmingData = farmingData;
            this.blockSection = blockSection;
            this.sectionRef = sectionRef;
            this.blockRef = blockRef;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType(), movementComponentType, transformComponentType);
    }
}