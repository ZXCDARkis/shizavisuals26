package dev.redstones.mediaplayerinfo;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import javax.imageio.ImageIO;

public class MediaInfo implements Serializable {
    private final String title;
    private final String artist;
    private final byte[] artworkPng;
    private final long position;
    private final long duration;
    private final boolean playing;

    public MediaInfo(String string, String string2, byte[] byArray, long l, long l2, boolean bl) {
        this.title = string;
        this.artist = string2;
        this.artworkPng = byArray;
        this.position = l;
        this.duration = l2;
        this.playing = bl;
    }

    public String getTitle() {
        return this.title;
    }

    public String getArtist() {
        return this.artist;
    }

    public byte[] getArtworkPng() {
        return this.artworkPng;
    }

    public long getPosition() {
        return this.position;
    }

    public long getDuration() {
        return this.duration;
    }

    public boolean isPlaying() {
        return this.playing;
    }

    public BufferedImage getArtwork() {
        try {
            return ImageIO.read(new ByteArrayInputStream(this.artworkPng));
        } catch (Exception exception) {
            return null;
        }
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || this.getClass() != object.getClass()) {
            return false;
        }
        MediaInfo mediaInfo = (MediaInfo) object;
        return this.position == mediaInfo.position && this.duration == mediaInfo.duration && this.playing == mediaInfo.playing && Objects.equals(this.title, mediaInfo.title) && Objects.equals(this.artist, mediaInfo.artist) && Arrays.equals(this.artworkPng, mediaInfo.artworkPng);
    }

    public int hashCode() {
        int n = Objects.hash(this.title, this.artist, this.position, this.duration, this.playing);
        n = 31 * n + Arrays.hashCode(this.artworkPng);
        return n;
    }

    public String toString() {
        return "MediaInfo{title='" + this.title + "', artist='" + this.artist + "', position=" + this.position + ", duration=" + this.duration + ", playing=" + this.playing + "}";
    }
}
