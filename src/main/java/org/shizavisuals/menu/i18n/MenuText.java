package org.shizavisuals.menu.i18n;

import org.shizavisuals.feature.setting.NumberSetting;

import java.util.Locale;
import java.util.Map;

public final class MenuText {
    private static final Map<String, String> UI_RU = Map.ofEntries(
            Map.entry("Settings", "Настройки"),
            Map.entry("Customize your cheat client to meet your needs with just one click.",
                    "Настройте чит-клиент под свои потребности одним нажатием."),
            Map.entry("Theme Editor", "Редактор темы"),
            Map.entry("Interface", "Интерфейс"),
            Map.entry("Cosmetics", "Косметика"),
            Map.entry("Pick Pulse wings, hats, backpacks and pets.",
                    "Выберите крылья, шляпы, рюкзаки и питомцев Pulse."),
            Map.entry("Wings", "Крылья"),
            Map.entry("Hats", "Шляпы"),
            Map.entry("Bodywear", "Одежда"),
            Map.entry("Pets", "Питомцы"),
            Map.entry("Glass Style", "Стиль стекла"),
            Map.entry("Solid panels or merged liquid glass with fused overlaps.",
                    "Обычные панели или жидкое стекло со слиянием перекрытий."),
            Map.entry("Liquid Glass", "Жидкое стекло"),
            Map.entry("Performance", "Производительность"),
            Map.entry("Controls", "Управление"),
            Map.entry("Client Color", "Цвет клиента"),
            Map.entry("Adjust the main accent color of the interface.",
                    "Настройте основной акцентный цвет интерфейса."),
            Map.entry("Theme Variable", "Параметр темы"),
            Map.entry("Fine-tune specific theme settings or scaling.",
                    "Точно настройте параметры и масштаб темы."),
            Map.entry("Language", "Язык"),
            Map.entry("Choose the language that suits you.", "Выберите язык интерфейса."),
            Map.entry("Low Performance Mode", "Режим для слабых устройств"),
            Map.entry("Optimize UI rendering to increase FPS.",
                    "Упрощает отрисовку интерфейса для повышения FPS."),
            Map.entry("Reduce Shadows", "Уменьшить тени"),
            Map.entry("Disable interface shadow effects to save resources.",
                    "Отключает тени интерфейса для экономии ресурсов."),
            Map.entry("Cache UI", "Кэшировать интерфейс"),
            Map.entry("Keep layout elements in memory to reduce stutters.",
                    "Хранит элементы интерфейса в памяти, уменьшая подлагивания."),
            Map.entry("UI Scale", "Масштаб интерфейса"),
            Map.entry("Change the overall size of the client menus.",
                    "Изменяет общий размер меню клиента."),
            Map.entry("Background Blur", "Размытие фона"),
            Map.entry("Enable or disable blurring behind the interface.",
                    "Выберите интенсивность размытия за интерфейсом."),
            Map.entry("Glass Blur", "Размытие стекла"),
            Map.entry("Controls blur inside frosted glass cards and popups.",
                    "Настраивает размытие внутри стеклянных карточек и всплывающих окон."),
            Map.entry("Background Dimming", "Затемнение фона"),
            Map.entry("Darken the animated menu background.",
                    "Затемняет анимированный фон меню."),
            Map.entry("Select a Menu Background to enable.",
                    "Сначала выберите фон меню."),
            Map.entry("Menu Background", "Фон меню"),
            Map.entry("Animated shader behind the menu content.",
                    "Анимированный шейдер на фоне содержимого меню."),
            Map.entry("Rounded Corners", "Скругление углов"),
            Map.entry("Select the corner smoothness for menu elements.",
                    "Выберите радиус скругления элементов меню."),
            Map.entry("Toggle Sound", "Звуки переключений"),
            Map.entry("Enable or disable audio feedback for clicks.",
                    "Включает или отключает звуки нажатий."),
            Map.entry("Visible", "Показывать в HUD"),
            Map.entry("Unbound", "Не назначено"),
            Map.entry("Reset", "Сбросить")
            ,Map.entry("Reset layout", "Сбросить расположение")
            ,Map.entry("Empty", "Пусто")
            ,Map.entry("New Hotkey", "Новая горячая клавиша")
            ,Map.entry("Delete", "Удалить")
            ,Map.entry("Press", "Нажмите")
            ,Map.entry("Cancel", "Отмена")
            ,Map.entry("Save", "Сохранить")
            ,Map.entry("Friends", "Друзья")
            ,Map.entry("Manage trusted players ignored by combat features.",
                    "Управляйте доверенными игроками, которых игнорируют боевые функции.")
            ,Map.entry("Player name", "Имя игрока")
            ,Map.entry("Add Friend", "Добавить друга")
            ,Map.entry("Enter a Minecraft player name.", "Введите имя игрока Minecraft.")
            ,Map.entry("No friends yet", "Список друзей пуст")
            ,Map.entry("Add a player here or use .friend add <name>",
                    "Добавьте игрока здесь или командой .friend add <имя>")
            ,Map.entry("Enter a valid player name", "Введите корректное имя игрока")
            ,Map.entry("This player is already a friend", "Этот игрок уже добавлен в друзья")
            ,Map.entry("Accounts", "Аккаунты")
            ,Map.entry("Switch between your saved Minecraft accounts.",
                    "Переключайтесь между сохранёнными аккаунтами Minecraft.")
            ,Map.entry("Type Nickname", "Введите ник")
            ,Map.entry("Create Account", "Создать аккаунт")
            ,Map.entry("Enter a new account nickname", "Введите новый ник аккаунта")
            ,Map.entry("below.", "ниже.")
            ,Map.entry("Recent", "Недавние")
            ,Map.entry("Oldest", "Старые")
            ,Map.entry("Playing now", "Используется сейчас")
            ,Map.entry("Never played", "Не использовался")
            ,Map.entry("Search", "Поиск")
            ,Map.entry("Start typing to find a module or setting.",
                    "Начните вводить название модуля или настройки.")
            ,Map.entry("No modules in this category yet",
                    "В этой категории пока нет модулей")
            ,Map.entry("Modules registered in this category will appear here automatically.",
                    "Модули этой категории появятся здесь автоматически.")
            ,Map.entry("Scout", "Найти")
            ,Map.entry("Connect", "Подключить")
            ,Map.entry("PvE State", "Состояние PvE")
            ,Map.entry("In development", "В разработке")
            ,Map.entry("Disabled", "Выключено")
            ,Map.entry("Mined ores", "Добыто руд")
            ,Map.entry("Known targets", "Найдено целей")
            ,Map.entry("Elapsed", "Прошло времени")
            ,Map.entry("Mine profile", "Профиль шахты")
            ,Map.entry("Tool profile", "Профиль кирки")
            ,Map.entry("Next mine", "Следующая шахта")
            ,Map.entry("Time left", "Осталось времени")
            ,Map.entry("Diamond", "Алмазная")
            ,Map.entry("Idle", "Ожидание")
            ,Map.entry("Warp to mine", "Телепортация в шахту")
            ,Map.entry("Following route", "Движение по маршруту")
            ,Map.entry("Mining", "Добыча")
            ,Map.entry("Going to deposit", "Движение к хранилищу")
            ,Map.entry("Opening deposit", "Открытие хранилища")
            ,Map.entry("Depositing ore", "Выгрузка руды")
            ,Map.entry("Returning", "Возвращение")
            ,Map.entry("Paused", "Пауза")
            ,Map.entry("Error", "Ошибка")
            ,Map.entry("low health", "мало здоровья")
            ,Map.entry("pickaxe missing", "нет кирки")
            ,Map.entry("pickaxe durability", "прочность кирки")
            ,Map.entry("nearby player", "игрок рядом")
            ,Map.entry("unexpected screen", "открыт посторонний экран")
    );

