package com.gradychastain.headcount.audit;

import com.gradychastain.headcount.util.Cfg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Append-only economy audit log written to {@code plugins/Headcount/audit/}. One line per event:
 * {@code <timestamp> <TYPE> key=value key=value ...}. Files rotate daily and by size, old files are optionally
 * gzipped, and files older than the retention period are deleted. Search scans files newest first.
 */
public final class AuditLog {
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Logger logger;
    private final File folder;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Headcount-Audit");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean enabled = true;
    private volatile Set<AuditType> types = EnumSet.allOf(AuditType.class);
    private volatile long maxBytes = 10L * 1024 * 1024;
    private volatile int keepDays = 90;
    private volatile boolean compress = true;
    private volatile boolean console = false;
    private volatile ZoneId zone = ZoneId.systemDefault();

    private BufferedWriter out;
    private LocalDate currentDay;
    private File currentFile;

    public AuditLog(Logger logger, File folder) {
        this.logger = logger;
        this.folder = folder;
    }

    public void configure(Cfg cfg, ZoneId zone) {
        enabled = cfg.getBool("enabled", true);
        Set<AuditType> t = EnumSet.noneOf(AuditType.class);
        Cfg typeCfg = cfg.getSection("types");
        for (AuditType type : AuditType.values()) {
            if (typeCfg.getBool(type.configKey(), true)) t.add(type);
        }
        types = t;
        maxBytes = Math.max(64 * 1024L, (long) (cfg.getDouble("max-file-size-mb", 10) * 1024 * 1024));
        keepDays = cfg.getInt("keep-days", 90);
        compress = cfg.getBool("compress-old-files", true);
        console = cfg.getBool("also-log-to-console", false);
        this.zone = zone;
        writer.execute(this::cleanup);
    }

    public boolean isEnabled(AuditType type) {
        return enabled && types.contains(type);
    }

