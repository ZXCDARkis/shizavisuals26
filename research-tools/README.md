# ShizaVisuals Research Tools

Автономный Java-модуль для генерации синтетических траекторий yaw/pitch и
оценки бинарных детекторов. Модуль не зависит от Minecraft и не подключается к
игровому клиенту.

## Запуск

Из корня `ShizaVisualsReload`:

```powershell
.\gradlew.bat -p research-tools test
.\gradlew.bat -p research-tools run
```

CSV примера появится в
`research-tools/build/trajectories/human-example.csv`. Собственный путь можно
передать первым аргументом:

```powershell
.\gradlew.bat -p research-tools run --args="C:\data\human.csv"
```

## Основной API

```java
HumanAimGenerator generator = new HumanAimGenerator(42L);
List<RotationFrame> frames = generator.generate(
        15.0,  // initialYaw
        -4.0,  // initialPitch
        92.0,  // targetYaw
        11.0,  // targetPitch
        140.0, // средняя скорость, градусов/с
        65     // ping, мс
);
CsvTrajectoryExporter.write(Path.of("human.csv"), frames);
```

Для моделирования усталости используйте `HumanAimGenerator.Request` и передайте
продолжительность сессии в `sessionElapsedMs`. Все коэффициенты доступны через
`HumanAimGenerator.Config`; значения по умолчанию следует калибровать на
добровольно собранной выборке реальных движений.

В `MetricsCalculator` положительным классом считается бот. Оценка детектора
должна возрастать вместе с уверенностью в классе «бот»:

```java
var metrics = MetricsCalculator.atThreshold(samples, 0.5);
double tpr = metrics.truePositiveRate();
double fpr = metrics.falsePositiveRate();
double auc = MetricsCalculator.rocAuc(samples);
```

Экспортируемые столбцы:

```text
timestamp,yaw,pitch,target_yaw,target_pitch,ping,is_human
```

Одинаковые seed и последовательность запросов дают одинаковый результат.
