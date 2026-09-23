/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.transport.plug;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import buildcraft.api.transport.IWireEmitter;
import buildcraft.api.transport.pipe.IPipeHolder;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.api.transport.pluggable.PluggableDefinition;

import buildcraft.transport.container.ContainerGate;
import buildcraft.transport.gate.GateVariant;

import buildcraft.BCTransportRegistries;

/**
 * Port of 1.12.2's {@code buildcraft.silicon.plug.PluggableGate}: the pluggable half of a gate, wrapping
 * {@link buildcraft.transport.gate.GateLogic} and forwarding this batch's real pipe-face contract
 * ({@code isBlocking}, {@code onTick}, {@code onPluggableActivate}) onto it.
 *
 * <p><b>Scope cuts</b> from the 1.12.2 original: the client-model variable machinery
 * ({@code MODEL_FUNC_CTX_*}/{@code clientModelData}) belongs to the rendering rewrite, out of scope the same way
 * {@code getModelRenderKey} is for every other pluggable in this batch; {@code ItemGateCopier} interaction
 * ({@code interactWithCopier}) and advancement unlocking are both dropped as orthogonal polish. Opening the GUI
 * goes through this port's own {@code Player#openMenu(MenuProvider)}/{@link AbstractContainerMenu} plumbing
 * ({@code BCSiliconGuis.GATE.openGui(...)} has no equivalent on this target) rather than 1.12.2's own GUI
 * handler id.
 */
public class PluggableGate extends PipePluggable implements IWireEmitter, MenuProvider {

    public final buildcraft.transport.gate.GateLogic logic;

    public PluggableGate(PluggableDefinition def, IPipeHolder holder, Direction side, GateVariant variant) {
        super(def, holder, side);
        logic = new buildcraft.transport.gate.GateLogic(this, variant);
    }

    public PluggableGate(
        PluggableDefinition def, IPipeHolder holder, Direction side, CompoundTag nbt, HolderLookup.Provider registries
    ) {
        super(def, holder, side);
        logic = new buildcraft.transport.gate.GateLogic(this, nbt.getCompoundOrEmpty("data"), registries);
    }

    @Override
    public CompoundTag writeToNbt(HolderLookup.Provider registries) {
        CompoundTag nbt = super.writeToNbt(registries);
        nbt.put("data", logic.writeToNbt(registries));
        return nbt;
    }

    // PipePluggable

    @Override
    public AABB getBoundingBox() {
        // A thin slab centred on the face -- 1.12.2's own gate bounding box, per-side.
        double min = 5 / 16.0, max = 11 / 16.0;
        return switch (side) {
            case DOWN -> new AABB(min, 2 / 16.0, min, max, 4 / 16.0, max);
            case UP -> new AABB(min, 12 / 16.0, min, max, 14 / 16.0, max);
            case NORTH -> new AABB(min, min, 2 / 16.0, max, max, 4 / 16.0);
            case SOUTH -> new AABB(min, min, 12 / 16.0, max, max, 14 / 16.0);
            case WEST -> new AABB(2 / 16.0, min, min, 4 / 16.0, max, max);
            case EAST -> new AABB(12 / 16.0, min, min, 14 / 16.0, max, max);
        };
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public ItemStack getPickStack() {
        return BCTransportRegistries.ITEM_PLUGGABLE_GATE.get().getStack(logic.variant);
    }

    @Override
    public boolean onPluggableActivate(Player player, BlockHitResult trace) {
        if (!player.level().isClientSide()) {
            if (interactWithCopier(player, player.getMainHandItem()) || interactWithCopier(player, player.getOffhandItem())) {
                return true;
            }
            player.openMenu(this);
        }
        return true;
    }

    /**
     * Port of 1.12.2's own {@code PluggableGate#interactWithCopier}, moved here from
     * {@code ItemGateCopier}/{@code PluggableGate} in 1.12.2 to match this port's own
     * {@code PipePluggable#onPluggableActivate} dispatch. Right-clicking a gate with an empty
     * {@link buildcraft.transport.item.ItemGateCopier} copies this gate's trigger/action/connection state onto
     * the item (stripping {@code wireBroadcasts} -- transient per-tick state, not configuration); right-clicking
     * with a loaded one pastes that state back through {@link buildcraft.transport.gate.GateLogic#readConfigData}
     * -- the same NBT shape {@link buildcraft.transport.gate.GateLogic#writeToNbt} already uses for save/load, no
     * new format invented. Per-target compatibility warnings (slot count, logic type, parameter count) that
     * 1.12.2 surfaced via chat are not reproduced -- {@code readConfigData} already tolerates a mismatched
     * variant by construction (extra/missing NBT indices are simply ignored/left unset), so nothing breaks,
     * only the warning text is missing.
     */
    private boolean interactWithCopier(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof buildcraft.transport.item.ItemGateCopier)) {
            return false;
        }
        var registries = player.level().registryAccess();
        CompoundTag stored = buildcraft.transport.item.ItemGateCopier.getCopiedGateData(stack);
        if (stored != null) {
            logic.readConfigData(stored, registries);
            player.sendOverlayMessage(Component.translatable("chat.gateCopier.gatePasted"));
        } else {
            stored = logic.writeToNbt(registries);
            stored.remove("wireBroadcasts");
            if (stored.size() == 1) {
                player.sendOverlayMessage(Component.translatable("chat.gateCopier.noInformation"));
                return false;
            }
            buildcraft.transport.item.ItemGateCopier.setCopiedGateData(stack, stored);
            player.sendOverlayMessage(Component.translatable("chat.gateCopier.gateCopied"));
        }
        return true;
    }

    @Override
    public boolean isEmitting(DyeColor colour) {
        return logic.isEmitting(colour);
    }

    @Override
    public void emitWire(DyeColor colour) {
        logic.emitWire(colour);
    }

    @Override
    public void onTick() {
        logic.onTick();
    }

    @Override
    public boolean canConnectToRedstone(@Nullable Direction to) {
        return true;
    }

    // MenuProvider

    @Override
    public Component getDisplayName() {
        return Component.translatable("buildcraft.gui.gate.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv, Player player) {
        return new ContainerGate(BCTransportRegistries.GATE_MENU.get(), windowId, playerInv, this);
    }

    /** Written by {@code Player#openMenu}; read back by {@link ContainerGate}'s own client-side factory. */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(holder.getPipePos());
        buffer.writeByte(side.get3DDataValue());
    }
}
