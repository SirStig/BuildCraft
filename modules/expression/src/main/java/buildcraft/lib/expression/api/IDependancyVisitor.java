/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.expression.api;

public interface IDependancyVisitor {
    /** Visits the given node. If the node is an instance of {@link IDependantNode} then all of it's children are
     * visited instead. */
    void dependOn(IExpressionNode node);

    /** Array version of {@link #dependOn(IExpressionNode)}. */
    void dependOn(IExpressionNode... nodes);

    /** {@link Iterable} version of {@link #dependOn(IExpressionNode)}. */
    void dependOnNodes(Iterable<? extends IExpressionNode> nodes);

    /** Single forced-{@link IDependantNode} version of {@link #dependOn(IExpressionNode)}. */
    void dependOn(IDependantNode child);

    /** Array forced-{@link IDependantNode} version of {@link #dependOn(IExpressionNode)}. */
    void dependOn(IDependantNode... children);

    /** {@link Iterable} forced-{@link IDependantNode} version of {@link #dependOn(IExpressionNode)}. */
    void dependOnChildren(Iterable<? extends IDependantNode> children);

    /** Visits the given node, and <i>not</i> it's children if it is an instance of {@link IDependantNode}. */
    void dependOnExplictly(IExpressionNode node);

    /** Implies that the caller depends on something that isn't native to the expression subsystem, and so should be
     * considered as an unknown {@link IVariableNode}. */
    void dependOnUnknown();
}
