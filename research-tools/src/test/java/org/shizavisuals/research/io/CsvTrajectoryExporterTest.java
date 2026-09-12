package org.shizavisuals.research.io;

import org.shizavisuals.research.aim.RotationFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTrajectoryExporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesExpectedColumnsAndValues() throws Exception {
        Path output = temporaryDirectory.resolve("trajectory.csv");
        CsvTrajectoryExporter.write(output, List.of(
                new RotationFrame(20L, 1.5, -2.0, 10.0, 3.0, 45, true)
        ));

        assertEquals(List.of(
                "timestamp,yaw,pitch,target_yaw,target_pitch,ping,is_human",
                "20,1.5,-2.0,10.0,3.0,45,true"
        ), Files.readAllLines(output));
    }
}
