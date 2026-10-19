package com.dlawregiets.hammertime.mining;

import com.dlawregiets.hammertime.config.HammerConfig;
import com.dlawregiets.hammertime.config.PlayerSettings;
import com.dlawregiets.hammertime.config.PlayerSettingsStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Breaks a cube of identical blocks when a sneaking player breaks one. */
public final class CubeMiner {
    /** Guards against the extra breaks re-entering this handler. */
    private static final Set<UUID> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private final HammerConfig config;
    private final PlayerSettingsStore store;

    public CubeMiner(HammerConfig config, PlayerSettingsStore store) {
        this.config = config;
        this.store = store;
    }

    public void onBlockBroken(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (config.requireSneak && !player.isShiftKeyDown()) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        if (!canHarvest(player.getMainHandItem(), state)) {
            return;
        }

        PlayerSettings settings = store.get(player.getUUID());
        if (!settings.enabled()) {
            return;
        }

        if (!IN_PROGRESS.add(player.getUUID())) {
            return;
        }
        try {
            mine(level, player, pos, state, settings.size());
        } finally {
            IN_PROGRESS.remove(player.getUUID());
        }
    }

    /** True only if the stack is a tool whose rules say it is correct for this block. */
    private static boolean canHarvest(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null && tool.isCorrectForDrops(state);
    }

    private void mine(ServerLevel level, ServerPlayer player, BlockPos target, BlockState targetState, int size) {
        Vec3 look = player.getViewVector(1.0F);
        int[] forward = CubeGeometry.dominantForward(look.x, look.y, look.z);
        List<CubeGeometry.Offset> offsets = CubeGeometry.offsets(size, forward[0], forward[1], forward[2]);

        ItemStack toolSnapshot = player.getMainHandItem().copy();
        int broken = 0;
        for (CubeGeometry.Offset offset : offsets) {
            if (broken >= config.maxBlocksPerBreak) {
                break;
            }
            BlockPos candidate = target.offset(offset.x(), offset.y(), offset.z());
            if (candidate.equals(target) || level.isOutsideBuildHeight(candidate)) {
                continue;
            }
            BlockState candidateState = level.getBlockState(candidate);
            if (candidateState.isAir() || candidateState.getBlock() != targetState.getBlock()) {
                continue;
            }
            if (!canHarvest(player.getMainHandItem(), candidateState)) {
                break;
            }
            if (player.gameMode.destroyBlock(candidate)) {
                broken++;
            }
        }

        if (!config.durabilityPerBlock) {
            player.setItemInHand(InteractionHand.MAIN_HAND, toolSnapshot);
        }
    }
}
