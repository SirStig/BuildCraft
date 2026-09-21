/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.expression.node.func;

import org.jetbrains.annotations.Nullable;

import buildcraft.lib.expression.api.IConstantNode;
import buildcraft.lib.expression.api.IExpressionNode;
import buildcraft.lib.expression.api.INodeFunc;
import buildcraft.lib.expression.api.INodeStack;

public abstract class NodeFuncBase implements INodeFunc {

    protected boolean canInline = true;

    @Nullable
    private String deprecationMessage;
    private INodeFunc deprecationRecomendation;

    /** Sets this function as one that relies on more factors than just the arguments given to it. As such
     * {@link IExpressionNode#inline()} will always return a function, rather than an {@link IConstantNode}. */
    public NodeFuncBase setNeverInline() {
        canInline = false;
        return this;
    }

    public NodeFuncBase deprecate(String msg) {
        return deprecate(msg, null);
    }

    public NodeFuncBase deprecate(INodeFunc useInstead) {
        return deprecate(null, useInstead);
    }

    public NodeFuncBase deprecate(String msg, INodeFunc useInstead) {
        deprecationMessage = msg;
        deprecationRecomendation = useInstead;
        return this;
    }

    public boolean isDeprecated() {
        return deprecationMessage != null || deprecationRecomendation != null;
    }

    /** @return The current deprecation message, or null if this either isn't deprecated or the deprecator didn't
     *         provide a message. */
    @Nullable
    public String getDeprecationMessage() {
        return deprecationMessage;
    }

    /** @return The recommended alternative function, or null if this either isn't deprecated or the deprecator didn't
     *         provide an alternative function. */
    @Nullable
    public INodeFunc getDeprecationRecomendation() {
        return deprecationRecomendation;
    }

    /** Implemented by the {@link IExpressionNode}'s that subclasses of {@link NodeFuncBase} return from
     * {@link NodeFuncBase#getNode(INodeStack)}. */
    public interface IFunctionNode {
        NodeFuncBase getFunction();
    }
}
