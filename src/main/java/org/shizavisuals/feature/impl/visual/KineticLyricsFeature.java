package org.shizavisuals.feature.impl.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.shizavisuals.event.EventTarget;
import org.shizavisuals.event.events.render.Render2DEvent;
import org.shizavisuals.feature.Feature;
import org.shizavisuals.feature.FeatureCategory;
import org.shizavisuals.feature.FeatureManager;
import org.shizavisuals.feature.setting.BindSetting;
import org.shizavisuals.feature.setting.ModeSetting;
import org.shizavisuals.feature.setting.NumberSetting;
import org.shizavisuals.hud.island.MusicStatus;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.media.KineticLyricLine;
import org.shizavisuals.utils.media.KineticLineSplitter;
import org.shizavisuals.utils.render.Render3DUtil;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.UiFontStyle;
import org.shizavisuals.utils.render.gui.UiFonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Kinetic Lyrics: words of the current track materialize as floating text
 * anchored to 3D positions around the player. Both the lyrics and the
 * playback clock come straight from {@link MusicStatus} — the exact lines the
 * Dynamic Island shows, on the same drift-corrected clock, so every surface
 * stays in sync.
 */
public final class KineticLyricsFeature extends Feature {
    private static final float BASE_TEXT_SIZE = 9.0F;

    public final ModeSetting animation = register(new ModeSetting(
            "Animation", "LyricFlow", "LyricFlow", "Typewriter", "PopScale", "KineticSlide", "Fade"));
    public final ModeSetting colorMode = register(new ModeSetting(
            "Color", "Theme", "Theme", "Theme Pulse", "White"));
    public final ModeSetting splitMode = register(new ModeSetting(
            "Split Mode", "Smart Split", "Smart Split", "Full Line"));
    public final NumberSetting maxLines = register(new NumberSetting("Max Lines", 3.0D, 1.0D, 6.0D, 1.0D, ""));
    public final NumberSetting minDistance = register(new NumberSetting("Min Distance", 2.0D, 1.2D, 4.0D, 0.1D, " blocks"));
    public final NumberSetting maxDistance = register(new NumberSetting("Max Distance", 4.5D, 2.0D, 7.0D, 0.1D, " blocks"));
    public final NumberSetting arcSpread = register(new NumberSetting("Arc Spread", 70.0D, 20.0D, 85.0D, 5.0D, "°"));
    public final NumberSetting floatHeight = register(new NumberSetting("Float Height", 0.5D, 0.1D, 2.0D, 0.1D, " blocks"));
    public final NumberSetting scale = register(new NumberSetting("Scale", 1.0D, 0.5D, 2.0D, 0.05D, "x"));

    private final List<KineticLyricLine> queue = new ArrayList<>();
    private final List<Particle> particles = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private String trackKey = "";
    private String lastSplitMode = "";
    private int queueIndex;
    private long lastAudioMs = -1L;
    private int lastSeekVersion = -1;

    public KineticLyricsFeature() {
        super("KineticLyrics", "Парящие 3D-слова песни в такт музыке",
                FeatureCategory.VISUAL, BindSetting.UNBOUND);
    }

    public static KineticLyricsFeature getEnabled() {
        return FeatureManager.INSTANCE.getEnabled(KineticLyricsFeature.class);
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        resetState();
    }

    @Override
    protected void onDisable() {
        super.onDisable();
        resetState();
    }

    private void resetState() {
        this.queue.clear();
        this.particles.clear();
        this.anchorOrigin = null;
        this.trackKey = "";
        this.lastSplitMode = "";
        this.queueIndex = 0;
        this.lastAudioMs = -1L;
        this.lastSeekVersion = -1;
        this.wasPlaying = false;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Minecraft mc = event.getClient();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!sync(mc) || this.queue.isEmpty()) {
            return;
        }

