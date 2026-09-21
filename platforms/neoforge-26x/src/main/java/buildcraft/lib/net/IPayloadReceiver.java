/*
 * Copyright (c) 2016 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.net;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Implemented by a block entity that wants to receive an opaque {@link MessageUpdateTile} payload addressed to
 * its position.
 *
 * <p>1.12.2's version returned an {@code IMessage} reply and took a {@code MessageContext}. The modern payload
 * system already gives every handler a direct {@code context.reply(...)} call and {@code context.enqueueWork(...)}
 * for main-thread scheduling (see {@link MessageUpdateTile#handle}, which already calls this off of one), so
 * there is nothing left for a return value or a dedicated context type to do -- a receiver that needs to reply
 * just calls {@link IPayloadContext#reply} itself. */
public interface IPayloadReceiver {
    void receivePayload(IPayloadContext ctx, PacketBufferBC buffer);
}