    private static final Map<String, String> SETTING_RU = Map.ofEntries(
            Map.entry("Action", "Действие"),
            Map.entry("Additive Glow", "Аддитивное свечение"),
            Map.entry("Aim Range", "Дополнительная дальность наведения"),
            Map.entry("Animation Speed", "Скорость анимации выбора"),
            Map.entry("Armor Color", "Отдельный цвет для игроков в броне"),
            Map.entry("Armor Color Value", "Цвет игроков в броне"),
            Map.entry("Armor Filter", "Фильтр брони"),
            Map.entry("Attack Range", "Поправка к дальности атаки"),
            Map.entry("Aura", "Aura"),
            Map.entry("Block Break", "Разрушение блоков"),
            Map.entry("Blocks", "Блоки"),
            Map.entry("Boost", "Ускорение"),
            Map.entry("Both Hands", "Обе руки"),
            Map.entry("Box", "Рамка"),
            Map.entry("Box Mode", "Режим рамки"),
            Map.entry("Break Shield", "Ломать щит"),
            Map.entry("Brightness", "Яркость"),
            Map.entry("Center Dot", "Центральная точка"),
            Map.entry("Center Size", "Размер центральной точки"),
            Map.entry("Change Fog", "Изменить туман"),
            Map.entry("Change Saturation", "Изменить насыщенность"),
            Map.entry("Change Sky", "Изменить небо"),
            Map.entry("Change Time", "Изменить время"),
            Map.entry("Change World Color", "Изменять цвет освещения мира"),
            Map.entry("Color", "Цвет"),
            Map.entry("Color Mode", "Режим цвета"),
            Map.entry("Command", "Команда"),
            Map.entry("Conditions", "Условия"),
            Map.entry("Cooldown", "Задержка"),
            Map.entry("Cooldown Animation", "Анимация перезарядки атаки"),
            Map.entry("Cooldown Gap Multiplier", "Расширение при перезарядке"),
            Map.entry("CPS", "CPS"),
            Map.entry("Dangers", "Опасности"),
            Map.entry("Delay", "Задержка"),
            Map.entry("Dim Rejected", "Затемнять отклонённые лоты"),
            Map.entry("Distance", "Дистанция"),
            Map.entry("Distortion", "Искажение"),
            Map.entry("Don Item Info", "Информация о донат-предмете"),
            Map.entry("Don't Hit While Eating", "Не бить во время еды"),
            Map.entry("Dynamic", "Динамическая яркость"),
            Map.entry("Effect", "Эффект"),
            Map.entry("Effect Duration", "Показывать длительность эффектов"),
            Map.entry("Effects", "Эффекты"),
            Map.entry("Elements", "Элементы HUD"),
            Map.entry("Entity", "Сущности"),
            Map.entry("Fill Opacity", "Непрозрачность заливки"),
            Map.entry("Fill Type", "Тип заливки"),
            Map.entry("Filter Reason", "Показывать причину отклонения"),
            Map.entry("Filled", "С заливкой"),
            Map.entry("Fishing Hook", "Рыболовный крючок"),
            Map.entry("Flame", "Пламя"),
            Map.entry("Flame Speed", "Скорость пламени"),
            Map.entry("Flame Trail", "Шлейф пламени"),
            Map.entry("Fog Color", "Цвет тумана"),
            Map.entry("Fog Distance", "Дистанция тумана"),
            Map.entry("FOV", "Угол обзора"),
            Map.entry("Full Effect Duration", "Максимальная длительность эффектов"),
            Map.entry("Gap", "Отступ"),
            Map.entry("Glass Blur", "Размытие стекла"),
            Map.entry("Glow", "Свечение"),
            Map.entry("Glow Radius", "Радиус свечения"),
            Map.entry("Glow Strength", "Сила свечения"),
            Map.entry("Health", "Здоровье"),
            Map.entry("Health Bar", "Полоса здоровья"),
            Map.entry("Highlight Don Items", "Подсвечивать донат-предметы"),
            Map.entry("Highlight Expired", "Подсвечивать просроченные лоты"),
            Map.entry("Highlight Potions", "Подсвечивать зелья"),
            Map.entry("Hit Amount", "Частиц за удар"),
            Map.entry("Ignore Depth", "Рисовать сквозь стены"),
            Map.entry("Ignore Naked", "Игнорировать игроков без брони"),
            Map.entry("Intensity", "Интенсивность эффекта"),
            Map.entry("Items", "Экипировка"),
            Map.entry("Jump", "Прыжок"),
            Map.entry("Key", "Клавиша"),
            Map.entry("Keep Sprint", "Поддерживать спринт"),
            Map.entry("Known Potions Only", "Только известные зелья"),
            Map.entry("Left Mouse", "Левая кнопка мыши"),
            Map.entry("Left X", "Левая рука X"),
            Map.entry("Left Y", "Левая рука Y"),
            Map.entry("Left Z", "Левая рука Z"),
            Map.entry("Length", "Длина"),
            Map.entry("Lifetime", "Время жизни"),
            Map.entry("Light From Items", "Свет от предметов"),
            Map.entry("Light From Others", "Свет от других сущностей"),
            Map.entry("Light Intensity", "Интенсивность света"),
            Map.entry("Light Radius", "Радиус света"),
            Map.entry("Light Style", "Стиль освещения"),
            Map.entry("Max Brightness", "Максимальная яркость"),
            Map.entry("Max Effect Level", "Максимальный уровень эффекта"),
            Map.entry("Max Particles", "Максимум частиц"),
            Map.entry("Min Brightness", "Минимальная яркость"),
            Map.entry("Minimum Health", "Минимальное здоровье"),
            Map.entry("Min Depth Strider", "Мин. уровень Подводной ходьбы"),
            Map.entry("Min Durability", "Мин. остаточная прочность"),
            Map.entry("Min Protection", "Мин. уровень Защиты"),
            Map.entry("Min Sharpness", "Мин. уровень Остроты"),
            Map.entry("Min Unbreaking", "Мин. уровень Прочности"),
            Map.entry("Mirror", "Зеркальное отражение"),
            Map.entry("Mode", "Режим"),
            Map.entry("Move Correction", "Коррекция движения"),
            Map.entry("Name", "Подменное имя"),
            Map.entry("Negative Effects", "Негативные эффекты"),
            Map.entry("No Knockback", "Без Отдачи"),
            Map.entry("No Thorns", "Без Шипов"),
            Map.entry("Not While Eating", "Не менять тотем во время еды"),
            Map.entry("Not With Head", "Не менять тотем с головой в руке"),
            Map.entry("Omni Directional", "Спринт во все стороны"),
            Map.entry("Only Criticals", "Только критические удары"),
            Map.entry("Only Friends", "Только друзья"),
            Map.entry("Opacity", "Непрозрачность"),
            Map.entry("Original Texture", "Исходная текстура"),
            Map.entry("Outline", "Обводка"),
            Map.entry("Outline Thickness", "Толщина обводки"),
            Map.entry("Overlay", "Оверлеи"),
            Map.entry("Password", "Пароль"),
            Map.entry("Plasma Speed", "Скорость плазмы"),
            Map.entry("Potion Effects", "Показывать эффекты зелий"),
            Map.entry("Potion Filter", "Фильтр зелий"),
            Map.entry("Predict Trajectory", "Предсказывать траекторию"),
            Map.entry("Price For 1 Item", "Цена за 1 предмет"),
            Map.entry("Primary Color", "Основной цвет"),
            Map.entry("Priority", "Приоритет цели"),
            Map.entry("Privilege", "Привилегия"),
            Map.entry("Radius", "Радиус"),
            Map.entry("Range", "Дальность"),
            Map.entry("Release Shield", "Опускать щит перед ударом"),
            Map.entry("Require Mending", "Требовать Починку"),
            Map.entry("Reset Position", "Сбросить расположение"),
            Map.entry("Right Click", "Правый клик"),
            Map.entry("Right Mouse", "Правая кнопка мыши"),
            Map.entry("Right X", "Правая рука X"),
            Map.entry("Right Y", "Правая рука Y"),
            Map.entry("Right Z", "Правая рука Z"),
            Map.entry("Rotation", "Ротация"),
            Map.entry("Rounded", "Скруглять рамку"),
            Map.entry("Saturation", "Насыщенность"),
            Map.entry("Scale", "Масштаб"),
            Map.entry("Secondary Color", "Вторичный цвет"),
            Map.entry("Server", "Сервер"),
            Map.entry("Shader", "Шейдер"),
            Map.entry("Shader Intensity", "Интенсивность шейдера"),
            Map.entry("Shader Speed", "Скорость шейдера"),
            Map.entry("Shape", "Форма"),
            Map.entry("Show Distance", "Показывать дистанцию"),
            Map.entry("Show Owner", "Показывать владельца"),
            Map.entry("Show Size", "Показывать хитбоксы"),
            Map.entry("Size", "Размер"),
            Map.entry("Skip Talismans", "Пропускать талисманы"),
            Map.entry("Slots", "Количество слотов"),
            Map.entry("Smart Criticals", "Умные критические удары"),
            Map.entry("Sounds", "Звуки"),
            Map.entry("Spawn Radius", "Радиус появления"),
            Map.entry("Spawn Rate", "Интервал появления"),
            Map.entry("Speed", "Скорость"),
            Map.entry("Strict", "Точный выбор"),
            Map.entry("Style", "Стиль"),
            Map.entry("Sword Filter", "Фильтр мечей"),
            Map.entry("T-Mode", "Т-образный режим"),
            Map.entry("Target ESP", "Подсветка цели"),
            Map.entry("Target ESP Color", "Цвет подсветки цели"),
            Map.entry("Targets", "Цели"),
            Map.entry("Textured", "С текстурой"),
            Map.entry("Themed Colors", "Цвета темы"),
            Map.entry("Thickness", "Толщина"),
            Map.entry("Tick Dilation", "Замедление повтора кликов"),
            Map.entry("Time of Day", "Время суток"),
            Map.entry("Tint", "Оттенок"),
            Map.entry("TPS Sync", "Синхронизация TPS"),
            Map.entry("Through Walls", "Сквозь стены"),
            Map.entry("Trigger Distance", "Дистанция срабатывания"),
            Map.entry("Type", "Тип"),
            Map.entry("Use Inventory", "Использовать инвентарь"),
            Map.entry("Variant", "Вариант"),
            Map.entry("Visible Color", "Цвет подсветки"),
            Map.entry("Vex Spawn Egg", "Яйцо призыва вредины"),
            Map.entry("Water", "Вода"),
            Map.entry("Wave Effect", "Эффект волны"),
            Map.entry("Wave Radius", "Радиус волны"),
            Map.entry("Wave Strength", "Сила волны"),
            Map.entry("Wave Time", "Длительность волны"),
            Map.entry("With Aura", "Использовать с Aura"),
            Map.entry("World", "Мир"),
            Map.entry("World Color", "Цвет освещения мира"),
            Map.entry("Y Size", "Расширять по Y")
    );

