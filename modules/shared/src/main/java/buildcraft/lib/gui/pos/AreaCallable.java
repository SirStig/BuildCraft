/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.gui.pos;

import java.util.function.DoubleSupplier;

public class AreaCallable implements IGuiArea {
    public final DoubleSupplier x, y, width, height;

    public AreaCallable(DoubleSupplier x, DoubleSupplier y, DoubleSupplier width, DoubleSupplier height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public AreaCallable(DoubleSupplier width, DoubleSupplier height) {
        this(() -> 0, () -> 0, width, height);
    }

    @Override
    public double getX() {
        return x.getAsDouble();
    }

    @Override
    public double getY() {
        return y.getAsDouble();
    }

    @Override
    public double getWidth() {
        return width.getAsDouble();
    }

    @Override
    public double getHeight() {
        return height.getAsDouble();
    }
}
