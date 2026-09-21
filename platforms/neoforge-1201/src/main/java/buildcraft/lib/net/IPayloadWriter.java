/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.net;

/** Writes one message's payload into a {@link PacketBufferBC}. Unaffected by the port -- this is unchanged. */
@FunctionalInterface
public interface IPayloadWriter {
    void write(PacketBufferBC buffer);
}
