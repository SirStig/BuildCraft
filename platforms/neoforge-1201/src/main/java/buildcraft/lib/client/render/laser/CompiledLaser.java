/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.laser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

import buildcraft.lib.client.render.laser.LaserData_BC8.LaserRow;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserSide;
import buildcraft.lib.client.render.laser.LaserData_BC8.LaserType;

/** One laser's finished geometry: a flat list of quads (4 vertices each) with position, atlas UV, normal and packed
 * lightmap coordinates already resolved, ready to be replayed into any {@code VertexConsumer} by
 * {@link LaserRenderer_BC8#emit}. This is the whole of 1.12.2's {@code CompiledLaserType}/{@code CompiledLaserRow}/
 * {@code LaserContext}/{@code LaserCompiledBuffer} quartet collapsed into a single build step plus a plain data
 * holder -- the display-list/VBO/{@code BufferBuilder} machinery those classes existed to feed has no counterpart on
 * either target, but the actual <em>geometry</em> they produced is reproduced here quad-for-quad (same local frame,
 * same cap/start/middle/end layout, same UV maths, same per-vertex world-light sampling), so a laser looks like
 * it did.
 *
 * <p>Positions are stored as {@code float} offsets from {@link #originX}/{@link #originY}/{@link #originZ} (the
 * laser's own start point, held as {@code double}) rather than as absolute {@code float} world coordinates: a
 * {@code float} has too few bits to place a vertex precisely thousands of blocks from the world origin, which is
 * exactly the precision problem camera-relative rendering exists to avoid.
 *
 * <p>Building one of these reads the level (for light) and the block atlas (for UVs), which on 26.x must happen
 * during render-state <em>extraction</em>, never during {@code submit} -- which is precisely why the build is a
 * separate step from the replay at all. */
public final class CompiledLaser {
    /** floats per vertex in {@link #vertexData}: dx, dy, dz, u, v, nx, ny, nz */
    static final int STRIDE = 8;

    public final double originX, originY, originZ;
    final float[] vertexData;
    final int[] lightData;
    public final int vertexCount;
    /** World-space bounds of every vertex, for frustum culling. */
    public final AABB bounds;

    private CompiledLaser(double ox, double oy, double oz, float[] vertexData, int[] lightData, AABB bounds) {
        this.originX = ox;
        this.originY = oy;
        this.originZ = oz;
        this.vertexData = vertexData;
        this.lightData = lightData;
        this.vertexCount = lightData.length;
        this.bounds = bounds;
    }

    /** Builds the geometry for {@code data}.
     *
     * @param level The level to sample light from, or null for "fully lit" (sky 15, block
     *            {@code max(minBlockLight, 0)}).
     * @param sprites Resolves a {@link LaserRow#sprite} id to a real block-atlas sprite. */
    public static CompiledLaser compile(LaserData_BC8 data, @Nullable Level level,
        Function<ResourceLocation, TextureAtlasSprite> sprites) {
        Builder builder = new Builder(data, level, sprites);
        builder.bakeType(data.laserType);
        return builder.build();
    }

    /** The mutable build state -- 1.12.2's {@code LaserContext} (the local-to-world transform plus the per-vertex
     * light sampling) and {@code CompiledLaserType}/{@code CompiledLaserRow} (what quads to emit), merged. */
    static final class Builder {
        private final LaserData_BC8 data;
        @Nullable
        private final Level level;
        private final Function<ResourceLocation, TextureAtlasSprite> sprites;
        /** The laser's length in its own local units (texture pixels), i.e. world length / scale. */
        final double length;
        private final double scale;
        /** cos/sin of the local-Z rotation (pitch) and the local-Y rotation (yaw) -- see the constructor. */
        private final double cosPitch, sinPitch, cosYaw, sinYaw;

        private final FloatArrayList vertices = new FloatArrayList();
        private final IntArrayList lights = new IntArrayList();
        /** Memoised per-block light: many vertices share the same containing block. */
        private final Long2IntOpenHashMap lightMemo = new Long2IntOpenHashMap();
        private double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;

        private float nx = 0, ny = 1, nz = 0;
        private TextureAtlasSprite sprite;
        private LaserRow row;

