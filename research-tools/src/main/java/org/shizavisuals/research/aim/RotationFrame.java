package org.shizavisuals.research.aim;

/**
 * Один отсчёт синтетической траектории поворота камеры.
 *
 * @param timestampMs время от начала примера в миллисекундах
 * @param yaw текущий горизонтальный угол в градусах
 * @param pitch текущий вертикальный угол в градусах
 * @param targetYaw заданный горизонтальный угол цели
 * @param targetPitch заданный вертикальный угол цели
 * @param pingMs сетевой пинг, сохранённый как признак датасета
 * @param human метка класса: {@code true} для человеческой траектории
 */
public record RotationFrame(
        long timestampMs,
        double yaw,
        double pitch,
        double targetYaw,
        double targetPitch,
        int pingMs,
        boolean human
) {
}
