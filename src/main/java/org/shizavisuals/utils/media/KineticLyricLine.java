package org.shizavisuals.utils.media;

/** One time-stamped lyric line of a track. */
public record KineticLyricLine(long timestampMs, String text) implements Comparable<KineticLyricLine> {
    @Override
    public int compareTo(KineticLyricLine o) {
        return Long.compare(this.timestampMs, o.timestampMs);
    }
}
