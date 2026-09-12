package org.shizavisuals.research.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsCalculatorTest {
    private static final List<MetricsCalculator.ScoredSample> SAMPLES = List.of(
            new MetricsCalculator.ScoredSample(0.9, true),
            new MetricsCalculator.ScoredSample(0.6, true),
            new MetricsCalculator.ScoredSample(0.7, false),
            new MetricsCalculator.ScoredSample(0.1, false)
    );

    @Test
    void calculatesThresholdMetricsWithBotAsPositiveClass() {
        MetricsCalculator.ThresholdMetrics result =
                MetricsCalculator.atThreshold(SAMPLES, 0.5);

        assertEquals(2L, result.confusionMatrix().truePositive());
        assertEquals(1L, result.confusionMatrix().falsePositive());
        assertEquals(1L, result.confusionMatrix().trueNegative());
        assertEquals(0L, result.confusionMatrix().falseNegative());
        assertEquals(1.0, result.truePositiveRate());
        assertEquals(0.5, result.falsePositiveRate());
    }

    @Test
    void calculatesRocAucAndHandlesTiedScores() {
        assertEquals(0.75, MetricsCalculator.rocAuc(SAMPLES), 1.0e-12);

        List<MetricsCalculator.ScoredSample> tied = List.of(
                new MetricsCalculator.ScoredSample(0.5, true),
                new MetricsCalculator.ScoredSample(0.5, false)
        );
        assertEquals(0.5, MetricsCalculator.rocAuc(tied), 1.0e-12);
    }
}
