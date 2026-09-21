/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.expression.node.value;

import java.util.Locale;

import buildcraft.lib.expression.api.IExpressionNode;
import buildcraft.lib.expression.api.IVariableNode;

public abstract class NodeVariable implements IVariableNode {

    public final String name;
    protected boolean isConst = false;

    public NodeVariable(String name) {
        this.name = name.toLowerCase(Locale.ROOT);
    }

    @Override
    public void setConstant(boolean isConst) {
        this.isConst = isConst;
    }

    @Override
    public boolean isConstant() {
        return isConst;
    }

    public abstract void setConstantSource(IExpressionNode source);

    @Override
    public String toString() {
        return "variable: " + name + " = " + evaluateAsString();
    }
}
