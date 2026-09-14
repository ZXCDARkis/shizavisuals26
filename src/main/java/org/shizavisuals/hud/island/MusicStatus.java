package org.shizavisuals.hud.island;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.shizavisuals.context.MinecraftContext;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MusicStatus extends ExtandableStatus {
    private static final float EXP_WIDTH = 164.0F;
    private static final float EXP_HEIGHT = 80.0F;
    private static final float MAX_COLLAPSED_WIDTH = 100.0F;
    private static final float KARAOKE_WIDTH = 140.0F;

    private static final Identifier NO_IMAGE = Identifier.parse("shizavisuals:icons/hud/island/music/no_image.png");
    private static final Identifier PREVIOUS = Identifier.parse("shizavisuals:icons/hud/island/music/previous.png");
    private static final Identifier PLAY = Identifier.parse("shizavisuals:icons/hud/island/music/play.png");
    private static final Identifier PAUSE = Identifier.parse("shizavisuals:icons/hud/island/music/pause.png");
    private static final Identifier NEXT = Identifier.parse("shizavisuals:icons/hud/island/music/next.png");

    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static volatile LyricsData lyrics = LyricsData.EMPTY;
    private static volatile String lyricsTrackKey = "";
    private static volatile boolean pollerStarted;
    private static volatile long trackStartWall;
    private static java.util.concurrent.ExecutorService lyricsExecutor;
    private static java.util.concurrent.ExecutorService artExecutor;
    private static volatile Identifier currentArtTexture;
    // От какой обложки текущий Identifier: натив отдаёт PNG байтами, ключом
    // служит hex CRC32 содержимого — перезапись того же трека новым кадром
    // (или смена трека) всегда даёт новый ключ, залипаний нет.
    private static volatile String loadedArtKey = "";
    private static volatile long lastArtLoadAttempt;
    private static final java.util.concurrent.atomic.AtomicBoolean artLoadPending =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // Millisecond-precision playback clock: anchored to the last reported
    // position and interpolated on the wall clock between reports. The key
    // property is that it SNAPS to the reported position on every report, so a
    // seek (backward or forward) is reflected immediately instead of the clock
    // "counting" from track start or freezing on a transient "paused" flag.
    // seekVersion is bumped only on a REAL seek — see updateSmoothClock.
    private static long smoothAnchorMs;
    private static long smoothAnchorWall;
    private static long smoothLastReportedPos = -1L;
    private static int seekVersion = 0;

    private String tickerPrevText;

    private final IslandAnimation[] waveAnims = new IslandAnimation[4];
    private final IslandAnimation pausingAnim = new IslandAnimation(300L, 0.0F, IslandEasing.BAKEK_SIZE);
    private final IslandAnimation hoverPrevious = new IslandAnimation(300L, 0.0F, IslandEasing.FIGMA_EASE_IN_OUT);
    private final IslandAnimation hoverPause = new IslandAnimation(300L, 0.0F, IslandEasing.FIGMA_EASE_IN_OUT);
    private final IslandAnimation hoverNext = new IslandAnimation(300L, 0.0F, IslandEasing.FIGMA_EASE_IN_OUT);
    private final IslandAnimation tickerSwap = new IslandAnimation(450L, 1.0F, IslandEasing.FIGMA_EASE_IN_OUT);
    private final IslandAnimation tickerScroll = new IslandAnimation(350L, 0.0F, IslandEasing.FIGMA_EASE_IN_OUT);
    private final IslandAnimation lyricsScroll = new IslandAnimation(600L, 0.0F, IslandEasing.FIGMA_EASE_IN_OUT);
    private float tickerScrollTarget;
    private float tickerPrevScroll;
    private int tickerLineIndex = -1;
    private String tickerTrackKey = "";
    // Дебаунс смены строки лирики (мс): позиция между нативными репортами
    // интерполируется и дрожит у границы строк.
    private static final long LYRIC_SWITCH_DEBOUNCE_MS = 250L;
    // Холд караоке-режима после конца строки (мс): гасит мигание
    // "строка <-> название" на границе lineSingEndMs.
    private static final long KARAOKE_HOLD_MS = 400L;
    private int tickerPendingIndex = -2;
    private long tickerPendingSince;
    private String debounceTrackKey = "";
    private long karaokeHoldUntil;

    public MusicStatus(DynamicIsland island) {
        super(island, "music");
        for (int i = 0; i < this.waveAnims.length; i++) {
            this.waveAnims[i] = new IslandAnimation(400L, 0.0F, IslandEasing.LINEAR);
        }
        startPoller();
    }

    private static void startPoller() {
        if (pollerStarted) {
            return;
        }
        synchronized (MusicStatus.class) {
            if (pollerStarted) {
                return;
            }
            pollerStarted = true;
            initExecutors();
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
            // Нативный захват как в rockstar: WinRT-сессии через MediaPlayerInfo.dll,
            // без powershell-процесса, JSON и файлов-команд.
            SmtcTracker.start();
            Thread reader = new Thread(() -> {
                while (pollerStarted) {
                    try {
                        ingest(SmtcTracker.current());
                        Thread.sleep(250L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (Throwable ignored) {
                    }
                }
            }, "ShizaVisuals-MediaTracker");
            reader.setDaemon(true);
            reader.start();
        }
    }

    private static void initExecutors() {
        lyricsExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ShizaVisuals-Lyrics");
            t.setDaemon(true);
            return t;
        });
        artExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ShizaVisuals-AlbumArt");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Один тик нативного трекера: выбор сессии, grace-период и перевод
     * секунд в миллисекунды уже сделаны в {@link SmtcTracker} — здесь только
     * снапшот, обложка, часы и триггер текстов. Выбор сессии (браузерный
     * приоритет, игнор telegram) тоже живёт в трекере.
     */
    private static void ingest(SmtcTracker.Track track) {
        try {
            if (track == null || track.title() == null || track.title().isEmpty()) {
                snapshot = Snapshot.EMPTY;
                return;
            }
            String title = sanitize(track.title());
            if (title.isEmpty()) {
                snapshot = Snapshot.EMPTY;
                return;
            }
            String artist = sanitize(track.artist());
            long posMs = Math.max(0L, track.posMs());
            long durMs = Math.max(0L, track.durMs());
            boolean playing = track.playing();
            String artKey = artKey(track.art());

            snapshot = new Snapshot(
                    track.owner(),
                    title,
                    artist,
                    playing,
                    posMs,
                    durMs,
                    System.currentTimeMillis(),
                    artKey
            );
            loadArtTexture(track.art(), artKey);

            String trackKey = title + "|" + artist;
            boolean trackChanged = !trackKey.equals(lyricsTrackKey);
            updateSmoothClock(posMs, playing, trackChanged);
            if (trackChanged) {
                lyricsTrackKey = trackKey;
                trackStartWall = System.currentTimeMillis();
                lyrics = LyricsData.EMPTY;
                java.util.concurrent.ExecutorService executor = lyricsExecutor;
                if (executor != null && !executor.isShutdown()) {
                    final String fetchKey = trackKey;
                    final String fetchTitle = title;
                    final String fetchArtist = artist;
                    final long fetchDuration = durMs > 0L ? Math.round(durMs / 1000.0D) : 0L;
                    executor.execute(() -> {
                        LyricsData fetched = fetchLyrics(fetchKey, fetchTitle, fetchArtist, fetchDuration);
                        // Пока качался текст, трек мог смениться — чужой результат
                        // выкидываем, иначе караоке залипает на чужих таймингах.
                        if (fetchKey.equals(lyricsTrackKey)) {
                            lyrics = fetched;
                        }
                    });
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void updateSmoothClock(long reportedPositionMs, boolean playing, boolean trackChanged) {
        long now = System.currentTimeMillis();
        long reportedMs = reportedPositionMs;
        if (reportedMs <= 0L && playing && !trackChanged && trackStartWall > 0) {
            // Some players never report a timeline position — interpolate from the
            // wall-clock time the track was first seen instead.
            reportedMs = Math.max(0L, now - trackStartWall);
        }
        // Between reports we interpolate on the wall clock; on each report we
        // compare the freshly reported position with where the clock "should" be.
        //   err = reportedMs - predicted
        // A large err means a real seek (the position jumped far away) -> snap
        // and bump seekVersion so lyric surfaces resync. A small err is just
        // quantization/rounding jitter (e.g. 79s<->80s) -> we gently nudge the
        // anchor toward the report instead of snapping, so the cursor never
        // oscillates or freezes. We deliberately never freeze on a "paused" flag:
        // SMTC врёт про паузу во время сика/скраба, заморозка рвёт тексты.
        long predicted = smoothAnchorMs + (now - smoothAnchorWall);
        long err = reportedMs - predicted;
        if (trackChanged) {
            seekVersion++;
            smoothAnchorMs = reportedMs;
        } else if (Math.abs(err) > 2000L) {
            // Real seek (or track restart): snap and resync lyrics.
            seekVersion++;
            smoothAnchorMs = reportedMs;
        } else {
            // Normal playback: pull the anchor a quarter of the way toward the
            // report so jitter averages out without visible jumps.
            smoothAnchorMs = predicted + err * 25L / 100L;
        }
        smoothAnchorWall = now;
        smoothLastReportedPos = reportedPositionMs;
        if (trackChanged) {
            trackStartWall = now - (reportedMs > 0L ? reportedMs : 0L);
        }
    }

    private static long smoothPositionMs() {
        long position = smoothAnchorMs + (System.currentTimeMillis() - smoothAnchorWall);
        return Math.max(0L, position);
    }

    /** Bumped on every track change and on every detected seek. */
    public static int seekVersion() {
        return seekVersion;
    }

    /**
     * When a lyric line actually stops being sung: the earlier of the next
     * timestamp and an estimate based on the line length, so long instrumental
     * gaps don't leave the karaoke cursor stuck on a finished line.
     */
    private static long lineSingEndMs(LyricsData lyr, int index) {
        long start = lyr.timesMs()[index];
        long next = index + 1 < lyr.timesMs().length ? lyr.timesMs()[index + 1] : start + 5000L;
        long byText = 600L + 65L * lyr.lines()[index].length();
        return Math.min(next, start + Math.max(1200L, byText));
    }

    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean space = false;
        for (char c : s.toCharArray()) {
            if (c >= 0x1F000 || (c >= 0x2190 && c <= 0x2BFF) || (c >= 0xFE00 && c <= 0xFE0F)
                    || c == 0x200D || c == 0x20E3 || (c < 32 && c != 32)) {
                continue;
            }
            if (c == 32 && space) {
                continue;
            }
            space = c == 32;
            sb.append(c);
        }
        return sb.toString().trim();
    }

    public static void shutdown() {
        pollerStarted = false;
        SmtcTracker.stop();
        if (lyricsExecutor != null) {
            lyricsExecutor.shutdownNow();
        }
        if (artExecutor != null) {
            artExecutor.shutdownNow();
        }
    }

    private static final java.util.Map<String, Identifier> artCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> artContentHash = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Deque<Identifier> artOrder = new java.util.concurrent.ConcurrentLinkedDeque<>();

    private static String artKey(byte[] art) {
        if (art == null || art.length == 0) {
            return "";
        }
        return Long.toHexString(hashBytes(art));
    }

    private static void loadArtTexture(byte[] artBytes, String artKey) {
        if (artBytes == null || artBytes.length == 0 || artKey.isEmpty()) {
            currentArtTexture = null;
            loadedArtKey = "";
            return;
        }
        java.util.concurrent.ExecutorService executor = artExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }
        long now = System.currentTimeMillis();
        // Трекер шлёт тик каждые ~250мс — не грузим текстуру чаще раза в 700мс
        // и не копим очередь загрузок.
        if (artKey.equals(loadedArtKey) && now - lastArtLoadAttempt < 700L) {
            return;
        }
        if (!artLoadPending.compareAndSet(false, true)) {
            return;
        }
        lastArtLoadAttempt = now;
        final byte[] bytes = artBytes.length > 2_000_000 ? null : artBytes;
        if (bytes == null) {
            artLoadPending.set(false);
            return;
        }
        executor.execute(() -> {
            try {
                // Та же картинка — просто перевыставляем текущую.
                if (artKey.equals(loadedArtKey) && currentArtTexture != null) {
                    return;
                }
                // Байты те же — текстуру не трогаем, иначе обложка мигала бы.
                long hash = hashBytes(bytes);
                Long known = artContentHash.get(artKey);
                Identifier cached = artCache.get(artKey);
                if (known != null && known == hash && cached != null) {
                    loadedArtKey = artKey;
                    currentArtTexture = cached;
                    return;
                }
                com.mojang.blaze3d.platform.NativeImage image;
                try (java.io.InputStream in = new java.io.ByteArrayInputStream(bytes)) {
                    image = com.mojang.blaze3d.platform.NativeImage.read(in);
                }
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc == null) {
                    image.close();
                    return;
                }
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(
                        "shizavisuals:dynamic_album/" + Long.toHexString(hash));
                // Регистрируем строго в клиентском потоке — TextureManager не потокобезопасен.
                mc.execute(() -> {
                    try {
                        net.minecraft.client.renderer.texture.DynamicTexture tex =
                                new net.minecraft.client.renderer.texture.DynamicTexture(
                                        () -> "shizavisuals_album", image);
                        mc.getTextureManager().register(id, tex);
                    } catch (Throwable ignored) {
                        try {
                            image.close();
                        } catch (Throwable ignored2) {
                        }
                        return;
                    }
                    artCache.put(artKey, id);
                    artContentHash.put(artKey, hash);
                    artOrder.addLast(id);
                    // Не копим GPU-память: держим последние ~8 обложек.
                    while (artOrder.size() > 8) {
                        Identifier old = artOrder.pollFirst();
                        if (old != null && !old.equals(id) && !artCache.containsValue(old)) {
                            try {
                                mc.getTextureManager().release(old);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    // Чистим записи кеша, вытесненные из очереди.
                    while (artCache.size() > 8) {
                        String eldest = null;
                        for (String k : artCache.keySet()) {
                            eldest = k;
                            break;
                        }
                        if (eldest == null || eldest.equals(artKey)) {
                            break;
                        }
                        Identifier removed = artCache.remove(eldest);
                        artContentHash.remove(eldest);
                        // Не выселяем текстуру, которая прямо сейчас на экране.
                        if (removed != null && !removed.equals(id) && !removed.equals(currentArtTexture)) {
                            try {
                                mc.getTextureManager().release(removed);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    loadedArtKey = artKey;
                    currentArtTexture = id;
                });
            } catch (Throwable ignored) {
            } finally {
                artLoadPending.set(false);
            }
        });
    }

    private static long hashBytes(byte[] bytes) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    private static final long DURATION_TOLERANCE_SECONDS = 7L;

    private static LyricsData fetchLyrics(String trackKey, String title, String artist, long durationSec) {
        String searchArtist = artist == null ? "" : artist.trim();
        String searchTitle = title == null ? "" : title.trim();
        if (searchTitle.isEmpty()) {
            return LyricsData.EMPTY;
        }
        if (searchArtist.isEmpty()) {
            // Radio/YouTube titles often arrive as "Artist - Title".
            String[] split = searchTitle.split("\\s+[-—–]\\s+", 2);
            if (split.length == 2 && !split[0].isBlank() && !split[1].isBlank()) {
                searchArtist = split[0].trim();
                searchTitle = split[1].trim();
            }
        }
        String cleanArtist = normalizeArtist(searchArtist);
        String cleanTitle = normalizeTitle(searchTitle);
        final String rawArtist = searchArtist;
        final String rawTitle = searchTitle;

        List<java.util.function.Supplier<LyricsData>> sources = new ArrayList<>();
        if (!rawArtist.isBlank()) {
            sources.add(() -> fetchGet(trackKey, rawArtist, rawTitle, durationSec));
            if (!rawArtist.equals(cleanArtist) || !rawTitle.equals(cleanTitle)) {
                sources.add(() -> fetchGet(trackKey, cleanArtist, cleanTitle, durationSec));
            }
            sources.add(() -> fetchSearch(trackKey, cleanArtist, cleanTitle, durationSec));
        }
        sources.add(() -> fetchSearch(trackKey, "", cleanTitle, durationSec));
        for (java.util.function.Supplier<LyricsData> source : sources) {
            LyricsData data = source.get();
            if (data != LyricsData.EMPTY) {
                return data;
            }
        }
        return LyricsData.EMPTY;
    }

    static String normalizeArtist(String artist) {
        if (artist == null || artist.isBlank()) {
            return "";
        }
        String result = artist.split("[,;]|\\s+(?i:feat\\.?|ft\\.?)\\s+")[0].trim();
        return result.isBlank() ? artist.trim() : result;
    }

    static String normalizeTitle(String title) {
        String result = title == null ? "" : title
                .replaceAll("(?i)\\s*[(\\[][^)\\]]*(feat\\.?|ft\\.?|remaster|remix|version|edit|live|prod\\.?|bonus)[^)\\]]*[)\\]]", " ")
                .replaceAll("(?i)\\s+(feat\\.?|ft\\.?)\\s+.*$", " ")
                .replaceAll("(?i)\\s*-\\s*(remaster(ed)?|single version|radio edit|bonus track|live|remix)\\b.*$", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return result.isBlank() ? title.trim() : result;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String httpGet(String url) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "ShizaVisuals/1.0 (Minecraft client)");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) {
                    body.append(l);
                }
            }
            return body.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static LyricsData fetchGet(String trackKey, String artist, String title, long durationSec) {
        StringBuilder url = new StringBuilder("https://lrclib.net/api/get?track_name=")
                .append(urlEncode(title))
                .append("&artist_name=").append(urlEncode(artist));
        if (durationSec > 0L) {
            url.append("&duration=").append(durationSec);
        }
        String body = httpGet(url.toString());
        if (body == null) {
            return LyricsData.EMPTY;
        }
        JsonElement element = JsonParser.parseString(body);
        return element.isJsonObject() ? parseSyncedEntry(trackKey, element.getAsJsonObject()) : LyricsData.EMPTY;
    }

    private static LyricsData fetchSearch(String trackKey, String artist, String title, long durationSec) {
        String url = artist.isBlank()
                ? "https://lrclib.net/api/search?track_name=" + urlEncode(title)
                : "https://lrclib.net/api/search?artist_name=" + urlEncode(artist)
                        + "&track_name=" + urlEncode(title);
        String body = httpGet(url);
        if (body == null) {
            return LyricsData.EMPTY;
        }
        JsonElement element = JsonParser.parseString(body);
        if (!element.isJsonArray()) {
            return LyricsData.EMPTY;
        }
        // Score every candidate by artist/title similarity (plus duration
        // bonus) and never return an unrelated song: showing nothing is
        // better than random foreign lyrics.
        LyricsData best = null;
        double bestScore = 0.0D;
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject entry = item.getAsJsonObject();
            LyricsData data = parseSyncedEntry(trackKey, entry);
            if (data == LyricsData.EMPTY) {
                continue;
            }
            double score = matchScore(artist, title, entry, durationSec);
            if (score > bestScore) {
                bestScore = score;
                best = data;
            }
        }
        return best != null && bestScore >= 0.45D ? best : LyricsData.EMPTY;
    }

    private static double matchScore(String artist, String title, JsonObject entry, long durationSec) {
        String entryArtist = entry.has("artistName") && !entry.get("artistName").isJsonNull()
                ? entry.get("artistName").getAsString() : "";
        String entryTitle = entry.has("trackName") && !entry.get("trackName").isJsonNull()
                ? entry.get("trackName").getAsString() : "";
        double titleScore = Math.max(textScore(title, entryTitle),
                textScore(transliterate(title), entryTitle));
        double score;
        if (artist == null || artist.isBlank()) {
            score = titleScore;
        } else {
            double artistScore = Math.max(textScore(artist, entryArtist),
                    textScore(transliterate(artist), entryArtist));
            score = titleScore * 0.65D + artistScore * 0.35D;
        }
        if (durationSec > 0L && entry.has("duration") && !entry.get("duration").isJsonNull()) {
            long resultDuration = Math.round(entry.get("duration").getAsDouble());
            if (resultDuration > 0L && Math.abs(resultDuration - durationSec) <= DURATION_TOLERANCE_SECONDS) {
                score += 0.25D;
            }
        }
        return score;
    }

    private static double textScore(String query, String candidate) {
        String q = normalizeText(query);
        String c = normalizeText(candidate);
        if (q.isEmpty() || c.isEmpty()) {
            return 0.0D;
        }
        if (q.equals(c)) {
            return 1.0D;
        }
        if (c.contains(q) || q.contains(c)) {
            return 0.8D;
        }
        java.util.Set<String> qs = new java.util.HashSet<>(java.util.Arrays.asList(q.split(" ")));
        java.util.Set<String> cs = new java.util.HashSet<>(java.util.Arrays.asList(c.split(" ")));
        int inter = 0;
        for (String token : qs) {
            if (cs.contains(token)) {
                inter++;
            }
        }
        int union = qs.size() + cs.size() - inter;
        return union <= 0 ? 0.0D : (double) inter / union;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String transliterate(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() * 2);
        for (char ch : value.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            out.append(switch (ch) {
                case 'а' -> "a";
                case 'б' -> "b";
                case 'в' -> "v";
                case 'г' -> "g";
                case 'д' -> "d";
                case 'е', 'э' -> "e";
                case 'ё' -> "yo";
                case 'ж' -> "zh";
                case 'з' -> "z";
                case 'и' -> "i";
                case 'й' -> "y";
                case 'к' -> "k";
                case 'л' -> "l";
                case 'м' -> "m";
                case 'н' -> "n";
                case 'о' -> "o";
                case 'п' -> "p";
                case 'р' -> "r";
                case 'с' -> "s";
                case 'т' -> "t";
                case 'у' -> "u";
                case 'ф' -> "f";
                case 'х' -> "kh";
                case 'ц' -> "ts";
                case 'ч' -> "ch";
                case 'ш' -> "sh";
                case 'щ' -> "shch";
                case 'ъ', 'ь' -> "";
                case 'ы' -> "y";
                case 'ю' -> "yu";
                case 'я' -> "ya";
                default -> String.valueOf(ch);
            });
        }
        return out.toString();
    }

    private static LyricsData parseSyncedEntry(String trackKey, JsonObject o) {
        if (!o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) {
            return LyricsData.EMPTY;
        }
        String synced = o.get("syncedLyrics").getAsString();
        if (synced == null || synced.isBlank()) {
            return LyricsData.EMPTY;
        }
        List<Long> times = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        var pattern = java.util.regex.Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\](.*)");
        for (String raw : synced.split("\n")) {
            var m = pattern.matcher(raw.trim());
            if (m.matches()) {
                times.add(Long.parseLong(m.group(1)) * 60_000L
                        + Math.round(Double.parseDouble(m.group(2)) * 1000.0D));
                lines.add(m.group(3).trim());
            }
        }
        if (times.isEmpty()) {
            return LyricsData.EMPTY;
        }
        // LRC lines are not guaranteed to be time-ordered (and may carry several
        // timestamps) — the line lookup assumes ascending order.
        Integer[] order = new Integer[times.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Long.compare(times.get(a), times.get(b)));
        long[] sortedTimes = new long[times.size()];
        String[] sortedLines = new String[times.size()];
        for (int i = 0; i < order.length; i++) {
            sortedTimes[i] = times.get(order[i]);
            sortedLines[i] = lines.get(order[i]);
        }
        return new LyricsData(trackKey, sortedTimes, sortedLines);
    }

    private record LyricsData(String trackKey, long[] timesMs, String[] lines) {
        static final LyricsData EMPTY = new LyricsData("", new long[0], new String[0]);

        boolean matches(String key) {
            return this.trackKey.equals(key);
        }

        String lineAt(long posMs) {
            String current = null;
            for (int i = 0; i < this.timesMs.length; i++) {
                if (this.timesMs[i] <= posMs) {
                    current = this.lines[i];
                } else {
                    break;
                }
            }
            return current;
        }

        int indexAt(long posMs) {
            int index = -1;
            for (int i = 0; i < this.timesMs.length; i++) {
                if (this.timesMs[i] <= posMs) {
                    index = i;
                } else {
                    break;
                }
            }
            return index;
        }
    }

    /**
     * Транспорт напрямую через WinRT-сессию ({@link SmtcTracker#control}),
     * без хелпера и файлов: системный OSD громкости не всплывает.
     */
    private static void sendControl(int action) {
        SmtcTracker.control(action);
    }

    @Override
    public void draw(float alpha) {
        Snapshot snap = snapshot;
        if (snap.title == null || snap.title.isEmpty()) {
            return;
        }
        float s = this.island.scale();
        float x = this.island.x();
        float y = this.island.y();
        int textColor = Theme.Colors.TEXT_TEXT;
        boolean extended = this.island.isExtended();
        float extending = this.island.extendingValue();
        float anim = this.animation.getValue();

        long posMs = smoothPositionMs();
        // The progress bar is clamped to duration, but the numeric time label is
        // NOT — a mis-reported (too-short) duration would otherwise stick the
        // counter at e.g. 29s forever while the track keeps playing.
        long rawPosMs = Math.max(0L, posMs);
        float displayPosition = rawPosMs / 1000.0F;

        LyricsData lyr = lyrics;
        boolean hasLyrics = lyr.matches(snap.title + "|" + snap.artist) && lyr.lines().length > 0;
        int rawIndex = hasLyrics ? lyr.indexAt(posMs) : -1;
        long nowMs = System.currentTimeMillis();
        // Дебаунс смены строки: позиция между нативными репортами
        // интерполируется и дрожит у границы строк — без выдержки тикер
        // перескакивал бы туда-сюда и перезапускал swap-анимацию (мигание).
        // Прыжок дальше чем на 2 строки — реальный сик, переключаем сразу.
        if (hasLyrics && !lyr.trackKey().equals(this.debounceTrackKey)) {
            this.debounceTrackKey = lyr.trackKey();
            this.tickerPendingIndex = -2;
        }
        if (rawIndex != this.tickerLineIndex) {
            boolean seekJump = this.tickerLineIndex >= 0 && Math.abs(rawIndex - this.tickerLineIndex) > 2;
            // Текстов нет (смена трека, лирика ещё летит) — гасим сразу,
            // держать чужую строку под новый трек нельзя.
            if (seekJump || !hasLyrics) {
                this.tickerPendingIndex = -2;
            } else {
                if (this.tickerPendingIndex != rawIndex) {
                    this.tickerPendingIndex = rawIndex;
                    this.tickerPendingSince = nowMs;
                }
                if (nowMs - this.tickerPendingSince < LYRIC_SWITCH_DEBOUNCE_MS) {
                    rawIndex = this.tickerLineIndex;
                } else {
                    this.tickerPendingIndex = -2;
                }
            }
        } else {
            this.tickerPendingIndex = -2;
        }
        int lyricIndex = rawIndex;
        String lyricLine = lyricIndex >= 0 ? lyr.lines()[lyricIndex] : null;
        boolean lineStillActive = lyricIndex >= 0 && posMs < Math.min(
                lyricIndex + 1 < lyr.timesMs().length ? lyr.timesMs()[lyricIndex + 1] : Long.MAX_VALUE,
                lineSingEndMs(lyr, lyricIndex) + 2500L);
        // Холд караоке: после конца строки держим её ещё чуть-чуть,
        // иначе дрожание позиции на границе дёргает "строка <-> название".
        if (lineStillActive) {
            this.karaokeHoldUntil = nowMs + KARAOKE_HOLD_MS;
        }
        // One font family for the title, the ticker and every lyric row —
        // choosing a font per line mixes typefaces when lines mix languages.
        MsdfFont titleFont = UiFonts.getFontForText(snap.title, UiFontStyle.MEDIUM.weight());
        float size7 = 7.0F * s;
        float ls7 = size7 * UiFontStyle.MEDIUM.letterSpacingEm();
        // Show the lyric from the playback position, independent of the raw
        // "playing" flag — SMTC reports paused across a seek/scrub, and gating
        // on it made the island stick on one line (or drop the text entirely)
        // every time the user rewound. When truly paused the position simply
        // holds the current line, which is correct karaoke behaviour.
        boolean karaoke = !extended && hasLyrics
                && (lineStillActive || nowMs < this.karaokeHoldUntil)
                && lyricLine != null && !lyricLine.isBlank();
        // The collapsed island never tries to fit the whole lyric line: long lines
        // live in a fixed-width window (like the reference client) and scroll as
        // the karaoke cursor advances. +38 keeps the cover art and wave bars clear.
        float defaultWidth = (32.0F + titleWidth(karaoke ? lyricLine : snap.title, s) / s);
        float collapsedWidth = karaoke
                ? Math.min(KARAOKE_WIDTH, titleFont.measureWidth(lyricLine, size7, ls7) / s + 38.0F)
                : Math.min(defaultWidth, MAX_COLLAPSED_WIDTH);
        this.size.width = extended ? EXP_WIDTH : collapsedWidth;
        this.size.height = extended ? (hasLyrics ? 125.0F : EXP_HEIGHT) : 15.0F;
        float width = this.size.width;
        float height = this.size.height;

        float imageMargin = 4.0F + 6.0F * extending;
        float imageSize = 7.0F + 19.0F * extending;
        float imageY = y + (extended ? imageMargin * s : (island.size().height * s - imageSize * s) / 2.0F);
        Identifier artTex = currentArtTexture;
        // Динамическая обложка зарегистрирована в TextureManager в рантайме —
        // её нельзя отдавать статическому GuiTexture-пайплайну (он ищет файл
        // в ресурс-паках и крашит кадр FileNotFoundException), только managed.
        var artBuilder = Render2DUtil.texture(
                        x + (imageMargin - 10.0F + 10.0F * anim) * s,
                        imageY,
                        imageSize * s, imageSize * s,
                        artTex != null ? artTex : NO_IMAGE)
                .radius((1.0F + 5.0F * extending) * s);
        if (artTex != null) {
            artBuilder.managed();
        }
        artBuilder.draw();

        float textX = x + (-5.0F + 20.0F * anim + 29.0F * extending) * s;
        // The title slides down to its own row when the island expands; in the
        // collapsed state it stays centered against the cover art.
        float titleY = titleFont.centeredTextY(y + (7.5F + 12.0F * extending) * s, size7);
        // Reserve the right end of the collapsed island for the wave bars so the
        // text never runs into them.
        float titleMax = extended ? EXP_WIDTH - 45.0F : Math.max(20.0F, collapsedWidth - 34.0F);

        if (karaoke && extending < 1.0F) {
            if (!lyr.trackKey().equals(this.tickerTrackKey)) {
                this.tickerTrackKey = lyr.trackKey();
                this.tickerLineIndex = lyricIndex;
                this.tickerPrevText = null;
                this.tickerScroll.setValue(0.0F);
                this.tickerScrollTarget = 0.0F;
                this.tickerSwap.setValue(1.0F);
            }
            if (lyricIndex != this.tickerLineIndex) {
                // A jump of more than one line means the user seeked — skip the
                // slide transition, otherwise the old line scrolls in from nowhere.
                boolean seekJump = this.tickerLineIndex >= 0
                        && Math.abs(lyricIndex - this.tickerLineIndex) > 2;
                String prevText = this.tickerLineIndex >= 0 && this.tickerLineIndex < lyr.lines().length
                        ? lyr.lines()[this.tickerLineIndex] : null;
                this.tickerPrevText = seekJump || prevText == null || prevText.isBlank() ? null : prevText;
                this.tickerPrevScroll = this.tickerScroll.getValue();
                this.tickerLineIndex = lyricIndex;
                this.tickerScroll.setValue(0.0F);
                this.tickerScrollTarget = 0.0F;
                this.tickerSwap.setValue(seekJump ? 1.0F : 0.0F);
            }
            float swap = this.tickerSwap.update(1.0F);
            String line = lyr.lines()[lyricIndex];
            String prevLine = this.tickerPrevText;
            // One font for both lines so the swap transition never mixes typefaces.
            MsdfFont lineFont = titleFont;
            float ls = size7 * UiFontStyle.MEDIUM.letterSpacingEm();
            long startMs = lyr.timesMs()[lyricIndex];
            long endMs = lineSingEndMs(lyr, lyricIndex);
            float progress = Math.clamp((posMs - startMs)
                    / (float) Math.max(1L, endMs - startMs), 0.0F, 1.0F);
            float lineWidth = lineFont.measureWidth(line, size7, ls);
            float cursorPx = Math.min(progress * lineWidth, lineWidth);
            float[][] words = IslandKaraoke.wordBounds(lineFont, line, size7, ls);
            int curWord = -1;
            for (int w = 0; w < words.length && words[w][0] <= cursorPx; w++) {
                curWord = w;
            }
            float window = collapsedWidth * s - 30.0F * s;
            float maxScroll = Math.max(0.0F, lineWidth - window + 6.0F * s);
            float scrollTarget = 0.0F;
            if (curWord >= 0) {
                float wordCenter = (words[curWord][0] + words[curWord][1]) * 0.5F;
                scrollTarget = Math.clamp(wordCenter - window * 0.5F, 0.0F, maxScroll);
            }
            // Раньше tickerScrollTarget никогда не обновлялся (всегда 0):
            // длинные строки не скроллились за курсором и выглядели залипшими.
            if (Math.abs(scrollTarget - this.tickerScrollTarget) > 0.5F) {
                this.tickerScrollTarget = scrollTarget;
            }
            this.tickerScroll.setDuration(maxScroll > 0.0F ? 250L : 600L);
            this.tickerScroll.update(this.tickerScrollTarget);
            float scroll = this.tickerScroll.getValue();
            float textY = lineFont.centeredTextY(y + 7.5F * s, size7);
            int tickerColor = ColorUtil.multiplyAlpha(textColor, (1.0F - extending) * alpha);

            Render2DUtil.pushScissor(textX - 10.0F * s, y, window + 20.0F * s, height * s);
            IslandKaraoke.draw(lineFont, line, size7, ls, textX, textY, scroll,
                    textX, textX + window, 7.0F * s,
                    cursorPx, swap, true, tickerColor, 0.4F);
            if (swap < 1.0F && prevLine != null) {
                IslandKaraoke.draw(lineFont, prevLine, size7, ls, textX, textY,
                        this.tickerPrevScroll, textX, textX + window, 7.0F * s,
                        -1.0F, swap, false, tickerColor, 0.4F);
            }
            Render2DUtil.popScissor();
        }

        if (!karaoke || extending > 0.0F) {
            drawFadeoutText(snap.title, textX, titleY, size7, textColor,
                    alpha * (karaoke ? extending : 1.0F), titleMax * s);
        }

        if (extending != 0.0F) {
            String artist = snap.artist == null ? "" : snap.artist;
            drawFadeoutText(artist, x + (20.0F + 24.0F * extending) * s,
                    y + (5.0F + 19.0F * extending) * s, 7.0F * s, textColor, 0.7F * alpha * extending,
                    (extended ? EXP_WIDTH - 30.0F : MAX_COLLAPSED_WIDTH + 5.0F) * s,
                    UiFontStyle.REGULAR);

            MsdfFont small = UiFonts.googleSansFlex(UiFontStyle.REGULAR.weight());
            float size5 = 5.0F * s;
            Render2DUtil.text(x + (11.0F * extending) * s, y + 43.0F * extending * s, size5, formatTime((long) displayPosition))
                    .font(small)
                    .color(ColorUtil.multiplyAlpha(textColor, alpha))
                    .draw();
            String durationText = formatTime(snap.duration / 1000L);
            Render2DUtil.text(x + (EXP_WIDTH - (9.5F + small.measureWidth(durationText, size5, spacing(size5)) / s * extending)) * s,
                    y + 43.0F * extending * s, size5, durationText)
                    .font(small)
                    .color(ColorUtil.multiplyAlpha(textColor, alpha))
                    .draw();

            float barWidth = 116.0F * s;
            float barX = MinecraftContext.mc.getWindow().getGuiScaledWidth() / 2.0F - barWidth / 2.0F;
            // The bar sits in the same row as the time labels, right under the
            // title/artist — with lyrics the island grows downward, not the bar.
            float barY = y + (height - (hasLyrics ? 45.0F : 0.0F) - 36.5F * extending) * s;
            Render2DUtil.rect(barX, barY, barWidth, 3.0F * s)
                    .radius(0.5F * s)
                    .color(ColorUtil.multiplyAlpha(textColor, 0.25F * alpha))
                    .draw();
            float progressWidth = snap.duration > 0
                    ? barWidth * Math.min(1.0F, posMs / (float) snap.duration)
                    : 0.0F;
            Render2DUtil.rect(barX, barY, progressWidth, 3.0F * s)
                    .radius(0.5F * s)
                    .color(ColorUtil.multiplyAlpha(textColor, 150.0F / 255.0F * alpha))
                    .draw();

            this.pausingAnim.setDuration(600L);
            this.pausingAnim.update(snap.playing ? 1.0F : 0.0F);
            if (extending > 0.7F) {
                drawControls(s, y, height, extending, textColor, alpha);
            }
            drawOwnerBadge(snap.owner, x, y, s, alpha, height);

            if (extending > 0.7F && hasLyrics && lyricIndex >= 0) {
                drawLyricsList(lyr, snap.title, lyricIndex, posMs, x, y, height, extending, s, alpha);
            }
        }
        drawWaveBars(snap, s, x, y, collapsedWidth, extending, anim, alpha, displayPosition);
    }

    private void drawLyricsList(LyricsData lyr, String fontKeyText, int currentIndex, long posMs,
                                float x, float y, float height, float extending, float s, float alpha) {
        String[] lines = lyr.lines();
        int visible = 4;
        int maxOffset = Math.max(0, lines.length - visible);
        float autoTarget = Math.min(Math.max(0, currentIndex - 1), maxOffset);
        this.lyricsScroll.update(autoTarget);
        float scroll = this.lyricsScroll.getValue();

        // Below the time/progress row; the island grows downward for lyrics.
        float listTop = y + 54.0F * s;
        float lineH = 8.0F * s;
        float rowX = x + 16.0F * s;
        float rowMaxWidth = (EXP_WIDTH - 26.0F) * s;

        Render2DUtil.pushScissor(x, y + 50.0F * s, EXP_WIDTH * s, Math.max(0.0F, height - 58.0F * s));
        int first = (int) Math.floor(scroll);
        for (int i = Math.max(0, first); i <= Math.min(lines.length - 1, first + visible); i++) {
            float rowPos = i - scroll;
            if (rowPos <= -1.0F || rowPos >= visible) {
                continue;
            }
            // Rows at the top/bottom of the window fade out instead of vanishing.
            float edge = Math.clamp(Math.min(rowPos + 1.0F, visible - rowPos), 0.0F, 1.0F);
            float slotY = listTop + rowPos * lineH;
            float rowAlpha = alpha * extending * edge;
            String lineText = lines[i];
            boolean current = i == currentIndex;
            if (lineText == null || lineText.isBlank()) {
                if (current) {
                    drawFadeoutText("• • •", rowX, slotY + (lineH - 6.0F * s) * 0.5F, 6.0F * s,
                            ColorUtil.rgba(150, 152, 160, 255), rowAlpha * 0.6F, rowMaxWidth, UiFontStyle.MEDIUM);
                }
                continue;
            }
            UiFontStyle style = current ? UiFontStyle.MEDIUM : UiFontStyle.REGULAR;
            float size = (current ? 7.0F : 6.0F) * s;
            float ls = size * style.letterSpacingEm();
            MsdfFont rowFont = UiFonts.getFontForText(fontKeyText, style.weight());
            // Center each row inside its slot so 6px REGULAR and 7px MEDIUM rows
            // share one visual baseline.
            float rowY = slotY + (lineH - rowFont.textHeight(size)) * 0.5F;
            if (current) {
                long startMs = lyr.timesMs()[i];
                long endMs = lineSingEndMs(lyr, i);
                float progress = Math.clamp((posMs - startMs)
                        / (float) Math.max(1L, endMs - startMs), 0.0F, 1.0F);
                float lineWidth = rowFont.measureWidth(lineText, size, ls);
                float cursorPx = Math.min(progress * lineWidth, lineWidth);
                IslandKaraoke.draw(rowFont, lineText, size, ls, rowX, rowY, 0.0F,
                        rowX, rowX + rowMaxWidth, 8.0F * s,
                        cursorPx, 1.0F, true,
                        ColorUtil.multiplyAlpha(ColorUtil.WHITE, rowAlpha), 0.45F);
            } else {
                float dim = i < currentIndex ? 0.6F : 0.5F;
                int color = ColorUtil.multiplyAlpha(
                        i < currentIndex ? ColorUtil.WHITE : ColorUtil.rgba(150, 152, 160, 255),
                        rowAlpha * dim);
                IslandKaraoke.draw(rowFont, lineText, size, ls, rowX, rowY, 0.0F,
                        rowX, rowX + rowMaxWidth, 8.0F * s,
                        -1.0F, 1.0F, true, color, 1.0F);
            }
        }
        Render2DUtil.popScissor();
    }
    private void drawControls(float s, float y, float height,
                              float extending, int textColor, float alpha) {
        float prevX = MinecraftContext.mc.getWindow().getGuiScaledWidth() / 2.0F - 40.0F * s;
        float pauseX = MinecraftContext.mc.getWindow().getGuiScaledWidth() / 2.0F - 8.0F * s;
        float nextX = MinecraftContext.mc.getWindow().getGuiScaledWidth() / 2.0F + 24.0F * s;
        float size = 16.0F * s;
        float controlY = y + (height - 25.0F * extending) * s;
        double mouseX = mouseX();
        double mouseY = mouseY();

        boolean hPrev = hovered(prevX, controlY, size, size, mouseX, mouseY);
        boolean hPause = hovered(pauseX, controlY, size, size, mouseX, mouseY);
        boolean hNext = hovered(nextX, controlY, size, size, mouseX, mouseY);

        this.hoverPrevious.update(hPrev);
        this.hoverPause.update(hPause);
        this.hoverNext.update(hNext);

        int iconBase = ColorUtil.multiplyAlpha(textColor, alpha);
        Render2DUtil.texture(prevX, controlY, size, size, PREVIOUS)
                .color(ColorUtil.multiplyAlpha(iconBase, 1.0F - 100.0F / 255.0F * this.hoverPrevious.getValue()))
                .draw();

        float anim = this.pausingAnim.getValue();
        float centerX = pauseX + size / 2.0F;
        float centerY = controlY + size / 2.0F;

        rotateAround(centerX, centerY, 90.0F * anim);
        scaleAround(centerX, centerY, 1.0F - anim);
        Render2DUtil.texture(pauseX, controlY, size, size, PLAY)
                .color(ColorUtil.multiplyAlpha(iconBase, Math.max(0.0F, 1.0F - anim) - 100.0F / 255.0F * this.hoverPause.getValue()))
                .draw();
        endMatrix();
        endMatrix();

        rotateAround(centerX, centerY, -90.0F + 90.0F * anim);
        scaleAround(centerX, centerY, anim);
        Render2DUtil.texture(pauseX, controlY, size, size, PAUSE)
                .color(ColorUtil.multiplyAlpha(iconBase, Math.max(0.0F, anim) - 100.0F / 255.0F * this.hoverPause.getValue()))
                .draw();
        endMatrix();
        endMatrix();

        Render2DUtil.texture(nextX, controlY, size, size, NEXT)
                .color(ColorUtil.multiplyAlpha(iconBase, 1.0F - 100.0F / 255.0F * this.hoverNext.getValue()))
                .draw();
    }

    private void drawOwnerBadge(String owner, float x, float y, float s, float alpha, float height) {
        String ownerLc = owner == null ? "" : owner.toLowerCase();
        Identifier ownerIcon = null;
        if (ownerLc.contains("yandex") || ownerLc.contains("яндекс")) {
            ownerIcon = Identifier.parse("shizavisuals:icons/hud/island/media/yandex_music.png");
        } else if (ownerLc.contains("soundcloud") || ownerLc.contains("cloud")) {
            ownerIcon = Identifier.parse("shizavisuals:icons/hud/island/media/soundcloud.png");
        } else if (ownerLc.contains("spotify")) {
            ownerIcon = Identifier.parse("shizavisuals:icons/hud/island/media/spotify.png");
        } else if (ownerLc.contains("youtube") || ownerLc.contains("chrome") || ownerLc.contains("firefox")
                || ownerLc.contains("msedge") || ownerLc.contains("edge")) {
            ownerIcon = Identifier.parse("shizavisuals:icons/hud/island/media/youtube.png");
        } else if (ownerLc.contains("telegram")) {
            ownerIcon = Identifier.parse("shizavisuals:icons/hud/island/media/telegram.png");
        }
        if (ownerIcon != null) {
            Render2DUtil.texture(x + (EXP_WIDTH - 22.0F) * s, y + (height - 21.0F) * s, 8.0F * s, 8.0F * s, ownerIcon)
                    .color(ColorUtil.multiplyAlpha(ColorUtil.WHITE, alpha))
                    .draw();
        }
    }

    private void drawWaveBars(Snapshot snap, float s, float x, float y, float collapsedWidth,
                              float extending, float anim, float alpha, float displayPosition) {
        long wall = System.currentTimeMillis();
        for (int i = 0; i < this.waveAnims.length; i++) {
            float size;
            if (snap.playing) {
                this.waveAnims[i].setDuration(90L);
                float phase = displayPosition * 2.4F + wall / 140.0F + (float) i * 0.8F;
                size = 2.0F + (float) Math.abs(Math.sin(phase)) * 7.5F;
            } else {
                this.waveAnims[i].setDuration(400L);
                size = 3.0F;
            }
            this.waveAnims[i].update(size);
            float waveX = x + (collapsedWidth + (EXP_WIDTH - 10.0F - collapsedWidth) * extending
                    - 2.0F - 10.0F * anim + (float) i * (2.0F + extending)) * s;
            float waveY = y + ((4.25F + (14.0F - 4.25F) * extending) + (7.0F - this.waveAnims[i].getValue()) / 2.0F) * s;
            Render2DUtil.rect(waveX, waveY, (1.0F + extending) * s, this.waveAnims[i].getValue() * s)
                    .radius(0.5F * s)
                    .color(ColorUtil.multiplyAlpha(Theme.getAccent(), alpha))
                    .draw();
        }
    }

    @Override
    public void click(float mouseX, float mouseY, int button) {
        if (button != 0) {
            return;
        }
        Snapshot snap = snapshot;
        if (snap.title == null || snap.title.isEmpty()) {
            return;
        }
        float s = this.island.scale();
        float y = this.island.y();
        float width = this.size.width * s;
        float height = this.size.height * s;
        float controlY = y + height - 9.0F * s - 16.0F * s;
        int action = 0;
        if (hovered(this.island.x() + width / 2.0F - 40.0F * s, controlY, 16.0F * s, 16.0F * s, mouseX, mouseY)) {
            action = 1;
        }
        if (hovered(this.island.x() + width / 2.0F - 8.0F * s, controlY, 16.0F * s, 16.0F * s, mouseX, mouseY)) {
            action = 2;
        }
        if (hovered(this.island.x() + width / 2.0F + 24.0F * s, controlY, 16.0F * s, 16.0F * s, mouseX, mouseY)) {
            action = 3;
        }
        sendControl(action);
    }

    @Override
    public boolean canShow() {
        Snapshot snap = snapshot;
        if (snap.title == null || snap.title.isEmpty()) {
            return false;
        }
        String owner = snap.owner;
        return owner == null || !owner.toLowerCase().contains("telegram");
    }
    private static float titleWidth(String title, float s) {
        if (title == null || title.isEmpty()) {
            return 0.0F;
        }
        MsdfFont font = UiFonts.getFontForText(title, UiFontStyle.MEDIUM.weight());
        float size = 7.0F * s;
        return font.measureWidth(title, size, spacingStatic(size));
    }

    private static void drawFadeoutText(String text, float x, float y, float size, int color,
                                        float alpha, float maxWidthPx) {
        drawFadeoutText(text, x, y, size, color, alpha, maxWidthPx, UiFontStyle.MEDIUM);
    }

    private static void drawFadeoutText(String text, float x, float y, float size, int color,
                                        float alpha, float maxWidthPx, UiFontStyle style) {
        if (text == null || text.isEmpty() || alpha <= 0.01F) {
            return;
        }
        MsdfFont font = UiFonts.getFontForText(text, style.weight());
        float ls = spacingStatic(size);
        String clipped = font.ellipsize(text, size, ls, maxWidthPx);
        Render2DUtil.text(x, y, size, clipped)
                .font(font)
                .style(style)
                .color(ColorUtil.multiplyAlpha(color, alpha))
                .draw();
    }

    protected static float spacingStatic(float size) {
        return size * UiFontStyle.MEDIUM.letterSpacingEm();
    }

    private static boolean hovered(float x, float y, float w, float h, double mx, double my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static double mouseX() {
        var mc = MinecraftContext.mc;
        return mc.mouseHandler.getScaledXPos(mc.getWindow());
    }

    private static double mouseY() {
        var mc = MinecraftContext.mc;
        return mc.mouseHandler.getScaledYPos(mc.getWindow());
    }

    private static void rotateAround(float x, float y, float degrees) {
        var pose = RenderContext.currentGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.rotate((float) Math.toRadians(degrees));
        pose.translate(-x, -y);
    }

    private static void scaleAround(float x, float y, float factor) {
        var pose = RenderContext.currentGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(factor, factor);
        pose.translate(-x, -y);
    }

    private static void endMatrix() {
        RenderContext.currentGuiGraphicsExtractor().pose().popMatrix();
    }

    public static String formatTime(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%d:%02d", minutes, seconds);
    }

    /** Current media snapshot for other features (lyrics, etc). */
    public record MediaTrack(String title, String artist, boolean playing) {
        static final MediaTrack EMPTY = new MediaTrack("", "", false);

        /** Stable per-track identity for change detection. */
        public String key() {
            return (this.title + "|" + this.artist).toLowerCase();
        }
    }

    public static MediaTrack currentTrack() {
        Snapshot snap = snapshot;
        if (snap.title == null || snap.title.isEmpty()) {
            return MediaTrack.EMPTY;
        }
        return new MediaTrack(snap.title, snap.artist, snap.playing);
    }

    /** Smooth playback position in milliseconds (drift-corrected). */
    public static long playbackPositionMs() {
        return smoothPositionMs();
    }

    /**
     * The lyrics currently bound to the playing track — the exact lines the
     * island renders (duration-validated, sorted). Other consumers (Kinetic
     * Lyrics) must read this instead of fetching their own copy so every
     * lyric surface shows identical text on identical timings.
     */
    public record SharedLyrics(String trackKey, long[] timesMs, String[] lines) {
    }

    public static SharedLyrics currentLyrics() {
        LyricsData lyr = lyrics;
        Snapshot snap = snapshot;
        if (snap.title == null || snap.title.isEmpty()
                || !lyr.matches(snap.title + "|" + snap.artist)
                || lyr.lines().length == 0) {
            return new SharedLyrics("", new long[0], new String[0]);
        }
        return new SharedLyrics(lyr.trackKey(), lyr.timesMs(), lyr.lines());
    }

    private static final class Snapshot {
        private static final Snapshot EMPTY = new Snapshot(null, "", "", false, 0L, 0L, 0L, "");

        private final String owner;
        private final String title;
        private final String artist;
        private final boolean playing;
        private final long position;
        private final long duration;
        private final long capturedAt;
        private final String art;

        private Snapshot(String owner, String title, String artist,
                         boolean playing, long position, long duration, long capturedAt, String art) {
            this.owner = owner;
            this.title = title;
            this.artist = artist;
            this.playing = playing;
            this.position = position;
            this.duration = duration;
            this.capturedAt = capturedAt;
            this.art = art;
        }
    }
}
