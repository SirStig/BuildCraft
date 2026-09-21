/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.Equippable;

/**
 * 1.12.2's {@code ItemGoggles} extended {@code ItemArmor} but zeroed its own contribution out through Forge's
 * {@code ISpecialArmor} -- {@code getProperties} always returned a zero {@code ArmorProperties}, {@code
 * getArmorDisplay} always returned 0, and {@code damageArmor} was overridden to a no-op, so the item occupied
 * the head slot while granting no protection and never losing durability.
 *
 * <p>On 26.x, {@code ArmorItem} and {@code ISpecialArmor} are both gone: {@code javap} against the merged jar
 * reports no {@code net.minecraft.world.item.ArmorItem} class at all, and equipping is now purely a data
 * component, {@link Equippable} (see its own source under {@code net.minecraft.world.item.equipment}), which
 * carries a slot/sound/asset/interaction configuration and nothing resembling a defense value -- defense comes
 * only from a separate, entirely optional {@code ItemAttributeModifiers} component that this item simply never
 * sets. Vanilla's own {@code Items.CARVED_PUMPKIN} is the equivalent "wearable head slot item with zero
 * defense" precedent: a plain {@link Item} whose {@code Properties} carries {@code DataComponents.EQUIPPABLE}
 * and nothing else armor-related. That makes the port simpler than 1.12.2's version, not just different: there
 * is no zero-value material or properties object to construct, because the modern default *is* zero defense
 * unless an attribute-modifiers component is added on top.
 *
 * <p>{@code damageArmor}'s no-op has two parts here. {@code Equippable#damageOnHurt} is set false explicitly,
 * matching the original override's intent; it would already be a no-op regardless, since {@code
 * LivingEntity#hurtArmor} only calls {@code ItemStack#hurtAndBreak} when {@code isDamageableItem()} is true,
 * and this item is never given a {@code DataComponents.MAX_DAMAGE} component, so it is never damageable in the
 * first place.
 */
public class ItemGoggles extends Item {
    public ItemGoggles(Properties properties) {
        super(properties
            .stacksTo(1)
            .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setDamageOnHurt(false).build()));
    }
}
