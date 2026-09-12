package org.shizavisuals.research.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Метрики бинарного детектора. Положительный класс во всех методах — бот.
 */
public final class MetricsCalculator {
    private MetricsCalculator() {
    }

    /**
     * Считает confusion matrix для правила: score >= threshold означает «бот».
     */
    public static ThresholdMetrics atThreshold(List<ScoredSample> samples, double threshold) {
        validateSamples(samples);
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("threshold должен быть конечным числом");
        }

        long truePositive = 0L;
        long falsePositive = 0L;
        long trueNegative = 0L;
        long falseNegative = 0L;

        for (ScoredSample sample : samples) {
            boolean predictedBot = sample.score() >= threshold;
            if (sample.bot() && predictedBot) {
                truePositive++;
            } else if (!sample.bot() && predictedBot) {
                falsePositive++;
            } else if (!sample.bot()) {
                trueNegative++;
            } else {
                falseNegative++;
            }
        }

        ConfusionMatrix matrix = new ConfusionMatrix(
                truePositive,
                falsePositive,
                trueNegative,
                falseNegative
        );
        return new ThresholdMetrics(threshold, matrix, matrix.truePositiveRate(), matrix.falsePositiveRate());
    }

    /**
     * ROC-AUC через статистику Манна—Уитни. Одинаковые score получают средний
     * ранг, поэтому ничьи учитываются как 0.5.
     */
    public static double rocAuc(List<ScoredSample> samples) {
        validateSamples(samples);
        List<ScoredSample> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparingDouble(ScoredSample::score));

        long positives = sorted.stream().filter(ScoredSample::bot).count();
        long negatives = sorted.size() - positives;
        if (positives == 0L || negatives == 0L) {
            throw new IllegalArgumentException("Для ROC-AUC нужны оба класса");
        }

        double positiveRankSum = 0.0;
        int start = 0;
        while (start < sorted.size()) {
            int end = start + 1;
            while (end < sorted.size()
                    && Double.compare(sorted.get(start).score(), sorted.get(end).score()) == 0) {
                end++;
            }

            // Ранги нумеруются с единицы; для группы равных score берётся средний.
            double averageRank = ((start + 1) + end) / 2.0;
            for (int i = start; i < end; i++) {
                if (sorted.get(i).bot()) {
                    positiveRankSum += averageRank;
                }
            }
            start = end;
        }

        double minimumPositiveRankSum = positives * (positives + 1.0) / 2.0;
        return (positiveRankSum - minimumPositiveRankSum) / (positives * (double) negatives);
    }

    private static void validateSamples(List<ScoredSample> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Список samples не может быть пустым");
        }
        for (ScoredSample sample : samples) {
            if (sample == null || !Double.isFinite(sample.score())) {
                throw new IllegalArgumentException("Все samples должны иметь конечный score");
            }
        }
    }

    /**
     * @param score оценка детектора: большее значение означает большую
     *              уверенность в классе «бот»
     * @param bot истинная метка положительного класса
     */
    public record ScoredSample(double score, boolean bot) {
    }

    public record ConfusionMatrix(
            long truePositive,
            long falsePositive,
            long trueNegative,
            long falseNegative
    ) {
        public double truePositiveRate() {
            return safeDivide(truePositive, truePositive + falseNegative);
        }

        public double falsePositiveRate() {
            return safeDivide(falsePositive, falsePositive + trueNegative);
        }

        public double precision() {
            return safeDivide(truePositive, truePositive + falsePositive);
        }

        public double accuracy() {
            return safeDivide(
                    truePositive + trueNegative,
                    truePositive + falsePositive + trueNegative + falseNegative
            );
        }

        private static double safeDivide(long numerator, long denominator) {
            return denominator == 0L ? Double.NaN : numerator / (double) denominator;
        }
    }

    public record ThresholdMetrics(
            double threshold,
            ConfusionMatrix confusionMatrix,
            double truePositiveRate,
            double falsePositiveRate
    ) {
    }
}