        // Не доверяем сырому SMTC-флагу: он мигает на каждом сике/буферизации, а
        // раньше текст на нём умирал по стене и исчезал на пару кадров.
        boolean playing = MusicStatus.playing();
        long audioMs = Math.max(0L, MusicStatus.playbackPositionMs());
        // A seek (or track change) bumps MusicStatus.seekVersion(); resync the
        // floating words to the new position immediately instead of letting the
        // old line sit frozen on screen.
        int sv = MusicStatus.seekVersion();
        if (sv != this.lastSeekVersion) {
            this.lastSeekVersion = sv;
            this.particles.clear();
        this.anchorOrigin = null;
            // Пересчитываем индекс от текущей позиции, а не с нуля:
            // иначе после сика все пропущенные строки спавнятся разом
            // или текущая залипает.
            this.queueIndex = indexFor(audioMs);
            // Текущую строку показываем сразу, чтобы не было пустого кадра.
            int cur = this.queueIndex - 1;
            if (cur >= 0 && cur < this.queue.size()) {
                KineticLyricLine curLine = this.queue.get(cur);
                long nextStart = cur + 1 < this.queue.size()
                        ? this.queue.get(cur + 1).timestampMs()
                        : curLine.timestampMs() + 3500L;
                long duration = Mth.clamp(nextStart - curLine.timestampMs(), 1200L, 5500L);
                if (audioMs >= curLine.timestampMs() && audioMs - curLine.timestampMs() < duration) {
                    spawnAt(curLine.text(), duration, curLine.timestampMs(), mc);
                }
            }
            this.lastAudioMs = audioMs;
        }
        if (playing) {
            // Always advance the queue. The internal seek check (big position jump)
            // resyncs particles, and when truly paused the cursor simply holds the
            // current line — so we must not gate this on the raw "playing" flag,
            // which SMTC can misreport around a seek.
            advanceQueue(audioMs, mc);
            // Возобновление: на паузе частицы умирали по стене, а очередь уже
            // прошла текущую строку — после снятия паузы экран оставался пустым
            // до следующей строки. Возвращаем текущую строку на экран.
            if (!this.wasPlaying && this.particles.isEmpty()) {
                respawnCurrent(audioMs, mc);
            }
        }
        this.wasPlaying = playing;