    private static final Map<String, String> PVE_SETTING_RU = Map.ofEntries(
            Map.entry("Acceleration", "Ускорение"),
            Map.entry("Action Interval", "Интервал действий"),
            Map.entry("Anarchy", "Анархия"),
            Map.entry("Apple Storage", "Хранилище яблок"),
            Map.entry("Auto Reconnect", "Автопереподключение"),
            Map.entry("Auto Reporter", "Автожалобы"),
            Map.entry("Auto Sell", "Автопродажа"),
            Map.entry("Avoid Crowded Chests", "Избегать занятых сундуков"),
            Map.entry("Before Opening", "Время до открытия"),
            Map.entry("Bone Meal Per Cycle", "Костной муки за цикл"),
            Map.entry("Bone Meal Reserve", "Запас костной муки"),
            Map.entry("Bone Storage", "Хранилище костей"),
            Map.entry("Buy Emeralds", "Покупать изумруды"),
            Map.entry("Buy XP Bottles", "Покупать пузырьки опыта"),
            Map.entry("Chat Text", "Текст сообщения"),
            Map.entry("Chest Open Delay", "Задержка открытия сундука"),
            Map.entry("Chest Radius", "Радиус сундуков"),
            Map.entry("Chest Sign", "Табличка сундука"),
            Map.entry("Chunk Load Timeout", "Ожидание загрузки чанка"),
            Map.entry("Chunk Step", "Шаг по чанкам"),
            Map.entry("Clan Name", "Название клана"),
            Map.entry("Clan Unload Command", "Команда выгрузки в клан"),
            Map.entry("Cleanup At Free Slots", "Очистка при свободных слотах"),
            Map.entry("Connect Telegram", "Подключить Telegram"),
            Map.entry("Craft", "Крафтить"),
            Map.entry("Craft Blocks", "Крафтить блоки"),
            Map.entry("Crowd Penalty", "Штраф за игроков"),
            Map.entry("Crowd Radius", "Радиус игроков"),
            Map.entry("Currency Threshold", "Порог баланса"),
            Map.entry("Current State", "Текущее состояние"),
            Map.entry("Debug Logging", "Отладочные логи"),
            Map.entry("Deposit Apple Stacks", "Стаков яблок для выгрузки"),
            Map.entry("Deposit At Free Slots", "Выгружать при свободных слотах"),
            Map.entry("Deposit Command", "Команда перед выгрузкой"),
            Map.entry("Deposit Items", "Предметы для выгрузки"),
            Map.entry("Deposit Mode", "Режим выгрузки"),
            Map.entry("Deposit Position", "Позиция выгрузки"),
            Map.entry("Digging Mode", "Режим копания"),
            Map.entry("Drop Interval", "Интервал выброса"),
            Map.entry("Drop Junk", "Выбрасывать мусор"),
            Map.entry("Emerald Reserve", "Запас изумрудов"),
            Map.entry("Enchanted Golden Apples", "Зачарованные золотые яблоки"),
            Map.entry("Fallback Sell Price", "Резервная цена продажи"),
            Map.entry("Farm Position", "Позиция фермы"),
            Map.entry("Farm Radius", "Радиус фермы"),
            Map.entry("Features", "Функции"),
            Map.entry("Flee From Warden", "Убегать от вардена"),
            Map.entry("Golden Apples", "Золотые яблоки"),
            Map.entry("Grief Number", "Номер грифа"),
            Map.entry("Height Mode", "Режим высоты"),
            Map.entry("Helpers", "Помощники"),
            Map.entry("Home Anarchy", "Домашняя анархия"),
            Map.entry("Home Name", "Название дома"),
            Map.entry("Home Scan Radius", "Радиус поиска дома"),
            Map.entry("Home Wait Threshold", "Порог ожидания дома"),
            Map.entry("Ignore Enchanted Golden Apples", "Игнорировать зачарованные яблоки"),
            Map.entry("Ignore Golden Apples", "Игнорировать золотые яблоки"),
            Map.entry("Instant", "Мгновенно"),
            Map.entry("Invest Into Clan", "Вкладывать в клан"),
            Map.entry("Invest Percentage", "Процент вклада"),
            Map.entry("Keep Hand Empty", "Держать руку пустой"),
            Map.entry("Loot Anarchies", "Анархии для лута"),
            Map.entry("Max Blocks Per Cycle", "Максимум блоков за цикл"),
            Map.entry("Max Chest Wait", "Максимальное ожидание сундука"),
            Map.entry("Max Speed", "Максимальная скорость"),
            Map.entry("Mega", "Мега"),
            Map.entry("Min Food", "Минимум еды"),
            Map.entry("Min Invisibility", "Минимум зелий невидимости"),
            Map.entry("Min Loot", "Минимум лута"),
            Map.entry("Min Speed", "Минимум зелий скорости"),
            Map.entry("Mine Down", "Копать вниз"),
            Map.entry("Mine Neighbor", "Ломать соседние блоки"),
            Map.entry("Mine Profile", "Профиль шахты"),
            Map.entry("Mine Region", "Регион шахты"),
            Map.entry("Minimum Containers", "Минимум контейнеров"),
            Map.entry("Minimum Tool Durability", "Минимальная прочность инструмента"),
            Map.entry("Money Mode", "Режим денег"),
            Map.entry("Money Per Gunpowder", "Доход за единицу пороха"),
            Map.entry("Only PvP", "Только в PvP"),
            Map.entry("Pages To Parse", "Страниц для проверки"),
            Map.entry("Pause Near Players", "Пауза рядом с игроками"),
            Map.entry("Pitch Speed", "Скорость по вертикали"),
            Map.entry("Player Radius", "Радиус игроков"),
            Map.entry("Potions", "Зелья"),
            Map.entry("Radius XZ", "Радиус XZ"),
            Map.entry("Radius Y", "Радиус Y"),
            Map.entry("Reconnect Delay", "Задержка переподключения"),
            Map.entry("Region Height", "Высота региона"),
            Map.entry("Region Max", "Максимальная граница региона"),
            Map.entry("Region Min", "Минимальная граница региона"),
            Map.entry("Region Radius", "Радиус региона"),
            Map.entry("Repair Below", "Чинить при прочности ниже"),
            Map.entry("Repair To", "Чинить до прочности"),
            Map.entry("Require Food", "Требовать еду"),
            Map.entry("Require Invisibility", "Требовать невидимость"),
            Map.entry("Require Speed", "Требовать скорость"),
            Map.entry("Restock Check", "Проверка запасов"),
            Map.entry("Restock Sign", "Табличка пополнения"),
            Map.entry("Return Buffer", "Запас времени на возврат"),
            Map.entry("Return Radius", "Радиус возврата"),
            Map.entry("Rotate", "Ротация"),
            Map.entry("Route", "Маршрут"),
            Map.entry("Route Radius", "Радиус маршрута"),
            Map.entry("RTP Mode", "Режим RTP"),
            Map.entry("Sapling Reserve", "Запас саженцев"),
            Map.entry("Save Rod", "Беречь удочку"),
            Map.entry("Scan Radius", "Радиус сканирования"),
            Map.entry("Scout Now", "Найти сейчас"),
            Map.entry("Scout Warden Cities", "Искать города вардена"),
            Map.entry("Search Height", "Высота поиска"),
            Map.entry("Sell Markup", "Наценка продажи"),
            Map.entry("Sell Sign", "Табличка продажи"),
            Map.entry("Send Chat Message", "Отправлять сообщение"),
            Map.entry("Sell Blocks", "Продавать блоки"),
            Map.entry("Server Profile", "Профиль сервера"),
            Map.entry("Server Transfer", "Смена сервера"),
            Map.entry("Show Statistics", "Показывать статистику"),
            Map.entry("Show Warden Zone", "Показывать зону вардена"),
            Map.entry("Storage Position", "Позиция хранилища"),
            Map.entry("Store Gold", "Складировать золото"),
            Map.entry("Swap Delay", "Задержка смены"),
            Map.entry("Take Bones", "Забирать кости"),
            Map.entry("Take from Crafter Chest", "Брать из сундука крафтера"),
            Map.entry("Target Timeout", "Тайм-аут цели"),
            Map.entry("Telegram Notifications", "Уведомления Telegram"),
            Map.entry("Teleport Cooldown", "Задержка телепортации"),
            Map.entry("Transfer Interval", "Интервал смены сервера"),
            Map.entry("Trash Items", "Мусорные предметы"),
            Map.entry("Trees Per Cycle", "Деревьев за цикл"),
            Map.entry("Tunnel Segment", "Длина тоннеля"),
            Map.entry("Turn Head", "Поворачивать голову"),
            Map.entry("Unload At", "Выгружать при"),
            Map.entry("Unload Gunpowder", "Выгружать порох"),
            Map.entry("Unload Target", "Место выгрузки"),
            Map.entry("Use Home Command", "Использовать команду дома"),
            Map.entry("Use Server Mine", "Использовать серверную шахту"),
            Map.entry("Vertical Scan", "Вертикальный поиск"),
            Map.entry("Villager Radius", "Радиус жителей"),
            Map.entry("Work Mode", "Режим работы"),
            Map.entry("XP Bottle Buy Command", "Команда покупки пузырьков опыта"),
            Map.entry("Yaw Speed", "Скорость по горизонтали")
    );

