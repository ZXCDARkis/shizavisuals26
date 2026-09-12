package org.shizavisuals.hud.island;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Нативный захват музыки через WinRT (SMTC), тем же способом, что в rockstar:
 * JNI-библиотека {@code MediaPlayerInfo.dll} отдаёт живые сессии —
 * название, артист, обложку байтами, позицию/длительность и транспорт.
 *
 * <p>Никакого powershell-хелпера, JSON по stdout и файлов-команд:
 * демон-поток опрашивает сессии (100мс когда музыка есть, 1000мс когда тихо),
 * прошлый трек держится ещё 1.5с grace-периода, чтобы остров не мигал
 * на коротких провалах репортов. Позиция/длительность натива — в секундах,
 * наружу отдаём миллисекунды.</p>
 */
public final class SmtcTracker {
    private static final Logger LOG = LoggerFactory.getLogger("ShizaVisuals/SmtcTracker");
    private static final long GRACE_MS = 1500L;

    public record Track(String owner, String title, String artist,
                        boolean playing, long posMs, long durMs, byte[] art) {
        static final Track EMPTY = new Track("", "", "", false, 0L, 0L, null);
    }

    private static volatile IMediaSession session;
    private static volatile Track track = Track.EMPTY;
    private static volatile long lastSeen;
    private static volatile boolean started;
    private static volatile boolean nativeBroken;

    private SmtcTracker() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread poller = new Thread(() -> {
            while (started) {
                long tickStart = System.currentTimeMillis();
                poll();
                // 100мс пока сессия жива (позиция/лирика точнее), 1000мс в тишине.
                long interval = track != Track.EMPTY ? 100L : 1000L;
                long sleep = Math.max(interval, tickStart - System.currentTimeMillis() + interval);
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "ShizaVisuals-SMTC");
        poller.setDaemon(true);
        // Фоновый опрос не должен отъедать кадры.
        poller.setPriority(Thread.MIN_PRIORITY);
        poller.start();
    }

    public static void stop() {
        started = false;
        session = null;
        track = Track.EMPTY;
    }

    private static void poll() {
        if (nativeBroken) {
            return;
        }
        List<IMediaSession> sessions;
        try {
            sessions = MediaPlayerInfo.INSTANCE.getMediaSessions();
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
            // Нет DLL / не Windows — тихо деградируем в "музыки нет".
            nativeBroken = true;
            LOG.warn("Native SMTC unavailable, music island disabled: {}", e.toString());
            session = null;
            track = Track.EMPTY;
            return;
        } catch (Throwable t) {
            return;
        }
        IMediaSession best = pick(sessions);
        long now = System.currentTimeMillis();
        if (best != null) {
            lastSeen = now;
            session = best;
            track = toTrack(best);
        } else if (now - lastSeen >= GRACE_MS) {
            session = null;
            track = Track.EMPTY;
        }
        // Внутри grace-периода держим прошлый трек — остров не мигает.
    }

    private static IMediaSession pick(List<IMediaSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        // Первый проход: играющая браузерная сессия — у неё честная позиция
        // <video>, SMTC сики внутри браузера часто не видит.
        for (IMediaSession s : sessions) {
            String owner = s.getOwner();
            MediaInfo info = safeMedia(s);
            if (info != null && info.isPlaying() && owner != null
                    && owner.toLowerCase().startsWith("browser")
                    && !info.getTitle().isEmpty()) {
                return s;
            }
        }
        IMediaSession fallback = null;
        boolean fallbackPlaying = false;
        for (IMediaSession s : sessions) {
            String owner = s.getOwner();
            if (owner != null && owner.toLowerCase().contains("telegram")) {
                continue;
            }
            MediaInfo info = safeMedia(s);
            if (info == null || info.getTitle().isEmpty()) {
                continue;
            }
            boolean playing = info.isPlaying();
            if (fallback == null || (playing && !fallbackPlaying)) {
                fallback = s;
                fallbackPlaying = playing;
            }
        }
        return fallback;
    }

    private static MediaInfo safeMedia(IMediaSession s) {
        try {
            return s.getMedia();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Track toTrack(IMediaSession s) {
        MediaInfo info = safeMedia(s);
        if (info == null) {
            return Track.EMPTY;
        }
        String owner = s.getOwner();
        // Натив отдаёт секунды (могут быть дробными) — остров работает в мс.
        long posMs = Math.max(0L, Math.round(info.getPosition() * 1000.0D));
        long durMs = Math.max(0L, Math.round(info.getDuration() * 1000.0D));
        return new Track(owner == null ? "" : owner,
                info.getTitle() == null ? "" : info.getTitle(),
                info.getArtist() == null ? "" : info.getArtist(),
                info.isPlaying(), posMs, durMs, info.getArtworkPng());
    }

    /** Последний выбранный трек (или {@code EMPTY} в тишине). */
    public static Track current() {
        return track;
    }

    /**
     * Транспорт напрямую через WinRT-сессию — как раньше через файл-команду,
     * но без хелпера: системный OSD громкости не всплывает.
     * action: 1 = prev, 2 = play/pause, 3 = next.
     */
    public static void control(int action) {
        IMediaSession s = session;
        if (s == null) {
            return;
        }
        try {
            switch (action) {
                case 1 -> s.previous();
                case 2 -> s.playPause();
                case 3 -> s.next();
                default -> {
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
