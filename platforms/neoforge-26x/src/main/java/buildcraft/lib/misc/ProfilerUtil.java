/*
 * Copyright (c) 2026 Joshua Kac -- NeoForge port (BuildCraft 10)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL
 * was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package buildcraft.lib.misc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.util.List;

import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;

import buildcraft.api.core.BCLog;

/** Provides a few methods for writing the results of a vanilla profiler to a file or something else.
 *
 * <p>1.12.2's {@code net.minecraft.profiler.Profiler} was one concrete class that both recorded sections
 * ({@code startSection}/{@code endSection}) and could be read back afterwards
 * ({@code getProfilingData(String)} -> {@code List<Profiler.Result>}). That split into two interfaces here:
 * {@link ProfilerFiller} only records ({@code push}/{@code pop}, what {@link ProfilerEntry} wraps below), and
 * reading back the results needs a {@link ProfileResults} (obtained from a profiler that also implements
 * {@code ProfileCollector}, via its {@code getResults()}) -- {@link ProfileResults#getTimes(String)} returning
 * {@code List<}{@link ResultField}{@code >} is the direct replacement for {@code getProfilingData}, and
 * {@link ResultField}'s {@code name}/{@code percentage}/{@code globalPercentage} fields are
 * {@code Profiler.Result}'s {@code profilerName}/{@code usePercentage}/{@code totalUsePercentage} renamed. The
 * writing methods below take that {@link ProfileResults} directly rather than the profiler itself. */
public class ProfilerUtil {

    /** Calls {@link #writeProfilerResults(ProfileResults, String, ILogAcceptor)} with {@link System#out} as the
     * {@link ILogAcceptor}. */
    public static void printProfilerResults(ProfileResults results, String rootName) {
        printProfilerResults(results, rootName, -1);
    }

    /** Calls {@link #writeProfilerResults(ProfileResults, String, ILogAcceptor)} with {@link System#out} as the
     * {@link ILogAcceptor}. */
    public static void printProfilerResults(ProfileResults results, String rootName, long totalNanoseconds) {
        writeProfilerResults(results, rootName, totalNanoseconds, System.out::println);
    }

    /** Calls {@link #writeProfilerResults(ProfileResults, String, ILogAcceptor)} with {@link BCLog#logger
     * BCLog.logger}::{@link org.apache.logging.log4j.Logger#info(CharSequence) info} as the {@link ILogAcceptor}. */
    public static void logProfilerResults(ProfileResults results, String rootName) {
        logProfilerResults(results, rootName, -1);
    }

    /** Calls {@link #writeProfilerResults(ProfileResults, String, ILogAcceptor)} with {@link BCLog#logger
     * BCLog.logger}::{@link org.apache.logging.log4j.Logger#info(CharSequence) info} as the {@link ILogAcceptor}. */
    public static void logProfilerResults(ProfileResults results, String rootName, long totalNanoseconds) {
        writeProfilerResults(results, rootName, totalNanoseconds, BCLog.logger::info);
    }

    /** Calls {@link #writeProfilerResults(ProfileResults, String, ILogAcceptor)} but saves the output to a file.
     *
     * @throws IOException if the file exists but is a directory rather than a regular file, does not exist but cannot
     *             be created, or cannot be opened for any other reason, or if an I/O exception occurred while writing
     *             the profiler results. */
    public static void saveProfilerResults(ProfileResults results, String rootName, Path dest) throws IOException {
        saveProfilerResults(results, rootName, -1, dest);
    }

    /** Calls {@link #writeProfilerResults(ProfileResults, String, ILogAcceptor)} but saves the output to a file.
     *
     * @throws IOException if the file exists but is a directory rather than a regular file, does not exist but cannot
     *             be created, or cannot be opened for any other reason, or if an I/O exception occurred while wrting
     *             the profiler results. */
    public static void saveProfilerResults(ProfileResults results, String rootName, File dest) throws IOException {
        dest = dest.getAbsoluteFile();
        dest.getParentFile().mkdirs();
        saveProfilerResults(results, rootName, -1, dest.toPath());
    }

