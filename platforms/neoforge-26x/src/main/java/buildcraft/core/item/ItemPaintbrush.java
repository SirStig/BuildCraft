/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import buildcraft.api.blocks.CustomPaintHelper;

import buildcraft.lib.misc.ColourUtil;
import buildcraft.lib.misc.SoundUtil;

/**
 * 1.12.2's {@code ItemPaintbrush_BC8} was a single item with 17 metadata sub-items (one colourless "clean" plus
 * one per {@code EnumDyeColor}), its own {@code usesLeft}/{@code colour} pair packed into a "damage" NBT byte and
 * the metadata value, and hand-rolled {@code getDamage}/{@code setDamage}/{@code isDamaged}/
 * {@code showDurabilityBar}/{@code getDurabilityForDisplay} overrides that simulated a vanilla durability bar on
 * top of that metadata scheme -- metadata was the only way 1.12.2 had to give "the same item, several looks".
 * Modern Minecraft has no block/item metadata at all (see PORTING.md's structural-changes list) but does have a
 * real, stable, pre-data-component vanilla durability system, confirmed via {@code javap} against the merged jar:
 * {@code ItemStack#isDamageableItem()}/{@code getDamageValue()}/{@code setDamageValue(int)}/{@code getMaxDamage()}
 * are unchanged API on this target, and durability itself predates data components entirely (this target has no
 * {@code net.minecraft.core.component.DataComponentType} at all -- not relevant, this is 26.x). This therefore
 * becomes 17 separate registry entries -- following {@link buildcraft.core.block.BlockDecoration}'s own
 * precedent for the same "several looks" situation, just items instead of blocks -- each a plain
 * {@code Item.Properties().durability(MAX_USES).stacksTo(1)} damageable tool, needing none of 1.12.2's own
 * hand-rolled durability overrides at all.
 *
 * <p><b>The colourless variant is a real, distinct tool, not a placeholder.</b> 1.12.2's own guard,
 * {@code if (colour != null && usesLeft <= 0) return false;}, short-circuits false and is skipped entirely when
 * {@code colour == null} -- the colourless brush was never blocked by its own uses-left count. That guard is
 * reproduced faithfully in {@link #useOn(UseOnContext)} below. Calling
 * {@link CustomPaintHelper#attemptPaintBlock} with a {@code null} paint colour clears an existing paint back to
 * the plain block on this port's own already-ported painting infrastructure -- but only through a block-specific
 * {@code ICustomPaintHandler} that itself implements clearing; {@code CustomPaintHelper}'s own generic
 * {@code defaultAttemptPaint} fallback returns {@code FAIL} immediately for {@code paint == null} ("Clearing
 * paint has no generic form: there is no way to know which colour is the 'plain' one"). No such handler is
 * registered for any plain vanilla block yet, so the colourless brush's "clear paint" action is currently a
 * no-op against every dyeable vanilla block (wool, concrete, terracotta, stained glass, ...) until/unless a
 * future batch registers one -- this is honest, current platform behaviour, not a defect in this class.
 *
 * <p><b>Durability is spent identically for every variant, including the colourless one, matching a genuine
 * 1.12.2 quirk rather than "fixing" it.</b> 1.12.2 decremented {@code usesLeft} on every successful paint
 * regardless of colour, even though the colourless variant's own guard above never actually consults it. Ported
 * the same way here: {@link ItemStack#hurtAndBreak} is called whenever
 * {@link CustomPaintHelper#attemptPaintBlock} returns success, for every variant -- for the colourless brush this
 * is presently unreachable dead code (see the paragraph above: a null-paint attempt can never itself return
 * success without a registered clearing handler), exactly mirroring 1.12.2's own harmless, never-consulted
 * counter.
 *
 * <p><b>One real, deliberate behavioural difference from 1.12.2 follows directly from the redesign above, not
 * from any change to the painting logic itself.</b> 1.12.2's single shared-metadata item downgraded a spent
 * coloured brush in place to the colourless variant (metadata 0) rather than destroying it, because metadata was
 * simply a value on the same {@code ItemStack} that could be rewritten. With 17 separate registry entries there
 * is no equivalent "downgrade" -- {@code hurtAndBreak} runs vanilla's own tool-break behaviour instead: a
 * coloured brush that reaches its 64th successful use is consumed like any other vanilla tool, rather than
 * turning into a fresh colourless one. Noted here, not silently changed and not treated as a bug.
 *
 * <p>{@code getItemStackDisplayName} becomes {@link #getName(ItemStack)}. 1.12.2 prefixed the base name with
 * {@code ColourUtil.getTextFullTooltipSpecial(colour)}, which needs {@code SpecialColourFontRenderer} (not
 * ported -- client rendering is deferred generally) to render its private escape sequence; using it here would
 * embed unrenderable characters in the item's name for every colour except {@code BLACK}/{@code BLUE}.
 * {@link ColourUtil#getTextFullTooltip(DyeColor)} is the safe, already-ported equivalent that only ever emits
 * standard {@code ChatFormatting} codes, so that is used instead. Every variant's base name comes from one shared
 * translation key, {@code item.buildcraft.paintbrush}, computed directly here rather than through
 * {@code Item#getDescriptionId()} -- confirmed via {@code javap} that method is {@code final} on this target, so
 * a per-registry-entry description id cannot be overridden away from its default
 * {@code item.buildcraft.paintbrush_<colour>} derivation. Building the {@link Component} directly in
 * {@link #getName(ItemStack)} sidesteps that entirely and needs only the one lang entry the task specified.
 *
 * <p>1.12.2's {@code ParticleUtil.showChangeColour} feedback is dropped: {@code ParticleUtil} is not ported to
 * either platform (client-side particle rendering is deferred generally), and porting it just for this single
 * call is out of scope here. {@code SoundUtil.playChangeColour(Level, BlockPos, DyeColor)} is already ported
 * identically on both targets and is kept.
 *
 * <p>{@code onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ)} becomes {@link #useOn(UseOnContext)}
 * -- confirmed via {@code javap} against {@code Item} on this target: {@code InteractionResult
 * useOn(UseOnContext)}, the same hook {@link ItemWrench} already uses. {@code UseOnContext#getClickLocation()}
 * already returns the absolute world-space hit position 1.12.2 built by hand (adding {@code hitX}/{@code hitY}/
 * {@code hitZ} onto {@code pos}), so no equivalent of {@code VecUtil.add} is needed. {@code InteractionResult} is
 * a sealed interface on this target (see PORTING.md's API migration reference, item 14): a success check is
 * {@code instanceof InteractionResult.Success}, not {@code == InteractionResult.SUCCESS}.
 */
public class ItemPaintbrush extends Item {
    private static final int MAX_USES = 64;
    private static final String NAME_KEY = "item.buildcraft.paintbrush";

    @Nullable
    public final DyeColor colour;

    public ItemPaintbrush(Properties properties, @Nullable DyeColor colour) {
        super(properties.stacksTo(1).durability(MAX_USES));
        this.colour = colour;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component base = Component.translatable(NAME_KEY);
        if (colour == null) {
            return base;
        }
        return Component.literal(ColourUtil.getTextFullTooltip(colour) + " ").append(base);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (colour != null && stack.getDamageValue() >= stack.getMaxDamage()) {
            return InteractionResult.FAIL;
        }

        BlockState state = world.getBlockState(pos);
        InteractionResult result = CustomPaintHelper.INSTANCE.attemptPaintBlock(
            world, pos, state, context.getClickLocation(), context.getClickedFace(), colour);

        if (result instanceof InteractionResult.Success) {
            SoundUtil.playChangeColour(world, pos, colour);
            if (!player.isCreative()) {
                stack.hurtAndBreak(1, player, context.getHand());
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }
}
