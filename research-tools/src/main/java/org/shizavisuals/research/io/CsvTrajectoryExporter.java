package org.shizavisuals.research.io;

import org.shizavisuals.research.aim.RotationFrame;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Экспорт траекторий в UTF-8 CSV без внешних библиотек.
 */
public final class CsvTrajectoryExporter {
    private static final String HEADER =
            "timestamp,yaw,pitch,target_yaw,target_pitch,ping,is_human";

    private CsvTrajectoryExporter() {
    }

    public static void write(Path destination, List<RotationFrame> frames) throws IOException {
        if (destination == null) {
            throw new IllegalArgumentException("destination не может быть null");
        }
        if (frames == null) {
            throw new IllegalArgumentException("frames не может быть null");
        }

        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                destination,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            writer.write(HEADER);
            writer.newLine();
            for (RotationFrame frame : frames) {
                if (frame == null) {
                    throw new IllegalArgumentException("frames не должен содержать null");
                }
                writer.write(Long.toString(frame.timestampMs()));
                writer.write(',');
                writer.write(Double.toString(frame.yaw()));
                writer.write(',');
                writer.write(Double.toString(frame.pitch()));
                writer.write(',');
                writer.write(Double.toString(frame.targetYaw()));
                writer.write(',');
                writer.write(Double.toString(frame.targetPitch()));
                writer.write(',');
                writer.write(Integer.toString(frame.pingMs()));
                writer.write(',');
                writer.write(Boolean.toString(frame.human()));
                writer.newLine();
            }
        }
    }
}
