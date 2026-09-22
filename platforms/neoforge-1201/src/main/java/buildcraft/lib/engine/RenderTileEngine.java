/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.lib.engine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The first {@code BlockEntityRenderer} registered anywhere in this port -- see {@link TileEngineBase}'s own
 * javadoc "Ticking" entry for why the piston animation this reads ({@link TileEngineBase#getRenderProgress(float)})
 * exists at all. One instance is registered per concrete engine type in
 * {@code buildcraft.energy.client.BCEnergyClientRegistries#registerRenderers}, each with its own {@code texture}
 * argument -- there is otherwise nothing engine-type-specific here, so a single shared class covers all three
 * rather than three near-identical subclasses.
 *
 * <p>Deliberately not a reproduction of 1.12.2's {@code RenderEngine_BC8} -- that used a bespoke quad-based
 * {@code MutableQuad} model framework with no counterpart anywhere in this port (see this task's own scoping), and
 * a from-scratch {@code ModelPart} cuboid is both simpler and idiomatic to the modern rendering API. It reuses
 * each engine's own existing block texture ({@code buildcraft:block/engine_*_side.png}, already a real 16x16
 * resource shipped for the static baked model) rather than authoring a new dedicated texture asset -- a small,
 * honestly-scoped placeholder, not a claim that the UVs line up with anything meaningful on that texture.
 *
 * <p>{@code render} skips entirely once the rod is fully retracted ({@code distance <= 0}, true whenever the
 * engine has never pumped or has fully wound back down): the static baked model already covers that resting
 * appearance, so there is nothing this renderer needs to add on top of it.
 */
public class RenderTileEngine implements BlockEntityRenderer<TileEngineBase> {

    /** How far the rod travels from its resting (flush) position at full extension, in blocks -- small enough to
     * read as a piston stroke without the rod poking meaningfully past the engine's own hitbox. */
    private static final float MAX_TRAVEL = 0.3f;

    private final ResourceLocation texture;
    private final ModelPart rod;

    public RenderTileEngine(BlockEntityRendererProvider.Context context, ResourceLocation texture) {
        this.texture = texture;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(0, 0).addBox(5, 5, 5, 6, 6, 6), PartPose.ZERO);
        this.rod = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("rod");
    }

    @Override
    public void render(
        TileEngineBase tile, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay
    ) {
        float p = tile.getRenderProgress(partialTick);
        float distance = p <= 0.5f ? p / 0.5f : (1f - p) / 0.5f;
        if (distance <= 0f) {
            return;
        }

        Direction facing = tile.getBlockState().getValue(BlockStateProperties.FACING);
        Vec3i normal = facing.getNormal();
        float travel = distance * MAX_TRAVEL;

        poseStack.pushPose();
        poseStack.translate(normal.getX() * travel, normal.getY() * travel, normal.getZ() * travel);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(texture));
        rod.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
