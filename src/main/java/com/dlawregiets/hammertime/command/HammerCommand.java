package com.dlawregiets.hammertime.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.dlawregiets.hammertime.config.HammerConfig;
import com.dlawregiets.hammertime.config.PlayerSettings;
import com.dlawregiets.hammertime.config.PlayerSettingsStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/** Registers the {@code /hammer} command tree. */
public final class HammerCommand {
    private HammerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                HammerConfig config,
                                PlayerSettingsStore store,
                                Runnable reloadAction) {
        dispatcher.register(Commands.literal("hammer")
                .then(Commands.literal("size")
                        .then(Commands.argument("size", IntegerArgumentType.integer(1))
                                .executes(ctx -> setSize(ctx.getSource(),
                                        ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "size"), config, store))))
                .then(Commands.literal("on")
                        .executes(ctx -> setEnabled(ctx.getSource(),
                                ctx.getSource().getPlayerOrException(), true, store)))
                .then(Commands.literal("off")
                        .executes(ctx -> setEnabled(ctx.getSource(),
                                ctx.getSource().getPlayerOrException(), false, store)))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(),
                                ctx.getSource().getPlayerOrException(), store)))
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            reloadAction.run();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("HammerTime config reloaded"), true);
                            return 1;
                        }))
                .then(Commands.argument("player", EntityArgument.player())
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("size")
                                .then(Commands.argument("size", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setSize(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                config, store))))));
    }

    private static int setSize(CommandSourceStack source, ServerPlayer target, int size,
                               HammerConfig config, PlayerSettingsStore store) {
        if (!config.isAllowedSize(size)) {
            source.sendFailure(Component.literal(
                    "Size " + size + " is not allowed. Allowed sizes: " + Arrays.toString(config.allowedSizes)));
            return 0;
        }
        PlayerSettings current = store.get(target.getUUID());
        store.set(target.getUUID(), new PlayerSettings(size, current.enabled()));
        source.sendSuccess(() -> Component.literal(
                "Hammer size set to " + size + "x" + size + "x" + size
                        + " for " + target.getName().getString()), true);
        return 1;
    }

    private static int setEnabled(CommandSourceStack source, ServerPlayer target, boolean enabled,
                                  PlayerSettingsStore store) {
        PlayerSettings current = store.get(target.getUUID());
        store.set(target.getUUID(), new PlayerSettings(current.size(), enabled));
        source.sendSuccess(() -> Component.literal(
                "Hammer " + (enabled ? "enabled" : "disabled")
                        + " for " + target.getName().getString()), true);
        return 1;
    }

    private static int status(CommandSourceStack source, ServerPlayer target, PlayerSettingsStore store) {
        PlayerSettings settings = store.get(target.getUUID());
        source.sendSuccess(() -> Component.literal(
                "Hammer: " + (settings.enabled() ? "enabled" : "disabled")
                        + ", size " + settings.size() + "x" + settings.size() + "x" + settings.size()), false);
        return 1;
    }
}