    private static final Map<String, String> SETTING_EN = Map.ofEntries(
            Map.entry("Дезориентация", "Disorientation"),
            Map.entry("Явная пыль", "Sheer Dust"),
            Map.entry("Божья аура", "God's Aura"),
            Map.entry("Снежок заморозка", "Freezing Snowball"),
            Map.entry("Огненный смерч", "Fiery Tornado"),
            Map.entry("Пласт", "Stratum"),
            Map.entry("Трапка", "Trap"),
            Map.entry("Зелье силы", "Strength Potion"),
            Map.entry("Зелье невидимости", "Invisibility Potion"),
            Map.entry("Зелье скорости", "Speed Potion"),
            Map.entry("Зелье прыгучести", "Leaping Potion"),
            Map.entry("Зелье регенерации", "Regeneration Potion"),
            Map.entry("Зелье ночного зрения", "Night Vision Potion"),
            Map.entry("Зелье огнестойкости", "Fire Resistance Potion"),
            Map.entry("Зелье водного дыхания", "Water Breathing Potion"),
            Map.entry("Хлопушка", "Popper"),
            Map.entry("Святая вода", "Holy Water"),
            Map.entry("Зелье Гнева", "Rage Potion"),
            Map.entry("Зелье Палладина", "Paladin Potion"),
            Map.entry("Зелье Ассасина", "Assassin Potion"),
            Map.entry("Зелье Радиации", "Radiation Potion"),
            Map.entry("Снотворное", "Drowsiness Potion"),
            Map.entry("Зелье победителя", "Winner's Potion"),
            Map.entry("Улучшенное зелье силы", "Enhanced Strength Potion"),
            Map.entry("Улучшенное зелье скорости", "Enhanced Speed Potion"),
            Map.entry("Стан", "Stun"),
            Map.entry("Взрывная трапка", "Explosive Trap"),
            Map.entry("Тнт-Пушка", "TNT Cannon"),
            Map.entry("Динамит", "Dynamite"),
            Map.entry("Динамит A", "Dynamite A"),
            Map.entry("Динамит B", "Dynamite B"),
            Map.entry("Разрывная волна", "Blast Wave"),
            Map.entry("Динамит Б2", "Dynamite B2"),
            Map.entry("Стиллер", "Stealer"),
            Map.entry("Надёжный стиллер", "Reliable Stealer"),
            Map.entry("Ледяная волна", "Ice Wave"),
            Map.entry("Особый компас", "Special Compass"),
            Map.entry("Зачарованное яблоко", "Enchanted Apple"),
            Map.entry("Универсальный ключ", "Universal Key"),
            Map.entry("Золотой спавнер", "Golden Spawner"),
            Map.entry("Уникальный приват", "Unique Claim"),
            Map.entry("Открыть рюкзак", "Open Backpack")
    );

