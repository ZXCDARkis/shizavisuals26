package org.shizavisuals.menu.i18n;

import org.shizavisuals.feature.setting.NumberSetting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuTextTest {
    @AfterEach
    void resetLanguage() {
        UiLanguage.set(UiLanguage.ENGLISH);
    }

    @Test
    void canonicalValuesStayEnglishByDefault() {
        assertEquals("Attack Range", MenuText.setting("Attack Range"));
        assertEquals("Players", MenuText.option("Players"));
    }

    @Test
    void russianTranslatesSettingsOptionsAndSuffixes() {
        UiLanguage.set(UiLanguage.RUSSIAN);

        assertEquals("Поправка к дальности атаки", MenuText.setting("Attack Range"));
        assertEquals("Игроки без брони", MenuText.option("Naked Players"));
        assertEquals("3.00 блоков", MenuText.numberValue(
                new NumberSetting("Distance", 3.0, 0.0, 10.0, 1.0, " blocks")
        ));
    }

    @Test
    void legacyRussianServerActionsTranslateBackToEnglish() {
        assertEquals("Disorientation", MenuText.setting("Дезориентация"));
        assertEquals("Enhanced Strength Potion", MenuText.setting("Улучшенное зелье силы"));
    }

    @Test
    void contextualNamesAndGeneratedBindsAreTranslatedPrecisely() {
        UiLanguage.set(UiLanguage.RUSSIAN);

        assertEquals("Эффект", MenuText.setting("WorldTweaks", "Effect"));
        assertEquals("Длительность взмаха", MenuText.setting("SwingAnimation", "Speed"));
        assertEquals("ЛКМ + Пробел", MenuText.bindCombination("Mouse Left + Space"));
        assertEquals("Мышь 8", MenuText.bind("Mouse 8"));
    }

    @Test
    void stableLanguageKeysDoNotDependOnEnumOrder() {
        assertEquals(UiLanguage.RUSSIAN, UiLanguage.byStorageKey("russian"));
        assertEquals(UiLanguage.ENGLISH, UiLanguage.byStorageKey("unknown"));
    }

    @Test
    void featureDescriptionsAndPageTextTranslate() {
        UiLanguage.set(UiLanguage.RUSSIAN);

        assertEquals("Автоматически атакует сущностей вокруг вас.",
                MenuText.featureDescription("Automatically attacks entities around you."));
        assertEquals("Аккаунты", MenuText.ui("Accounts"));
        assertEquals("2 друга", MenuText.friendCount(2));
        assertEquals("21 друг", MenuText.friendCount(21));
        assertEquals("Не использовался", MenuText.ui("Never played"));
    }

    @Test
    void pveDescriptionsSettingsModesAndUnitsAreTranslated() {
        UiLanguage.set(UiLanguage.RUSSIAN);

        assertEquals("Общие настройки сервера, профиля, ротации и безопасности PvE",
                MenuText.featureDescription(
                        "Shared server, identity, rotation and safety settings for PvE"
                ));
        assertEquals("Автоматизирует маршруты по городам вардена, сундуки, запасы и продажу",
                MenuText.featureDescription(
                        "Automates Warden-city chest routes, storage, supplies and selling"
                ));
        assertEquals("Минимальная прочность инструмента",
                MenuText.setting("Minimum Tool Durability"));
        assertEquals("Анархии для лута", MenuText.setting("Loot Anarchies"));
        assertEquals("Режим выгрузки", MenuText.setting("Deposit Mode"));
        assertEquals("Приоритет руды", MenuText.option("Ore Priority"));
        assertEquals("Запрос и отправка", MenuText.option("Request & Send"));
        assertEquals("Подключить", MenuText.ui("Connect"));
        assertEquals("Состояние PvE", MenuText.ui("PvE State"));
        assertEquals("Добыто руд", MenuText.ui("Mined ores"));
        assertEquals("Текущее состояние", MenuText.setting("Current State"));
        assertEquals("4.00 чанков", MenuText.numberValue(
                new NumberSetting("Chunk Step", 4.0, 1.0, 8.0, 1.0, " chunks")
        ));
    }
}
