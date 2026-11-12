package com.gradychastain.headcount.studio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Studio's password and sign-ins. The password is random, made on first use and shown only by /hca studio. A browser
 * that signs in gets a session token in a cookie; the server keeps only a hash of each token, with its expiry, in
 * studio/sessions so sign-ins survive restarts. Failed sign-ins are limited per address.
 */
public final class StudioAuth {
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no 0/o, 1/l/i
    private static final int MAX_FAILURES = 5;
    private static final long FAILURE_WINDOW_MS = 5 * 60_000L;

    private final File passwordFile;
    private final File sessionsFile;
    private final Logger log;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();     // sha256(token) -> expires at
    private final Map<String, List<Long>> failures = new ConcurrentHashMap<>(); // address -> failure times
    private volatile String password;

    public StudioAuth(File folder, Logger log) {
        this.passwordFile = new File(folder, "password");
        this.sessionsFile = new File(folder, "sessions");
        this.log = log;
        load();
    }

    private void load() {
        try {
            if (passwordFile.isFile()) password = Files.readString(passwordFile.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.warning("Couldn't read the Studio password file: " + e.getMessage());
        }
        if (password == null || password.length() < 8) newPassword();
        try {
            if (sessionsFile.isFile()) {
                long now = System.currentTimeMillis();
                for (String line : Files.readAllLines(sessionsFile.toPath(), StandardCharsets.UTF_8)) {
                    String[] p = line.trim().split(" ");
                    if (p.length != 2) continue;
                    try {
                        long until = Long.parseLong(p[1]);
                        if (until > now) sessions.put(p[0], until);
                    } catch (NumberFormatException ignored) {
                        // a damaged line: that browser signs in again
                    }
                }
            }
        } catch (IOException e) {
            log.warning("Couldn't read Studio's sign-ins: " + e.getMessage());
        }
    }

    /** The current password, for /hca studio. */
    public String password() {
        return password;
    }

    /** Makes a new password and signs every browser out. */
    public synchronized String newPassword() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) b.append('-');
            b.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        password = b.toString();
        sessions.clear();
        write(passwordFile, password + "\n");
        saveSessions();
        return password;
    }

    /** How many browsers are signed in now. */
    public int signedIn() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(t -> t <= now);
        return sessions.size();
    }

    /** True while this address has had too many wrong passwords recently. */
    public boolean lockedOut(String address) {
        List<Long> f = failures.get(address);
        if (f == null) return false;
        long now = System.currentTimeMillis();
        synchronized (f) {
            f.removeIf(t -> now - t > FAILURE_WINDOW_MS);
            return f.size() >= MAX_FAILURES;
        }
    }

    /** Checks a password; on success returns a new session token, otherwise null (and counts the failure). */
    public String signIn(String address, String attempt, long lengthMs) {
        String expected = password;
        String given = attempt == null ? "" : attempt.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        // Accept the password with or without its dashes.
        boolean ok = MessageDigest.isEqual(given.replace("-", "").getBytes(StandardCharsets.UTF_8),
                expected.replace("-", "").getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            failures.computeIfAbsent(address, k -> new ArrayList<>());
            List<Long> f = failures.get(address);
            synchronized (f) {
                f.add(System.currentTimeMillis());
            }
            return null;
        }
        failures.remove(address);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        sessions.put(hash(token), System.currentTimeMillis() + lengthMs);
        saveSessions();
        return token;
    }

    public boolean valid(String token) {
        if (token == null || token.isEmpty()) return false;
        Long until = sessions.get(hash(token));
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            sessions.remove(hash(token));
            return false;
        }
        return true;
    }

    public void signOut(String token) {
        if (token != null && sessions.remove(hash(token)) != null) saveSessions();
    }

    private synchronized void saveSessions() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Long> e : sessions.entrySet()) b.append(e.getKey()).append(' ').append(e.getValue()).append('\n');
        write(sessionsFile, b.toString());
    }

    private void write(File f, String text) {
        try {
            Files.createDirectories(f.getParentFile().toPath());
            File tmp = new File(f.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), text, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warning("Couldn't write " + f.getName() + " for Studio: " + e.getMessage());
        }
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