    private static final Map<String, String> SETTING_CONTEXT_RU = Map.ofEntries(
            Map.entry("WorldTweaks/Effect", "Эффект"),
            Map.entry("WallClimb/Speed", "Скорость подъёма"),
            Map.entry("WorldTweaks/Speed", "Скорость эффекта"),
            Map.entry("SwingAnimation/Speed", "Длительность взмаха"),
            Map.entry("CreeperFarm/Attack Range", "Дальность атаки")
    );

    private static final Map<String, String> FEATURE_DESCRIPTION_RU = Map.ofEntries(
            Map.entry("HUD customization settings", "Настройка элементов HUD"),
            Map.entry("Attacks the entity you are aiming at", "Атакует сущность, на которую вы смотрите"),
            Map.entry("Automatically attacks entities around you.", "Автоматически атакует сущностей вокруг вас."),
            Map.entry("Quickly throws an ender pearl", "Быстро бросает жемчуг Края"),
            Map.entry("Keeps a totem of undying in your offhand", "Поддерживает тотем бессмертия во второй руке"),
            Map.entry("Pick a hotbar item on screen while holding a key",
                    "Позволяет выбрать предмет хотбара на экране при удержании клавиши"),
            Map.entry("Automatically jumps from ground", "Автоматически прыгает с земли"),
            Map.entry("Draws styled nametags above players", "Отображает стилизованные метки над игроками"),
            Map.entry("Prevents the player from being pushed", "Предотвращает толчки игрока"),
            Map.entry("Expands the collision size of surrounding entities for easier hits",
                    "Расширяет хитбоксы окружающих сущностей для более лёгких попаданий"),
            Map.entry("Walk around while your inventory is open", "Позволяет двигаться с открытым инвентарём"),
            Map.entry("Automatically sprints while moving", "Автоматически включает спринт при движении"),
            Map.entry("Spawns procedural particles when you attack an entity",
                    "Создаёт процедурные частицы при атаке сущности"),
            Map.entry("Spawns expanding rings at jump positions", "Создаёт расширяющиеся кольца в местах прыжков"),
            Map.entry("Spawns procedural particles around the player", "Создаёт процедурные частицы вокруг игрока"),
            Map.entry("Predicts projectile paths and impact points", "Показывает траектории снарядов и точки попадания"),
            Map.entry("Leaves a rising ghost model when a player uses a totem",
                    "Оставляет поднимающуюся призрачную модель после срабатывания тотема"),
            Map.entry("Automatic mouse clicking", "Автоматически нажимает кнопки мыши"),
            Map.entry("Predicts target movement and arrow ballistics",
                    "Учитывает движение цели и баллистику стрелы"),
            Map.entry("Removes knockback", "Убирает отбрасывание"),
            Map.entry("Automatically takes items from containers", "Автоматически забирает предметы из контейнеров"),
            Map.entry("Prevents walking off block edges", "Не позволяет сойти с края блока"),
            Map.entry("Swim faster", "Ускоряет плавание"),
            Map.entry("Automatically accepts teleport requests", "Автоматически принимает запросы на телепортацию"),
            Map.entry("Automatically jumps at block edges", "Автоматически прыгает у края блока"),
            Map.entry("Quick FunTime/HolyWorld DonItems actions",
                    "Быстро использует донат-предметы FunTime и HolyWorld"),
            Map.entry("Removes selected player action delays", "Убирает выбранные задержки действий игрока"),
            Map.entry("Protects your nickname in client UI", "Скрывает ваш ник в интерфейсе клиента"),
            Map.entry("Procedural configurable crosshair", "Настраиваемый процедурный прицел"),
            Map.entry("Climb walls like a ladder", "Позволяет взбираться по стенам как по лестнице"),
            Map.entry("Shows your coordinates on death", "Показывает координаты места смерти"),
            Map.entry("Adjusts first person hand positions", "Настраивает положение рук от первого лица"),
            Map.entry("Dropped items lie on the ground", "Укладывает выброшенные предметы на землю"),
            Map.entry("Animated hands and held items in first person",
                    "Добавляет анимации рук и предметов от первого лица"),
            Map.entry("Store items in the crafting grid", "Позволяет хранить предметы в сетке крафта"),
            Map.entry("Custom cosmetic capes visible on you", "Добавляет видимые на вас косметические плащи"),
            Map.entry("Renders a colored silhouette over your first-person hands",
                    "Накладывает цветной шейдерный силуэт на руки от первого лица"),
            Map.entry("Animated shader over the selected block", "Отображает анимированный шейдер на выбранном блоке"),
            Map.entry("Hides distracting overlays, world effects, particles, and sounds.",
                    "Скрывает мешающие оверлеи, эффекты мира, частицы и звуки."),
            Map.entry("Attack and mine while using items", "Позволяет атаковать и копать во время использования предметов"),
            Map.entry("Time, fog, sky, saturation, and world lighting",
                    "Настраивает время, туман, небо, насыщенность и освещение мира"),
            Map.entry("Picks the best tool for the targeted block", "Выбирает лучший инструмент для выбранного блока"),
            Map.entry("Adaptive brightness and dynamic item lights",
                    "Добавляет адаптивную яркость и динамический свет от предметов"),
            Map.entry("Custom hand swing animation", "Изменяет анимацию взмаха руки"),
            Map.entry("Removes jump boost effect", "Убирает эффект усиления прыжка"),
            Map.entry("Respawns immediately after death", "Мгновенно возрождает после смерти"),
            Map.entry("Automatically responds to server login and registration prompts",
                    "Автоматически отвечает на запросы входа и регистрации сервера"),
            Map.entry("Leaves when health, nearby players, or moderator presence becomes unsafe",
                    "Выходит при низком здоровье, появлении игрока рядом или модератора"),
            Map.entry("Respawns automatically after death",
                    "Автоматически возрождает после смерти"),
            Map.entry("Shared server, identity, rotation and safety settings for PvE",
                    "Общие настройки сервера, профиля, ротации и безопасности PvE"),
            Map.entry("Throws selected splash potions when their effects are missing",
                    "Бросает выбранные взрывные зелья, когда отсутствуют их эффекты"),
            Map.entry("Automatically eats food and maintains invisibility",
                    "Автоматически ест и поддерживает невидимость"),
            Map.entry("Uses the held clan-upgrade items on the block below",
                    "Использует предметы улучшения клана на блоке под игроком"),
            Map.entry("Loads a creeper farm, collects drops and unloads gunpowder",
                    "Загружает ферму криперов, собирает дроп и выгружает порох"),
            Map.entry("Plants, grows and harvests apple trees with storage and repair cycles",
                    "Сажает и выращивает деревья, собирает яблоки, разгружает их и чинит инструмент"),
            Map.entry("Breaks nearby blocks using safe target filtering",
                    "Ломает ближайшие блоки с безопасной фильтрацией целей"),
            Map.entry("Automates Warden-city chest routes, storage, supplies and selling",
                    "Автоматизирует маршруты по городам вардена, сундуки, запасы и продажу"),
            Map.entry("Searches configurable routes for container clusters",
                    "Ищет скопления контейнеров по настраиваемым маршрутам"),
            Map.entry("Shows mine timers, removes configured trash and protects pickaxes",
                    "Показывает таймеры шахт, выбрасывает выбранный мусор и бережёт кирки"),
            Map.entry("Crafts enchanted golden apples from signed resource chests",
                    "Крафтит зачарованные золотые яблоки из отмеченных сундуков с ресурсами"),
            Map.entry("Coordinates authenticated money requests between local game instances",
                    "Синхронизирует защищённые денежные запросы между локальными окнами игры"),
            Map.entry("Retries the selected grief server through its validated selector menus",
                    "Повторно подключается к выбранному гриф-серверу через проверенные меню"),
            Map.entry("Invests a percentage of the displayed balance after a threshold",
                    "Вкладывает в клан процент отображаемого баланса после достижения порога"),
            Map.entry("Periodically relists expired auction items",
                    "Периодически перевыставляет просроченные лоты на аукцион"),
            Map.entry("Buys emeralds, trades with clerics, and processes the resulting gold",
                    "Покупает изумруды, торгует со священниками и обрабатывает полученное золото"),
            Map.entry("Eats the strongest allowed golden apple at low health",
                    "Съедает самое сильное разрешённое золотое яблоко при низком здоровье"),
            Map.entry("Casts and reels a fishing rod automatically",
                    "Автоматически забрасывает и вытягивает удочку"),
            Map.entry("Accelerates flight to the nearest grounded item and returns",
                    "Ускоряет полёт к ближайшему предмету на земле и возвращает обратно"),
            Map.entry("Performs small idle actions at a configurable interval",
                    "Периодически выполняет небольшие действия для защиты от AFK"),
            Map.entry("Equips the strongest armor in your inventory",
                    "Надевает самую сильную броню из инвентаря")
    );

