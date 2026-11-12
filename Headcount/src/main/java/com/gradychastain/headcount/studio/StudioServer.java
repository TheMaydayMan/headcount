package com.gradychastain.headcount.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.gradychastain.headcount.HeadcountPlugin;
import com.gradychastain.headcount.audit.AuditType;
import com.gradychastain.headcount.util.Cfg;
import com.gradychastain.headcount.util.Durations;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Headcount Studio, served by the plugin. One JDK HTTP server answers the page and its API. With studio.port: same it
 * listens on a loopback port and {@link SamePortBridge} pipes browsers to it from the Minecraft port; with a port
 * number it listens there itself. Everything but the page's own files needs a signed-in browser (see
 * {@link StudioAuth}). Config saves are checked as YAML and the old file is kept in backups/studio/.
 */
public final class StudioServer {
    static final List<String> FILES = List.of("config.yml", "integrations.yml", "statuses.yml", "trade.yml", "scoring.yml", "outcomes.yml",
            "shop.yml", "titles.yml", "actions.yml", "lang/en_US.yml");
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry(".html", "text/html; charset=utf-8"), Map.entry(".css", "text/css; charset=utf-8"),
            Map.entry(".js", "text/javascript; charset=utf-8"), Map.entry(".json", "application/json; charset=utf-8"),
            Map.entry(".svg", "image/svg+xml"), Map.entry(".png", "image/png"), Map.entry(".ttf", "font/ttf"),
            Map.entry(".woff2", "font/woff2"), Map.entry(".txt", "text/plain; charset=utf-8"), Map.entry(".ogg", "audio/ogg"),
            Map.entry(".webmanifest", "application/manifest+json"), Map.entry(".ico", "image/x-icon"));
    private static final String COOKIE = "hc_studio";
    private static final int MAX_BODY = 32 << 20;
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping()
            .registerTypeHierarchyAdapter(Date.class, (JsonSerializer<Date>) (d, t, c) -> new JsonPrimitive(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d)))
            .create();

    private final HeadcountPlugin plugin;
    private final File folder;         // plugins/Headcount/studio
    private final StudioAuth auth;
    private final StudioLive live;
    private final Map<String, byte[]> staticCache = new ConcurrentHashMap<>();
    private StudioAssets assets;
    private HttpServer http;
    private ExecutorService pool;
    private SamePortBridge bridge;
    private Settings running;
    private volatile String problem;

    /** The studio section of config.yml, compared on /hca reload to see whether Studio needs a restart. */
    record Settings(boolean enabled, String port, String bind, String publicUrl, long sessionMs, boolean textures) {
        static Settings of(Cfg c) {
            return new Settings(c.getBool("enabled", true), c.getString("port", "same").trim().toLowerCase(Locale.ROOT),
                    c.getString("bind", "0.0.0.0").trim(), c.getString("public-url", "").trim(),
                    Math.max(60_000L, Durations.parseMillis(c.getString("session-length", "7d"), 7L * 86_400_000L)),
                    c.getBool("textures", true));
        }

        boolean samePort() {
            return port.equals("same") || port.isEmpty();
        }

        int portNumber() {
            try {
                return Integer.parseInt(port);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    public StudioServer(HeadcountPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "studio");
        this.auth = new StudioAuth(folder, plugin.getLogger());
        this.live = new StudioLive(
                () -> new File(plugin.getDataFolder(), plugin.configs().main().getString("storage.file", "headcount.db")),
                () -> new File(plugin.getDataFolder(), plugin.configs().main().getString("audit.folder", "audit")));
    }

    private Settings settings() {
        return Settings.of(plugin.configs().main().getSection("studio"));
    }

    // ---------------------------------------------------------------- lifecycle

    public synchronized void start() {
        Settings s = settings();
        running = s;
        problem = null;
        if (!s.enabled()) return;
        if (assets == null) assets = new StudioAssets(new File(folder, "cache"), Bukkit.getMinecraftVersion(), plugin.getLogger());
        try {
            pool = Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "Headcount Studio");
                t.setDaemon(true);
                return t;
            });
            if (s.samePort()) {
                http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 64);
                bridge = new SamePortBridge(plugin.getLogger(), http.getAddress().getPort());
                if (!bridge.install()) {
                    bridge = null;
                    http.stop(0);
                    http = null;
                    problem = "port sharing isn't available on this server";
                    pool.shutdownNow();
                    return;
                }
            } else {
                int port = s.portNumber();
                if (port < 1 || port > 65535) {
                    problem = "studio.port must be 'same' or a port number, not '" + s.port() + "'";
                    plugin.getLogger().warning("Headcount Studio is off: " + problem + ".");
                    pool.shutdownNow();
                    return;
                }
                http = HttpServer.create(new InetSocketAddress(s.bind().isEmpty() ? "0.0.0.0" : s.bind(), port), 64);
            }
            http.setExecutor(pool);
            http.createContext("/", this::handle);
            http.start();
            plugin.getLogger().info(s.samePort()
                    ? "Headcount Studio shares the Minecraft port: open http://<your server address>:" + Bukkit.getPort() + " in a browser. /hca studio shows the link and password."
                    : "Headcount Studio is on port " + s.portNumber() + ": open http://<your server address>:" + s.portNumber() + " in a browser. /hca studio shows the link and password.");
        } catch (IOException | RuntimeException e) {
            problem = e.getMessage() == null ? e.toString() : e.getMessage();
            plugin.getLogger().warning("Headcount Studio couldn't start: " + problem + (s.samePort() ? "" : ". Is something else using port " + s.port() + "?"));
            stopServer();
        }
    }

    private void stopServer() {
        if (bridge != null) bridge.uninstall();
        bridge = null;
        if (http != null) http.stop(0);
        http = null;
        if (pool != null) pool.shutdownNow();
        pool = null;
    }

    public synchronized void stop() {
        stopServer();
        if (assets != null) assets.close();
        assets = null;
    }

    /** /hca reload: restarts Studio only if its settings changed. */
    public synchronized void reload() {
        if (settings().equals(running)) return;
        stopServer();
        start();
    }

    public boolean isRunning() {
        return http != null;
    }

    public String problem() {
        return problem;
    }

    public StudioAuth auth() {
        return auth;
    }

    /** The link to show someone: public-url if set, otherwise built from the address they joined with. */
    public String link(CommandSender to) {
        Settings s = running == null ? settings() : running;
        if (!s.publicUrl().isEmpty()) return s.publicUrl().endsWith("/") ? s.publicUrl() : s.publicUrl() + "/";
        String host = null;
        int port = s.samePort() ? Bukkit.getPort() : s.portNumber();
        if (to instanceof Player p && p.getVirtualHost() != null) {
            host = p.getVirtualHost().getHostString();
            if (s.samePort()) port = p.getVirtualHost().getPort();
        }
        if (host == null || host.isBlank()) host = Bukkit.getIp() == null || Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        return "http://" + host + (port == 80 ? "" : ":" + port) + "/";
    }

    // ---------------------------------------------------------------- HTTP plumbing

    private static final class Reply extends RuntimeException {
        final int code;

        Reply(int code, String message) {
            super(message, null, false, false);
            this.code = code;
        }
    }

    private void handle(HttpExchange ex) {
        try (ex) {
            String path = ex.getRequestURI().getRawPath();
            String method = ex.getRequestMethod();
            try {
                if (method.equals("GET") || method.equals("HEAD")) get(ex, path);
                else if (method.equals("POST")) post(ex, path);
                else json(ex, 405, Map.of("error", "Not allowed."));
            } catch (Reply r) {
                json(ex, r.code, Map.of("error", r.getMessage()));
            } catch (StudioLive.Missing e) {
                json(ex, 404, Map.of("error", e.getMessage()));
            } catch (SQLException e) {
                json(ex, 503, Map.of("error", "Database: " + e.getMessage()));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Headcount Studio: " + method + " " + path + " failed", e);
                json(ex, 500, Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        } catch (IOException e) {
            // the browser went away mid-reply
        }
    }

    private void send(HttpExchange ex, int code, byte[] body, String type, String cache) throws IOException {
        var h = ex.getResponseHeaders();
        h.set("Content-Type", type);
        h.set("Cache-Control", cache == null ? "no-store" : cache);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        boolean head = ex.getRequestMethod().equals("HEAD");
        ex.sendResponseHeaders(code, head ? -1 : body.length == 0 ? -1 : body.length);
        if (!head && body.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private void json(HttpExchange ex, int code, Object body) throws IOException {
        send(ex, code, GSON.toJson(body).getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8", null);
    }

    private static String query(HttpExchange ex, String name, String def) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return def;
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? part : part.substring(0, eq), StandardCharsets.UTF_8);
            if (k.equals(name)) return eq < 0 ? "" : URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
        }
        return def;
    }

    private static byte[] body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY + 1);
            if (data.length > MAX_BODY) throw new Reply(413, "That's too big to send to Studio.");
            return data;
        }
    }

    private static JsonObject jsonBody(HttpExchange ex) throws IOException {
        String text = new String(body(ex), StandardCharsets.UTF_8);
        if (text.isBlank()) return new JsonObject();
        try {
            JsonElement e = JsonParser.parseString(text);
            if (!e.isJsonObject()) throw new Reply(400, "Expected a JSON object.");
            return e.getAsJsonObject();
        } catch (RuntimeException e) {
            if (e instanceof Reply r) throw r;
            throw new Reply(400, "That isn't valid JSON.");
        }
    }

    private static String str(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? def : e.getAsString();
    }

    private String token(HttpExchange ex) {
        for (String header : ex.getRequestHeaders().getOrDefault("Cookie", List.of())) {
            for (String part : header.split(";")) {
                String p = part.trim();
                if (p.startsWith(COOKIE + "=")) return p.substring(COOKIE.length() + 1);
            }
        }
        return null;
    }

    private boolean signedIn(HttpExchange ex) {
        return auth.valid(token(ex));
    }

    private String clientAddress(HttpExchange ex) {
        InetSocketAddress a = SamePortBridge.realAddress(ex.getRemoteAddress());
        return a == null ? "unknown" : a.getAddress().getHostAddress();
    }

    /** Writes must come from Studio's own page: a custom header (which a cross-site form can't send) and, when the
     * browser says where the request comes from, the same host. */
    private void checkWrite(HttpExchange ex) {
        if (!"1".equals(ex.getRequestHeaders().getFirst("X-Studio"))) throw new Reply(403, "Requests to Headcount Studio must come from its own page.");
        String origin = ex.getRequestHeaders().getFirst("Origin");
        String host = ex.getRequestHeaders().getFirst("Host");
        if (origin != null && host != null) {
            try {
                String oh = URI.create(origin).getAuthority();
                if (oh == null || !oh.equalsIgnoreCase(host)) throw new Reply(403, "Requests to Headcount Studio must come from its own page.");
            } catch (IllegalArgumentException e) {
                throw new Reply(403, "Requests to Headcount Studio must come from its own page.");
            }
        }
    }

    private void requireSignIn(HttpExchange ex) {
        if (!signedIn(ex)) throw new Reply(401, "Sign in to Studio first. /hca studio in game or in the console shows the password.");
    }

    // ---------------------------------------------------------------- routes

    private void get(HttpExchange ex, String path) throws IOException, SQLException {
        if (path.equals("/api/ping")) {
            json(ex, 200, Map.of("ok", true, "app", "headcount-studio", "version", plugin.getPluginMeta().getVersion()));
            return;
        }
        if (path.equals("/api/session")) {
            json(ex, 200, Map.of("signedIn", signedIn(ex), "hosted", true));
            return;
        }
        if (!path.startsWith("/api/") && !path.startsWith("/mc/") && !path.startsWith("/sound/") && !path.startsWith("/skin/")) {
            staticFile(ex, path);
            return;
        }
        requireSignIn(ex);
        if (path.startsWith("/mc/")) {
            File f = assets.path(URLDecoder.decode(path.substring(4), StandardCharsets.UTF_8));
            fileReply(ex, f, "max-age=86400");
            return;
        }
        if (path.startsWith("/sound/")) {
            String ev = URLDecoder.decode(path.substring(7), StandardCharsets.UTF_8);
            if (ev.endsWith(".ogg")) ev = ev.substring(0, ev.length() - 4);
            fileReply(ex, assets.sound(ev), "max-age=86400");
            return;
        }
        if (path.startsWith("/skin/")) {
            String who = URLDecoder.decode(path.substring(6), StandardCharsets.UTF_8);
            if (who.endsWith(".png")) who = who.substring(0, who.length() - 4);
            File f = assets.skin(who);
            String cache = "max-age=3600";
            if (f == null) {
                // No skin to be had (offline, or not a real account): the default one, so the page still shows a face.
                f = assets.path("entity/player/wide/steve.png");
                cache = "max-age=300";
            }
            fileReply(ex, f, cache);
            return;
        }
        switch (path) {
            case "/api/all" -> json(ex, 200, all());
            case "/api/catalog" -> resource(ex, "studio/catalog.json", "catalog.json is missing from the jar.");
            case "/api/doc" -> doc(ex);
            case "/api/backups" -> json(ex, 200, backups());
            case "/api/backup" -> backup(ex);
            case "/api/export" -> export(ex);
            case "/api/textures" -> json(ex, 200, assets.status());
            case "/api/live/summary" -> json(ex, 200, live.summary());
            case "/api/live/players" -> json(ex, 200, live.players());
            case "/api/live/player" -> {
                Map<String, Object> p = live.player(query(ex, "uuid", ""));
                if (p == null) throw new Reply(404, "No such player.");
                json(ex, 200, p);
            }
            case "/api/live/history" -> json(ex, 200, live.history());
            case "/api/live/cycle" -> {
                long id;
                try {
                    id = Long.parseLong(query(ex, "id", "0"));
                } catch (NumberFormatException e) {
                    throw new Reply(400, "That isn't a cycle number.");
                }
                json(ex, 200, live.cycleResults(id));
            }
            case "/api/live/effects" -> json(ex, 200, live.effects());
            case "/api/audit" -> {
                int page;
                try {
                    page = Math.max(1, Integer.parseInt(query(ex, "page", "1")));
                } catch (NumberFormatException e) {
                    page = 1;
                }
                json(ex, 200, live.audit(query(ex, "q", ""), query(ex, "type", "").strip().toUpperCase(Locale.ROOT), page));
            }
            case "/api/audit/head" -> {
                String id = query(ex, "id", "").strip().toLowerCase(Locale.ROOT);
                if (!id.matches("[0-9a-f-]{8,}")) throw new Reply(400, "That isn't a head ID.");
                json(ex, 200, live.auditHead(id));
            }
            default -> json(ex, 404, Map.of("error", "not found"));
        }
    }

    private void post(HttpExchange ex, String path) throws IOException {
        checkWrite(ex);
        if (path.equals("/api/login")) {
            login(ex);
            return;
        }
        if (path.equals("/api/logout")) {
            auth.signOut(token(ex));
            ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
            json(ex, 200, Map.of("ok", true));
            return;
        }
        requireSignIn(ex);
        switch (path) {
            case "/api/save" -> save(ex);
            case "/api/parse" -> parse(ex);
            case "/api/command" -> command(ex);
            case "/api/import" -> importZip(ex);
            case "/api/textures/download" -> {
                JsonObject b = jsonBody(ex);
                boolean force = b.has("force") && b.get("force").getAsBoolean();
                Map<String, Object> out = new LinkedHashMap<>(assets.status());
                out.put("started", assets.start(force));
                json(ex, 200, out);
            }
            default -> json(ex, 404, Map.of("error", "not found"));
        }
    }

    // ---- sign-in

    private void login(HttpExchange ex) throws IOException {
        String from = clientAddress(ex);
        if (auth.lockedOut(from)) throw new Reply(429, "Too many wrong passwords. Wait five minutes and try again.");
        JsonObject b = jsonBody(ex);
        long length = (running == null ? settings() : running).sessionMs();
        String token = auth.signIn(from, str(b, "password", ""), length);
        if (token == null) {
            plugin.getLogger().info("Headcount Studio: a wrong password from " + from + ".");
            throw new Reply(401, "That isn't the password. /hca studio shows it.");
        }
        boolean https = "https".equalsIgnoreCase(ex.getRequestHeaders().getFirst("X-Forwarded-Proto"));
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + token + "; Path=/; Max-Age=" + (length / 1000)
                + "; HttpOnly; SameSite=Strict" + (https ? "; Secure" : ""));
        plugin.getLogger().info("Headcount Studio: a browser at " + from + " signed in.");
        plugin.audit().log(AuditType.ADMIN, "action", "studio-sign-in", "from", from);
        json(ex, 200, Map.of("ok", true));
    }

    // ---- the page's own files, from the jar

    private void staticFile(HttpExchange ex, String path) throws IOException {
        String name = path.equals("/") || path.isEmpty() ? "index.html" : URLDecoder.decode(path.substring(1), StandardCharsets.UTF_8);
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null : TYPES.get(name.substring(dot));
        if (type == null || name.contains("..") || name.contains("\\") || name.startsWith("/")) {
            json(ex, 404, Map.of("error", "not found"));
            return;
        }
        byte[] data = staticCache.computeIfAbsent(name, n -> {
            try (InputStream in = plugin.getResource("studio/web/" + n)) {
                return in == null ? new byte[0] : in.readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });
        if (data.length == 0) {
            staticCache.remove(name);
            json(ex, 404, Map.of("error", "not found"));
            return;
        }
        // The jar only changes with a restart, so fonts and images can be kept; code and the page are checked each time.
        send(ex, 200, data, type, name.startsWith("fonts/") ? "max-age=604800" : "no-cache");
    }

    private void resource(HttpExchange ex, String name, String missing) throws IOException {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) throw new Reply(404, missing);
            send(ex, 200, in.readAllBytes(), "application/json; charset=utf-8", null);
        }
    }

    private void fileReply(HttpExchange ex, File f, String cache) throws IOException {
        if (f == null || !f.isFile()) throw new Reply(404, "not found");
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        String type = dot < 0 ? null : TYPES.get(n.substring(dot));
        if (type == null) throw new Reply(404, "not found");
        send(ex, 200, Files.readAllBytes(f.toPath()), type, cache);
    }

    // ---- config files

    private File data() {
        return plugin.getDataFolder();
    }

    private static String defaultText(Plugin plugin, String name) {
        try (InputStream in = plugin.getResource(name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static double mtime(File f) {
        return f.lastModified() / 1000.0;
    }

    private Map<String, Object> all() throws IOException {
        List<Map<String, Object>> files = new ArrayList<>();
        for (String n : FILES) {
            File cur = new File(data(), n);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", n);
            item.put("exists", cur.isFile());
            String def = defaultText(plugin, n);
            if (cur.isFile()) {
                item.put("text", Files.readString(cur.toPath(), StandardCharsets.UTF_8));
                item.put("mtime", mtime(cur));
            } else if (def != null) {
                item.put("text", def);
                item.put("mtime", null);
            }
            if (def != null) item.put("defaultText", def);
            files.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dir", data().getAbsolutePath());
        out.put("files", files);
        out.put("yamlCheck", true);
        out.put("live", live.available());
        out.put("hosted", true);
        out.put("version", plugin.getPluginMeta().getVersion());
        out.put("plugins", Arrays.stream(Bukkit.getPluginManager().getPlugins()).map(Plugin::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList());
        if (running != null && running.textures()) assets.start(false); // first visit: fetch the textures for the previews
        out.put("textures", assets.status());
        return out;
    }

    private static Yaml yaml() {
        LoaderOptions o = new LoaderOptions();
        o.setMaxAliasesForCollections(50);
        o.setCodePointLimit(8 << 20);
        return new Yaml(new SafeConstructor(o));
    }

    /** Null when the text is valid YAML, otherwise what's wrong and where. */
    static String checkYaml(String text) {
        try {
            yaml().load(text);
            return null;
        } catch (MarkedYAMLException e) {
            Mark m = e.getProblemMark();
            String what = e.getProblem() != null ? e.getProblem() : e.getMessage();
            return what + (m == null ? "" : " (line " + (m.getLine() + 1) + ", column " + (m.getColumn() + 1) + ")");
        } catch (YAMLException e) {
            return e.getMessage();
        }
    }

    private void save(HttpExchange ex) throws IOException {
        String name = query(ex, "name", "");
        if (!FILES.contains(name)) throw new Reply(400, "Unknown file '" + name + "'.");
        JsonObject b = jsonBody(ex);
        String text = str(b, "text", "");
        String problem = checkYaml(text);
        if (problem != null) throw new Reply(422, "Not saved, because " + name + " wouldn't be valid YAML: " + problem);
        File target = new File(data(), name);
        boolean force = b.has("force") && !b.get("force").isJsonNull() && b.get("force").getAsBoolean();
        if (target.isFile() && b.has("mtime") && !b.get("mtime").isJsonNull() && !force
                && Math.abs(mtime(target) - b.get("mtime").getAsDouble()) > 0.001) {
            throw new Reply(409, name + " changed on disk after Studio loaded it.");
        }
        Files.createDirectories(target.getParentFile().toPath());
        String backup = null;
        if (target.isFile()) {
            Path dir = data().toPath().resolve("backups").resolve("studio");
            Files.createDirectories(dir);
            Path to = dir.resolve(name.replace('/', '_') + "." + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            Files.copy(target.toPath(), to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            backup = data().toPath().relativize(to).toString().replace('\\', '/');
        }
        Path tmp = target.toPath().resolveSibling(target.getName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        plugin.audit().log(AuditType.ADMIN, "action", "studio-save", "file", name, "from", clientAddress(ex));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", name);
        out.put("mtime", mtime(target));
        out.put("backup", backup);
        json(ex, 200, out);
    }

    private void parse(HttpExchange ex) throws IOException {
        JsonObject b = jsonBody(ex);
        Map<String, Object> out = new LinkedHashMap<>();
        JsonObject texts = b.has("texts") && b.get("texts").isJsonObject() ? b.getAsJsonObject("texts") : new JsonObject();
        for (String n : texts.keySet()) {
            String text = texts.get(n).isJsonNull() ? "" : texts.get(n).getAsString();
            try {
                Object data = yaml().load(text);
                out.put(n, Collections.singletonMap("data", plainKeys(data)));
            } catch (YAMLException e) {
                out.put(n, Map.of("error", String.valueOf(checkYaml(text))));
            }
        }
        json(ex, 200, out);
    }

    /** YAML maps can have non-string keys (numbers, booleans); JSON can't, so they become strings, as Python's json did. */
    private static Object plainKeys(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object k = e.getKey();
                String key = k == null ? "null" : k instanceof Boolean bo ? (bo ? "true" : "false") : String.valueOf(k);
                out.put(key, plainKeys(e.getValue()));
            }
            return out;
        }
        if (o instanceof List<?> l) return l.stream().map(StudioServer::plainKeys).toList();
        if (o instanceof Set<?> s) return s.stream().map(StudioServer::plainKeys).toList();
        if (o instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return o;
    }

    // ---- backups, import, export

    private static String whichFile(String rel) {
        String base = rel.substring(rel.lastIndexOf('/') + 1);
        for (String n : FILES) {
            String flat = n.replace('/', '_');
            String nb = n.substring(n.lastIndexOf('/') + 1);
            if (base.equals(nb) || base.startsWith(flat + ".") || base.equals(flat)) return n;
        }
        return null;
    }

    private List<Map<String, Object>> backups() throws IOException {
        Path root = data().toPath().resolve("backups");
        if (!Files.isDirectory(root)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String n = p.getFileName().toString();
                if (n.endsWith(".db") || n.endsWith(".db-wal") || n.endsWith(".db-shm") || n.endsWith(".gz") || n.endsWith(".log")) continue;
                String rel = data().toPath().relativize(p).toString().replace('\\', '/');
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("path", rel);
                m.put("file", whichFile(rel));
                m.put("mtime", mtime(p.toFile()));
                m.put("size", Files.size(p));
                out.add(m);
            }
        }
        out.sort(Comparator.comparingDouble((Map<String, Object> m) -> (double) m.get("mtime")).reversed());
        return out.size() > 400 ? out.subList(0, 400) : out;
    }

    private void backup(HttpExchange ex) throws IOException {
        String rel = query(ex, "path", "");
        Path root = data().toPath().resolve("backups").toAbsolutePath().normalize();
        Path full = data().toPath().resolve(rel).toAbsolutePath().normalize();
        if (!full.startsWith(root) || full.equals(root) || !Files.isRegularFile(full)) throw new Reply(404, "No such backup.");
        json(ex, 200, Map.of("path", rel, "text", new String(Files.readAllBytes(full), StandardCharsets.UTF_8)));
    }

    private void export(HttpExchange ex) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(buf)) {
            for (String n : FILES) {
                File f = new File(data(), n);
                if (!f.isFile()) continue;
                z.putNextEntry(new ZipEntry(n));
                z.write(Files.readAllBytes(f.toPath()));
                z.closeEntry();
            }
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"headcount-config-" + stamp + ".zip\"");
        send(ex, 200, buf.toByteArray(), "application/zip", null);
    }

    private void importZip(HttpExchange ex) throws IOException {
        byte[] data = body(ex);
        Map<String, Object> out = new LinkedHashMap<>();
        boolean any = false;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                any = true;
                String name = e.getName().replace('\\', '/');
                for (String n : FILES) {
                    if (name.equals(n) || name.endsWith("/" + n)) {
                        out.put(n, new String(z.readNBytes(4 << 20), StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (IOException e) {
            throw new Reply(400, "That isn't a .zip file.");
        }
        if (!any) throw new Reply(400, "That isn't a .zip file.");
        json(ex, 200, Map.of("files", out));
    }

    // ---- commands, run as the console

    private void command(HttpExchange ex) throws IOException {
        String cmd = str(jsonBody(ex), "command", "").strip();
        while (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) throw new Reply(400, "No command given.");
        if (!plugin.isEnabled()) throw new Reply(503, "Headcount is shutting down.");
        StringBuffer reply = new StringBuffer();
        CommandSender sender = Bukkit.createCommandSender(c -> {
            synchronized (reply) {
                if (!reply.isEmpty()) reply.append('\n');
                reply.append(LegacyComponentSerializer.legacySection().serialize(c));
            }
        });
        String from = clientAddress(ex);
        final String line = cmd;
        plugin.getLogger().info("Headcount Studio (" + from + ") ran /" + line);
        plugin.audit().log(AuditType.ADMIN, "action", "studio-command", "command", line.replace(' ', '_'), "from", from);
        Future<Boolean> done = Bukkit.getScheduler().callSyncMethod(plugin, () -> Bukkit.dispatchCommand(sender, line));
        try {
            done.get(15, TimeUnit.SECONDS);
            Thread.sleep(250); // replies that come a moment later, like an async lookup
        } catch (java.util.concurrent.TimeoutException e) {
            throw new Reply(504, "The server didn't run it within 15 seconds.");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new Reply(500, "The command failed: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        json(ex, 200, Map.of("reply", reply.toString()));
    }

    private void doc(HttpExchange ex) throws IOException {
        String name = query(ex, "name", "");
        name = name.substring(name.lastIndexOf('/') + 1);
        String res = name.equals("README") ? "studio/docs/README.md" : name.endsWith(".md") ? "studio/docs/" + name : null;
        String text = res == null ? null : defaultText(plugin, res);
        if (text == null) throw new Reply(404, "no doc " + name);
        json(ex, 200, Map.of("name", name, "text", text));
    }
}