    /** Logs an event. Field values are written verbatim with spaces replaced so lines stay parseable. */
    public void log(AuditType type, Object... keyValues) {
        if (!isEnabled(type)) return;
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) fields.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        log(type, fields);
    }

    public void log(AuditType type, Map<String, Object> fields) {
        if (!isEnabled(type)) return;
        ZonedDateTime now = ZonedDateTime.now(zone);
        StringBuilder sb = new StringBuilder(128).append(TS.format(now)).append(' ').append(type.name());
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (e.getValue() == null) continue;
            sb.append(' ').append(e.getKey()).append('=').append(clean(String.valueOf(e.getValue())));
        }
        String line = sb.toString();
        if (console) logger.info("[audit] " + line);
        try {
            writer.execute(() -> append(now.toLocalDate(), line));
        } catch (RuntimeException rejected) {
            logger.warning("Audit writer stopped; lost line: " + line);
        }
    }

    private static String clean(String v) {
        return v.replace('\n', ' ').replace('\r', ' ').replace(' ', '_');
    }

    private void append(LocalDate day, String line) {
        try {
            if (out == null || !day.equals(currentDay) || (currentFile != null && currentFile.length() > maxBytes)) openFile(day);
            out.write(line);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            logger.warning("Could not write audit log: " + e.getMessage());
        }
    }

    private void openFile(LocalDate day) throws IOException {
        boolean newDay = currentDay != null && !day.equals(currentDay);
        if (out != null) {
            out.close();
            out = null;
            if (compress && currentFile != null && (!day.equals(currentDay) || currentFile.length() > maxBytes)) gzip(currentFile);
        }
        if (!folder.exists() && !folder.mkdirs()) throw new IOException("Cannot create " + folder);
        currentDay = day;
        String base = "audit-" + DAY.format(day);
        File f = new File(folder, base + ".log");
        int n = 1;
        while ((f.exists() && f.length() > maxBytes) || new File(folder, f.getName() + ".gz").exists()) {
            f = new File(folder, base + "." + n++ + ".log");
        }
        currentFile = f;
        out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8));
        // Retention also runs at each day change, not only on start and reload, so long uptimes don't keep old files.
        if (newDay) cleanup();
    }

    private void gzip(File f) {
        File gz = new File(f.getParentFile(), f.getName() + ".gz");
        try (InputStream in = new FileInputStream(f); OutputStream o = new GZIPOutputStream(new FileOutputStream(gz))) {
            in.transferTo(o);
        } catch (IOException e) {
            logger.warning("Could not compress " + f.getName() + ": " + e.getMessage());
            return;
        }
        try {
            Files.deleteIfExists(f.toPath());
        } catch (IOException ignored) {
            // leave the plain file
        }
    }

    private void cleanup() {
        File[] files = folder.listFiles((d, n) -> n.startsWith("audit-"));
        if (files == null) return;
        LocalDate cutoff = LocalDate.now(zone).minusDays(Math.max(1, keepDays));
        for (File f : files) {
            LocalDate d = dayOf(f.getName());
            if (keepDays > 0 && d != null && d.isBefore(cutoff)) {
                if (!f.delete()) logger.fine("Could not delete old audit file " + f.getName());
                continue;
            }
            if (compress && f.getName().endsWith(".log") && !f.equals(currentFile) && d != null && d.isBefore(LocalDate.now(zone))) gzip(f);
        }
    }

    private static LocalDate dayOf(String name) {
        try {
            return LocalDate.parse(name.substring(6, 16), DAY);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Searches newest files first for lines containing every term (case-insensitive), optionally filtered by type.
     * Runs off the main thread.
     */
    public CompletableFuture<List<String>> search(List<String> terms, AuditType typeFilter, int limit) {
        CompletableFuture<List<String>> f = new CompletableFuture<>();
        writer.execute(() -> {
            try {
                if (out != null) out.flush();
            } catch (IOException ignored) {
                // search what is on disk
            }
            CompletableFuture.runAsync(() -> {
                try {
                    f.complete(doSearch(terms, typeFilter, limit));
                } catch (RuntimeException e) {
                    f.completeExceptionally(e);
                }
            });
        });
        return f;
    }

    private List<String> doSearch(List<String> terms, AuditType typeFilter, int limit) {
        List<String> lower = terms.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        File[] files = folder.listFiles((d, n) -> n.startsWith("audit-"));
        if (files == null) return List.of();
        Arrays.sort(files, Comparator.comparing(File::getName).reversed());
        List<String> results = new ArrayList<>();
        for (File file : files) {
            List<String> fileMatches = new ArrayList<>();
            try (BufferedReader r = reader(file)) {
                String line;
                while ((line = r.readLine()) != null) {
                    String l = line.toLowerCase(Locale.ROOT);
                    if (typeFilter != null && !line.contains(" " + typeFilter.name() + " ") && !line.contains(" " + typeFilter.name())) continue;
                    boolean all = true;
                    for (String t : lower) {
                        if (!l.contains(t)) { all = false; break; }
                    }
                    if (all) fileMatches.add(line);
                }
            } catch (IOException e) {
                logger.warning("Could not read " + file.getName() + ": " + e.getMessage());
            }
            // newest lines first within a file too
            for (int i = fileMatches.size() - 1; i >= 0; i--) {
                results.add(fileMatches.get(i));
                if (results.size() >= limit) return results;
            }
        }
        return results;
    }

    private static BufferedReader reader(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        if (f.getName().endsWith(".gz")) in = new GZIPInputStream(in);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /**
     * Moves every audit file into {@code dest} (a hard reset's backup folder); new lines start a fresh file. Waits
     * until it is done. @return how many files were moved
     */
    public int archive(File dest) {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        writer.execute(() -> {
            int moved = 0;
            try {
                if (out != null) out.close();
                out = null;
                currentDay = null;
                currentFile = null;
                File[] files = folder.listFiles((d, n) -> n.startsWith("audit-"));
                if (files != null && files.length > 0) {
                    if (!dest.exists() && !dest.mkdirs()) throw new IOException("Cannot create " + dest);
                    for (File file : files) {
                        Files.move(file.toPath(), new File(dest, file.getName()).toPath());
                        moved++;
                    }
                }
            } catch (IOException e) {
                logger.warning("Could not move the audit log to " + dest + ": " + e.getMessage());
            }
            f.complete(moved);
        });
        return f.join();
    }

    public void close() {
        writer.execute(() -> {
            try {
                if (out != null) out.close();
            } catch (IOException ignored) {
                // closing anyway
            }
            out = null;
        });
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
