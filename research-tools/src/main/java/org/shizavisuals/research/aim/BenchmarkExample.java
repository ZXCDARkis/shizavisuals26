package org.shizavisuals.research.aim;

import org.shizavisuals.research.io.CsvTrajectoryExporter;
import org.shizavisuals.research.metrics.MetricsCalculator;

import java.nio.file.Path;
import java.util.List;

/**
 * Минимальный пример запуска автономного генератора.
 */
public final class BenchmarkExample {
    private BenchmarkExample() {
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0
                ? Path.of("build", "trajectories", "human-example.csv")
                : Path.of(args[0]);

        HumanAimGenerator generator = new HumanAimGenerator(42L);
        List<RotationFrame> frames = generator.generate(new HumanAimGenerator.Request(
                15.0,
                -4.0,
                92.0,
                11.0,
                140.0,
                65,
                25 * 60_000L
        ));
        CsvTrajectoryExporter.write(output, frames);

        // Пример оценок внешнего детектора: высокий score означает «бот».
        List<MetricsCalculator.ScoredSample> scores = List.of(
                new MetricsCalculator.ScoredSample(0.91, true),
                new MetricsCalculator.ScoredSample(0.73, true),
                new MetricsCalculator.ScoredSample(0.42, false),
                new MetricsCalculator.ScoredSample(0.08, false)
        );
        MetricsCalculator.ThresholdMetrics metrics =
                MetricsCalculator.atThreshold(scores, 0.5);

        System.out.printf(
                "CSV: %s%nframes=%d, TPR=%.3f, FPR=%.3f, ROC-AUC=%.3f%n",
                output.toAbsolutePath(),
                frames.size(),
                metrics.truePositiveRate(),
                metrics.falsePositiveRate(),
                MetricsCalculator.rocAuc(scores)
        );
    }
}
