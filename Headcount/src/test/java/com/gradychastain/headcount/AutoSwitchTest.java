package com.gradychastain.headcount;

import com.gradychastain.headcount.integration.AutoSwitch;
import com.gradychastain.headcount.integration.AutoSwitch.Change;
import com.gradychastain.headcount.integration.AutoSwitch.Job;
import com.gradychastain.headcount.integration.AutoSwitch.Kind;
import com.gradychastain.headcount.integration.AutoSwitch.Mode;
import com.gradychastain.headcount.util.YamlLines;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoSwitchTest {
    private static final List<String> MOD = List.of("litebans", "libertybans", "advancedban", "essentials");
    private static final List<Job> JOBS = List.of(
            new Job("pvp.provider", Kind.SINGLE, List.of("pvpmanager"), List.of("pvpmanager"), "included"),
            new Job("permissions.provider", Kind.SINGLE, List.of("luckperms"), List.of("luckperms", "vault"), "included"),
            new Job("moderation.provider", Kind.SINGLE, List.of("litebans", "libertybans", "advancedban"), MOD, "included"),
            new Job("moderation.kinds.jail", Kind.OVERRIDE, List.of(), MOD, "default"),
            new Job("economy.provider", Kind.SINGLE, List.of("vault"), List.of("vault"), "none"),
            new Job("items.providers", Kind.LIST, List.of("itemsadder", "oraxen", "nexo"), List.of("itemsadder", "oraxen", "nexo"), "none"),
            new Job("bedrock.provider", Kind.SINGLE, List.of("floodgate", "geyser"), List.of("floodgate", "geyser"), "none"));

    private static Map<String, Object> defaults() {
        Map<String, Object> m = new HashMap<>();
        m.put("pvp.provider", "included");
        m.put("permissions.provider", "included");
        m.put("moderation.provider", "included");
        m.put("economy.provider", "none");
        m.put("items.providers", List.of());
        m.put("bedrock.provider", "none");
        return m;
    }

    private static Map<String, Change> run(Mode mode, Map<String, Object> cur, Set<String> seen, Set<String> enabled, Set<String> installed) {
        AutoSwitch.Result r = AutoSwitch.decide(mode, JOBS, cur::get, seen, enabled, installed, (j, p) -> true);
        Map<String, Change> out = new HashMap<>();
        for (Change c : r.changes()) out.put(c.key(), c);
        return out;
    }

    private static Map<String, Change> run(Mode mode, Map<String, Object> cur, Set<String> seen, Set<String> now) {
        return run(mode, cur, seen, now, now);
    }

    @Test
    void newPluginTakesItsJob() {
        var c = run(Mode.NORMAL, defaults(), Set.of(), Set.of("pvpmanager", "luckperms"));
        assertEquals("pvpmanager", c.get("pvp.provider").to());
        assertEquals("included", c.get("pvp.provider").from());
        assertEquals("luckperms", c.get("permissions.provider").to());
        assertEquals(2, c.size());
    }

    @Test
    void pluginSeenBeforeIsNotNew() {
        // The owner set pvp back to included after a switch: it stays that way while PvPManager stays installed.
        assertTrue(run(Mode.NORMAL, defaults(), Set.of("pvpmanager"), Set.of("pvpmanager")).isEmpty());
    }

    @Test
    void anotherInstalledPluginKeepsTheJob() {
        Map<String, Object> cur = defaults();
        cur.put("moderation.provider", "litebans");
        var c = run(Mode.NORMAL, cur, Set.of("litebans"), Set.of("litebans", "advancedban"));
        assertFalse(c.containsKey("moderation.provider"));
        // ...but a configured plugin that isn't installed doesn't block a new one.
        cur.put("moderation.provider", "libertybans");
        c = run(Mode.NORMAL, cur, Set.of(), Set.of("advancedban"));
        assertEquals("advancedban", c.get("moderation.provider").to());
    }

    @Test
    void severalNewPickTheFirstInOrder() {
        var c = run(Mode.NORMAL, defaults(), Set.of(), Set.of("geyser", "floodgate", "advancedban", "litebans"));
        assertEquals("floodgate", c.get("bedrock.provider").to());
        assertEquals("litebans", c.get("moderation.provider").to());
    }

    @Test
    void essentialsNeverTakesModerationAndVaultNeverPermissions() {
        var c = run(Mode.FRESH, defaults(), Set.of(), Set.of("essentials", "vault"));
        assertFalse(c.containsKey("moderation.provider"));
        assertFalse(c.containsKey("permissions.provider"));
        assertEquals("vault", c.get("economy.provider").to());
    }

    @Test
    void canDoIsRespected() {
        AutoSwitch.Result r = AutoSwitch.decide(Mode.NORMAL, JOBS, defaults()::get, Set.of(), Set.of("vault"), Set.of("vault"),
                (j, p) -> !p.equals("vault"));
        assertTrue(r.changes().isEmpty());
        assertEquals(Set.of("vault"), r.record());
    }

    @Test
    void removedPluginGoesBackToFallback() {
        Map<String, Object> cur = defaults();
        cur.put("pvp.provider", "pvpmanager");
        cur.put("economy.provider", "vault");
        cur.put("permissions.provider", "vault");
        var c = run(Mode.NORMAL, cur, Set.of("pvpmanager", "vault"), Set.of());
        assertEquals("included", c.get("pvp.provider").to());
        assertEquals("none", c.get("economy.provider").to());
        assertEquals("included", c.get("permissions.provider").to());
    }

    @Test
    void removedPluginHandsOverToAnotherInstalledOne() {
        Map<String, Object> cur = defaults();
        cur.put("bedrock.provider", "floodgate");
        var c = run(Mode.NORMAL, cur, Set.of("floodgate", "geyser"), Set.of("geyser"));
        assertEquals("geyser", c.get("bedrock.provider").to());
    }

    @Test
    void removalIsOnlyNoticedForPluginsSeenBefore() {
        // Configured but never installed: the existing "isn't installed" warning covers it.
        Map<String, Object> cur = defaults();
        cur.put("pvp.provider", "pvpmanager");
        assertTrue(run(Mode.NORMAL, cur, Set.of(), Set.of()).isEmpty());
    }

    @Test
    void installedButDisabledIsNeitherNewNorGone() {
        Map<String, Object> cur = defaults();
        cur.put("permissions.provider", "luckperms");
        AutoSwitch.Result r = AutoSwitch.decide(Mode.NORMAL, JOBS, cur::get, Set.of("luckperms"), Set.of(), Set.of("luckperms"), (j, p) -> true);
        assertTrue(r.changes().isEmpty());
        assertEquals(Set.of("luckperms"), r.record()); // kept, so it isn't "new" next start
        r = AutoSwitch.decide(Mode.NORMAL, JOBS, defaults()::get, Set.of(), Set.of(), Set.of("luckperms"), (j, p) -> true);
        assertTrue(r.changes().isEmpty());
        assertTrue(r.record().isEmpty());
    }

    @Test
    void listJobsAddAndDrop() {
        Map<String, Object> cur = defaults();
        cur.put("items.providers", List.of("oraxen"));
        var c = run(Mode.NORMAL, cur, Set.of("oraxen"), Set.of("nexo", "itemsadder"));
        assertEquals(List.of("itemsadder", "nexo"), c.get("items.providers").to());
        assertEquals(3, c.get("items.providers").reasons().size());
        cur.put("items.providers", List.of("none"));
        c = run(Mode.NORMAL, cur, Set.of(), Set.of("nexo"));
        assertEquals(List.of("nexo"), c.get("items.providers").to());
    }

    @Test
    void overrideOnlyReactsToRemoval() {
        Map<String, Object> cur = defaults();
        cur.put("moderation.kinds.jail", "essentials");
        assertEquals("default", run(Mode.NORMAL, cur, Set.of("essentials"), Set.of()).get("moderation.kinds.jail").to());
        assertNull(run(Mode.NORMAL, cur, Set.of(), Set.of("litebans")).get("moderation.kinds.jail"));
    }

    @Test
    void freshTreatsEverythingAsNewAndNeverRemoves() {
        Map<String, Object> cur = defaults();
        var c = run(Mode.FRESH, cur, Set.of("pvpmanager", "luckperms"), Set.of("pvpmanager"));
        assertEquals("pvpmanager", c.get("pvp.provider").to());
        assertEquals(1, c.size());
    }

    @Test
    void recordOnlyChangesNothingButRecords() {
        AutoSwitch.Result r = AutoSwitch.decide(Mode.RECORD_ONLY, JOBS, defaults()::get, Set.of(), Set.of("pvpmanager"), Set.of("pvpmanager"), (j, p) -> true);
        assertTrue(r.changes().isEmpty());
        assertEquals(Set.of("pvpmanager"), r.record());
    }

    @Test
    void missingKeyStillDecides() {
        // The key isn't in the file: the change is reported (the caller logs it can't be written).
        Map<String, Object> cur = new HashMap<>();
        var c = run(Mode.NORMAL, cur, Set.of(), Set.of("pvpmanager"));
        assertEquals("pvpmanager", c.get("pvp.provider").to());
    }

    // ---------------------------------------------------------------- YamlLines

    private static final String SAMPLE = String.join("\n",
            "# header",
            "config-version: 3",
            "auto-switch: true",
            "",
            "pvp:",
            "  # Which plugin",
            "  provider: included   # keep me",
            "  included:",
            "    provider: nested-not-me",
            "  pvpmanager:",
            "    let-players-toggle: true",
            "moderation:",
            "  provider: \"included\"",
            "  kinds:",
            "    jail: essentials",
            "  other: {a: b}",
            "items:",
            "  providers:",
            "    - oraxen",
            "    # a comment",
            "    - nexo",
            "bedrock:",
            "  provider: none",
            "flow:",
            "  providers: []",
            "");

    @Test
    void yamlSetKeepsEverythingElse() {
        List<String> lines = YamlLines.lines(SAMPLE);
        assertTrue(YamlLines.set(lines, "pvp.provider", "pvpmanager"));
        assertEquals("  provider: pvpmanager   # keep me", lines.get(6));
        assertEquals("    provider: nested-not-me", lines.get(8));
        assertTrue(YamlLines.set(lines, "moderation.provider", "litebans"));
        assertEquals("  provider: litebans", lines.get(12));
        assertTrue(YamlLines.set(lines, "moderation.kinds.jail", "default"));
        assertEquals("    jail: default", lines.get(14));
        assertTrue(YamlLines.set(lines, "bedrock.provider", "floodgate"));
        String out = YamlLines.join(lines, SAMPLE);
        assertEquals(SAMPLE.replace("provider: included   # keep me", "provider: pvpmanager   # keep me")
                .replace("provider: \"included\"", "provider: litebans").replace("jail: essentials", "jail: default")
                .replace("bedrock:\n  provider: none", "bedrock:\n  provider: floodgate"), out);
    }

    @Test
    void yamlLists() {
        List<String> lines = YamlLines.lines(SAMPLE);
        assertEquals(List.of("oraxen", "nexo"), YamlLines.get(lines, "items.providers"));
        assertEquals(List.of(), YamlLines.get(lines, "flow.providers"));
        assertTrue(YamlLines.set(lines, "items.providers", "[nexo, itemsadder]"));
        assertTrue(YamlLines.set(lines, "flow.providers", "[oraxen]"));
        String out = YamlLines.join(lines, SAMPLE);
        assertTrue(out.contains("items:\n  providers: [nexo, itemsadder]\n    # a comment\nbedrock:"), out);
        assertTrue(out.contains("flow:\n  providers: [oraxen]\n"), out);
    }

    @Test
    void yamlRefusesWhatItCantEdit() {
        List<String> lines = YamlLines.lines(SAMPLE);
        assertFalse(YamlLines.set(lines, "moderation.other.a", "x"));   // inside a flow map
        assertFalse(YamlLines.set(lines, "pvp.included", "x"));         // a mapping
        assertFalse(YamlLines.set(lines, "nothing.here", "x"));
        assertEquals(SAMPLE, YamlLines.join(lines, SAMPLE));
        assertEquals("included", YamlLines.get(lines, "moderation.provider"));
        assertNull(YamlLines.get(lines, "moderation.other"));
    }

    @Test
    void yamlKeepsWindowsLineEndings() {
        String crlf = "a:\r\n  provider: none\r\n";
        List<String> lines = YamlLines.lines(crlf);
        assertTrue(YamlLines.set(lines, "a.provider", "vault"));
        assertEquals("a:\r\n  provider: vault\r\n", YamlLines.join(lines, crlf));
    }

    @Test
    void bundledIntegrationsFileEditsCleanly() throws Exception {
        String text;
        try (var in = AutoSwitchTest.class.getResourceAsStream("/integrations.yml")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        List<String> lines = YamlLines.lines(text);
        for (String key : List.of("pvp.provider", "placeholders.provider", "economy.provider", "permissions.provider",
                "moderation.provider", "discord.provider", "bedrock.provider", "player-state.provider")) {
            Object before = YamlLines.get(lines, key);
            assertTrue(before instanceof String, key);
            assertTrue(YamlLines.set(lines, key, "changed"), key);
            assertEquals("changed", YamlLines.get(lines, key), key);
        }
        assertEquals(List.of(), YamlLines.get(lines, "items.providers"));
        assertTrue(YamlLines.set(lines, "items.providers", "[nexo]"));
        // The default kinds: {} is a flow map, so a kind can't be set there; it is only ever switched once written out.
        assertFalse(YamlLines.set(lines, "moderation.kinds.jail", "default"));
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        y.loadFromString(YamlLines.join(lines, text));
        assertEquals("changed", y.getString("pvp.provider"));
        assertEquals("changed", y.getString("bedrock.provider"));
        assertEquals(List.of("nexo"), y.getStringList("items.providers"));
        assertEquals(text.lines().count(), YamlLines.join(lines, text).lines().count());
    }
}
