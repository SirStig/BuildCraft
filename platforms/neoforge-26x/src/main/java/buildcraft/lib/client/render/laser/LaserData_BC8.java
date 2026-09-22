/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.lib.client.render.laser;

import java.util.Objects;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Holds information on a single laser in the world: its {@link LaserType}, its start/end position (absolute world
 * coordinates), its scale, and the one remaining lighting knob ({@link #minBlockLight}).
 *
 * <p>Deliberately still a plain immutable value type with a real {@link #equals}/{@link #hashCode}, exactly like
 * 1.12.2's: {@link LaserRenderer_BC8} caches compiled geometry keyed by it, the same way the original's
 * {@code COMPILED_STATIC_LASERS} cache did. Nothing in here touches a client-only type ({@link Vec3} and
 * {@link Identifier} are both common classes), so a {@link LaserType} constant can be declared from common code
 * without dragging client classes onto a dedicated server.
 *
 * <p><b>Changed from 1.12.2</b>, all deliberate:
 * <ul>
 * <li>{@link LaserRow#sprite} is a block-atlas sprite {@link Identifier}, not a {@code SpriteHolderRegistry}
 * {@code ISprite}. That whole custom sprite-registration system is not ported (and does not need to be: a mod gets
 * arbitrary textures into the block atlas through a data-driven atlas source now -- see
 * {@code assets/minecraft/atlases/blocks.json} and PORTING.md). The sprite is resolved to a real
 * {@code TextureAtlasSprite} at compile time, every time, so a resource reload is picked up automatically.</li>
 * <li>{@code enableDiffuse}/{@code doubleFace} are gone. The original baked a per-face "diffuse" grey level into
 * the vertex colour itself ({@code MutableQuad.diffuseLight}) because its immediate-mode draw had no lighting of
 * its own; the modern entity shaders this port draws lasers with apply real per-face directional shading from each
 * vertex's normal instead, so baking it in as well would double-darken. {@code doubleFace} only ever emitted a
 * reversed copy of every quad for a culling draw; the render type used here never back-face culls at all, so it
 * would be a no-op.</li>
 * <li>{@link #minBlockLight} keeps its exact 1.12.2 meaning ({@code >= 15} is fully bright, anything lower is a
 * floor under the world's own block light) -- it is the "bright/enabled" knob later consumers (the silicon laser,
 * builder robots) need.</li>
 * </ul> */
public class LaserData_BC8 {
    public final LaserType laserType;
    public final Vec3 start, end;
    public final double scale;
    public final int minBlockLight;
    private final int hash;

    public LaserData_BC8(LaserType laserType, Vec3 start, Vec3 end, double scale) {
        this(laserType, start, end, scale, 0);
    }

    public LaserData_BC8(LaserType laserType, Vec3 start, Vec3 end, double scale, int minBlockLight) {
        this.laserType = laserType;
        this.start = start;
        this.end = end;
        this.scale = scale;
        this.minBlockLight = minBlockLight;
        hash = Objects.hash(laserType, start, end, Double.doubleToLongBits(scale), minBlockLight);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() != getClass()) return false;
        LaserData_BC8 other = (LaserData_BC8) obj;
        if (laserType != other.laserType) return false;
        if (!start.equals(other.start)) return false;
        if (!end.equals(other.end)) return false;
        if (Double.compare(scale, other.scale) != 0) return false;
        if (minBlockLight != other.minBlockLight) return false;
        return true;
    }

    /** Holds information about a specific type of laser: what textures should be used for different parts. Compared
     * by identity (as in 1.12.2), so declare each one once as a constant -- see
     * {@code buildcraft.core.client.BuildCraftLaserManager}. */
    public static class LaserType {
        /** The square end-caps of the laser. These are never shrunk or stretched. */
        public final LaserRow capStart, capEnd;
        /** The (partially-drawn) first and last segments along the laser's length. */
        public final LaserRow start, end;
        /** The repeating middle segments, cycled through in order along the laser's length. */
        public final LaserRow[] variations;

        public LaserType(LaserRow capStart, LaserRow start, LaserRow[] middle, LaserRow end, LaserRow capEnd) {
            this.capStart = capStart;
            this.start = start;
            this.variations = middle;
            this.end = end;
            this.capEnd = capEnd;
        }

        /** The same row layout as {@code from}, but read off a different sprite. */
        public LaserType(LaserType from, Identifier replacementSprite) {
            this.capStart = new LaserRow(from.capStart, replacementSprite);
            this.capEnd = new LaserRow(from.capEnd, replacementSprite);
            this.start = new LaserRow(from.start, replacementSprite);
            this.end = new LaserRow(from.end, replacementSprite);
            this.variations = new LaserRow[from.variations.length];
            for (int i = 0; i < variations.length; i++) {
                this.variations[i] = new LaserRow(from.variations[i], replacementSprite);
            }
        }
    }

    /** One rectangle of a sprite. {@link #uMin}/{@link #vMin}/{@link #uMax}/{@link #vMax} are fractions of the
     * sprite (0..1); {@link #width}/{@link #height} are the rectangle's size in texture pixels, which is also the
     * size (before {@link LaserData_BC8#scale}) of the geometry drawn with it. */
    public static class LaserRow {
        /** A block-atlas sprite id, e.g. {@code buildcraft:lasers/marker_volume_connected}. */
        public final Identifier sprite;
        public final double uMin, vMin, uMax, vMax;
        public final int width, height;
        public final LaserSide[] validSides;

        public LaserRow(Identifier sprite, int uMin, int vMin, int uMax, int vMax, int textureSize, LaserSide... sides) {
            this.sprite = sprite;
            this.uMin = uMin / (double) textureSize;
            this.vMin = vMin / (double) textureSize;
            this.uMax = uMax / (double) textureSize;
            this.vMax = vMax / (double) textureSize;
            this.width = uMax - uMin;
            this.height = vMax - vMin;
            if (sides == null || sides.length == 0) {
                validSides = LaserSide.VALUES;
            } else {
                validSides = sides;
            }
        }

        public LaserRow(Identifier sprite, int uMin, int vMin, int uMax, int vMax, LaserSide... sides) {
            this(sprite, uMin, vMin, uMax, vMax, 16, sides);
        }

        public LaserRow(LaserRow from, Identifier sprite) {
            this.sprite = sprite;
            this.uMin = from.uMin;
            this.vMin = from.vMin;
            this.uMax = from.uMax;
            this.vMax = from.vMax;
            this.width = from.width;
            this.height = from.height;
            this.validSides = from.validSides;
        }
    }

    /** The four long faces of a laser, in its own local frame (local +X runs from start to end). */
    public enum LaserSide {
        TOP,
        BOTTOM,
        /** -Z (local) */
        LEFT,
        /** +Z (local) */
        RIGHT;

        public static final LaserSide[] VALUES = values();
    }
}