    private static final Map<String, String> OPTION_RU = Map.ofEntries(
            Map.entry("All", "Все параметры"),
            Map.entry("Animals", "Животные"),
            Map.entry("Anchor", "Якорь возрождения"),
            Map.entry("AppIcon", "AppIcon"),
            Map.entry("Armor", "Броня"),
            Map.entry("ASCII", "ASCII"),
            Map.entry("Bad Effects", "Негативные визуальные эффекты"),
            Map.entry("Barcode", "Штрихкод"),
            Map.entry("English", "Английский"),
            Map.entry("Russian", "Русский"),
            Map.entry("FPS Boost", "Максимум FPS"),
            Map.entry("Balanced", "Баланс"),
            Map.entry("Quality", "Качество"),
            Map.entry("No effects", "Без эффектов"),
            Map.entry("No blur", "Без размытия"),
            Map.entry("Soft blur", "Мягкое размытие"),
            Map.entry("Strong blur", "Сильное размытие"),
            Map.entry("Small", "Малое"),
            Map.entry("Medium", "Среднее"),
            Map.entry("Large", "Большое"),
            Map.entry("None", "Нет"),
            Map.entry("No background", "Без фона"),
            Map.entry("Nothing selected", "Ничего не выбрано"),
            Map.entry("Off", "Выкл."),
            Map.entry("Nebula", "Туманность"),
            Map.entry("Plasma", "Плазма"),
            Map.entry("Gyroid", "Gyroid")
            ,Map.entry("Both", "Оба")
            ,Map.entry("Boss Bar", "Полоса босса")
            ,Map.entry("Box", "Полная рамка")
            ,Map.entry("Butterfly", "Butterfly")
            ,Map.entry("Camera", "По камере")
            ,Map.entry("Circle", "Круг")
            ,Map.entry("Classic", "Классический")
            ,Map.entry("Corners", "Углы")
            ,Map.entry("Crystal", "Кристалл Края")
            ,Map.entry("Custom", "Свой цвет")
            ,Map.entry("Custom Password", "Пользовательский пароль")
            ,Map.entry("Day", "День")
            ,Map.entry("Deep Space", "Глубокий космос")
            ,Map.entry("Default", "Стандартный")
            ,Map.entry("Disconnect", "Отключиться")
            ,Map.entry("Distance", "Дистанция")
            ,Map.entry("Dollars", "Доллары")
            ,Map.entry("Eat", "Еда и питьё")
            ,Map.entry("Embers", "Угольки")
            ,Map.entry("Explosion", "Взрыв")
            ,Map.entry("External", "Внешний")
            ,Map.entry("Effects", "Все частицы")
            ,Map.entry("Fall", "Падение")
            ,Map.entry("Fill", "Заливка")
            ,Map.entry("Fire", "Огонь")
            ,Map.entry("Firework", "Фейерверки")
            ,Map.entry("Friends", "Друзья")
            ,Map.entry("FOV", "Угол обзора")
            ,Map.entry("Generated", "Сгенерированный")
            ,Map.entry("Ghosts", "Призраки")
            ,Map.entry("Glass", "Стекло")
            ,Map.entry("Glossy Gradients", "Глянцевые градиенты")
            ,Map.entry("Glow", "Свечение")
            ,Map.entry("Hearts", "Сердца")
            ,Map.entry("Health", "Здоровье")
            ,Map.entry("Hit", "Удары")
            ,Map.entry("Hostile", "Враждебные")
            ,Map.entry("Hurt", "Получение урона")
            ,Map.entry("Internal", "Внутренний")
            ,Map.entry("Invisible", "Невидимые")
            ,Map.entry("Invisibles", "Невидимые")
            ,Map.entry("Items", "Предметы")
            ,Map.entry("Jitter", "Jitter")
            ,Map.entry("Low Health", "Низкое здоровье")
            ,Map.entry("Marker", "Маркер")
            ,Map.entry("Mace", "Булава")
            ,Map.entry("Midnight", "Полночь")
            ,Map.entry("MinimalPattern", "Минималистичный узор")
            ,Map.entry("Mobs", "Мобы")
            ,Map.entry("Monsters", "Монстры")
            ,Map.entry("Moderator", "Модератор")
            ,Map.entry("Motto", "Девиз")
            ,Map.entry("Naked Players", "Игроки без брони")
            ,Map.entry("Nearby Player", "Игрок рядом")
            ,Map.entry("Night", "Ночь")
            ,Map.entry("Normal", "Обычный")
            ,Map.entry("Obsidian", "Обсидиан")
            ,Map.entry("Outline", "Обводка")
            ,Map.entry("Passive", "Мирные")
            ,Map.entry("Players", "Игроки")
            ,Map.entry("Prismatic Flow", "Призматический поток")
            ,Map.entry("Projectiles", "Снаряды")
            ,Map.entry("Pumpkins", "Тыквы")
            ,Map.entry("Random", "Случайно")
            ,Map.entry("Scoreboard", "Таблица счёта")
            ,Map.entry("Self", "Свои")
            ,Map.entry("Server Command", "Серверная команда")
            ,Map.entry("Shader", "Шейдер")
            ,Map.entry("Shader Fill", "Шейдерная заливка")
            ,Map.entry("Shaking", "Тряска от урона")
            ,Map.entry("Slice", "Рубящий удар")
            ,Map.entry("Snowflakes", "Снежинки")
            ,Map.entry("Solid", "Сплошная заливка")
            ,Map.entry("Sounds", "Звуки")
            ,Map.entry("Sparks", "Искры")
            ,Map.entry("Spear", "Копьё")
            ,Map.entry("Spiral", "Спираль")
            ,Map.entry("Stars", "Звёзды")
            ,Map.entry("Step", "Шаги")
            ,Map.entry("Sunrise", "Рассвет")
            ,Map.entry("Sunset", "Закат")
            ,Map.entry("Sync", "Цвет темы")
            ,Map.entry("Thrust", "Выпад")
            ,Map.entry("Totem", "Тотем")
            ,Map.entry("Totem Overlay", "Анимация активации тотема")
            ,Map.entry("Totem Particles", "Частицы тотема")
            ,Map.entry("Toggle", "Переключение")
            ,Map.entry("Hold", "Удержание")
            ,Map.entry("True Light", "True Light")
            ,Map.entry("Vandal", "Vandal")
            ,Map.entry("Villagers", "Жители")
            ,Map.entry("Warden", "Варден и скалк")
            ,Map.entry("Water Caustics", "Водная каустика")
            ,Map.entry("Weather", "Погода")
            ,Map.entry("World", "Мир")
            ,Map.entry("Watermark", "Водяной знак")
            ,Map.entry("Coordinates", "Координаты")
            ,Map.entry("Keybinds", "Горячие клавиши")
            ,Map.entry("Potions", "Эффекты зелий")
            ,Map.entry("Target", "Цель")
            ,Map.entry("Notifications", "Уведомления")
    );

