package de.baspla.crouchgrow.commands;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class CrouchGrowInfo extends AbstractCommand {

    private static final Set<UUID> INFO_ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();

    public CrouchGrowInfo(String name, String description) {
        super(name, description);
        requirePermission("crouchgrow.info");
    }

    public static boolean isInfoEnabled(@Nonnull UUID playerUuid) {
        return INFO_ENABLED_PLAYERS.contains(playerUuid);
    }

    public static boolean toggleInfo(@Nonnull UUID playerUuid) {
        if (!INFO_ENABLED_PLAYERS.add(playerUuid)) {
            INFO_ENABLED_PLAYERS.remove(playerUuid);
            return false;
        }

        return true;
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("/crouchgrowinfo can only be used by a player."));
            return CompletableFuture.completedFuture(null);
        }

        PlayerRef playerRef = context.senderAs(PlayerRef.class);
        boolean enabled = toggleInfo(playerRef.getUuid());

        context.sendMessage(Message.raw(enabled
                ? "CrouchGrow info messages enabled."
                : "CrouchGrow info messages disabled."));
        return CompletableFuture.completedFuture(null);
    }

}