        Builder(LaserData_BC8 data, @Nullable Level level, Function<ResourceLocation, TextureAtlasSprite> sprites) {
            this.data = data;
            this.level = level;
            this.sprites = sprites;
            this.scale = data.scale;
            lightMemo.defaultReturnValue(-1);

            // Straight from 1.12.2's LaserContext: the local frame has +X running from start to end, and is
            // produced by rotating about local Z by "angleY" (pitch) and then about Y by "angleZ" (yaw) -- the
            // original's own (misleading) variable names, kept in these comments so the two can be compared.
            // Reproduced exactly rather than replaced by an arbitrary orthonormal basis, because the roll of the
            // resulting frame decides which face is TOP/BOTTOM/LEFT/RIGHT, and a LaserType can put different
            // sprite rows on different sides (the path marker laser does).
            double dx = data.start.x - data.end.x;
            double dy = data.start.y - data.end.y;
            double dz = data.start.z - data.end.z;
            double realLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
            this.length = realLength / data.scale;
            double angleZ = Math.PI - Math.atan2(dz, dx);
            double angleY;
            if (dx == 0 && dz == 0) {
                angleY = dy < 0 ? Math.PI / 2 : -Math.PI / 2;
            } else {
                // The original's sqrt(realLength^2 - dy^2); dx^2 + dz^2 is the same value without the risk of a
                // rounding error pushing a near-vertical laser's argument just below zero (NaN).
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                angleY = -Math.atan2(dy, horizontal);
            }
            cosPitch = Math.cos(angleY);
            sinPitch = Math.sin(angleY);
            cosYaw = Math.cos(angleZ);
            sinYaw = Math.sin(angleZ);
        }

        // ---- LaserContext: transform + light ----

        /** javax.vecmath rotZ(pitch) then rotY(yaw), applied to a local vector (1.12.2's matrix order was
         * translate * scale * rotY * rotZ, so rotZ is applied first). Writes into {@link #tmp}. */
        private final double[] tmp = new double[3];

        private void rotate(double x, double y, double z) {
            double x1 = cosPitch * x - sinPitch * y;
            double y1 = sinPitch * x + cosPitch * y;
            double z1 = z;
            tmp[0] = cosYaw * x1 + sinYaw * z1;
            tmp[1] = y1;
            tmp[2] = -sinYaw * x1 + cosYaw * z1;
        }

        void setFaceNormal(double x, double y, double z) {
            rotate(x, y, z);
            double len = Math.sqrt(tmp[0] * tmp[0] + tmp[1] * tmp[1] + tmp[2] * tmp[2]);
            nx = (float) (tmp[0] / len);
            ny = (float) (tmp[1] / len);
            nz = (float) (tmp[2] / len);
        }

        void addPoint(double x, double y, double z, double u, double v) {
            rotate(x, y, z);
            double ox = tmp[0] * scale;
            double oy = tmp[1] * scale;
            double oz = tmp[2] * scale;
            double wx = data.start.x + ox;
            double wy = data.start.y + oy;
            double wz = data.start.z + oz;
            minX = Math.min(minX, wx);
            minY = Math.min(minY, wy);
            minZ = Math.min(minZ, wz);
            maxX = Math.max(maxX, wx);
            maxY = Math.max(maxY, wy);
            maxZ = Math.max(maxZ, wz);
            vertices.add((float) ox);
            vertices.add((float) oy);
            vertices.add((float) oz);
            vertices.add((float) u);
            vertices.add((float) v);
            vertices.add(nx);
            vertices.add(ny);
            vertices.add(nz);
            lights.add(computeLight(wx, wy, wz));
        }