    private static final Map<String, String> PVE_OPTION_RU = Map.ofEntries(
            Map.entry("Auto", "Авто"),
            Map.entry("Auto Eat", "Автоеда"),
            Map.entry("Auto Invisibility", "Автоневидимость"),
            Map.entry("Big", "Большой"),
            Map.entry("Chest", "Сундук"),
            Map.entry("Clan", "Клан"),
            Map.entry("Clean Inventory", "Очистка инвентаря"),
            Map.entry("Connect", "Подключить"),
            Map.entry("Enchanted Golden Apple", "Зачарованное золотое яблоко"),
            Map.entry("Ender Chest", "Эндер-сундук"),
            Map.entry("Everyone", "Все блоки"),
            Map.entry("Everywhere", "Везде"),
            Map.entry("Fire Resistance", "Огнестойкость"),
            Map.entry("Fixed", "Фиксированная"),
            Map.entry("Generic", "Обычный"),
            Map.entry("Next Mine", "Следующая шахта"),
            Map.entry("Only Mine", "Только в шахте"),
            Map.entry("Only Ore", "Только руда"),
            Map.entry("Ore Priority", "Приоритет руды"),
            Map.entry("Request & Send", "Запрос и отправка"),
            Map.entry("Request Only", "Только запрос"),
            Map.entry("Save Pickaxe", "Сохранение кирки"),
            Map.entry("Scout", "Найти"),
            Map.entry("Send Only", "Только отправка"),
            Map.entry("Smart", "Умная"),
            Map.entry("Strength", "Сила"),
            Map.entry("Speed", "Скорость")
    );

