/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.expression;

import buildcraft.lib.expression.api.IExpressionNode;
import buildcraft.lib.expression.api.IExpressionNode.INodeDouble;
import buildcraft.lib.expression.api.NodeTypes;
import buildcraft.lib.expression.node.func.NodeFuncBase;
import buildcraft.lib.expression.node.func.NodeFuncBase.IFunctionNode;
import buildcraft.lib.expression.node.func.NodeFuncDoubleDoubleToDouble.FuncDoubleDoubleToDouble;
import buildcraft.lib.expression.node.value.NodeConstantDouble;

public class OptimizingInliningHelper {
    public static IExpressionNode tryOptimizedInline(IExpressionNode node) {
        if (isDoubleMultiply(node)) {
            FuncDoubleDoubleToDouble n = (FuncDoubleDoubleToDouble) node;
            INodeDouble n1 = n.argA.inline();
            INodeDouble n2 = n.argB.inline();
            boolean hasConst = false;
            if (n1 instanceof NodeConstantDouble) {
                if (n2 instanceof NodeConstantDouble) {
                    // ...right, that's odd but i guess we'll just inline it ourselves
                    return new NodeConstantDouble(n1.evaluate() * n2.evaluate());
                }
                hasConst = true;
            } else {
                if (n2 instanceof NodeConstantDouble) {
                    INodeDouble t = n1;
                    n1 = n2;
                    n2 = t;
                    hasConst = true;
                }
            }

            if (hasConst) {
                if (isDoubleMultiply(n2)) {
                    FuncDoubleDoubleToDouble mul2 = (FuncDoubleDoubleToDouble) n2;
                    if (mul2.argA instanceof NodeConstantDouble) {
                        double c1 = n1.evaluate();
                        double c2 = mul2.argA.evaluate();
                        return NodeTypes.DoubleFunctions.MUL.create(new NodeConstantDouble(c1 * c2), mul2.argB);
                    }
                } else {
                    return NodeTypes.DoubleFunctions.MUL.create(n1, n2);
                }
            }
        }
        return null;
    }

    public static boolean isDoubleMultiply(IExpressionNode node) {
        if (node instanceof IFunctionNode) {
            NodeFuncBase base = ((IFunctionNode) node).getFunction();
            if (base == NodeTypes.DoubleFunctions.MUL) {
                return true;
            }
        }
        return false;
    }
}
