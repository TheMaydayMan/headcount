package com.gradychastain.headcount.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Minecraft textures for Headcount Studio's previews (a port of tools/studio/mcassets.py; the two write the same files).
 *
 * <p>Mojang's textures can't ship with the plugin, so on first use Studio downloads the official client jar for the
 * server's Minecraft version (from Mojang), keeps the item, block and a few GUI and entity textures in a cache folder,
 * and deletes the jar. From the jar's item models it writes index.json (how to draw each material: a flat item or a
 * 3D model) and shapes.json (the 3D models already turned and projected the way the inventory shows them, as textured
 * quads), turns the bitmap font into a TrueType font, and lists the sound events. Player skins come from mc-heads.net.
 * Everything downloads on a background daemon thread, never the server thread. No Bukkit classes, so {@link #main}
 * runs on its own.
 */
public final class StudioAssets {
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCES = "https://resources.download.minecraft.net";
    private static final String UA = "HeadcountStudio";
    private static final String A = "assets/minecraft/";
    /** Bumped when index.json changes shape, so an older cache is downloaded again (FORMAT in mcassets.py). */
    static final int FORMAT = 2;
    private static final List<String> EXTRA = List.of(
            "textures/gui/container/generic_54.png",
            "textures/entity/player/wide/steve.png",
            "textures/entity/player/wide/alex.png",
            "textures/misc/enchanted_glint_item.png");
    private static final Map<String, String> BIOME_TINT = Map.of(
            "minecraft:grass", "#91bd59", "minecraft:foliage", "#77ab2f", "minecraft:dry_foliage", "#a17e46");
    private static final Pattern EVENT = Pattern.compile("^[a-z0-9_.\\-/]+$");
    private static final Pattern SHA1 = Pattern.compile("^[0-9a-f]{40}$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$|^[0-9a-fA-F-]{32,36}$");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private final File cacheDir;
    private final File folder;
    private final String minecraftVersion;
    private final Logger log;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Object lock = new Object();
    private volatile Map<String, Object> job = job("idle", 0, null, null);
    private volatile Thread worker;
    private volatile boolean closed;

    /** cacheDir holds minecraft/ (the textures) and skins/. */
    public StudioAssets(File cacheDir, String minecraftVersion, Logger log) {
        this.cacheDir = cacheDir;
        this.folder = new File(cacheDir, "minecraft");
        this.minecraftVersion = minecraftVersion;
        this.log = log != null ? log : Logger.getLogger("HeadcountStudio");
    }

    private static Map<String, Object> job(String state, int progress, String error, String step) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", state);
        m.put("progress", progress);
        m.put("error", error);
        if (step != null) m.put("step", step);
        return m;
    }

    private void setJob(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>(job);
        m.put(key, value);
        job = m;
    }

    // ---------------------------------------------------------------- status

    private JsonObject meta() {
        try {
            JsonElement m = JsonParser.parseString(Files.readString(new File(folder, "meta.json").toPath()));
            if (m.isJsonObject() && m.getAsJsonObject().has("format") && isInt(m.getAsJsonObject().get("format"))
                    && m.getAsJsonObject().get("format").getAsInt() == FORMAT) return m.getAsJsonObject();
        } catch (Exception ignored) {
            // no cache yet, or a broken one
        }
        return null; // missing, or an older layout: download again
    }

    /** The download's state and what the cache holds; the same JSON shape as Assets.status() in mcassets.py. */
    public Map<String, Object> status() {
        JsonObject m = meta();
        Map<String, Object> out = new LinkedHashMap<>(job);
        out.put("wanted", minecraftVersion);
        if (m != null) {
            out.put("ready", true);
            out.put("version", plain(m.get("version")));
            out.put("items", plain(m.get("items")));
            out.put("font", plain(m.get("font")));
            out.put("sounds", m.has("sounds") ? plain(m.get("sounds")) : 0);
        } else {
            out.put("ready", false);
        }
        return out;
    }

    private static Object plain(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) return isInt(p) ? (Object) p.getAsLong() : (Object) p.getAsDouble();
            return p.getAsString();
        }
        return GSON.fromJson(e, Object.class);
    }

    /** The cached file for a URL path under /mc/, or null (never anything outside the cache folder). */
    public File path(String rel) {
        if (rel == null || rel.indexOf('\0') >= 0) return null;
        try {
            Path base = folder.toPath().toAbsolutePath().normalize();
            Path full = base.resolve(rel).toAbsolutePath().normalize();
            if (!full.startsWith(base) || full.equals(base)) return null;
            File f = full.toFile();
            if (!f.isFile()) return null;
            // and not through a link that leads out of the cache
            if (!f.getCanonicalFile().toPath().startsWith(base.toFile().getCanonicalFile().toPath())) return null;
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- download

    /** Starts the background download and extract. False when it's already running, or already done and !force. */
    public boolean start(boolean force) {
        synchronized (lock) {
            if (closed) return false;
            if ("running".equals(job.get("state"))) return false;
            if (meta() != null && !force) return false;
            job = job("running", 0, null, "Finding the version");
            Thread t = new Thread(this::run, "Headcount Studio textures");
            t.setDaemon(true);
            worker = t;
            t.start();
        }
        return true;
    }

    /** Stops background work (an unfinished download is thrown away). */
    public void close() {
        closed = true;
        Thread t = worker;
        if (t != null) t.interrupt();
    }

    private byte[] get(String url, int timeoutSeconds) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA)
                .timeout(Duration.ofSeconds(timeoutSeconds)).GET().build();
        HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " from " + url);
        return r.body();
    }

    private static List<Integer> versionKey(String s) {
        List<Integer> out = new ArrayList<>();
        for (String x : s.split("[.\\-]", -1)) out.add(!x.isEmpty() && x.chars().allMatch(Character::isDigit) ? parseIntSafe(x) : 0);
        return out;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static int compareKeys(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int c = Integer.compare(a.get(i), b.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(a.size(), b.size());
    }

    private void run() {
        File tmp = new File(folder.getPath() + ".part");
        try {
            JsonObject manifest = JsonParser.parseString(new String(get(MANIFEST, 30), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray versions = manifest.getAsJsonArray("versions");
            String wanted = minecraftVersion != null && !minecraftVersion.isBlank() ? minecraftVersion
                    : manifest.getAsJsonObject("latest").get("release").getAsString();
            JsonObject entry = null;
            for (JsonElement v : versions) if (v.getAsJsonObject().get("id").getAsString().equals(wanted)) { entry = v.getAsJsonObject(); break; }
            if (entry == null) { // a version Mojang doesn't list (a fork's own name): the closest release before it
                List<JsonObject> releases = new ArrayList<>(), older = new ArrayList<>();
                for (JsonElement v : versions) if ("release".equals(v.getAsJsonObject().get("type").getAsString())) releases.add(v.getAsJsonObject());
                List<Integer> key = versionKey(wanted);
                for (JsonObject v : releases) if (compareKeys(versionKey(v.get("id").getAsString()), key) <= 0) older.add(v);
                entry = (older.isEmpty() ? releases : older).get(0);
            }
            String id = entry.get("id").getAsString();
            JsonObject info = JsonParser.parseString(new String(get(entry.get("url").getAsString(), 30), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject client = info.getAsJsonObject("downloads").getAsJsonObject("client");
            setJob("step", "Downloading Minecraft " + id);
            File parent = folder.getAbsoluteFile().getParentFile();
            if (parent != null) parent.mkdirs();
            deleteTree(tmp);
            if (!tmp.mkdirs()) throw new IOException("couldn't create " + tmp);
            File jar = new File(tmp, "client.jar");
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            long size = Math.max(1, client.get("size").getAsLong());
            HttpRequest req = HttpRequest.newBuilder(URI.create(client.get("url").getAsString())).header("User-Agent", UA)
                    .timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) throw new IOException("HTTP " + resp.statusCode() + " downloading the client jar");
            try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(jar.toPath())) {
                byte[] buf = new byte[1 << 16];
                long got = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (closed) throw new InterruptedException("stopped");
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    got += n;
                    setJob("progress", (int) Math.round(80.0 * got / size));
                }
            }
            if (!hex(sha.digest()).equals(client.get("sha1").getAsString()))
                throw new IOException("the download didn't match Mojang's checksum");
            setJob("step", "Unpacking textures");
            int count;
            boolean font = false;
            try (ZipFile z = new ZipFile(jar)) {
                count = extract(z, tmp);
                setJob("progress", 92);
                try {
                    buildFont(z, new File(tmp, "font/minecraft.ttf"));
                    font = true;
                } catch (Exception e) { // previews fall back to the bundled font
                    log.warning("Couldn't build the Minecraft font: " + e);
                }
            }
            Files.deleteIfExists(jar.toPath());
            setJob("step", "Listing sounds");
            int sounds = 0;
            try {
                sounds = sounds(info, tmp);
            } catch (Exception e) { // sound fields fall back to plain text
                log.warning("Couldn't list Minecraft's sounds: " + e);
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("version", id);
            meta.put("items", count);
            meta.put("font", font);
            meta.put("sounds", sounds);
            meta.put("format", FORMAT);
            Files.writeString(new File(tmp, "meta.json").toPath(), GSON.toJson(meta));
            if (closed) throw new InterruptedException("stopped");
            deleteTree(folder);
            Files.move(tmp.toPath(), folder.toPath(), StandardCopyOption.REPLACE_EXISTING);
            job = job("done", 100, null, null);
        } catch (Throwable e) {
            deleteTree(tmp);
            job = job("error", 0, e.getClass().getSimpleName() + ": " + e.getMessage(), null);
            if (!closed) log.log(Level.WARNING, "Couldn't download Minecraft's textures for Studio: " + e);
        }
    }

    private int sounds(JsonObject info, File out) throws IOException, InterruptedException {
        JsonObject objects = JsonParser.parseString(new String(get(info.getAsJsonObject("assetIndex").get("url").getAsString(), 30),
                StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("objects");
        String h = objects.getAsJsonObject("minecraft/sounds.json").get("hash").getAsString();
        JsonObject events = JsonParser.parseString(new String(get(RESOURCES + "/" + h.substring(0, 2) + "/" + h, 30),
                StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> table = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> ev : events.entrySet()) {
            JsonElement list = ev.getValue().isJsonObject() ? ev.getValue().getAsJsonObject().get("sounds") : null;
            if (list == null || !list.isJsonArray()) continue;
            for (JsonElement snd : list.getAsJsonArray()) {
                String name;
                if (snd.isJsonPrimitive()) name = snd.getAsString();
                else if (snd.isJsonObject()) {
                    JsonObject o = snd.getAsJsonObject();
                    if (o.has("type") && "event".equals(str(o.get("type")))) continue;
                    name = str(o.get("name"));
                } else continue;
                JsonElement obj = objects.get("minecraft/sounds/" + name + ".ogg");
                if (obj != null && obj.isJsonObject()) {
                    table.put(ev.getKey(), obj.getAsJsonObject().get("hash").getAsString());
                    break;
                }
            }
        }
        Files.writeString(new File(out, "sounds.json").toPath(), GSON.toJson(table));
        return table.size();
    }

    /** The .ogg file for a sound event (downloaded once from Mojang's asset server), or null. */
    public File sound(String event) {
        if (event == null) return null;
        if (event.startsWith("minecraft:")) event = event.substring("minecraft:".length());
        if (!EVENT.matcher(event).matches()) return null;
        String h;
        try {
            JsonElement t = JsonParser.parseString(Files.readString(new File(folder, "sounds.json").toPath()));
            JsonElement v = t.isJsonObject() ? t.getAsJsonObject().get(event) : null;
            h = v != null && v.isJsonPrimitive() ? v.getAsString() : null;
        } catch (Exception e) {
            return null;
        }
        if (h == null || !SHA1.matcher(h).matches()) return null;
        File p = new File(folder, "sound/" + h + ".ogg");
        if (!p.exists()) {
            try {
                byte[] data = get(RESOURCES + "/" + h.substring(0, 2) + "/" + h, 10);
                if (!hex(MessageDigest.getInstance("SHA-1").digest(data)).equals(h)) return null;
                p.getParentFile().mkdirs();
                Files.write(p.toPath(), data);
            } catch (Exception e) {
                return null;
            }
        }
        return p;
    }

    /** A player's skin PNG (cached for a day), downloaded from mc-heads.net, or null. */
    public File skin(String who) {
        if (who == null || !NAME.matcher(who).matches()) return null;
        File d = new File(cacheDir, "skins");
        d.mkdirs();
        File p = new File(d, who.toLowerCase(java.util.Locale.ROOT).replace("-", "") + ".png");
        if (p.exists() && System.currentTimeMillis() - p.lastModified() < 86_400_000L) return p;
        try {
            byte[] data = get("https://mc-heads.net/skin/" + who, 6);
            if (data.length < 8 || !Arrays.equals(Arrays.copyOf(data, 8), new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}))
                throw new IOException("not a PNG");
            Files.write(p.toPath(), data);
            return p;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return p.exists() ? p : null;
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xff));
        return s.toString();
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.isDirectory() && !Files.isSymbolicLink(f.toPath()) ? f.listFiles() : null;
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    // ---------------------------------------------------------------- extracting and indexing
    // See mcassets.py ("extracting and indexing") for the formats of index.json and shapes.json.

    private static final Map<String, Integer> DYES = new HashMap<>();
    static {
        Object[] d = {"white", 0xF9FFFE, "orange", 0xF9801D, "magenta", 0xC74EBD, "light_blue", 0x3AB3DA, "yellow", 0xFED83D,
                "lime", 0x80C71F, "pink", 0xF38BAA, "gray", 0x474F52, "light_gray", 0x9D9D97, "cyan", 0x169C9C,
                "purple", 0x8932B8, "blue", 0x3C44AA, "brown", 0x835432, "green", 0x5E7C16, "red", 0xB02E26, "black", 0x1D1D21};
        for (int i = 0; i < d.length; i += 2) DYES.put((String) d[i], (Integer) d[i + 1]);
    }
    private static final double LIGHT_POWER = 0.6, AMBIENT = 0.4;
    private static final double PI = 3.1415927, HALF_PI = 1.5707964;

    /** One cube of an entity model (ModelPart.Cube): texture offset, origin, size, growth, mirroring. */
    private record Box(double u, double v, double x, double y, double z, double w, double h, double d,
                       double grow, boolean mirror, String var, int tint, String only) {
    }

    private static Box box(double u, double v, double x, double y, double z, double w, double h, double d) {
        return new Box(u, v, x, y, z, w, h, d, 0.0, false, "0", -1, null);
    }

    private static Box grow(Box b, double g) {
        return new Box(b.u, b.v, b.x, b.y, b.z, b.w, b.h, b.d, g, b.mirror, b.var, b.tint, b.only);
    }

    private static Box mirror(Box b) {
        return new Box(b.u, b.v, b.x, b.y, b.z, b.w, b.h, b.d, b.grow, true, b.var, b.tint, b.only);
    }

    private static Box tex(Box b, String var, int tint, String only) {
        return new Box(b.u, b.v, b.x, b.y, b.z, b.w, b.h, b.d, b.grow, b.mirror, var, tint, only);
    }

    /** A model part: offset (already scaled), rotation in radians, scale, cubes and children. */
    private record Part(double[] pose, List<Box> boxes, List<Part> kids) {
    }

    private static Part part(double x, double y, double z, double xr, double yr, double zr, double scale, List<Box> boxes, List<Part> kids) {
        return new Part(new double[]{x * scale, y * scale, z * scale, xr, yr, zr, scale}, boxes, kids);
    }

    private static Part part(double x, double y, double z, List<Box> boxes) {
        return part(x, y, z, 0, 0, 0, 1.0, boxes, List.of());
    }

    private static Part rotated(double x, double y, double z, double xr, double yr, double zr, List<Box> boxes, List<Part> kids) {
        return part(x, y, z, xr, yr, zr, 1.0, boxes, kids);
    }

    private record Layer(Map<String, double[]> sizes, List<Part> parts) {
    }

    private static Map<String, double[]> sizes(Object... kv) {
        Map<String, double[]> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 3) m.put((String) kv[i], new double[]{((Number) kv[i + 1]).doubleValue(), ((Number) kv[i + 2]).doubleValue()});
        return m;
    }

    // Entity models, copied from the game's model code (offsets in pixels, rotations in radians); same as LAYERS in mcassets.py.
    private static final Map<String, Layer> LAYERS = new HashMap<>();
    private static final Map<String, String> HEADS = Map.of("skeleton", "entity/skeleton/skeleton",
            "wither_skeleton", "entity/skeleton/wither_skeleton", "zombie", "entity/zombie/zombie",
            "creeper", "entity/creeper/creeper", "piglin", "entity/piglin/piglin", "dragon", "entity/enderdragon/dragon");
    static {
        List<Box> head = List.of(box(0, 0, -4, -8, -4, 8, 8, 8));
        Box flag = box(0, 0, -10, 0, -2, 20, 40, 1);
        List<Box> potSide = List.of(tex(box(1, 0, 0, 0, 0, 14, 16, 0), "1", -1, "north"));
        LAYERS.put("chest", new Layer(sizes("0", 64, 64), List.of(
                part(0, 0, 0, List.of(box(0, 19, 1, 0, 1, 14, 10, 14))),
                part(0, 9, 1, List.of(box(0, 0, 1, 0, 0, 14, 5, 14))),
                part(0, 9, 1, List.of(box(0, 0, 7, -2, 14, 2, 4, 1))))));
        LAYERS.put("shulker_box", new Layer(sizes("0", 64, 64), List.of(
                part(0, 24, 0, List.of(box(0, 0, -8, -16, -8, 16, 12, 16))),
                part(0, 24, 0, List.of(box(0, 28, -8, -8, -8, 16, 8, 16))))));
        LAYERS.put("banner", new Layer(sizes("0", 64, 64, "1", 64, 64), List.of(
                part(0, 0, 0, List.of(box(44, 0, -1, -42, -1, 2, 42, 2), box(0, 42, -10, -44, -1, 20, 2, 2))),
                part(0, -44, 0, List.of(flag, tex(flag, "1", 0, null))))));
        LAYERS.put("shield", new Layer(sizes("0", 64, 64), List.of(
                part(0, 0, 0, List.of(box(0, 0, -6, -11, -2, 12, 22, 1), box(26, 0, -1, -3, -1, 2, 6, 6))))));
        LAYERS.put("conduit", new Layer(sizes("0", 32, 16), List.of(part(0, 0, 0, List.of(box(0, 0, -3, -3, -3, 6, 6, 6))))));
        LAYERS.put("mob_head", new Layer(sizes("0", 64, 32), List.of(part(0, 0, 0, head))));
        LAYERS.put("humanoid_head", new Layer(sizes("0", 64, 64), List.of(part(0, 0, 0, head),
                part(0, 0, 0, List.of(grow(box(32, 0, -4, -8, -4, 8, 8, 8), 0.25))))));
        LAYERS.put("piglin_head", new Layer(sizes("0", 64, 64), List.of(rotated(0, 0, 0, 0, 0, 0,
                List.of(box(0, 0, -5, -8, -4, 10, 8, 8), box(31, 1, -2, -4, -5, 4, 4, 1), box(2, 4, 2, -2, -5, 1, 2, 1),
                        box(2, 0, -3, -2, -5, 1, 2, 1)),
                List.of(rotated(4.5, -6, 0, 0, 0, -0.7, List.of(box(51, 6, 0, 0, -2, 1, 5, 4)), List.of()),
                        rotated(-4.5, -6, 0, 0, 0, 0.7, List.of(box(39, 6, -1, 0, -2, 1, 5, 4)), List.of()))))));
        LAYERS.put("dragon_head", new Layer(sizes("0", 256, 256), List.of(part(0, -7.986666, 0, 0, 0, 0, 0.75,
                List.of(box(176, 44, -6, -1, -24, 12, 5, 16), box(112, 30, -8, -8, -10, 16, 16, 16),
                        mirror(box(0, 0, -5, -12, -4, 2, 4, 6)), mirror(box(112, 0, -5, -3, -22, 2, 2, 4)),
                        box(0, 0, 3, -12, -4, 2, 4, 6), box(112, 0, 3, -3, -22, 2, 2, 4)),
                List.of(rotated(0, 4, -8, 0.2, 0, 0, List.of(box(176, 65, -6, 0, -16, 12, 4, 16)), List.of()))))));
        LAYERS.put("decorated_pot", new Layer(sizes("0", 32, 32, "1", 16, 16), List.of(
                rotated(0, 37, 16, PI, 0, 0, List.of(grow(box(0, 0, 4, 17, 4, 8, 3, 8), -0.1), grow(box(0, 5, 5, 20, 5, 6, 1, 6), 0.2)), List.of()),
                part(1, 16, 1, List.of(box(-14, 13, 0, 0, 0, 14, 0, 14))),
                part(1, 0, 1, List.of(box(-14, 13, 0, 0, 0, 14, 0, 14))),
                rotated(15, 16, 1, 0, 0, PI, potSide, List.of()),
                rotated(1, 16, 1, 0, -HALF_PI, PI, potSide, List.of()),
                rotated(15, 16, 15, 0, HALF_PI, PI, potSide, List.of()),
                rotated(1, 16, 15, PI, 0, 0, potSide, List.of()))));
        LAYERS.put("copper_golem_statue", new Layer(sizes("0", 64, 64), List.of(rotated(0, 0, 0, 0, 0, PI, List.of(), List.of(
                rotated(0, -3, 2.325, 0, 0, 0, List.of(box(3, 19, -3, -4, -4.525, 6, 1, 6), box(0, 15, -4, -3, -3.525, 8, 6, 6)), List.of(
                        rotated(0, -1, -4.325, 0, 0, -3.1416, List.of(box(3, 18, -4, -3, -2.2, 8, 6, 3)), List.of()),
                        part(0, -6, -0.2, List.of(grow(box(37, 8, -1, -7, -3.3, 2, 4, 2), -0.015),
                                grow(box(37, 0, -2, -11, -4.3, 4, 4, 4), -0.015),
                                box(0, 0, -4, -3, -7.325, 8, 5, 10), box(56, 0, -1, 0, -8.325, 2, 3, 2))),
                        rotated(-4, -5.6, -1.8, 0.4363, 0, 0, List.of(), List.of(
                                rotated(0, 0.0893, 0.1198, -1.0472, 0, 0, List.of(box(36, 16, -3.075, -0.9733, -1.9966, 3, 10, 4)), List.of()))),
                        rotated(4, -5.6, -1.7, 0.4363, 0, 0, List.of(), List.of(
                                rotated(0, -0.0015, -0.0808, -1.0472, 0, 0, List.of(box(50, 16, 0.075, -1.0443, -1.8997, 3, 10, 4)), List.of()))))),
                rotated(-2.1, -2.1, -2.075, 0, 0, 0, List.of(), List.of(
                        rotated(0.05, -1.9, 1.075, -1.5708, 0, 0, List.of(box(0, 27, -2, 0.975, 0, 4, 5, 4)), List.of()))),
                rotated(2, -2, -2.075, 0, 0, 0, List.of(), List.of(
                        rotated(0.05, -2, 1.075, -1.5708, 0, 0, List.of(box(16, 27, -2, 0.975, 0, 4, 5, 4)), List.of()))))))));
    }

    // ---- 3 x 4 affine matrices, in the same arithmetic order as mcassets.py so both write the same numbers

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][4];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 4; j++)
                r[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j] + (j == 3 ? a[i][3] : 0.0);
        return r;
    }

    private static double[] apply(double[][] m, double[] p) {
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) r[i] = m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3];
        return r;
    }

    private static double[] turn(double[][] m, double[] n) {
        double[] r = new double[3];
        for (int i = 0; i < 3; i++) r[i] = m[i][0] * n[0] + m[i][1] * n[1] + m[i][2] * n[2];
        return r;
    }

    private static double[][] translate(double x, double y, double z) {
        return new double[][]{{1.0, 0.0, 0.0, x}, {0.0, 1.0, 0.0, y}, {0.0, 0.0, 1.0, z}};
    }

    private static double[][] scale(double x, double y, double z) {
        return new double[][]{{x, 0.0, 0.0, 0.0}, {0.0, y, 0.0, 0.0}, {0.0, 0.0, z, 0.0}};
    }

    private static double[][] rot(char axis, double a) {
        double c = StrictMath.cos(a), s = StrictMath.sin(a);
        if (axis == 'x') return new double[][]{{1.0, 0.0, 0.0, 0.0}, {0.0, c, -s, 0.0}, {0.0, s, c, 0.0}};
        if (axis == 'y') return new double[][]{{c, 0.0, s, 0.0}, {0.0, 1.0, 0.0, 0.0}, {-s, 0.0, c, 0.0}};
        return new double[][]{{c, -s, 0.0, 0.0}, {s, c, 0.0, 0.0}, {0.0, 0.0, 1.0, 0.0}};
    }

    private static double[][] quat(double x, double y, double z, double w) {
        return new double[][]{
                {1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), 0.0},
                {2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), 0.0},
                {2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), 0.0}};
    }

    private static final double[][] IDENTITY = scale(1.0, 1.0, 1.0);

    private static double radians(double deg) {
        return deg * (Math.PI / 180.0);
    }

    private static double[] nums(JsonElement e, double... def) {
        if (e == null || !e.isJsonArray()) return def;
        JsonArray a = e.getAsJsonArray();
        double[] out = new double[a.size()];
        for (int i = 0; i < a.size(); i++) out[i] = num(a.get(i));
        return out;
    }

    private static double num(JsonElement e) {
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : 0.0;
        } catch (Exception x) {
            return 0.0;
        }
    }

    private static double[][] rotation(JsonElement r) {
        if (r != null && r.isJsonObject()) {
            JsonObject o = r.getAsJsonObject();
            double[] ax = nums(o.get("axis"), 0, 1, 0);
            double n = Math.sqrt(ax[0] * ax[0] + ax[1] * ax[1] + ax[2] * ax[2]);
            if (n == 0) n = 1.0;
            double h = num(o.get("angle")) / 2;
            double s = StrictMath.sin(h) / n;
            return quat(ax[0] * s, ax[1] * s, ax[2] * s, StrictMath.cos(h));
        }
        if (r != null && r.isJsonArray() && r.getAsJsonArray().size() == 4) {
            double[] q = nums(r);
            return quat(q[0], q[1], q[2], q[3]);
        }
        return IDENTITY;
    }

    private static double[][] transformation(JsonElement t) {
        if (t != null && t.isJsonArray() && t.getAsJsonArray().size() == 16) {
            double[] v = nums(t);
            double[][] m = new double[3][4];
            for (int i = 0; i < 3; i++) for (int j = 0; j < 4; j++) m[i][j] = v[i * 4 + j];
            return m;
        }
        if (t == null || !t.isJsonObject()) return IDENTITY;
        JsonObject o = t.getAsJsonObject();
        double[] tr = nums(o.get("translation"), 0, 0, 0), sc = nums(o.get("scale"), 1, 1, 1);
        double[][] m = mul(translate(tr[0], tr[1], tr[2]), rotation(o.get("left_rotation")));
        return mul(mul(m, scale(sc[0], sc[1], sc[2])), rotation(o.get("right_rotation")));
    }

    private static double[][] gui(JsonObject d) {
        double[] r = d != null ? nums(d.get("rotation"), 0, 0, 0) : new double[]{0, 0, 0};
        double[] t = d != null ? nums(d.get("translation"), 0, 0, 0) : new double[]{0, 0, 0};
        double[] s = d != null ? nums(d.get("scale"), 1, 1, 1) : new double[]{1, 1, 1};
        double[][] m = translate(t[0] / 16, t[1] / 16, t[2] / 16);
        m = mul(m, mul(mul(rot('x', radians(r[0])), rot('y', radians(r[1]))), rot('z', radians(r[2]))));
        return mul(mul(m, scale(s[0], s[1], s[2])), translate(-0.5, -0.5, -0.5));
    }

    private static double[] norm(double[] v) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return n > 0 ? new double[]{v[0] / n, v[1] / n, v[2] / n} : new double[]{0.0, 0.0, 0.0};
    }

    private static double[][] lights(boolean side) {
        double[] d0 = norm(new double[]{0.2, 1.0, -0.7}), d1 = norm(new double[]{-0.2, 1.0, 0.7});
        if (side) {
            double[][] m = mul(mul(rot('y', 1.0821041), rot('x', 3.2375858)), mul(rot('y', -0.3926991), rot('x', 2.3561945)));
            return new double[][]{turn(m, d0), turn(m, d1)};
        }
        double[][] m = mul(rot('y', -0.3926991), rot('x', 2.3561945));
        double[] a = turn(m, d0), b = turn(m, d1);
        return new double[][]{{a[0], -a[1], a[2]}, {b[0], -b[1], b[2]}};
    }

    private static final double[][] LIGHT_SIDE = lights(true), LIGHT_FRONT = lights(false);
    private static final double[][] SLOT = {{16.0, 0.0, 0.0, 8.0}, {0.0, -16.0, 0.0, 8.0}, {0.0, 0.0, 16.0, 0.0}};

    /** Python's round(x, places), written as an int when whole. */
    private static Number r(double x, int places) {
        if (!Double.isFinite(x)) return 0;
        double v = new BigDecimal(x).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return (long) v;
        return v;
    }

    private static double rd(double x, int places) {
        return new BigDecimal(x).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** Collects the visible quads of one item, already projected into the slot. */
    private static final class Shape {
        final double[][] view;
        final double[][] light;
        final List<Object[]> quads = new ArrayList<>(); // {depth, order, quad}

        Shape(double[][] view, String light) {
            this.view = view;
            this.light = "front".equals(light) ? LIGHT_FRONT : LIGHT_SIDE;
        }

        void add(double[][] m, double[][] corners, double[][] uvs, double[] normal, String var, int tint) {
            double[][] full = mul(view, m);
            double[] n = norm(turn(full, normal));
            if (n[2] <= 1e-6) return;
            double[][] p = new double[4][];
            for (int i = 0; i < 4; i++) p[i] = apply(full, corners[i]);
            double du1 = uvs[1][0] - uvs[0][0], dv1 = uvs[1][1] - uvs[0][1];
            double du2 = uvs[3][0] - uvs[0][0], dv2 = uvs[3][1] - uvs[0][1];
            double det = du1 * dv2 - dv1 * du2;
            if (Math.abs(det) < 1e-9) return;
            double[][] s = new double[4][];
            for (int i = 0; i < 4; i++) s[i] = apply(SLOT, p[i]);
            double[][] out = new double[2][];
            for (int k = 0; k < 2; k++) {
                double e1 = s[1][k] - s[0][k], e2 = s[3][k] - s[0][k];
                double a = (e1 * dv2 - e2 * dv1) / det;
                double c = (e2 * du1 - e1 * du2) / det;
                out[k] = new double[]{a, c, s[0][k] - a * uvs[0][0] - c * uvs[0][1]};
            }
            double[] l0 = light[0], l1 = light[1];
            double b = AMBIENT + LIGHT_POWER * (Math.max(0.0, n[0] * l0[0] + n[1] * l0[1] + n[2] * l0[2])
                    + Math.max(0.0, n[0] * l1[0] + n[1] * l1[1] + n[2] * l1[2]));
            double depth = (p[0][2] + p[1][2] + p[2][2] + p[3][2]) / 4;
            double umin = Math.min(Math.min(uvs[0][0], uvs[1][0]), Math.min(uvs[2][0], uvs[3][0]));
            double umax = Math.max(Math.max(uvs[0][0], uvs[1][0]), Math.max(uvs[2][0], uvs[3][0]));
            double vmin = Math.min(Math.min(uvs[0][1], uvs[1][1]), Math.min(uvs[2][1], uvs[3][1]));
            double vmax = Math.max(Math.max(uvs[0][1], uvs[1][1]), Math.max(uvs[2][1], uvs[3][1]));
            List<Object> q = new ArrayList<>(List.of(var, tint, r(Math.min(1.0, b), 3), r(umin, 5), r(vmin, 5), r(umax, 5), r(vmax, 5),
                    r(out[0][0], 5), r(out[1][0], 5), r(out[0][1], 5), r(out[1][1], 5), r(out[0][2], 5), r(out[1][2], 5)));
            quads.add(new Object[]{rd(depth, 4), quads.size(), q});
        }

        @SuppressWarnings("unchecked")
        List<List<Object>> result() {
            List<Object[]> sorted = new ArrayList<>(quads);
            sorted.sort(Comparator.<Object[]>comparingDouble(o -> (Double) o[0]).thenComparingInt(o -> (Integer) o[1]));
            List<List<Object>> out = new ArrayList<>();
            for (Object[] o : sorted) out.add((List<Object>) o[2]);
            return out;
        }
    }

    private static final String[] BLOCK_FACES = {"down", "up", "north", "south", "west", "east"};

    /** Block model face: corners (texture top-left, top-right, bottom-right, bottom-left), outward normal, default UV. */
    private static Object[] face(String name, double[] f, double[] t) {
        double x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
        return switch (name) {
            case "up" -> new Object[]{new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, new double[]{0, 1, 0}, new double[]{x0, z0, x1, z1}};
            case "down" -> new Object[]{new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}, new double[]{0, -1, 0}, new double[]{x0, 16 - z1, x1, 16 - z0}};
            case "north" -> new Object[]{new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}, new double[]{0, 0, -1}, new double[]{16 - x1, 16 - y1, 16 - x0, 16 - y0}};
            case "south" -> new Object[]{new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}, new double[]{0, 0, 1}, new double[]{x0, 16 - y1, x1, 16 - y0}};
            case "west" -> new Object[]{new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}, new double[]{-1, 0, 0}, new double[]{z0, 16 - y1, z1, 16 - y0}};
            default -> new Object[]{new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}, new double[]{1, 0, 0}, new double[]{16 - z1, 16 - y1, 16 - z0, 16 - y0}};
        };
    }

    private static double[][] elementMatrix(JsonObject e) {
        JsonElement re = e.get("rotation");
        if (re == null || !re.isJsonObject()) return IDENTITY;
        JsonObject r = re.getAsJsonObject();
        double[] o = nums(r.get("origin"), 8, 8, 8);
        double[][] rot;
        if (r.has("axis")) {
            String axis = r.get("axis").isJsonPrimitive() ? r.get("axis").getAsString() : "y";
            double angle = radians(r.has("angle") ? num(r.get("angle")) : 0);
            rot = rot(axis.isEmpty() ? 'y' : axis.charAt(0), angle);
            if (truthy(r.get("rescale")) && Math.abs(StrictMath.cos(angle)) > 1e-6) {
                double k = 1 / Math.abs(StrictMath.cos(angle));
                rot = mul(scale(axis.equals("x") ? 1.0 : k, axis.equals("y") ? 1.0 : k, axis.equals("z") ? 1.0 : k), rot);
            }
        } else {
            rot = mul(mul(rot('x', radians(num(r.get("x")))), rot('y', radians(num(r.get("y"))))), rot('z', radians(num(r.get("z")))));
        }
        return mul(mul(translate(o[0], o[1], o[2]), rot), translate(-o[0], -o[1], -o[2]));
    }

    private static boolean truthy(JsonElement e) {
        if (e == null || e.isJsonNull()) return false;
        if (!e.isJsonPrimitive()) return e.isJsonArray() ? !e.getAsJsonArray().isEmpty() : !e.getAsJsonObject().isEmpty();
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber()) return p.getAsDouble() != 0;
        return !p.getAsString().isEmpty();
    }

    private static String stripHashes(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '#') i++;
        return s.substring(i);
    }

    private static void blockQuads(Shape shape, double[][] m, JsonArray elements, String prefix, boolean tinted) {
        m = mul(m, scale(1 / 16.0, 1 / 16.0, 1 / 16.0));
        for (JsonElement ee : elements) {
            if (!ee.isJsonObject()) continue;
            JsonObject e = ee.getAsJsonObject();
            JsonElement f = e.get("from"), t = e.get("to");
            if (f == null || t == null || !f.isJsonArray() || !t.isJsonArray() || f.getAsJsonArray().size() != 3 || t.getAsJsonArray().size() != 3) continue;
            double[][] em = mul(m, elementMatrix(e));
            JsonObject faces = e.get("faces") != null && e.get("faces").isJsonObject() ? e.getAsJsonObject("faces") : new JsonObject();
            for (String name : BLOCK_FACES) {
                JsonElement se = faces.get(name);
                if (se == null || !se.isJsonObject()) continue;
                JsonObject spec = se.getAsJsonObject();
                if (!isString(spec.get("texture"))) continue;
                Object[] fc = face(name, nums(f), nums(t));
                double[] auto = (double[]) fc[2];
                double[] uv = spec.get("uv") != null && spec.get("uv").isJsonArray() && spec.getAsJsonArray("uv").size() == 4 ? nums(spec.get("uv")) : auto;
                double u0 = uv[0] / 16, v0 = uv[1] / 16, u1 = uv[2] / 16, v1 = uv[3] / 16;
                double[][] natural = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                int k = Math.floorMod(Math.floorDiv((int) num(spec.get("rotation")), 90), 4);
                double[][] uvs = new double[4][];
                for (int i = 0; i < 4; i++) uvs[i] = natural[Math.floorMod(i - k, 4)];
                int tint = tinted && isInt(spec.get("tintindex")) ? spec.get("tintindex").getAsInt() : -1;
                shape.add(em, (double[][]) fc[0], uvs, (double[]) fc[1], prefix + stripHashes(spec.get("texture").getAsString()), tint);
            }
        }
    }

    private static void entityQuads(Shape shape, double[][] m, Layer layer) {
        for (Part p : layer.parts) partQuads(shape, m, p, layer.sizes);
    }

    private static void partQuads(Shape shape, double[][] pm, Part p, Map<String, double[]> sizes) {
        double[] po = p.pose;
        pm = mul(pm, translate(po[0] / 16, po[1] / 16, po[2] / 16));
        if (po[3] != 0 || po[4] != 0 || po[5] != 0) pm = mul(pm, mul(mul(rot('z', po[5]), rot('y', po[4])), rot('x', po[3])));
        if (po[6] != 1) pm = mul(pm, scale(po[6], po[6], po[6]));
        double[][] vm = mul(pm, scale(1 / 16.0, 1 / 16.0, 1 / 16.0));
        for (Box b : p.boxes) cube(shape, vm, b, sizes.get(b.var));
        for (Part k : p.kids) partQuads(shape, pm, k, sizes);
    }

    private static void cube(Shape shape, double[][] m, Box b, double[] size) {
        double u = b.u, v = b.v, w = b.w, h = b.h, d = b.d, g = b.grow;
        double x0 = b.x - g, y0 = b.y - g, z0 = b.z - g, x1 = b.x + w + g, y1 = b.y + h + g, z1 = b.z + d + g;
        if (b.mirror) { double t = x0; x0 = x1; x1 = t; }
        double[] v0 = {x0, y0, z0}, v1 = {x1, y0, z0}, v2 = {x1, y1, z0}, v3 = {x0, y1, z0};
        double[] v4 = {x0, y0, z1}, v5 = {x1, y0, z1}, v6 = {x1, y1, z1}, v7 = {x0, y1, z1};
        double a = u, bb = u + d, c = u + d + w, dd = u + d + w + w, e = u + d + w + d, f = u + d + w + d + w;
        double t0 = v, t1 = v + d, t2 = v + d + h;
        Object[][] faces = {
                {"down", new double[][]{v5, v4, v0, v1}, new double[]{bb, t0, c, t1}, new double[]{0, -1, 0}},
                {"up", new double[][]{v2, v3, v7, v6}, new double[]{c, t1, dd, t0}, new double[]{0, 1, 0}},
                {"west", new double[][]{v0, v4, v7, v3}, new double[]{a, t1, bb, t2}, new double[]{-1, 0, 0}},
                {"north", new double[][]{v1, v0, v3, v2}, new double[]{bb, t1, c, t2}, new double[]{0, 0, -1}},
                {"east", new double[][]{v5, v1, v2, v6}, new double[]{c, t1, e, t2}, new double[]{1, 0, 0}},
                {"south", new double[][]{v4, v5, v6, v7}, new double[]{e, t1, f, t2}, new double[]{0, 0, 1}}};
        double tw = size[0], th = size[1];
        for (Object[] fc : faces) {
            String name = (String) fc[0];
            if (b.only != null && !name.equals(b.only)) continue;
            double[][] verts = (double[][]) fc[1];
            double[] puv = (double[]) fc[2];
            double[] normal = (double[]) fc[3];
            double pu1 = puv[0], pv1 = puv[1], pu2 = puv[2], pv2 = puv[3];
            double[][] uvs = {{pu2 / tw, pv1 / th}, {pu1 / tw, pv1 / th}, {pu1 / tw, pv2 / th}, {pu2 / tw, pv2 / th}};
            if (b.mirror) {
                verts = new double[][]{verts[3], verts[2], verts[1], verts[0]};
                uvs = new double[][]{uvs[3], uvs[2], uvs[1], uvs[0]};
                if (name.equals("west") || name.equals("east")) normal = new double[]{-normal[0], 0, 0};
            }
            shape.add(m, verts, uvs, normal, b.var, b.tint);
        }
    }

    // ---- JSON helpers

    private static boolean isString(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    private static boolean isInt(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return false;
        return e.getAsString().matches("-?\\d+");
    }

    private static String str(JsonElement e) {
        return e == null || e.isJsonNull() ? "" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static String type(JsonObject node) {
        return str(node.get("type")).replace("minecraft:", "");
    }

    /** A model with its parents merged (mcassets.py: model()). */
    private record Model(JsonObject textures, JsonArray elements, JsonObject gui, String light, boolean flat) {
    }

    private static final class Extractor {
        final ZipFile z;
        final Set<String> names = new HashSet<>();
        final Map<String, Model> models = new HashMap<>();

        Extractor(ZipFile z) {
            this.z = z;
            z.stream().forEach(e -> names.add(e.getName()));
        }

        JsonElement readJson(String name) throws IOException {
            ZipEntry e = z.getEntry(name);
            if (e == null) return null;
            try (InputStream in = z.getInputStream(e)) {
                return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        Model model(String ref) {
            ref = ref.replace("minecraft:", "");
            Model have = models.get(ref);
            if (have != null) return have;
            List<JsonObject> chain = new ArrayList<>();
            boolean generated = false;
            String seen = ref;
            while (seen != null && !seen.isEmpty() && chain.size() + (generated ? 1 : 0) < 16) {
                seen = seen.replace("minecraft:", "");
                if (seen.equals("item/generated") || seen.equals("builtin/generated")) {
                    generated = true;
                    break;
                }
                JsonElement m;
                try {
                    m = readJson(A + "models/" + seen + ".json");
                } catch (Exception e) {
                    break;
                }
                if (m == null) break;
                JsonObject o = m.isJsonObject() ? m.getAsJsonObject() : new JsonObject();
                chain.add(o);
                seen = isString(o.get("parent")) ? o.get("parent").getAsString() : null;
            }
            JsonObject textures = new JsonObject();
            JsonArray elements = null;
            JsonObject gui = null;
            String light = null;
            boolean flat = generated; // the generated marker is the root of the chain, applied first
            for (int i = chain.size() - 1; i >= 0; i--) {
                JsonObject m = chain.get(i);
                if (m.has("textures") && m.get("textures").isJsonObject())
                    for (Map.Entry<String, JsonElement> t : m.getAsJsonObject("textures").entrySet()) textures.add(t.getKey(), t.getValue());
                if (m.has("elements") && m.get("elements").isJsonArray()) {
                    elements = m.getAsJsonArray("elements");
                    flat = false;
                }
                if (m.has("display") && m.get("display").isJsonObject()) {
                    JsonElement g = m.getAsJsonObject("display").get("gui");
                    if (g != null && g.isJsonObject()) gui = g.getAsJsonObject();
                }
                if (isString(m.get("gui_light"))) light = m.get("gui_light").getAsString();
            }
            Model res = new Model(textures, elements, gui, light, flat);
            models.put(ref, res);
            return res;
        }

        static String resolve(JsonObject tex, String key) {
            int seen = 0;
            JsonElement v = tex.get(key);
            while (seen < 8) {
                if (v != null && v.isJsonObject()) v = v.getAsJsonObject().get("sprite"); // {"sprite": ..., "force_translucent": ...}
                if (!(isString(v) && v.getAsString().startsWith("#"))) break;
                v = tex.get(v.getAsString().substring(1));
                seen++;
            }
            return isString(v) && !v.getAsString().startsWith("#") ? v.getAsString().replace("minecraft:", "") : null;
        }

        record Pick(JsonObject node, double[][] m) {
        }

        List<Pick> pick(JsonElement ne, double[][] m) {
            if (ne == null || !ne.isJsonObject()) return List.of();
            JsonObject node = ne.getAsJsonObject();
            if (node.has("transformation")) m = mul(m, transformation(node.get("transformation")));
            String t = type(node);
            if (t.equals("model") || t.equals("special")) return List.of(new Pick(node, m));
            if (t.equals("composite")) {
                List<Pick> out = new ArrayList<>();
                JsonElement ms = node.get("models");
                if (ms != null && ms.isJsonArray()) for (JsonElement sub : ms.getAsJsonArray()) out.addAll(pick(sub, m));
                return out;
            }
            if (t.equals("select") && str(node.get("property")).endsWith("display_context")) {
                JsonElement cases = node.get("cases");
                if (cases != null && cases.isJsonArray()) for (JsonElement c : cases.getAsJsonArray()) {
                    if (!c.isJsonObject()) continue;
                    JsonElement when = c.getAsJsonObject().get("when");
                    boolean gui = false;
                    if (when != null && when.isJsonArray()) {
                        for (JsonElement w : when.getAsJsonArray()) if (isString(w) && w.getAsString().equals("gui")) gui = true;
                    } else if (isString(when) && when.getAsString().equals("gui")) gui = true;
                    if (gui) return pick(c.getAsJsonObject().get("model"), m);
                }
            }
            for (String k : new String[]{"fallback", "on_false", "model"}) {
                JsonElement v = node.get(k);
                if (v != null && v.isJsonObject()) {
                    List<Pick> r = pick(v, m);
                    if (!r.isEmpty()) return r;
                }
            }
            for (Map.Entry<String, JsonElement> en : node.entrySet()) {
                JsonElement v = en.getValue();
                List<JsonElement> subs = new ArrayList<>();
                if (v.isJsonArray()) v.getAsJsonArray().forEach(subs::add);
                else if (v.isJsonObject()) subs.add(v);
                for (JsonElement sub : subs) {
                    JsonElement target = sub.isJsonObject() && !sub.getAsJsonObject().has("type") ? sub.getAsJsonObject().get("model") : sub;
                    List<Pick> r = pick(target, m);
                    if (!r.isEmpty()) return r;
                }
            }
            return List.of();
        }

        static String colour(JsonElement te) {
            if (te == null || !te.isJsonObject()) return null;
            JsonObject t = te.getAsJsonObject();
            JsonElement v = t.has("value") ? t.get("value") : t.get("default");
            if (isInt(v)) return String.format("#%06x", v.getAsLong() & 0xFFFFFF);
            if (v != null && v.isJsonArray() && v.getAsJsonArray().size() >= 3) {
                StringBuilder s = new StringBuilder("#");
                for (int i = 0; i < 3; i++) s.append(String.format("%02x", (int) (Math.max(0, Math.min(1, num(v.getAsJsonArray().get(i)))) * 255)));
                return s.toString();
            }
            return BIOME_TINT.get(str(t.get("type")));
        }

        /** Adds a special (code-drawn) item's entity model; returns {owner variable or null} or null when it can't. */
        static String[] special(JsonObject spec, double[][] m, Shape shape, Map<String, String> textures) {
            String kind = type(spec);
            String tex = spec.has("texture") ? str(spec.get("texture")).replace("minecraft:", "") : "";
            if (tex.startsWith("textures/")) tex = tex.substring("textures/".length());
            if (tex.endsWith(".png")) tex = tex.substring(0, tex.length() - 4);
            Layer layer = null;
            String owner = null;
            switch (kind) {
                case "chest" -> { layer = LAYERS.get("chest"); textures.put("0", "entity/chest/" + (tex.isEmpty() ? "normal" : tex)); }
                case "shulker_box" -> { layer = LAYERS.get("shulker_box"); textures.put("0", "entity/shulker/" + (tex.isEmpty() ? "shulker" : tex)); }
                case "banner" -> { layer = LAYERS.get("banner"); textures.put("0", "entity/banner/banner_base"); textures.put("1", "entity/banner/base"); }
                case "shield" -> { layer = LAYERS.get("shield"); textures.put("0", "entity/shield/shield_base_nopattern"); }
                case "conduit" -> { layer = LAYERS.get("conduit"); textures.put("0", "entity/conduit/base"); }
                case "decorated_pot" -> { layer = LAYERS.get("decorated_pot"); textures.put("0", "entity/decorated_pot/decorated_pot_base"); textures.put("1", "entity/decorated_pot/decorated_pot_side"); }
                case "copper_golem_statue" -> { layer = LAYERS.get("copper_golem_statue"); textures.put("0", tex.isEmpty() ? "entity/copper_golem/copper_golem" : tex); }
                case "player_head" -> { layer = LAYERS.get("humanoid_head"); textures.put("0", "entity/player/wide/steve"); owner = "0"; }
                case "head" -> {
                    String k = str(spec.get("kind"));
                    textures.put("0", tex.isEmpty() ? HEADS.getOrDefault(k, "") : tex);
                    layer = LAYERS.get(k.equals("dragon") ? "dragon_head" : k.equals("piglin") ? "piglin_head" : k.equals("zombie") ? "humanoid_head" : "mob_head");
                }
                default -> { }
            }
            if (layer == null || textures.get("0") == null || textures.get("0").isEmpty()) return null;
            entityQuads(shape, m, layer);
            return new String[]{owner};
        }

        int run(File out) throws IOException {
            for (String n : names) {
                if (n.endsWith(".png") && (n.startsWith(A + "textures/item/") || n.startsWith(A + "textures/block/")))
                    copy(n, new File(out, n.substring((A + "textures/").length())));
            }
            for (String rel : EXTRA) if (names.contains(A + rel)) copy(A + rel, new File(out, rel.substring("textures/".length())));

            Map<String, Map<String, Object>> index = new LinkedHashMap<>();
            Map<String, List<List<Object>>> shapes = new LinkedHashMap<>();
            Map<String, String> shapeIds = new HashMap<>();
            for (String n : new TreeSet<>(names)) {
                if (!(n.startsWith(A + "items/") && n.endsWith(".json"))) continue;
                String item = n.substring((A + "items/").length(), n.length() - 5);
                List<Pick> picked;
                try {
                    JsonElement d = readJson(n);
                    picked = pick(d != null && d.isJsonObject() ? d.getAsJsonObject().get("model") : null, IDENTITY);
                } catch (Exception e) {
                    continue;
                }
                if (picked.isEmpty()) continue;
                JsonObject first = picked.get(0).node;
                List<String> tints = new ArrayList<>();
                if (first.has("tints") && first.get("tints").isJsonArray()) for (JsonElement t : first.getAsJsonArray("tints")) tints.add(colour(t));
                if (str(first.get("type")).endsWith("model") && picked.size() == 1) {
                    Model md = model(str(first.get("model")));
                    if (md.flat) {
                        List<String> layers = new ArrayList<>();
                        int k = 0;
                        while (resolve(md.textures, "layer" + k) != null) {
                            layers.add(resolve(md.textures, "layer" + k));
                            k++;
                        }
                        if (!layers.isEmpty()) {
                            Map<String, Object> e = new LinkedHashMap<>();
                            e.put("t", "flat");
                            e.put("l", layers);
                            boolean anyTint = false;
                            for (int i = 0; i < Math.min(layers.size(), tints.size()); i++) if (tints.get(i) != null) anyTint = true;
                            if (anyTint) {
                                List<String> c = new ArrayList<>();
                                for (int i = 0; i < layers.size(); i++) c.add(i < tints.size() ? tints.get(i) : null);
                                e.put("c", c);
                            }
                            index.put(item, e);
                        }
                        continue;
                    }
                }
                // A drawn model: every picked layer's quads in one shape, in the first layer's lighting and display.
                Map<String, String> textures = new HashMap<>();
                String owner = null;
                Shape shape = null;
                for (int i = 0; i < picked.size(); i++) {
                    JsonObject node = picked.get(i).node;
                    double[][] m = picked.get(i).m;
                    String kind = type(node);
                    Model base = model(str(node.get(kind.equals("special") ? "base" : "model")));
                    if (shape == null) shape = new Shape(gui(base.gui), base.light != null && !base.light.isEmpty() ? base.light : "side");
                    if (kind.equals("special")) {
                        JsonObject sm = node.has("model") && node.get("model").isJsonObject() ? node.getAsJsonObject("model") : new JsonObject();
                        if (i == 0 && node.has("model") && node.get("model").isJsonObject() && str(sm.get("type")).endsWith("banner")) {
                            Integer dye = DYES.get(str(sm.get("color")));
                            tints = new ArrayList<>(List.of(String.format("#%06x", dye != null ? dye : 0xFFFFFF)));
                        }
                        String[] o = special(sm, m, shape, textures);
                        if (owner == null && o != null) owner = o[0];
                        continue;
                    }
                    if (base.flat || base.elements == null || base.elements.isEmpty()) continue;
                    String prefix = i == 0 ? "" : i + "/";
                    blockQuads(shape, m, base.elements, prefix, i == 0);
                    for (JsonElement ee : base.elements) {
                        if (!ee.isJsonObject()) continue;
                        JsonElement fs = ee.getAsJsonObject().get("faces");
                        if (fs == null || !fs.isJsonObject()) continue;
                        for (Map.Entry<String, JsonElement> fe : fs.getAsJsonObject().entrySet()) {
                            JsonElement spec = fe.getValue();
                            if (!spec.isJsonObject() || !isString(spec.getAsJsonObject().get("texture"))) continue;
                            String var = stripHashes(spec.getAsJsonObject().get("texture").getAsString());
                            if (!textures.containsKey(prefix + var)) {
                                // a face names a texture variable, with or without "#"; a bare path is a texture itself
                                String res = resolve(base.textures, var);
                                textures.put(prefix + var, res != null && !res.isEmpty() ? res : var.contains("/") ? var.replace("minecraft:", "") : null);
                            }
                        }
                    }
                }
                List<List<Object>> quads = new ArrayList<>();
                if (shape != null) for (List<Object> q : shape.result()) {
                    String tx = textures.get((String) q.get(0));
                    if (tx != null && !tx.isEmpty()) quads.add(q);
                }
                if (quads.isEmpty()) continue;
                String key = GSON.toJson(quads);
                String id = shapeIds.get(key);
                if (id == null) {
                    id = String.valueOf(shapeIds.size());
                    shapeIds.put(key, id);
                    shapes.put(id, quads);
                }
                TreeSet<String> used = new TreeSet<>();
                for (List<Object> q : quads) used.add((String) q.get(0));
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("t", "model");
                e.put("g", id);
                Map<String, String> x = new LinkedHashMap<>();
                for (String v : used) x.put(v, textures.get(v));
                e.put("x", x);
                boolean anyQuadTint = false, anyTint = false;
                for (List<Object> q : quads) if (((Number) q.get(1)).intValue() >= 0) anyQuadTint = true;
                for (String t : tints) if (t != null && !t.isEmpty()) anyTint = true;
                if (anyQuadTint && anyTint) e.put("c", tints);
                if (owner != null) e.put("o", owner);
                index.put(item, e);
            }
            // Copy the entity textures the shapes use, then keep only entries whose textures are all there.
            for (Map<String, Object> e : index.values()) {
                @SuppressWarnings("unchecked") Map<String, String> x = (Map<String, String>) e.get("x");
                if (x == null) continue;
                for (String p : x.values())
                    if (p.startsWith("entity/") && names.contains(A + "textures/" + p + ".png")) copy(A + "textures/" + p + ".png", new File(out, p + ".png"));
            }
            Map<String, Map<String, Object>> clean = new LinkedHashMap<>();
            Set<String> usedShapes = new HashSet<>();
            for (Map.Entry<String, Map<String, Object>> en : index.entrySet()) {
                Map<String, Object> e = en.getValue();
                List<String> files = new ArrayList<>();
                if (e.get("l") instanceof List<?> l) for (Object o : l) files.add((String) o);
                else if (e.get("x") instanceof Map<?, ?> x) for (Object o : x.values()) files.add((String) o);
                boolean all = !files.isEmpty();
                for (String f : files) if (f == null || !new File(out, f + ".png").exists()) all = false;
                if (all) {
                    clean.put(en.getKey(), e);
                    if (e.get("g") != null) usedShapes.add((String) e.get("g"));
                }
            }
            Map<String, List<List<Object>>> keep = new LinkedHashMap<>();
            for (Map.Entry<String, List<List<Object>>> s : shapes.entrySet()) if (usedShapes.contains(s.getKey())) keep.put(s.getKey(), s.getValue());
            Files.writeString(new File(out, "index.json").toPath(), GSON.toJson(clean));
            Files.writeString(new File(out, "shapes.json").toPath(), GSON.toJson(keep));
            return clean.size();
        }

        void copy(String name, File dest) throws IOException {
            dest.getParentFile().mkdirs();
            try (InputStream in = z.getInputStream(z.getEntry(name))) {
                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Copies the textures Studio uses and writes index.json and shapes.json. Returns how many items it can draw. */
    static int extract(ZipFile z, File out) throws IOException {
        return new Extractor(z).run(out);
    }

    // ---------------------------------------------------------------- the bitmap font as TrueType

    private static final int UNIT = 128;      // font units per Minecraft pixel
    private static final int EM = 8 * UNIT;   // the em is one 8-pixel line

    private record Glyph(int advance, List<double[]> rects) {
    }

    /** Writes Minecraft's default bitmap font (ascii, accented, nonlatin_european) as a TrueType font. */
    static void buildFont(ZipFile z, File dest) throws IOException {
        JsonObject inc;
        try (InputStream in = z.getInputStream(z.getEntry(A + "font/include/default.json"))) {
            inc = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Map<Integer, Glyph> glyphs = new LinkedHashMap<>();
        JsonArray providers = inc.has("providers") ? inc.getAsJsonArray("providers") : new JsonArray();
        for (JsonElement pe : providers) {
            JsonObject prov = pe.getAsJsonObject();
            if (!"bitmap".equals(str(prov.get("type")))) continue;
            String fname = prov.get("file").getAsString().replace("minecraft:", "");
            BufferedImage img;
            try (InputStream in = z.getInputStream(z.getEntry(A + "textures/" + fname))) {
                img = ImageIO.read(new ByteArrayInputStream(in.readAllBytes()));
            }
            if (img == null) throw new IOException("can't read " + fname);
            int w = img.getWidth(), h = img.getHeight();
            List<int[]> chars = new ArrayList<>();
            if (prov.has("chars")) for (JsonElement r : prov.getAsJsonArray("chars")) chars.add(r.getAsString().codePoints().toArray());
            if (chars.isEmpty()) continue;
            int cols = 0;
            for (int[] r : chars) cols = Math.max(cols, r.length);
            int nrows = chars.size();
            int cw = w / cols, ch = h / nrows;
            double height = prov.has("height") ? prov.get("height").getAsDouble() : 8;
            double ascent = prov.has("ascent") ? prov.get("ascent").getAsDouble() : 7;
            double scale = height / ch;
            for (int ry = 0; ry < nrows; ry++) {
                int[] row = chars.get(ry);
                for (int rx = 0; rx < row.length; rx++) {
                    int c = row[rx];
                    if (c == 0 || c == ' ' || glyphs.containsKey(c)) continue;
                    boolean[][] cells = new boolean[ch][cw];
                    for (int y = 0; y < ch; y++)
                        for (int x = 0; x < cw; x++) cells[y][x] = ((img.getRGB(rx * cw + x, ry * ch + y) >>> 24) & 0xff) > 0;
                    int width = 0;
                    for (int x = 0; x < cw; x++) for (int y = 0; y < ch; y++) if (cells[y][x]) { width = x + 1; break; }
                    List<double[]> rects = new ArrayList<>();
                    for (int y = 0; y < ch; y++) {
                        int x = 0;
                        while (x < cw) {
                            if (cells[y][x]) {
                                int x0 = x;
                                while (x < cw && cells[y][x]) x++;
                                double top = ascent - y * scale;
                                rects.add(new double[]{x0 * scale, top - scale, x * scale, top});
                            } else x++;
                        }
                    }
                    List<double[]> merged = new ArrayList<>(); // merge rectangles stacked with the same columns
                    for (double[] r : rects) {
                        boolean done = false;
                        for (int i = 0; i < merged.size(); i++) {
                            double[] m = merged.get(i);
                            if (m[0] == r[0] && m[2] == r[2] && Math.abs(m[1] - r[3]) < 1e-6) {
                                merged.set(i, new double[]{m[0], r[1], m[2], m[3]});
                                done = true;
                                break;
                            }
                        }
                        if (!done) merged.add(r);
                    }
                    glyphs.put(c, new Glyph((int) (width * scale + 0.5) + 1, merged));
                }
            }
        }
        glyphs.put((int) ' ', new Glyph(4, List.of()));
        glyphs.put(0xA0, new Glyph(4, List.of()));
        byte[] data = ttf(glyphs);
        dest.getParentFile().mkdirs();
        Files.write(dest.toPath(), data);
    }

    private static final class Bytes {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Bytes u32(long v) { ByteBuffer b = ByteBuffer.allocate(4); b.putInt((int) v); out.writeBytes(b.array()); return this; }
        Bytes u16(int v) { out.write((v >> 8) & 0xff); out.write(v & 0xff); return this; }
        Bytes i16(int v) { return u16(v & 0xffff); }
        Bytes i64(long v) { ByteBuffer b = ByteBuffer.allocate(8); b.putLong(v); out.writeBytes(b.array()); return this; }
        Bytes raw(byte[] v) { out.writeBytes(v); return this; }
        Bytes pad4() { while (out.size() % 4 != 0) out.write(0); return this; }
        int size() { return out.size(); }
        byte[] get() { return out.toByteArray(); }
    }

    private static int bitLength(int n) {
        return 32 - Integer.numberOfLeadingZeros(n);
    }

    private static byte[] ttf(Map<Integer, Glyph> glyphs) {
        List<Integer> chars = new ArrayList<>();
        for (int c : glyphs.keySet()) if (c < 0xFFFF) chars.add(c);
        chars.sort(Integer::compare);
        List<Integer> order = new ArrayList<>();
        order.add(null); // glyph 0 is .notdef
        order.addAll(chars);
        Bytes glyf = new Bytes(), hmtx = new Bytes();
        List<Integer> loca = new ArrayList<>();
        int maxPts = 0, maxCont = 0;
        int xmin = 1 << 30, ymin = 1 << 30, xmax = -(1 << 30), ymax = -(1 << 30);
        int advMax = 0;
        for (Integer c : order) {
            loca.add(glyf.size());
            int adv;
            List<double[]> rects;
            if (c == null) {
                adv = 6;
                rects = List.of(new double[]{0, 0, 5, 1}, new double[]{0, 6, 5, 7}, new double[]{0, 1, 1, 6}, new double[]{4, 1, 5, 6});
            } else {
                adv = glyphs.get(c).advance;
                rects = glyphs.get(c).rects;
            }
            int advu = adv * UNIT;
            advMax = Math.max(advMax, advu);
            if (rects.isEmpty()) {
                hmtx.u16(advu).i16(0);
                continue;
            }
            List<int[]> pts = new ArrayList<>();
            List<Integer> ends = new ArrayList<>();
            for (double[] r : rects) {
                int a = (int) Math.rint(r[0] * UNIT), b = (int) Math.rint(r[1] * UNIT), cc = (int) Math.rint(r[2] * UNIT), d = (int) Math.rint(r[3] * UNIT);
                pts.add(new int[]{a, b});
                pts.add(new int[]{a, d});
                pts.add(new int[]{cc, d});
                pts.add(new int[]{cc, b});
                ends.add(pts.size() - 1);
            }
            int gx0 = Integer.MAX_VALUE, gy0 = Integer.MAX_VALUE, gx1 = Integer.MIN_VALUE, gy1 = Integer.MIN_VALUE;
            for (int[] p : pts) { gx0 = Math.min(gx0, p[0]); gy0 = Math.min(gy0, p[1]); gx1 = Math.max(gx1, p[0]); gy1 = Math.max(gy1, p[1]); }
            xmin = Math.min(xmin, gx0); ymin = Math.min(ymin, gy0); xmax = Math.max(xmax, gx1); ymax = Math.max(ymax, gy1);
            maxPts = Math.max(maxPts, pts.size());
            maxCont = Math.max(maxCont, ends.size());
            Bytes g = new Bytes();
            g.i16(ends.size()).i16(gx0).i16(gy0).i16(gx1).i16(gy1);
            for (int e : ends) g.u16(e);
            g.u16(0);
            for (int i = 0; i < pts.size(); i++) g.out.write(1);
            Bytes xs = new Bytes(), ys = new Bytes();
            int px = 0, py = 0;
            for (int[] p : pts) {
                xs.i16(p[0] - px);
                ys.i16(p[1] - py);
                px = p[0];
                py = p[1];
            }
            g.raw(xs.get()).raw(ys.get()).pad4();
            glyf.raw(g.get());
            hmtx.u16(advu).i16(gx0);
        }
        loca.add(glyf.size());
        int n = order.size();
        int asc = 7 * UNIT, desc = -1 * UNIT;
        byte[] head = new Bytes().u32(0x00010000).u32(0x00010000).u32(0).u32(0x5F0F3CF5L).u16(0x000B).u16(EM).i64(0).i64(0)
                .i16(xmin).i16(ymin).i16(xmax).i16(ymax).u16(0).u16(8).i16(2).i16(1).i16(0).get();
        byte[] hhea = new Bytes().u32(0x00010000).i16(asc).i16(desc).i16(UNIT).u16(advMax).i16(0).i16(0).i16(xmax).i16(1)
                .i16(0).i16(0).i16(0).i16(0).i16(0).i16(0).i16(0).u16(n).get();
        byte[] maxp = new Bytes().u32(0x00010000).u16(n).u16(maxPts).u16(maxCont).u16(0).u16(0).u16(2).u16(0).u16(0).u16(0)
                .u16(0).u16(0).u16(0).u16(0).u16(0).get();
        int first = chars.get(0), last = chars.get(chars.size() - 1);
        Bytes os2 = new Bytes().u16(4).i16(6 * UNIT).u16(400).u16(5).u16(0);
        os2.i16(4 * UNIT).i16(4 * UNIT).i16(0).i16(UNIT).i16(4 * UNIT).i16(4 * UNIT).i16(0).i16(3 * UNIT).i16(UNIT).i16(3 * UNIT);
        os2.i16(0).raw(new byte[10]).u32(3).u32(0).u32(0).u32(0).raw("HCST".getBytes(StandardCharsets.US_ASCII));
        os2.u16(0x40).u16(first).u16(last).i16(asc).i16(desc).i16(UNIT).u16(11 * UNIT).u16(3 * UNIT);
        os2.u32(1).u32(0).i16(5 * UNIT).i16(7 * UNIT).u16(0).u16(32).u16(1);
        // cmap format 4: one segment per character
        List<int[]> segs = new ArrayList<>();
        for (int i = 0; i < chars.size(); i++) segs.add(new int[]{chars.get(i), chars.get(i), (i + 1 - chars.get(i)) & 0xFFFF});
        segs.add(new int[]{0xFFFF, 0xFFFF, 1});
        int sc = segs.size();
        int sr = 2 * (1 << (bitLength(sc) - 1));
        Bytes sub = new Bytes().u16(4).u16(0).u16(0).u16(sc * 2).u16(sr).u16(bitLength(sc) - 1).u16(sc * 2 - sr);
        for (int[] s : segs) sub.u16(s[1]);
        sub.u16(0);
        for (int[] s : segs) sub.u16(s[0]);
        for (int[] s : segs) sub.u16(s[2]);
        for (int i = 0; i < sc; i++) sub.u16(0);
        byte[] subb = sub.get();
        subb[2] = (byte) ((subb.length >> 8) & 0xff);
        subb[3] = (byte) (subb.length & 0xff);
        byte[] cmap = new Bytes().u16(0).u16(1).u16(3).u16(1).u32(12).raw(subb).get();
        int[] nids = {1, 2, 3, 4, 6};
        String[] nstr = {"Minecraft Studio", "Regular", "Minecraft Studio Regular", "Minecraft Studio", "MinecraftStudio-Regular"};
        Bytes strs = new Bytes(), recs = new Bytes();
        for (int i = 0; i < nids.length; i++) {
            byte[] enc = nstr[i].getBytes(StandardCharsets.UTF_16BE);
            recs.u16(3).u16(1).u16(0x409).u16(nids[i]).u16(enc.length).u16(strs.size());
            strs.raw(enc);
        }
        byte[] name = new Bytes().u16(0).u16(nids.length).u16(6 + 12 * nids.length).raw(recs.get()).raw(strs.get()).get();
        byte[] post = new Bytes().u32(0x00030000).u32(0).i16(-UNIT).i16(UNIT).u32(0).u32(0).u32(0).u32(0).u32(0).get();
        Bytes locaT = new Bytes();
        for (int o : loca) locaT.u32(o);
        Map<String, byte[]> tables = new java.util.TreeMap<>(); // sorted by tag, as bytes (ASCII)
        tables.put("OS/2", os2.get());
        tables.put("cmap", cmap);
        tables.put("glyf", glyf.get());
        tables.put("head", head);
        tables.put("hhea", hhea);
        tables.put("hmtx", hmtx.get());
        tables.put("loca", locaT.get());
        tables.put("maxp", maxp);
        tables.put("name", name);
        tables.put("post", post);
        int nt = tables.size();
        int es = bitLength(nt) - 1;
        Bytes font = new Bytes().u32(0x00010000).u16(nt).u16(16 * (1 << es)).u16(es).u16(nt * 16 - 16 * (1 << es));
        int offset = 12 + 16 * nt;
        Bytes body = new Bytes();
        int hpos = -1;
        for (Map.Entry<String, byte[]> t : tables.entrySet()) {
            byte[] d = t.getValue();
            if (t.getKey().equals("head")) hpos = offset + body.size();
            font.raw(t.getKey().getBytes(StandardCharsets.US_ASCII)).u32(sum(d)).u32(offset + body.size()).u32(d.length);
            body.raw(d).pad4();
        }
        byte[] all = new Bytes().raw(font.get()).raw(body.get()).get();
        long adj = (0xB1B0AFBAL - sum(all)) & 0xFFFFFFFFL;
        all[hpos + 8] = (byte) (adj >>> 24);
        all[hpos + 9] = (byte) (adj >>> 16);
        all[hpos + 10] = (byte) (adj >>> 8);
        all[hpos + 11] = (byte) adj;
        return all;
    }

    private static long sum(byte[] data) {
        long s = 0;
        for (int i = 0; i < data.length; i += 4) {
            long v = 0;
            for (int k = 0; k < 4; k++) v = (v << 8) | (i + k < data.length ? data[i + k] & 0xff : 0);
            s += v;
        }
        return s & 0xFFFFFFFFL;
    }

    // ---------------------------------------------------------------- standalone check

    /** Runs the whole download and extract synchronously into {@code <cacheDir>/minecraft}: {@code <cacheDir> <mcVersion>}. */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: StudioAssets <cacheDir> <mcVersion>");
            System.exit(2);
        }
        StudioAssets a = new StudioAssets(new File(args[0]), args[1], Logger.getLogger("HeadcountStudio"));
        synchronized (a.lock) {
            a.job = job("running", 0, null, "Finding the version");
        }
        long t = System.currentTimeMillis();
        a.run();
        System.out.println(GSON.toJson(a.status()) + " in " + (System.currentTimeMillis() - t) / 1000.0 + "s");
        if (!"done".equals(a.job.get("state"))) System.exit(1);
    }
}
