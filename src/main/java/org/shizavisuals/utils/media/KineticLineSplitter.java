package org.shizavisuals.utils.media;

import java.util.ArrayList;
import java.util.List;

/** Splits long lyric lines into short rhythmic chunks with interpolated timings. */
public final class KineticLineSplitter {
    private KineticLineSplitter() {
    }

    public static List<KineticLyricLine> split(List<KineticLyricLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<KineticLyricLine> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            KineticLyricLine current = lines.get(i);
            long nextStart = i + 1 < lines.size() ? lines.get(i + 1).timestampMs() : current.timestampMs() + 4000L;
            long duration = Math.max(1500L, nextStart - current.timestampMs());

            String[] words = current.text().trim().split("\\s+");
            if (words.length <= 4) {
                result.add(current);
                continue;
            }

            // 3-word chunks for very long lines, 2 otherwise; a trailing single
            // word merges back into the previous chunk so orphans never show.
            int perChunk = words.length > 7 ? 3 : 2;
            List<String> chunks = new ArrayList<>();
            StringBuilder chunk = new StringBuilder();
            int count = 0;
            for (String word : words) {
                if (!chunk.isEmpty()) {
                    chunk.append(' ');
                }
                chunk.append(word);
                count++;
                if (count >= perChunk) {
                    chunks.add(chunk.toString());
                    chunk.setLength(0);
                    count = 0;
                }
            }
            if (!chunk.isEmpty()) {
                if (!chunks.isEmpty() && count <= 1) {
                    chunks.set(chunks.size() - 1, chunks.get(chunks.size() - 1) + " " + chunk);
                } else {
                    chunks.add(chunk.toString());
                }
            }

            long chunkDuration = duration / chunks.size();
            for (int c = 0; c < chunks.size(); c++) {
                result.add(new KineticLyricLine(current.timestampMs() + c * chunkDuration, chunks.get(c)));
            }
        }
        result.sort(KineticLyricLine::compareTo);
        return result;
    }
}
