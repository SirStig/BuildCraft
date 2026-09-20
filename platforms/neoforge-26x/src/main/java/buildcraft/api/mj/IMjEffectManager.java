/** Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license,
 * which should be located as "LICENSE.API" in the BuildCraft source code distribution. */
package buildcraft.api.mj;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Various effects for showing power loss visibly, and for large amounts of power, causes some damage to nearby
 * entities. Ported from 1.12.2 with {@code World} -> {@link Level}, {@code Vec3d} -> {@link Vec3} and
 * {@code EnumFacing} -> {@link Direction}; the {@link BlockPos} overload is new, and saves every caller converting
 * to a block centre by hand. */
public interface IMjEffectManager {

    void createPowerLossEffect(Level level, Vec3 center, long microJoulesLost);

    void createPowerLossEffect(Level level, Vec3 center, Direction direction, long microJoulesLost);

    void createPowerLossEffect(Level level, Vec3 center, Vec3 direction, long microJoulesLost);

    default void createPowerLossEffect(Level level, BlockPos pos, long microJoulesLost) {
        createPowerLossEffect(level, Vec3.atCenterOf(pos), microJoulesLost);
    }
}