    /** Calls {@link #writeProfilerResults(ProfileResults, String, ILogAcceptor)} but saves the output to a file.
     *
     * @param totalNanoseconds The total amount of time that the profiler's root section took, or -1 if this isn't
     *            known.
     * @throws IOException if the file exists but is a directory rather than a regular file, does not exist but cannot
     *             be created, or cannot be opened for any other reason, or if an I/O exception occurred while writing
     *             the profiler results. */
    public static void saveProfilerResults(ProfileResults results, String rootName, long totalNanoseconds, Path dest)
        throws IOException {
        try (BufferedWriter br = Files.newBufferedWriter(dest, StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            writeProfilerResults(results, rootName, str -> {
                br.write(str);
                br.newLine();
            });
            br.flush();
        }
    }

    /** @param rootName The base name to use. Most of the time you just want to use "root".
     * @param dest The method to call with the finished lines.
     * @throws E if {@link ILogAcceptor#write(String)} throws an exception. */
    public static <E extends Throwable> void writeProfilerResults(ProfileResults results, String rootName,
        ILogAcceptor<E> dest) throws E {
        writeProfilerResults(results, rootName, -1, dest);
    }

    /** @param rootName The base name to use. Most of the time you just want to use "root".
     * @param totalNanoseconds The total amount of time that the profiler's root section took, or -1 if this isn't
     *            known.
     * @param dest The method to call with the finished lines.
     * @throws E if {@link ILogAcceptor#write(String)} throws an exception. */
    public static <E extends Throwable> void writeProfilerResults(ProfileResults results, String rootName,
        long totalNanoseconds, ILogAcceptor<E> dest) throws E {
        writeProfilerResults_Internal(results, rootName, totalNanoseconds, 0, dest);
    }

    private static <E extends Throwable> void writeProfilerResults_Internal(ProfileResults results, String sectionName,
        long totalNanoseconds, int indent, ILogAcceptor<E> dest) throws E {

        List<ResultField> list = results.getTimes(sectionName);

        if (list != null && list.size() >= 3) {
            for (int i = 1; i < list.size(); ++i) {
                ResultField result = list.get(i);
                StringBuilder builder = new StringBuilder();
                builder.append(StringUtilBC.formatDirect("[%02d] ", indent));

                for (int j = 0; j < indent; ++j) {
                    builder.append("|   ");
                }

                builder.append(result.name);
                builder.append(" - ");
                builder.append(StringUtilBC.formatDirect("%.2f", result.percentage));
                builder.append("%/");
                builder.append(StringUtilBC.formatDirect("%.2f", result.globalPercentage));
                if (totalNanoseconds > 0) {
                    builder.append(" (");
                    long nano = (long) (result.globalPercentage * totalNanoseconds / 100);
                    if (nano < 99_999) {
                        builder.append(NumberFormat.getInstance().format(nano));
                        builder.append("ns");
                    } else if (nano < 99_999_999) {
                        builder.append(NumberFormat.getInstance().format(nano / 1000));
                        builder.append("µs");
                    } else if (nano < 99_999_999_999L) {
                        builder.append(NumberFormat.getInstance().format(nano / 1_000_000));
                        builder.append("ms");
                    } else {
                        builder.append(NumberFormat.getInstance().format(nano / 1_000_000_000));
                        builder.append("s");
                    }
                    builder.append(")");
                }
                dest.write(builder.toString());

                if (!"unspecified".equals(result.name)) {
                    if (indent > 20) {
                        // Something probably went wrong
                        dest.write("[[ Too deep! ]]");
                        continue;
                    }
                    writeProfilerResults_Internal(results, sectionName + "." + result.name, totalNanoseconds,
                        indent + 1, dest);
                }
            }
        }
    }

    /** @param <E> The base exception type that {@link #write(String)} might throw. Used to allow writing to files to
     *            throw a (checked) exception, but {@link System#out} to never throw. */
    public interface ILogAcceptor<E extends Throwable> {
        void write(String line) throws E;
    }

    public interface ProfilerEntry {
        void startSection(String name);

        void endSection();

        default void endStartSection(String name) {
            endSection();
            startSection(name);
        }
    }

    /** True if {@code p} actually records anything. 1.12.2's {@code Profiler} had a {@code profilingEnabled}
     * field for this; {@link ProfilerFiller} is an interface with no such field, and the modern equivalent of
     * "this profiler is off" is being the shared {@link InactiveProfiler#INSTANCE} no-op singleton. */
    private static boolean isActive(ProfilerFiller p) {
        return p != InactiveProfiler.INSTANCE;
    }

    public static ProfilerEntry createEntry(ProfilerFiller p1, ProfilerFiller p2) {
        if (isActive(p1)) {
            if (isActive(p2)) {
                return new ProfilerEntry2(p1, p2);
            } else {
                return new ProfilerEntry1(p1);
            }
        } else {
            if (isActive(p2)) {
                return new ProfilerEntry1(p2);
            } else {
                return ProfilerEntry0.INSTANCE;
            }
        }
    }

    static enum ProfilerEntry0 implements ProfilerEntry {
        INSTANCE;

        @Override
        public void startSection(String name) {
            // NO-OP
        }

        @Override
        public void endSection() {
            // NO-OP
        }
    }

    static final class ProfilerEntry1 implements ProfilerEntry {
        final ProfilerFiller p;

        ProfilerEntry1(ProfilerFiller p) {
            this.p = p;
        }

        @Override
        public void startSection(String name) {
            p.push(name);
        }

        @Override
        public void endSection() {
            p.pop();
        }
    }

    static final class ProfilerEntry2 implements ProfilerEntry {
        final ProfilerFiller p1, p2;

        ProfilerEntry2(ProfilerFiller p1, ProfilerFiller p2) {
            this.p1 = p1;
            this.p2 = p2;
        }

        @Override
        public void startSection(String name) {
            p1.push(name);
            p2.push(name);
        }

        @Override
        public void endSection() {
            p1.pop();
            p2.pop();
        }
    }

}
