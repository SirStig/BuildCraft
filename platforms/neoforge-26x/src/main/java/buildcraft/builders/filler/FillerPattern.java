/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This file is part of the BuildCraft 10 port and is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located as "LICENSE.PORT" in the BuildCraft
 * source code distribution.
 */
package buildcraft.builders.filler;

/**
 * One shape the Filler can build, ported from 1.12.2's {@code buildcraft.builders.snapshot.pattern.Pattern}
 * (itself a {@code BCStatement}/{@code IFillerPattern}). The whole statement/gate-action system that let 1.12.2
 * drag a pattern icon onto the filler from a gate GUI ({@code IStatementContainer}, {@code IActionExternal},
 * {@code FullStatement<IFillerPattern>}) is not ported -- see {@link TileFiller}'s own javadoc for why -- so this
 * is a plain small class, cycled through by a GUI button instead of dragged in. {@link #fill} is the one part of
 * every original {@code Pattern} subclass that survives close to verbatim: {@code fillTemplate(IFilledTemplate,
 * IStatementParameter[])} becomes {@code fill(FilledArea, int[])}, with {@code IStatementParameter} enum instances
 * replaced by plain {@code int} indices into {@link #paramValueCount}/{@link #paramLabelKey} so a GUI button can
 * cycle them without the original's drag-and-drop parameter widgets.
 */
public abstract class FillerPattern {
    public final String id;

    protected FillerPattern(String id) {
        this.id = id;
    }

    public String translationKey() {
        return "fillerpattern." + id;
    }

    /** How many cyclable parameters this pattern exposes (0-2 for every pattern ported so far). */
    public int paramCount() {
        return 0;
    }

    public int defaultParam(int index) {
        return 0;
    }

    /** How many distinct values parameter {@code index} can cycle through. */
    public int paramValueCount(int index) {
        return 1;
    }

    /** A short translation key describing parameter {@code index}'s current {@code value} -- shown on the GUI's
     * parameter button. */
    public String paramLabelKey(int index, int value) {
        return "buildcraft.gui.filler.param_none";
    }

    /** Writes this pattern's shape into {@code area} (freshly all-{@code false}), using the current values of
     * {@code params} (one entry per {@link #paramCount()} slot, already clamped to
     * {@code [0, paramValueCount(index))}). */
    public abstract void fill(FilledArea area, int[] params);
}
