/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import net.minecraftforge.network.NetworkEvent;

/** Implemented by a block entity that wants to receive an opaque {@link MessageUpdateTile} payload addressed to
 * its position.
 *
 * <p>1.12.2's version returned an {@code IMessage} reply and took a {@code MessageContext}. This target's
 * {@code NetworkEvent.Context} already exposes {@code reply(...)} and {@code enqueueWork(...)} directly (see
 * {@link MessageUpdateTile#handle}, which already calls this off of one), so there is nothing left for a return
 * value or a dedicated context type to do -- a receiver that needs to reply just calls it itself. */
public interface IPayloadReceiver {
    void receivePayload(NetworkEvent.Context ctx, PacketBufferBC buffer);
}
