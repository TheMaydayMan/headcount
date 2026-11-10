package com.gradychastain.headcount.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite access through one connection owned by one writer thread. Every statement runs on that thread in
 * submission order, so writes queued from the main thread are applied exactly in the order they happened.
 */
public final class Database {
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection c) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlTask {
        void run(Connection c) throws SQLException;
    }

    private static final int SCHEMA_VERSION = 1;

    private final Logger logger;
    private final File file;
    private final ExecutorService executor;
    private Thread dbThread;
    private Connection connection;

    public Database(Logger logger, File file) {
        this.logger = logger;
        this.file = file;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Headcount-Database");
            t.setDaemon(true);
            dbThread = t;
            return t;
        });
    }

    public void open() {
        sync(c -> null); // starts the thread
        sync(c -> {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite driver not found", e);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA synchronous=NORMAL");
                s.execute("PRAGMA busy_timeout=5000");
            }
            createSchema(connection);
            return null;
        });
    }

    private void createSchema(Connection c) throws SQLException {
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)",
                """
                CREATE TABLE IF NOT EXISTS players (
                  uuid TEXT PRIMARY KEY, name TEXT NOT NULL, name_lower TEXT NOT NULL,
                  first_join INTEGER NOT NULL DEFAULT 0, last_join INTEGER NOT NULL DEFAULT 0, last_quit INTEGER NOT NULL DEFAULT 0,
                  points INTEGER NOT NULL DEFAULT 0, points_next INTEGER NOT NULL DEFAULT 0,
                  streak INTEGER NOT NULL DEFAULT 0, last_score INTEGER, last_unique INTEGER,
                  sidebar INTEGER NOT NULL DEFAULT 0, equipped_title TEXT,
                  texture_value TEXT, texture_signature TEXT,
                  setup_cycle INTEGER NOT NULL DEFAULT 0, vault_size INTEGER NOT NULL DEFAULT 0,
                  next_heads_mod TEXT, next_size_mod TEXT, rollover_override REAL,
                  score_bonus INTEGER NOT NULL DEFAULT 0, bedrock INTEGER NOT NULL DEFAULT 0)""",
                "CREATE INDEX IF NOT EXISTS players_name ON players(name_lower)",
                """
                CREATE TABLE IF NOT EXISTS cycles (
                  id INTEGER PRIMARY KEY, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL, length_days INTEGER NOT NULL,
                  vault_size INTEGER NOT NULL, starting_heads INTEGER NOT NULL, state TEXT NOT NULL,
                  ended_ms INTEGER, summary TEXT)""",
                """
                CREATE TABLE IF NOT EXISTS cycle_players (
                  cycle INTEGER NOT NULL, uuid TEXT NOT NULL, first_join_ms INTEGER NOT NULL DEFAULT 0,
                  active INTEGER NOT NULL DEFAULT 0, playtime_ms INTEGER NOT NULL DEFAULT 0,
                  kills INTEGER NOT NULL DEFAULT 0, deaths INTEGER NOT NULL DEFAULT 0, trades INTEGER NOT NULL DEFAULT 0,
                  heads_in INTEGER NOT NULL DEFAULT 0, heads_out INTEGER NOT NULL DEFAULT 0,
                  points_spent INTEGER NOT NULL DEFAULT 0, purchases INTEGER NOT NULL DEFAULT 0,
                  PRIMARY KEY (cycle, uuid))""",
                """
                CREATE TABLE IF NOT EXISTS vault_items (
                  uuid TEXT NOT NULL, slot INTEGER NOT NULL, head_id TEXT NOT NULL, head_owner TEXT NOT NULL,
                  head_owner_name TEXT NOT NULL DEFAULT '', created_ms INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL DEFAULT 'cycle-start',
                  cycle INTEGER NOT NULL, item BLOB, PRIMARY KEY (uuid, slot))""",
                "CREATE INDEX IF NOT EXISTS vault_items_head ON vault_items(head_id)",
                """
                CREATE TABLE IF NOT EXISTS heads (
                  head_id TEXT PRIMARY KEY, owner TEXT NOT NULL, cycle INTEGER NOT NULL, source TEXT NOT NULL,
                  created_ms INTEGER NOT NULL, recipient TEXT)""",
                """
                CREATE TABLE IF NOT EXISTS placed_heads (
                  world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, head_id TEXT NOT NULL,
                  item BLOB, PRIMARY KEY (world, x, y, z))""",
                """
                CREATE TABLE IF NOT EXISTS kill_drops (
                  cycle INTEGER NOT NULL, killer TEXT NOT NULL, victim TEXT NOT NULL, ms INTEGER NOT NULL,
                  PRIMARY KEY (cycle, killer, victim))""",
                """
                CREATE TABLE IF NOT EXISTS results (
                  cycle INTEGER NOT NULL, uuid TEXT NOT NULL, name TEXT, active INTEGER NOT NULL,
                  unique_heads INTEGER NOT NULL, total_heads INTEGER NOT NULL, own_heads INTEGER NOT NULL,
                  score INTEGER, points_earned INTEGER NOT NULL, points_spent INTEGER NOT NULL,
                  points_before INTEGER NOT NULL, points_after INTEGER NOT NULL, rolled_over INTEGER NOT NULL,
                  streak INTEGER NOT NULL, rank INTEGER, percentile REAL, outcomes TEXT, playtime_ms INTEGER NOT NULL,
                  kills INTEGER NOT NULL, deaths INTEGER NOT NULL, trades INTEGER NOT NULL,
                  PRIMARY KEY (cycle, uuid))""",
                "CREATE INDEX IF NOT EXISTS results_uuid ON results(uuid)",
                """
                CREATE TABLE IF NOT EXISTS titles (
                  uuid TEXT NOT NULL, title TEXT NOT NULL, granted_ms INTEGER NOT NULL, source TEXT,
                  PRIMARY KEY (uuid, title))""",
                """
                CREATE TABLE IF NOT EXISTS purchases (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, cycle INTEGER NOT NULL, uuid TEXT NOT NULL, tier TEXT NOT NULL,
                  entry TEXT NOT NULL, price INTEGER NOT NULL, ms INTEGER NOT NULL)""",
                "CREATE INDEX IF NOT EXISTS purchases_uuid ON purchases(uuid)",
                "CREATE TABLE IF NOT EXISTS shop_stock (entry TEXT NOT NULL, cycle INTEGER NOT NULL, sold INTEGER NOT NULL, PRIMARY KEY (entry, cycle))",
                """
                CREATE TABLE IF NOT EXISTS active_actions (
                  id TEXT PRIMARY KEY, uuid TEXT NOT NULL, source TEXT NOT NULL, action_key TEXT NOT NULL, type TEXT NOT NULL,
                  spec TEXT NOT NULL, vars TEXT, data TEXT, state TEXT NOT NULL, created_ms INTEGER NOT NULL,
                  duration_kind TEXT NOT NULL, time_mode TEXT NOT NULL, ends_ms INTEGER, remaining_ms INTEGER,
                  cycles_left INTEGER, next_repeat_ms INTEGER, cycle INTEGER NOT NULL)""",
                "CREATE INDEX IF NOT EXISTS active_actions_uuid ON active_actions(uuid)",
                """
                CREATE TABLE IF NOT EXISTS pending_hooks (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, hook TEXT NOT NULL, cycle INTEGER NOT NULL,
                  vars TEXT, created_ms INTEGER NOT NULL)""",
                """
                CREATE TABLE IF NOT EXISTS pending_actions (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, source TEXT NOT NULL, spec TEXT NOT NULL,
                  vars TEXT, created_ms INTEGER NOT NULL)""",
                """
                CREATE TABLE IF NOT EXISTS pending_items (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, item BLOB NOT NULL, reason TEXT,
                  created_ms INTEGER NOT NULL)""",
                """
                CREATE TABLE IF NOT EXISTS pending_heads (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, owner TEXT NOT NULL, owner_name TEXT NOT NULL,
                  amount INTEGER NOT NULL, source TEXT, for_cycle INTEGER NOT NULL)""",
                """
                CREATE TABLE IF NOT EXISTS trade_items (
                  trade_id TEXT NOT NULL, owner TEXT NOT NULL, slot INTEGER NOT NULL, item BLOB NOT NULL,
                  PRIMARY KEY (trade_id, owner, slot))""",
                "CREATE INDEX IF NOT EXISTS trade_items_owner ON trade_items(owner)",
                "CREATE TABLE IF NOT EXISTS flags (uuid TEXT NOT NULL, flag TEXT NOT NULL, PRIMARY KEY (uuid, flag))",
                "CREATE TABLE IF NOT EXISTS cycle_warnings (cycle INTEGER NOT NULL, warning TEXT NOT NULL, PRIMARY KEY (cycle, warning))",
        };
        try (Statement s = c.createStatement()) {
            for (String sql : ddl) s.execute(sql);
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO meta(key, value) VALUES ('schema', ?)")) {
            ps.setString(1, Integer.toString(SCHEMA_VERSION));
            ps.executeUpdate();
        }
    }

    /** Queues work and returns its result asynchronously. */
    public <T> CompletableFuture<T> query(SqlWork<T> work) {
        CompletableFuture<T> f = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    f.complete(work.run(connection));
                } catch (Throwable t) {
                    logger.log(Level.SEVERE, "Database error", t);
                    f.completeExceptionally(t);
                }
            });
        } catch (RuntimeException rejected) {
            f.completeExceptionally(rejected);
        }
        return f;
    }

    /** Queues a write. Errors are logged. */
    public void write(SqlTask task) {
        query(c -> {
            task.run(c);
            return null;
        });
    }

    /** Queues a write inside a transaction. */
    public void transaction(SqlTask task) {
        write(c -> inTransaction(c, task));
    }

    /** Runs work on the database thread and waits for it. Safe to call from the database thread itself. */
    public <T> T sync(SqlWork<T> work) {
        if (Thread.currentThread() == dbThread) {
            try {
                return work.run(connection);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }
        return query(work).join();
    }

    public void syncTransaction(SqlTask task) {
        sync(c -> {
            inTransaction(c, task);
            return null;
        });
    }

    private static void inTransaction(Connection c, SqlTask task) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            task.run(c);
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    /** Waits until every queued statement has run. */
    public void flush() {
        sync(c -> null);
    }

    public void close() {
        try {
            flush();
            sync(c -> {
                if (connection != null) connection.close();
                return null;
            });
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Error closing database", e);
        }
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
