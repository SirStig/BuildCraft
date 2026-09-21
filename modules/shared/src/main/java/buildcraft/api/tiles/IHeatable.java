/*
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License. Please check the contents of the license, which
 * should be located as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.tiles;

/** Implemented by block entities that have an internal heat value. */
public interface IHeatable {
    /** @return The minimum heat value, in degrees. */
    double getMinHeatValue();

    /** @return The preferred heat value, in degrees. */
    double getIdealHeatValue();

    /** @return The maximum heat value, in degrees. */
    double getMaxHeatValue();

    /** @return The current heat value, in degrees. */
    double getCurrentHeatValue();

    /**
     * Set the heat of the block entity.
     *
     * @param value Heat value, in degrees.
     * @return The heat the block entity has after the set.
     */
    double setHeatValue(double value);
}