        /** 1.12.2's {@code LaserRenderer_BC8.computeLightmap}, using its non-smooth-lighting branch (the maximum of
         * each light layer over the 3x3x3 blocks around the vertex) unconditionally. The original's smooth-lighting
         * branch only looked at the vertex's own block unless it was within 0.3 of a face, so a laser whose centre
         * line runs <em>through</em> a solid block (a volume box edge through terrain, a quarry frame through the
         * ground) sampled light 0 there and drew pitch black; the max-of-neighbours branch never does. */
        private int computeLight(double x, double y, double z) {
            if (data.minBlockLight >= 15) {
                return LightTexture.FULL_BRIGHT;
            }
            if (level == null) {
                return LightTexture.pack(Math.max(0, data.minBlockLight), 15);
            }
            BlockPos centre = BlockPos.containing(x, y, z);
            long key = centre.asLong();
            int cached = lightMemo.get(key);
            if (cached != -1) {
                return cached;
            }
            int block = 0;
            int sky = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        pos.set(centre.getX() + ox, centre.getY() + oy, centre.getZ() + oz);
                        block = Math.max(block, level.getBrightness(LightLayer.BLOCK, pos));
                        sky = Math.max(sky, level.getBrightness(LightLayer.SKY, pos));
                    }
                }
            }
            block = Math.max(block, data.minBlockLight);
            int packed = LightTexture.pack(block, sky);
            lightMemo.put(key, packed);
            return packed;
        }

        // ---- CompiledLaserRow: UVs ----

        private void useRow(LaserRow row) {
            this.row = row;
            this.sprite = sprites.apply(row.sprite);
        }

        /** {@code between} is 0..1 across the row's own rectangle (and may go slightly outside it for a start/end
         * segment, exactly as in 1.12.2). */
        private double texU(double between) {
            double interp = row.uMin * (1 - between) + row.uMax * between;
            return sprite.getU0() + (sprite.getU1() - sprite.getU0()) * interp;
        }

        private double texV(double between) {
            double interp = row.vMin * (1 - between) + row.vMax * between;
            return sprite.getV0() + (sprite.getV1() - sprite.getV0()) * interp;
        }

        void bakeStartCap(LaserRow cap) {
            useRow(cap);
            double h = cap.height / 2.0;
            setFaceNormal(-1, 0, 0);
            addPoint(0, h, h, texU(1), texV(1));
            addPoint(0, h, -h, texU(1), texV(0));
            addPoint(0, -h, -h, texU(0), texV(0));
            addPoint(0, -h, h, texU(0), texV(1));
        }

        void bakeEndCap(LaserRow cap) {
            useRow(cap);
            double h = cap.height / 2.0;
            setFaceNormal(1, 0, 0);
            addPoint(length, -h, h, texU(0), texV(1));
            addPoint(length, -h, -h, texU(0), texV(0));
            addPoint(length, h, -h, texU(1), texV(0));
            addPoint(length, h, h, texU(1), texV(1));
        }

        /** The four long faces of one segment from local x = {@code ls} to {@code lb}, with the row's U running from
         * {@code uFrom} to {@code uTo}. The per-side vertex order is 1.12.2's own. */
        private void bakeSide(LaserSide side, double ls, double lb, double h, double uFrom, double uTo) {
            switch (side) {
                case TOP -> {
                    setFaceNormal(0, 1, 0);
                    addPoint(ls, h, -h, texU(uFrom), texV(0));
                    addPoint(ls, h, h, texU(uFrom), texV(1));
                    addPoint(lb, h, h, texU(uTo), texV(1));
                    addPoint(lb, h, -h, texU(uTo), texV(0));
                }
                case BOTTOM -> {
                    setFaceNormal(0, -1, 0);
                    addPoint(lb, -h, -h, texU(uTo), texV(0));
                    addPoint(lb, -h, h, texU(uTo), texV(1));
                    addPoint(ls, -h, h, texU(uFrom), texV(1));
                    addPoint(ls, -h, -h, texU(uFrom), texV(0));
                }
                case LEFT -> {
                    setFaceNormal(0, 0, -1);
                    addPoint(ls, -h, -h, texU(uFrom), texV(0));
                    addPoint(ls, h, -h, texU(uFrom), texV(1));
                    addPoint(lb, h, -h, texU(uTo), texV(1));
                    addPoint(lb, -h, -h, texU(uTo), texV(0));
                }
                case RIGHT -> {
                    setFaceNormal(0, 0, 1);
                    addPoint(lb, -h, h, texU(uTo), texV(0));
                    addPoint(lb, h, h, texU(uTo), texV(1));
                    addPoint(ls, h, h, texU(uFrom), texV(1));
                    addPoint(ls, -h, h, texU(uFrom), texV(0));
                }
            }
        }

        /** 1.12.2's {@code CompiledLaserRow#bakeStart}: the <em>last</em> {@code segLength} pixels of the start row,
         * so the texture lines up with the first middle segment. */
        void bakeStart(LaserRow start, double segLength) {
            useRow(start);
            double h = start.height / 2.0;
            double i = 1 - (segLength / start.width);
            for (LaserSide side : LaserSide.VALUES) {
                bakeSide(side, 0, segLength, h, i, 1);
            }
        }

        /** 1.12.2's {@code CompiledLaserRow#bakeEnd}: the <em>first</em> {@code segLength} pixels of the end row. */
        void bakeEnd(LaserRow end, double segLength) {
            useRow(end);
            double h = end.height / 2.0;
            double i = segLength / end.width;
            for (LaserSide side : LaserSide.VALUES) {
                bakeSide(side, length - segLength, length, h, 0, i);
            }
        }

        /** 1.12.2's {@code CompiledLaserRow#bakeFor}: {@code count} full-width middle segments on one side, cycling
         * through that side's valid rows in order. */
        void bakeMiddle(LaserRow[] rows, LaserSide side, double startX, int count) {
            double width = rows[0].width;
            double h = rows[0].height / 2.0;
            double xMin = startX;
            for (int i = 0; i < count; i++) {
                useRow(rows[i % rows.length]);
                bakeSide(side, xMin, xMin + width, h, 0, 1);
                xMin += width;
            }
        }

        /** 1.12.2's {@code CompiledLaserType#bakeFor}, verbatim in structure: caps first, then a whole number of
         * middle segments (rounded up), with whatever length is left split between the start and end rows. */
        void bakeType(LaserType type) {
            double startWidth = type.start == null ? 0 : type.start.width;
            double endWidth = type.end == null ? 0 : type.end.width;
            List<LaserRow[]> sideRows = new ArrayList<>(LaserSide.VALUES.length);
            for (LaserSide side : LaserSide.VALUES) {
                List<LaserRow> valid = new ArrayList<>();
                for (LaserRow r : type.variations) {
                    for (LaserSide inner : r.validSides) {
                        if (inner == side) {
                            valid.add(r);
                            break;
                        }
                    }
                }
                if (valid.isEmpty()) {
                    throw new IllegalArgumentException("Laser type has no middle row for side " + side);
                }
                sideRows.add(valid.toArray(new LaserRow[0]));
            }
            double middleWidth = sideRows.get(LaserSide.BOTTOM.ordinal())[0].width;

            bakeStartCap(type.capStart);
            bakeEndCap(type.capEnd);

            double lengthForMiddle = Math.max(0, length - startWidth - endWidth);
            int numMiddle = Mth.floor(lengthForMiddle / middleWidth);
            double leftOverFromMiddle = lengthForMiddle - middleWidth * numMiddle;
            if (leftOverFromMiddle > 0) {
                numMiddle++;
            }
            double lengthEnds = length - middleWidth * numMiddle;
            final double startLength, endLength;
            if (startWidth > 0 && endWidth > 0) {
                double ratioStartEnd = startWidth / endWidth;
                startLength = (lengthEnds / 2) * ratioStartEnd;
                endLength = (lengthEnds / 2) / ratioStartEnd;
            } else if (startWidth <= 0) {
                startLength = 0;
                endLength = lengthEnds;
            } else {// endWidth <= 0
                startLength = lengthEnds;
                endLength = 0;
            }
            if (startLength > 0) bakeStart(type.start, startLength);
            if (endLength > 0) bakeEnd(type.end, endLength);

            if (numMiddle > 0) {
                for (LaserSide side : LaserSide.VALUES) {
                    bakeMiddle(sideRows.get(side.ordinal()), side, startLength, numMiddle);
                }
            }
        }

        CompiledLaser build() {
            AABB bounds = lights.isEmpty()
                ? new AABB(data.start, data.start)
                : new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            return new CompiledLaser(data.start.x, data.start.y, data.start.z, vertices.toFloatArray(),
                lights.toIntArray(), bounds);
        }
    }
}
