package com.gradychastain.headcount.integration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * The integrations.yml auto-switch rules, kept free of Bukkit so they can be tested. {@link IntegrationAutoSwitch}
 * feeds it what is installed and what the file says, then writes the result back.
 * <p>
 * For each job:
 * <ul>
 *   <li>A plugin that was the job's provider and has been removed since the last start hands the job to another
 *   installed plugin that can do it, or back to the job's fallback ({@code included}, or {@code none}).</li>
 *   <li>A plugin that is new since the last start takes the job, unless the job's provider is another plugin that is
 *   still installed. When several new plugins can do the same job, the first in the job's order wins.</li>
 *   <li>A list job (custom items) adds new plugins and drops removed ones.</li>
 *   <li>An override job (a moderation kind) only reacts to removals, going back to {@code default}.</li>
 * </ul>
 * On a fresh install every installed plugin counts as new. With no record of the last start (an upgrade from 1.1.0),
 * or with auto-switch off, nothing changes and only the record is written.
 */
public final class AutoSwitch {
    private AutoSwitch() {}

    public enum Kind { SINGLE, LIST, OVERRIDE }

    /**
     * @param key      the dotted path in integrations.yml
     * @param takeover providers that take the job when they appear, best first
     * @param known    every plugin provider the job accepts (removal of any of these is noticed)
     * @param fallback what the job goes back to when its plugin is removed and no other can take it
     */
    public record Job(String key, Kind kind, List<String> takeover, List<String> known, String fallback) {}

    /** Why a value changed: a plugin appeared (added true) or disappeared. */
    public record Reason(String provider, boolean added) {}

    /** One job's change. {@code from} and {@code to} are a String, or a List for list jobs. */
    public record Change(String key, Object from, Object to, List<Reason> reasons) {}

    public enum Mode {
        /** integrations.yml was created this start: every installed plugin is new. */
        FRESH,
        /** There is a record of the last start: compare with it. */
        NORMAL,
        /** No record yet (upgrade), or auto-switch is off: change nothing. */
        RECORD_ONLY
    }

    public record Result(List<Change> changes, Set<String> record) {}

    /**
     * @param current   the job's value in the file: a String, a List of Strings, or null when the key isn't there
     * @param seen      providers installed at the last start
     * @param enabled   providers whose plugin is installed and enabled now
     * @param installed providers whose plugin is installed now, enabled or not
     * @param canDo     whether an enabled provider can actually do a job right now (Vault needs an economy plugin)
     */
    public static Result decide(Mode mode, List<Job> jobs, Function<String, Object> current, Set<String> seen,
                                Set<String> enabled, Set<String> installed, BiPredicate<Job, String> canDo) {
        // Remember what is enabled now, and keep plugins that are installed but failed to enable this time, so one bad
        // start doesn't make them look new on the next.
        Set<String> record = new LinkedHashSet<>(enabled);
        for (String s : seen) if (installed.contains(s)) record.add(s);
        List<Change> changes = new ArrayList<>();
        if (mode == Mode.RECORD_ONLY) return new Result(changes, record);
        boolean fresh = mode == Mode.FRESH;
        for (Job job : jobs) {
            Change c = switch (job.kind()) {
                case SINGLE -> single(job, norm(current.apply(job.key())), fresh, seen, enabled, installed, canDo);
                case LIST -> list(job, current.apply(job.key()), fresh, seen, enabled, installed, canDo);
                case OVERRIDE -> override(job, norm(current.apply(job.key())), fresh, seen, installed);
            };
            if (c != null) changes.add(c);
        }
        return new Result(changes, record);
    }

    private static String norm(Object v) {
        if (v == null) return null;
        return String.valueOf(v).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean gone(String provider, boolean fresh, Set<String> seen, Set<String> installed) {
        return !fresh && seen.contains(provider) && !installed.contains(provider);
    }

    private static boolean isNew(String provider, boolean fresh, Set<String> seen, Set<String> enabled) {
        return enabled.contains(provider) && (fresh || !seen.contains(provider));
    }

    private static Change single(Job job, String cur, boolean fresh, Set<String> seen, Set<String> enabled, Set<String> installed,
                                 BiPredicate<Job, String> canDo) {
        if (cur != null && job.known().contains(cur) && gone(cur, fresh, seen, installed)) {
            String to = job.fallback();
            for (String p : job.takeover()) {
                if (!p.equals(cur) && enabled.contains(p) && canDo.test(job, p)) {
                    to = p;
                    break;
                }
            }
            List<Reason> reasons = new ArrayList<>();
            reasons.add(new Reason(cur, false));
            if (!to.equals(job.fallback())) reasons.add(new Reason(to, true));
            return new Change(job.key(), cur, to, reasons);
        }
        String pick = null;
        for (String p : job.takeover()) {
            if (isNew(p, fresh, seen, enabled) && canDo.test(job, p)) {
                pick = p;
                break;
            }
        }
        if (pick == null || pick.equals(cur)) return null;
        // Another plugin the owner chose is still installed: leave it alone.
        if (cur != null && job.known().contains(cur) && installed.contains(cur)) return null;
        return new Change(job.key(), cur == null ? job.fallback() : cur, pick, List.of(new Reason(pick, true)));
    }

    private static Change list(Job job, Object raw, boolean fresh, Set<String> seen, Set<String> enabled, Set<String> installed,
                               BiPredicate<Job, String> canDo) {
        List<String> before = new ArrayList<>();
        if (raw instanceof List<?> l) {
            for (Object o : l) before.add(norm(o));
        } else if (raw != null && !String.valueOf(raw).isBlank()) {
            before.add(norm(raw));
        }
        List<String> after = new ArrayList<>(before);
        List<Reason> reasons = new ArrayList<>();
        for (String p : before) {
            if (job.known().contains(p) && gone(p, fresh, seen, installed)) {
                after.remove(p);
                reasons.add(new Reason(p, false));
            }
        }
        for (String p : job.takeover()) {
            if (isNew(p, fresh, seen, enabled) && canDo.test(job, p) && !after.contains(p)) {
                after.remove("none");
                after.add(p);
                reasons.add(new Reason(p, true));
            }
        }
        if (reasons.isEmpty()) return null;
        return new Change(job.key(), before, after, reasons);
    }

    private static Change override(Job job, String cur, boolean fresh, Set<String> seen, Set<String> installed) {
        if (cur == null || !job.known().contains(cur) || !gone(cur, fresh, seen, installed)) return null;
        return new Change(job.key(), cur, job.fallback(), List.of(new Reason(cur, false)));
    }

    /** How a value is written back into the file. */
    public static String yaml(Object value) {
        if (value instanceof List<?> l) return "[" + String.join(", ", l.stream().map(String::valueOf).toList()) + "]";
        return String.valueOf(value);
    }
}
