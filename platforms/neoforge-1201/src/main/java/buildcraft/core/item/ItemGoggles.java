/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.core.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 1.12.2's {@code ItemGoggles} extended {@code ItemArmor} but zeroed its own contribution out through Forge's
 * {@code ISpecialArmor} -- {@code getProperties} always returned a zero {@code ArmorProperties}, {@code
 * getArmorDisplay} always returned 0, and {@code damageArmor} was overridden to a no-op, so the item occupied
 * the head slot while granting no protection and never losing durability.
 *
 * <p><strong>This target diverges from 26.x here</strong> (see the 26.x copy of this class): 1.20.1's Forge
 * fork still has a real {@code ArmorItem}/{@code ArmorMaterial} pair -- {@code javap} confirms both exist, with
 * {@code ArmorMaterial} now a plain interface ({@code getDurabilityForType}/{@code getDefenseForType}/{@code
 * getToughness}/{@code getKnockbackResistance}/...) that {@code ArmorItem}'s constructor reads directly to
 * build its {@code Attributes.ARMOR}/{@code ARMOR_TOUGHNESS}/{@code KNOCKBACK_RESISTANCE} modifiers.
 * {@code ISpecialArmor} itself is gone ({@code javap} reports the class not found) -- there is nothing left to
 * override, because the defense value is no longer computed through a side interface the item can intercept;
 * it is just data on the material. So the zero contribution 1.12.2 expressed via {@code ISpecialArmor} is
 * expressed here by implementing {@link ArmorMaterial} with every numeric value at zero, in place of reusing
 * {@code ArmorMaterials.CHAIN} the way 1.12.2 did.
 *
 * <p>{@code damageArmor}'s no-op falls out of the same zero material rather than needing its own override:
 * {@code ArmorItem}'s constructor calls {@code Properties#defaultDurability(material.getDurabilityForType(type))},
 * and a durability of 0 leaves {@code Item#isDamageableItem()} false (it is defined as {@code maxDamage > 0}),
 * which is exactly what {@code ItemStack#hurtAndBreak} checks before doing anything -- so the item is simply
 * never eligible for durability damage, the same end state 1.12.2 reached by overriding {@code damageArmor} to
 * do nothing.
 *
 * <p>The enchantment value (12) is kept matching {@code ArmorMaterials.CHAIN}, since 1.12.2 never overrode
 * {@code getItemEnchantability} and so inherited chainmail's default -- the one piece of the original material
 * worth preserving even though every numeric defense value is zeroed.
 */
public class ItemGoggles extends ArmorItem {
    private static final ArmorMaterial GOGGLES_MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 12;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_CHAIN;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return "buildcraft:goggles";
        }

        @Override
        public float getToughness() {
            return 0.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };

    public ItemGoggles(Item.Properties properties) {
        super(GOGGLES_MATERIAL, ArmorItem.Type.HELMET, properties);
    }
}
