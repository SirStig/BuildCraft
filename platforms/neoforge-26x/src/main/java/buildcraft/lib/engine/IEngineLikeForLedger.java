/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.engine;

import buildcraft.api.enums.EnumPowerStage;

/** Unchanged from 1.12.2. Yes, the name is bad. BC8 is nearly dead anyway. This is the interface a future
 * power-ledger UI would query to summarise any engine-like machine, regardless of its concrete engine type. */
public interface IEngineLikeForLedger {

    EnumPowerStage getPowerStage();

    boolean isEngineOn();

    long getCurrentMjOutput();

    long getMjStored();

    double getHeat();
}