    private static final Map<String, String> SUFFIX_RU = Map.ofEntries(
            Map.entry(" HP", " ОЗ"),
            Map.entry(" blocks", " блоков"),
            Map.entry(" blocks/tick", " блоков/тик"),
            Map.entry(" chunks", " чанков"),
            Map.entry(" deg/tick", " град/тик"),
            Map.entry(" items", " предметов"),
            Map.entry(" ms", " мс"),
            Map.entry(" s", " с"),
            Map.entry(" stacks", " стаков"),
            Map.entry("min", " мин"),
            Map.entry("ms", " мс"),
            Map.entry("s", " с"),
            Map.entry(" ticks", " тиков"),
            Map.entry("px", " пкс")
    );

    private static final Map<String, String> BIND_RU = Map.ofEntries(
            Map.entry("None", "Не назначено"),
            Map.entry("Mouse Left", "ЛКМ"),
            Map.entry("Mouse Right", "ПКМ"),
            Map.entry("Mouse Middle", "СКМ"),
            Map.entry("Space", "Пробел"),
            Map.entry("Enter", "Ввод"),
            Map.entry("Tab", "Tab"),
            Map.entry("Escape", "Esc"),
            Map.entry("Backspace", "Backspace"),
            Map.entry("Delete", "Delete"),
            Map.entry("Insert", "Insert"),
            Map.entry("Home", "Home"),
            Map.entry("End", "End"),
            Map.entry("Page Up", "Страница вверх"),
            Map.entry("Page Down", "Страница вниз"),
            Map.entry("Up", "Вверх"),
            Map.entry("Down", "Вниз"),
            Map.entry("Left", "Влево"),
            Map.entry("Right", "Вправо")
    );

    private MenuText() {
    }

    public static String ui(String english) {
        return translate(UI_RU, english);
    }

    public static String setting(String canonicalName) {
        if (canonicalName == null) {
            return null;
        }
        return UiLanguage.current() == UiLanguage.ENGLISH
                ? SETTING_EN.getOrDefault(canonicalName, canonicalName)
                : SETTING_RU.getOrDefault(
                        canonicalName,
                        PVE_SETTING_RU.getOrDefault(canonicalName, canonicalName)
                );
    }

    public static String setting(String featureName, String canonicalName) {
        if (UiLanguage.current() == UiLanguage.RUSSIAN && featureName != null) {
            String contextual = SETTING_CONTEXT_RU.get(featureName + "/" + canonicalName);
            if (contextual != null) {
                return contextual;
            }
        }
        return setting(canonicalName);
    }

    public static String option(String canonicalValue) {
        if (canonicalValue == null || UiLanguage.current() == UiLanguage.ENGLISH) {
            return canonicalValue;
        }
        return OPTION_RU.getOrDefault(
                canonicalValue,
                PVE_OPTION_RU.getOrDefault(canonicalValue, canonicalValue)
        );
    }

    public static String featureDescription(String englishDescription) {
        return translate(FEATURE_DESCRIPTION_RU, englishDescription);
    }

    public static String numberValue(NumberSetting setting) {
        String suffix = setting.getSuffix();
        if (UiLanguage.current() == UiLanguage.RUSSIAN) {
            suffix = SUFFIX_RU.getOrDefault(suffix, suffix);
        }
        return String.format(Locale.ROOT, "%.2f", setting.getValue()) + suffix;
    }

    public static String bind(String canonicalLabel) {
        if (canonicalLabel == null || UiLanguage.current() == UiLanguage.ENGLISH) {
            return canonicalLabel;
        }
        String exact = BIND_RU.get(canonicalLabel);
        if (exact != null) {
            return exact;
        }
        if (canonicalLabel.startsWith("Mouse ")) {
            return "Мышь " + canonicalLabel.substring("Mouse ".length());
        }
        if (canonicalLabel.startsWith("Key ")) {
            return "Клавиша " + canonicalLabel.substring("Key ".length());
        }
        return canonicalLabel;
    }

    public static String bindCombination(String canonicalLabels) {
        if (canonicalLabels == null || UiLanguage.current() == UiLanguage.ENGLISH
                || !canonicalLabels.contains(" + ")) {
            return bind(canonicalLabels);
        }
        String[] labels = canonicalLabels.split(" \\+ ");
        StringBuilder translated = new StringBuilder(canonicalLabels.length());
        for (String label : labels) {
            if (!translated.isEmpty()) {
                translated.append(" + ");
            }
            translated.append(bind(label));
        }
        return translated.toString();
    }

    public static String friendCount(int count) {
        if (UiLanguage.current() == UiLanguage.ENGLISH) {
            return count + (count == 1 ? " Friend" : " Friends");
        }
        int lastTwo = Math.floorMod(count, 100);
        int last = Math.floorMod(count, 10);
        String noun = lastTwo >= 11 && lastTwo <= 14
                ? " друзей"
                : last == 1 ? " друг" : last >= 2 && last <= 4 ? " друга" : " друзей";
        return count + noun;
    }

    public static String friendStatus(boolean online, long lastSeenMillis) {
        if (online) {
            return UiLanguage.current() == UiLanguage.RUSSIAN ? "Сейчас в игре" : "Playing now";
        }
        if (lastSeenMillis <= 0L) {
            return UiLanguage.current() == UiLanguage.RUSSIAN
                    ? "Ещё не играли вместе"
                    : "Never played together";
        }
        long seconds = Math.max(0L, (System.currentTimeMillis() - lastSeenMillis) / 1000L);
        if (seconds < 60L) {
            return UiLanguage.current() == UiLanguage.RUSSIAN
                    ? "Играли только что"
                    : "Last played just now";
        }
        if (seconds < 3_600L) {
            long minutes = seconds / 60L;
            return UiLanguage.current() == UiLanguage.RUSSIAN
                    ? "Играли " + minutes + " мин. назад"
                    : "Last played " + minutes + "m ago";
        }
        if (seconds < 86_400L) {
            long hours = seconds / 3_600L;
            return UiLanguage.current() == UiLanguage.RUSSIAN
                    ? "Играли " + hours + " ч назад"
                    : "Last played " + hours + "h ago";
        }
        long days = seconds / 86_400L;
        return UiLanguage.current() == UiLanguage.RUSSIAN
                ? "Играли " + days + " дн. назад"
                : "Last played " + days + "d ago";
    }

    private static String translate(Map<String, String> russian, String english) {
        if (english == null || UiLanguage.current() == UiLanguage.ENGLISH) {
            return english;
        }
        return russian.getOrDefault(english, english);
    }
}
