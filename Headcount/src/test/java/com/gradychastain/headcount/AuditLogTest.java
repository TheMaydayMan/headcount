package com.gradychastain.headcount;

import com.gradychastain.headcount.audit.AuditLog;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.util.Cfg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rotation, compression, retention and search of the audit log files. */
class AuditLogTest {

    private static final Logger LOG = Logger.getLogger("audit-test");

    private static Cfg cfg(int keepDays) {
        // 0 MB is clamped to the 64 KB minimum
        return Cfg.of(Map.of("max-file-size-mb", 0, "keep-days", keepDays, "compress-old-files", true));
    }

    /** Waits for everything queued on the writer thread (search flushes on that thread first). */
    private static List<String> search(AuditLog audit, String term) {
        return audit.search(List.of(term), null, 100_000).join();
    }

    @Test
    void sizeRotationCompressesAndSearchReadsEveryFile(@TempDir File dir) {
        AuditLog audit = new AuditLog(LOG, dir);
        audit.configure(cfg(90), ZoneOffset.UTC);
        String pad = "x".repeat(80);
        for (int i = 0; i < 1200; i++) audit.log(AuditType.HEAD_CREATE, "head", "id-" + i, "pad", pad);
        List<String> all = search(audit, "pad=");
        String day = LocalDate.now(ZoneOffset.UTC).toString();

        assertTrue(new File(dir, "audit-" + day + ".log.gz").exists(), "first full file compressed");
        assertTrue(new File(dir, "audit-" + day + ".1.log.gz").exists(), "second full file compressed");
        assertTrue(new File(dir, "audit-" + day + ".2.log").exists(), "third file still open");
        assertFalse(new File(dir, "audit-" + day + ".log").exists(), "compressed file's plain copy removed");
        assertEquals(1200, all.size(), "no line lost across rotations");
        assertEquals(1, search(audit, "head=id-0 ").size(), "search finds a line inside a .gz file");
        assertEquals(1, search(audit, "head=id-1199 ").size(), "search finds a line in the open file");
        assertTrue(search(audit, "head=id-1199 ").getFirst().contains(" HEAD_CREATE "), "line format");
        audit.close();
    }

    @Test
    void dayChangeCompressesThePreviousDayAndAppliesRetention(@TempDir File dir) throws IOException {
        // UTC+14 and UTC-12 are 26 hours apart, so their dates always differ.
        ZoneId ahead = ZoneId.of("Etc/GMT-14");
        ZoneId behind = ZoneId.of("Etc/GMT+12");
        AuditLog audit = new AuditLog(LOG, dir);
        audit.configure(cfg(90), ahead);
        audit.log(AuditType.HEAD_CREATE, "head", "first");
        search(audit, "first");
        String day1 = LocalDate.now(ahead).toString();
        assertTrue(new File(dir, "audit-" + day1 + ".log").exists());

        audit.configure(cfg(90), behind);
        search(audit, "first");                      // the configure-time cleanup has run
        File old = new File(dir, "audit-2000-01-01.log");
        Files.writeString(old.toPath(), "2000-01-01 00:00:00 HEAD_CREATE head=ancient\n");

        audit.log(AuditType.HEAD_CREATE, "head", "second");   // first line of a new day
        search(audit, "second");
        String day2 = LocalDate.now(behind).toString();
        assertTrue(new File(dir, "audit-" + day1 + ".log.gz").exists(), "previous day compressed at the day change");
        assertFalse(new File(dir, "audit-" + day1 + ".log").exists());
        assertTrue(new File(dir, "audit-" + day2 + ".log").exists(), "new day's file");
        assertFalse(old.exists(), "files past keep-days deleted at the day change, not only on start and reload");
        assertEquals(1, search(audit, "head=first").size(), "the compressed day is still searchable");
        audit.close();
    }

    @Test
    void retentionOnConfigure(@TempDir File dir) throws IOException {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        File ancient = new File(dir, "audit-2000-01-02.log.gz");
        File recent = new File(dir, "audit-" + today.minusDays(3) + ".log");
        File unrelated = new File(dir, "notes.txt");
        Files.write(ancient.toPath(), new byte[] {0});
        Files.writeString(recent.toPath(), today.minusDays(3) + " 12:00:00 HEAD_CREATE head=recent\n");
        Files.writeString(unrelated.toPath(), "keep me");

        AuditLog audit = new AuditLog(LOG, dir);
        audit.configure(cfg(90), ZoneOffset.UTC);
        assertEquals(1, search(audit, "head=recent").size(), "the recent file was compressed and is searchable");
        assertFalse(ancient.exists(), "older than keep-days: deleted");
        assertFalse(recent.exists(), "older than today: compressed");
        assertTrue(new File(dir, recent.getName() + ".gz").exists());
        assertTrue(unrelated.exists(), "files that aren't audit logs are left alone");
        audit.close();

        File ancient2 = new File(dir, "audit-2000-01-03.log.gz");
        Files.write(ancient2.toPath(), new byte[] {0});
        AuditLog keepAll = new AuditLog(LOG, dir);
        keepAll.configure(cfg(0), ZoneOffset.UTC);
        search(keepAll, "anything");
        assertTrue(ancient2.exists(), "keep-days 0 keeps everything");
        keepAll.close();
    }
}