        // На паузе аудио-часы стоят, поэтому чистим частицы по стене:
        // иначе текст висит вечно и выглядит залипшим.
        long wallNow = System.currentTimeMillis();
        this.particles.removeIf(p -> playing ? p.isDead(audioMs) : p.isDeadWall(wallNow));
        float guiScale = mc.getWindow().getGuiScale();
        for (Particle particle : this.particles) {
            particle.render(mc, guiScale, playing ? audioMs : particle.frozenMs());
        }
    }

    private MusicStatus.MediaTrack track;

    // ------------------------------------------------------------------
    // Sync: both lyrics and clock come from MusicStatus (the island source)
    // ------------------------------------------------------------------

    private boolean sync(Minecraft mc) {
        this.track = MusicStatus.currentTrack();
        if (this.track.title().isEmpty()) {
            if (!this.particles.isEmpty()) {
                this.particles.clear();
        this.anchorOrigin = null;
            }
            this.trackKey = "";
            this.queue.clear();
            this.queueIndex = 0;
            this.lastAudioMs = -1L;
            return false;
        }
        if (!this.track.key().equals(this.trackKey)) {
            this.trackKey = this.track.key();
            this.queue.clear();
            this.particles.clear();
        this.anchorOrigin = null;
            this.queueIndex = 0;
            this.lastAudioMs = -1L;
            return false;
        }
        if (this.queue.isEmpty()) {
            pullSharedLyrics();
            this.lastAudioMs = -1L;
            this.lastSplitMode = this.splitMode.getValue();
            if (!this.queue.isEmpty()) {
                this.queueIndex = indexFor(Math.max(0L, MusicStatus.playbackPositionMs()));
            }
            return !this.queue.isEmpty();
        }
        if (!this.lastSplitMode.equals(this.splitMode.getValue())) {
            this.lastSplitMode = this.splitMode.getValue();
            pullSharedLyrics();
            this.queueIndex = 0;
            this.particles.clear();
        this.anchorOrigin = null;
            this.lastAudioMs = -1L;
        }
        return true;
    }

    /**
     * Pulls the lyrics the Dynamic Island is showing for this track. Both
     * surfaces share one validated LRCLIB result and one playback clock, so
     * the floating words are always identical to (and in sync with) the
     * island text. The island fetches asynchronously — we simply wait until
     * its lyrics appear.
     */
    private void pullSharedLyrics() {
        MusicStatus.SharedLyrics shared = MusicStatus.currentLyrics();
        if (shared.trackKey().isEmpty()) {
            return;
        }
        this.queue.clear();
        List<KineticLyricLine> lines = new ArrayList<>();
        for (int i = 0; i < shared.lines().length; i++) {
            String text = shared.lines()[i];
            if (text != null && !text.isBlank()) {
                lines.add(new KineticLyricLine(shared.timesMs()[i], text));
            }
        }
        if (!lines.isEmpty()) {
            this.queue.addAll(this.splitMode.is("Smart Split")
                    ? KineticLineSplitter.split(lines)
                    : lines);
        }
    }

    private int indexFor(long audioMs) {
        int newIndex = 0;
        for (int i = this.queue.size() - 1; i >= 0; i--) {
            if (this.queue.get(i).timestampMs() <= audioMs) {
                newIndex = i + 1;
                break;
            }
        }
        return newIndex;
    }

    private boolean wasPlaying;

    /** Re-spawns the line that is currently being sung, if it is still alive. */
    private void respawnCurrent(long audioMs, Minecraft mc) {
        int cur = indexFor(audioMs) - 1;
        if (cur < 0 || cur >= this.queue.size()) {
            return;
        }
        KineticLyricLine line = this.queue.get(cur);
        long nextStart = cur + 1 < this.queue.size()
                ? this.queue.get(cur + 1).timestampMs()
                : line.timestampMs() + 3500L;
        long duration = Mth.clamp(nextStart - line.timestampMs(), 1200L, 5500L);
        if (audioMs >= line.timestampMs() && audioMs - line.timestampMs() < duration) {
            spawnAt(line.text(), duration, line.timestampMs(), mc);
        }
    }

    private void advanceQueue(long audioMs, Minecraft mc) {
        // Дрейф часов между репортами хелпера (300мс) — это не сик:
        // порог 1.5с ловил обычный тик + джиттер и сбрасывал строку.
        // Реальный сик = большой скачок ЗА ОДИН КАДР (~50мс).
        // Плюс сверяемся с seekVersion из MusicStatus (там два подряда).
        long frameDelta = this.lastAudioMs != -1L ? audioMs - this.lastAudioMs : 0L;
        if (this.lastAudioMs != -1L && Math.abs(frameDelta) > 1500L) {
            int newIndex = indexFor(audioMs);
            // Сик вперёд/назад: промежуточные строки не спамим.
            this.particles.clear();
        this.anchorOrigin = null;
            int cur = newIndex - 1;
            if (cur >= 0 && cur < this.queue.size()) {
                KineticLyricLine curLine = this.queue.get(cur);
                long nextStart = cur + 1 < this.queue.size()
                        ? this.queue.get(cur + 1).timestampMs()
                        : curLine.timestampMs() + 3500L;
                long duration = Mth.clamp(nextStart - curLine.timestampMs(), 1200L, 5500L);
                if (audioMs - curLine.timestampMs() < duration && audioMs >= curLine.timestampMs()) {
                    spawnAt(curLine.text(), duration, curLine.timestampMs(), mc);
                }
            }
            this.queueIndex = newIndex;
            this.lastAudioMs = audioMs;
            return;
        }

        while (this.queueIndex < this.queue.size()) {
            KineticLyricLine line = this.queue.get(this.queueIndex);
            if (audioMs < line.timestampMs()) {
                break;
            }
            long nextStart = this.queueIndex + 1 < this.queue.size()
                    ? this.queue.get(this.queueIndex + 1).timestampMs()
                    : line.timestampMs() + 3500L;
            long duration = Mth.clamp(nextStart - line.timestampMs(), 1200L, 5500L);
            if (audioMs - line.timestampMs() < duration) {
                spawnAt(line.text(), duration, line.timestampMs(), mc);
            }
            this.queueIndex++;
        }
        this.lastAudioMs = audioMs;
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    private void spawnAt(String text, long durationMs, long timestampMs, Minecraft mc) {
        if (text == null || text.isBlank()) {
            return;
        }
        while (this.particles.size() >= this.maxLines.getValue().intValue()) {
            this.particles.remove(0);
        }
        Vec3 anchor = findAnchor(mc);
        if (anchor == null) {
            return;
        }
        this.particles.add(new Particle(text, anchor, timestampMs, durationMs,
                this.random.nextBoolean() ? 1.0F : -1.0F));
    }

    private void spawn(String text, long durationMs, Minecraft mc) {
        spawnAt(text, durationMs, MusicStatus.playbackPositionMs(), mc);
    }

    /**
     * Fixed world point the whole lyric field is scattered around. Anchors used to
     * be sampled from the player's live eye position, so every new word appeared
     * wherever the player was standing at that moment and the cloud followed them
     * around the world. Captured once and kept until the field is cleared.
     */
    private Vec3 anchorOrigin;

    /** Random point inside the view arc around the fixed origin, avoiding overlaps. */
    private Vec3 findAnchor(Minecraft mc) {
        if (this.anchorOrigin == null) {
            this.anchorOrigin = mc.player.getEyePosition();
        }
        double minD = this.minDistance.getValue();
        double maxD = Math.max(minD + 0.5D, this.maxDistance.getValue());
        float spread = this.arcSpread.getValue().floatValue();
        float yaw = mc.gameRenderer.mainCamera().yRot();
        Vec3 eyes = this.anchorOrigin;

        Vec3 best = null;
        double bestClearance = -1.0D;
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = Math.toRadians(yaw + (this.random.nextDouble() * 2.0D - 1.0D) * spread);
            double dist = minD + this.random.nextDouble() * (maxD - minD);
            double dy = -0.25D + this.random.nextDouble() * 0.8D;
            Vec3 candidate = new Vec3(
                    eyes.x - Math.sin(angle) * dist,
                    eyes.y + dy,
                    eyes.z + Math.cos(angle) * dist);

            double clearance = Double.MAX_VALUE;
            for (Particle p : this.particles) {
                clearance = Math.min(clearance, candidate.distanceTo(p.anchor));
            }
            if (this.particles.isEmpty() || clearance >= 1.35D) {
                return candidate;
            }
            if (clearance > bestClearance) {
                bestClearance = clearance;
                best = candidate;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Particle
    // ------------------------------------------------------------------

    private final class Particle {
        private final String text;
        private final Vec3 anchor;
        private final long timestampMs;
        private final long durationMs;
        private final float tiltSign;
        private final long wallBirthMs = System.currentTimeMillis();

        Particle(String text, Vec3 anchor, long timestampMs, long durationMs, float tiltSign) {
            this.text = text;
            this.anchor = anchor;
            this.timestampMs = timestampMs;
            this.durationMs = durationMs;
            this.tiltSign = tiltSign;
        }

        boolean isDead(long audioMs) {
            return audioMs - this.timestampMs >= this.durationMs;
        }

        boolean isDeadWall(long wallNow) {
            // На паузе аудио-часы стоят: умираем по стене + небольшой хвост,
            // чтобы текст гас, а не висел вечно.
            return wallNow - this.wallBirthMs >= this.durationMs + 1200L;
        }

        long frozenMs() {
            // Замороженный прогресс на момент паузы: дальше анимация идёт
            // по стене, чтобы текст гас, а не висел вечно.
            long wallAge = System.currentTimeMillis() - this.wallBirthMs;
            return this.timestampMs + Math.clamp(wallAge, 0L, this.durationMs);
        }

        void render(Minecraft mc, float guiScale, long audioMs) {
            long now = System.currentTimeMillis();
            float progress = Mth.clamp((audioMs - this.timestampMs) / (float) this.durationMs, 0.0F, 1.0F);

            String display = this.text;
            float alpha;
            float scaleAnim;
            float rise;
            float tilt;
            switch (KineticLyricsFeature.this.animation.getValue()) {
                case "Typewriter" -> {
                    if (progress < 0.30F) {
                        float t = progress / 0.30F;
                        int chars = Mth.ceil(this.text.length() * t);
                        display = this.text.substring(0, Mth.clamp(chars, 1, this.text.length()));
                        alpha = Math.min(1.0F, progress / 0.08F);
                    } else if (progress < 0.75F) {
                        alpha = 1.0F;
                    } else {
                        float t = (progress - 0.75F) / 0.25F;
                        int chars = Mth.floor(this.text.length() * (1.0F - t));
                        display = this.text.substring(0, Mth.clamp(chars, 0, this.text.length()));
                        alpha = 1.0F - t * 0.7F;
                    }
                    scaleAnim = 1.0F;
                    rise = progress * 0.2F;
                    tilt = 0.0F;
                }
                case "PopScale" -> {
                    if (progress < 0.18F) {
                        float t = progress / 0.18F;
                        alpha = easeOutCubic(t);
                        scaleAnim = 0.35F + 0.65F * easeOutBack(t);
                        rise = -0.04F * (1.0F - easeOutCubic(t));
                    } else if (progress < 0.80F) {
                        alpha = 1.0F;
                        scaleAnim = 1.0F;
                        rise = 0.0F;
                    } else {
                        float t = (progress - 0.80F) / 0.20F;
                        alpha = 1.0F - easeInCubic(t);
                        scaleAnim = 1.0F - 0.05F * t;
                        rise = 0.03F * easeInCubic(t);
                    }
                    tilt = 0.0F;
                }
                case "KineticSlide" -> {
                    if (progress < 0.20F) {
                        float t = progress / 0.20F;
                        alpha = easeOutCubic(t);
                        scaleAnim = 0.92F + 0.08F * t;
                        rise = -0.15F * (1.0F - easeOutCubic(t));
                        tilt = (1.0F - t) * 4.0F * this.tiltSign;
                    } else if (progress < 0.80F) {
                        alpha = 1.0F;
                        scaleAnim = 1.0F;
                        rise = 0.0F;
                        tilt = 0.0F;
                    } else {
                        float t = (progress - 0.80F) / 0.20F;
                        alpha = 1.0F - easeInCubic(t);
                        scaleAnim = 1.0F - 0.04F * t;
                        rise = 0.05F * easeInCubic(t);
                        tilt = 0.0F;
                    }
                }
                case "Fade" -> {
                    alpha = progress < 0.15F ? progress / 0.15F
                            : progress < 0.80F ? 1.0F
                            : 1.0F - (progress - 0.80F) / 0.20F;
                    scaleAnim = 1.0F;
                    rise = 0.0F;
                    tilt = 0.0F;
                }
                default -> { // LyricFlow
                    if (progress < 0.15F) {
                        float t = progress / 0.15F;
                        alpha = easeOutCubic(t);
                        scaleAnim = 0.93F + 0.07F * easeOutBack(t);
                        rise = -0.06F * (1.0F - easeOutCubic(t));
                    } else if (progress < 0.80F) {
                        alpha = 1.0F;
                        scaleAnim = 1.0F;
                        rise = 0.0F;
                    } else {
                        float t = (progress - 0.80F) / 0.20F;
                        alpha = 1.0F - easeInCubic(t);
                        scaleAnim = 1.0F - 0.03F * t;
                        rise = 0.04F * easeInCubic(t);
                    }
                    tilt = 0.0F;
                }
            }

            if (alpha <= 0.01F || display.isBlank()) {
                return;
            }

            double bob = Math.sin((audioMs - this.timestampMs) / 480.0D) * 0.02D;
            Vec3 world = new Vec3(this.anchor.x,
                    this.anchor.y + rise * KineticLyricsFeature.this.floatHeight.getValue() + bob,
                    this.anchor.z);
            Render3DUtil.ScreenPoint screen = Render3DUtil.projectToScreen(mc, world);
            if (screen == null) {
                return;
            }

            double dist = mc.gameRenderer.mainCamera().position().distanceTo(world);
            float distanceScale = (float) Mth.clamp(6.0D / Math.max(2.0D, dist), 0.45D, 1.15D);

            // Шрифт выбираем по ПОЛНОЙ строке, а не по обрезанному display: в Typewriter
            // подстрока без кириллицы возвращала другой начертанный, и ширина
            // строки скакала каждый кадр — текст «дёргался» и переезжал.
            MsdfFont font = UiFonts.getFontForText(this.text, UiFontStyle.MEDIUM.weight());
            float textSize = BASE_TEXT_SIZE * guiScale
                    * KineticLyricsFeature.this.scale.getValue().floatValue()
                    * scaleAnim * distanceScale;
            float letterSpacing = textSize * UiFontStyle.MEDIUM.letterSpacingEm();
            float textWidth = font.measureWidth(display, textSize, letterSpacing);

            float centerX = screen.x() + tilt * 0.35F;
            float centerY = screen.y();

            int color = pickColor();
            int argb = ColorUtil.multiplyAlpha(color, alpha);
            Render2DUtil.text(centerX - textWidth / 2.0F,
                    font.centeredTextY(centerY, textSize),
                    textSize, display)
                    .font(font)
                    .style(UiFontStyle.MEDIUM)
                    .color(argb)
                    .draw();
        }

        private int pickColor() {
            return switch (KineticLyricsFeature.this.colorMode.getValue()) {
                case "White" -> 0xFFFFFFFF;
                case "Theme Pulse" -> {
                    float ratio = (float) (Math.sin(System.currentTimeMillis() / 450.0D) * 0.5D + 0.5D);
                    yield ColorUtil.lerp(Theme.getAccent(), 0xFFFFFFFF, ratio * 0.55F);
                }
                default -> Theme.getAccent();
            };
        }
    }

    private static float easeOutCubic(float t) {
        return 1.0F - (float) Math.pow(1.0F - t, 3.0F);
    }

    private static float easeInCubic(float t) {
        return (float) Math.pow(t, 3.0F);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(t - 1.0F, 3.0F) + c1 * (float) Math.pow(t - 1.0F, 2.0F);
    }
}
