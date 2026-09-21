/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.test.api.mj;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.api.mj.MjAPI;
import buildcraft.api.mj.MjBattery;

/**
 * Covers {@link MjBattery}'s arithmetic, which the port had to restructure: 1.12.2 shed excess power inside
 * {@code tick(World, Vec3d)}, which cannot be shared between platforms, so the accounting moved to
 * {@link MjBattery#shedExcessPower()} and only the particle effect stayed behind. These tests pin the numbers that
 * move produced, so a later change to the effect side cannot quietly alter the power maths.
 */
public class MjBatteryTester {

    private static final long CAPACITY = 1000;

    @Test
    public void testAddAndExtract() {
        MjBattery battery = new MjBattery(CAPACITY);
        Assertions.assertEquals(0, battery.getStored());
        Assertions.assertEquals(CAPACITY, battery.getCapacity());

        battery.addPower(400, false);
        Assertions.assertEquals(400, battery.getStored());

        // Simulating must not change anything.
        battery.addPower(100, true);
        Assertions.assertEquals(400, battery.getStored());

        Assertions.assertEquals(150, battery.extractPower(0, 150));
        Assertions.assertEquals(250, battery.getStored());
    }

    @Test
    public void testExtractRespectsMinimum() {
        MjBattery battery = new MjBattery(CAPACITY);
        battery.addPower(100, false);

        // Below the minimum, nothing is taken and the battery is untouched.
        Assertions.assertEquals(0, battery.extractPower(200, 500));
        Assertions.assertEquals(100, battery.getStored());

        // Capped at what is actually stored.
        Assertions.assertEquals(100, battery.extractPower(50, 500));
        Assertions.assertEquals(0, battery.getStored());
    }

    @Test
    public void testIsFullAndAddPowerChecking() {
        MjBattery battery = new MjBattery(CAPACITY);
        battery.addPower(CAPACITY, false);
        Assertions.assertTrue(battery.isFull());

        // A full battery refuses the lot, handing it all back as excess.
        Assertions.assertEquals(500, battery.addPowerChecking(500, false));
        Assertions.assertEquals(CAPACITY, battery.getStored());
    }

    @Test
    public void testAddPowerOverfillsDeliberately() {
        // addPower ignores capacity and always reports no excess; over-filling is corrected by shedding
        // later. This is 1.12.2 behaviour and is what makes an over-supplied machine visibly spark.
        MjBattery battery = new MjBattery(CAPACITY);
        Assertions.assertEquals(0, battery.addPower(CAPACITY * 5, false));
        Assertions.assertEquals(CAPACITY * 5, battery.getStored());
    }

    @Test
    public void testShedExcessPower() {
        MjBattery battery = new MjBattery(CAPACITY);

        // At or below twice capacity nothing is shed.
        battery.setStored(CAPACITY * 2);
        Assertions.assertEquals(0, battery.shedExcessPower());
        Assertions.assertEquals(CAPACITY * 2, battery.getStored());

        // Above it, a 32nd of the excess goes, rounded up: ceil(1000 / 32) == 32.
        battery.setStored(CAPACITY * 3);
        Assertions.assertEquals(32, battery.shedExcessPower());
        Assertions.assertEquals(CAPACITY * 3 - 32, battery.getStored());
    }

    @Test
    public void testShedExcessPowerDrainsTowardsTwiceCapacity() {
        MjBattery battery = new MjBattery(CAPACITY);
        battery.setStored(CAPACITY * 10);

        // Repeated shedding must converge on 2x capacity and then stop, never overshooting below it.
        for (int i = 0; i < 10_000 && battery.shedExcessPower() > 0; i++) {
            // drain
        }
        Assertions.assertEquals(CAPACITY * 2, battery.getStored());
        Assertions.assertEquals(0, battery.shedExcessPower());
    }

    @Test
    public void testBufferRoundTrip() {
        MjBattery source = new MjBattery(CAPACITY);
        source.addPower(777, false);

        ByteBuf buffer = Unpooled.buffer();
        source.writeToBuffer(buffer);

        MjBattery target = new MjBattery(CAPACITY);
        target.readFromBuffer(buffer);
        Assertions.assertEquals(777, target.getStored());
    }

    @Test
    public void testFormatMj() {
        Assertions.assertEquals("1", MjAPI.formatMj(MjAPI.MJ));
        Assertions.assertEquals("1.5", MjAPI.formatMj(MjAPI.MJ * 3 / 2));
    }
}
