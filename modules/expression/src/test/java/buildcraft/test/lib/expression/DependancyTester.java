/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.test.lib.expression;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import buildcraft.lib.expression.ExpressionDebugManager;
import buildcraft.lib.expression.FunctionContext;
import buildcraft.lib.expression.GenericExpressionCompiler;
import buildcraft.lib.expression.api.IExpressionNode;
import buildcraft.lib.expression.api.IExpressionNode.INodeBoolean;
import buildcraft.lib.expression.api.InvalidExpressionException;
import buildcraft.lib.expression.info.DependencyVisitorCollector;
import buildcraft.lib.expression.node.value.NodeVariableBoolean;

public class DependancyTester {

    static {
        ExpressionDebugManager.debug = true;
    }

    @Test
    public void findDependantsSimple() throws InvalidExpressionException {
        FunctionContext ctx = new FunctionContext("all");

        INodeBoolean node = GenericExpressionCompiler.compileExpressionBoolean("true", ctx);

        DependencyVisitorCollector visitor = DependencyVisitorCollector.createFullSearch();
        visitor.dependOn(node);

        Assertions.assertTrue(visitor.areAllConstant());
        Assertions.assertFalse(visitor.needsUnkown());
        Assertions.assertEquals(0, visitor.getMutableNodes().size());
    }

    @Test
    public void findDependantsSimplevariable() throws InvalidExpressionException {
        FunctionContext ctx = new FunctionContext("all");

        NodeVariableBoolean var = ctx.putVariableBoolean("some_variable");

        INodeBoolean node = GenericExpressionCompiler.compileExpressionBoolean("some_variable", ctx);

        DependencyVisitorCollector visitor = DependencyVisitorCollector.createFullSearch();
        visitor.dependOn(node);

        Assertions.assertFalse(visitor.areAllConstant());
        Assertions.assertFalse(visitor.needsUnkown());
        Set<IExpressionNode> nodes = visitor.getMutableNodes();
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertTrue(nodes.contains(var));
    }

    @Test
    public void findDependantsBranch() throws InvalidExpressionException {
        FunctionContext ctx = new FunctionContext("all");

        NodeVariableBoolean var = ctx.putVariableBoolean("some_variable");
        NodeVariableBoolean var2 = ctx.putVariableBoolean("other_variable");

        INodeBoolean node =
            GenericExpressionCompiler.compileExpressionBoolean("some_variable ? true : other_variable", ctx);

        DependencyVisitorCollector visitor = DependencyVisitorCollector.createFullSearch();
        visitor.dependOn(node);

        Assertions.assertFalse(visitor.areAllConstant());
        Assertions.assertFalse(visitor.needsUnkown());
        Set<IExpressionNode> nodes = visitor.getMutableNodes();
        Assertions.assertEquals(2, nodes.size());
        Assertions.assertTrue(nodes.contains(var));
        Assertions.assertTrue(nodes.contains(var2));
    }

    @Test
    public void findDependantsBranchInline() throws InvalidExpressionException {
        FunctionContext ctx = new FunctionContext("all");

        NodeVariableBoolean var = ctx.putVariableBoolean("some_variable");
        NodeVariableBoolean var2 = ctx.putVariableBoolean("other_variable");

        INodeBoolean node =
            GenericExpressionCompiler.compileExpressionBoolean("true ? some_variable : other_variable", ctx);

        DependencyVisitorCollector visitor = DependencyVisitorCollector.createFullSearch();
        visitor.dependOn(node);

        Assertions.assertFalse(visitor.areAllConstant());
        Assertions.assertFalse(visitor.needsUnkown());
        Set<IExpressionNode> nodes = visitor.getMutableNodes();
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertTrue(nodes.contains(var));
        Assertions.assertFalse(nodes.contains(var2));
    }
}
