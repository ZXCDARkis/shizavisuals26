package org.shizavisuals.menu.ps4;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of the PS4 menu SoundPlayer: UI interaction sounds plus the looping
 * ambient music, driven by the persistent volume/mute settings.
 */
public final class Ps4Sound {
    private static final Map<String, Clip[]> SOUNDS = new ConcurrentHashMap<>();
    /** Sound names currently being loaded off-thread (dedupe). */
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static Clip ambient;
    private static boolean ambientLoaded;
    private static volatile boolean ambientStopped;
    private static volatile float menuVolume = 0.8F;
    private static volatile boolean muted;

    private Ps4Sound() {
    }

    public static void initFromConfig(float volume, boolean ambientMuted) {
        menuVolume = Math.max(0.0F, Math.min(1.0F, volume));
        muted = ambientMuted;
    }

    public static float getMenuVolume() {
        MenuSettings.load();
        return menuVolume;
    }

    public static boolean isAmbientMuted() {
        MenuSettings.load();
        return muted;
    }

    public static void setMenuVolume(float volume) {
        menuVolume = Math.max(0.0F, Math.min(1.0F, volume));
        applyGain();
        MenuSettings.save();
    }

    /** Toggles ambient music mute; unmuting restarts the ambient loop. */
    public static void toggleAmbientMuted() {
        muted = !muted;
        if (!muted) {
            startAmbientMusic();
        } else if (ambient != null) {
            ambient.stop();
        }
        MenuSettings.save();
    }

    public static void updateMenuVolume(float volume) {
        menuVolume = Math.max(0.0F, Math.min(1.0F, volume));
        applyGain();
    }

    private static void applyGain() {
        float gain = gainFor(menuVolume);
        synchronized (Ps4Sound.class) {
            if (ambient != null && ambient.isOpen()) {
                try {
                    ((FloatControl) ambient.getControl(FloatControl.Type.MASTER_GAIN)).setValue(gain);
                } catch (Exception ignored) {
                }
            }
            for (Clip[] clips : SOUNDS.values()) {
                for (Clip clip : clips) {
                    if (clip != null && clip.isOpen()) {
                        try {
                            ((FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN)).setValue(gainFor(Math.max(0.35F, menuVolume)));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }

    private static float gainFor(float volume) {
        if (volume <= 0.0001F) {
            return -60.0F;
        }
        return Math.max(-40.0F, 20.0F * (float) Math.log10(Math.max(volume, 0.0001F)) * 0.8F);
    }

    /** Starts (or restarts) the looping ambient track honoring the mute flag. */
    public static void startAmbientMusic() {
        ambientStopped = false;
        if (MenuSettings.isAmbientMuted()) {
            return;
        }
        synchronized (Ps4Sound.class) {
            if (ambient == null && !ambientLoaded) {
                ambientLoaded = true;
                ambient = openClip("ps4_ambient", false);
                if (ambient != null) {
                    ambient.addLineListener(event -> {
                        // Loop the track, but never fight an explicit stop.
                        if (event.getType() == LineEvent.Type.STOP
                                && ambient != null && !muted && !ambientStopped) {
                            ambient.setFramePosition(0);
                            ambient.start();
                        }
                    });
                }
            }
            if (ambient != null && ambient.isOpen()) {
                try {
                    ((FloatControl) ambient.getControl(FloatControl.Type.MASTER_GAIN)).setValue(gainFor(menuVolume));
                } catch (Exception ignored) {
                }
                ambient.setFramePosition(0);
                ambient.start();
            }
        }
    }

    public static void stopAmbientMusic() {
        ambientStopped = true;
        synchronized (Ps4Sound.class) {
            if (ambient != null && ambient.isOpen()) {
                ambient.stop();
            }
        }
    }

    public static void playUiSound(String name) {
        Clip[] clips = SOUNDS.get(name);
        if (clips == null) {
            // Load once, off-thread, and publish the filled array atomically:
            // the render thread never iterates a half-filled clip array.
            if (LOADING.add(name)) {
                Thread loader = new Thread(() -> {
                    Clip[] loaded = new Clip[4];
                    for (int i = 0; i < loaded.length; i++) {
                        loaded[i] = openClip(name, true);
                    }
                    SOUNDS.put(name, loaded);
                    LOADING.remove(name);
                });
                loader.setName("Ps4Sound-Loader-" + name);
                loader.setDaemon(true);
                loader.start();
            }
            return;
        }

        for (int i = 0; i < clips.length; i++) {
            Clip clip = clips[i];
            if (clip != null && clip.isOpen() && (!clip.isRunning() || i == clips.length - 1)) {
                try {
                    // Scroll/secondary sounds sit a few dB under select sounds
                    // so rapid hover scrolling is not blasting.
                    float gain = gainFor(Math.max(0.35F, menuVolume))
                            + (name.endsWith("scroll") ? -7.0F : -3.0F);
                    ((FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN)).setValue(Math.max(-60.0F, gain));
                    clip.stop();
                    clip.setFramePosition(0);
                    clip.start();
                } catch (Exception ignored) {
                }
                return;
            }
        }
    }

    private static Clip openClip(String name, boolean cacheUi) {
        try (InputStream raw = Ps4Sound.class.getResourceAsStream("/assets/shizavisuals/sound/" + name + ".wav")) {
            if (raw == null) {
                return null;
            }
            Clip clip = AudioSystem.getClip();
            try (BufferedInputStream stream = new BufferedInputStream(raw)) {
                clip.open(AudioSystem.getAudioInputStream(stream));
            }
            try {
                ((FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN)).setValue(gainFor(Math.max(0.35F, menuVolume)));
            } catch (Exception ignored) {
            }
            return clip;
        } catch (Exception e) {
            return null;
        }
    }
}
