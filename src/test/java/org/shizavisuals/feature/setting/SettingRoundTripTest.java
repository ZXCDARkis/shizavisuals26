package org.shizavisuals.feature.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence contract: every setting type must survive write() -> read()
 * into a fresh instance (configs are saved and loaded through exactly that
 * path), plus the normalization rules applied by setValue (clamping,
 * snapping, canonicalization).
 */
class SettingRoundTripTest {

    /** Serializes through JSON text to mirror what the config store does. */
    private static JsonElement roundTripJson(Setting<?> setting) {
        return JsonParser.parseString(setting.write().toString());
    }

    @Test
    void booleanSettingRoundTrip() {
        BooleanSetting setting = new BooleanSetting("Enabled", false);
        setting.setValue(true);

        BooleanSetting fresh = new BooleanSetting("Enabled", false);
        fresh.read(roundTripJson(setting));
        assertEquals(Boolean.TRUE, fresh.getValue());
    }

    @Test
    void numberSettingRoundTrip() {
        NumberSetting setting = new NumberSetting("Speed", 1.0, 0.0, 10.0, 0.5, "x");
        setting.setValue(7.5);

        NumberSetting fresh = new NumberSetting("Speed", 1.0, 0.0, 10.0, 0.5, "x");
        fresh.read(roundTripJson(setting));
        assertEquals(7.5, fresh.getValue());
    }

    @Test
    void numberSettingClampsToRange() {
        NumberSetting setting = new NumberSetting("Speed", 1.0, 0.0, 10.0, 0.5, "x");

        setting.setValue(99.0);
        assertEquals(10.0, setting.getValue(), "values above max must clamp to max");

        setting.setValue(-99.0);
        assertEquals(0.0, setting.getValue(), "values below min must clamp to min");
    }

    @Test
    void numberSettingSnapsToStep() {
        NumberSetting setting = new NumberSetting("Speed", 1.0, 0.0, 10.0, 0.5, "x");

        setting.setValue(3.24);
        assertEquals(3.0, setting.getValue(), "3.24 snaps down to the nearest 0.5 step");

        setting.setValue(3.26);
        assertEquals(3.5, setting.getValue(), "3.26 snaps up to the nearest 0.5 step");
    }

    @Test
    void numberSettingWithoutStepOnlyClamps() {
        NumberSetting setting = new NumberSetting("Alpha", 0.5, 0.0, 1.0, 0.0, "");
        setting.setValue(0.123456);
        assertEquals(0.123456, setting.getValue());
    }

    @Test
    void modeSettingRoundTrip() {
        ModeSetting setting = new ModeSetting("Mode", "First", "First", "Second", "Third");
        setting.setValue("Second");

        ModeSetting fresh = new ModeSetting("Mode", "First", "First", "Second", "Third");
        fresh.read(roundTripJson(setting));
        assertEquals("Second", fresh.getValue());
    }

    @Test
    void modeSettingNormalizesCaseAndRejectsUnknownModes() {
        ModeSetting setting = new ModeSetting("Mode", "First", "First", "Second");

        setting.setValue("second");
        assertEquals("Second", setting.getValue(), "setValue must canonicalize mode casing");

        assertThrows(IllegalArgumentException.class, () -> setting.setValue("Nope"));
    }

    @Test
    void modeSettingReadFallsBackToDefaultForUnknownMode() {
        ModeSetting setting = new ModeSetting("Mode", "First", "First", "Second");
        setting.read(new JsonPrimitive("DoesNotExist"));
        assertEquals("First", setting.getValue());
    }

    @Test
    void colorSettingRoundTrip() {
        ColorSetting setting = new ColorSetting("Color", 0xFF112233);
        setting.setValue(0xFF3355AA);

        ColorSetting fresh = new ColorSetting("Color", 0xFF112233);
        fresh.read(roundTripJson(setting));
        assertEquals(0xFF3355AA, fresh.getValue());
    }

    @Test
    void colorSettingForcesOpaqueAlpha() {
        ColorSetting setting = new ColorSetting("Color", 0x00112233);
        assertEquals(0xFF112233, setting.getValue(), "default value must be normalized to alpha 255");

        setting.setValue(0x22334455);
        assertEquals(0xFF334455, setting.getValue(), "setValue must force alpha 255");
    }

    @Test
    void textSettingRoundTrip() {
        TextSetting setting = new TextSetting("Name", "initial");
        setting.setValue("changed text");

        TextSetting fresh = new TextSetting("Name", "initial");
        fresh.read(roundTripJson(setting));
        assertEquals("changed text", fresh.getValue());
    }

    @Test
    void textSettingTruncatesToMaxLength() {
        TextSetting setting = new TextSetting("Name", "abc", 5);
        setting.setValue("abcdefgh");
        assertEquals("abcde", setting.getValue());
    }

    @Test
    void multiSelectSettingRoundTrip() {
        MultiSelectSetting setting = new MultiSelectSetting(
                "Options", List.of("Alpha"), "Alpha", "Beta", "Gamma");
        setting.setValue(Set.of("Beta", "Gamma"));

        MultiSelectSetting fresh = new MultiSelectSetting(
                "Options", List.of("Alpha"), "Alpha", "Beta", "Gamma");
        fresh.read(roundTripJson(setting));
        assertEquals(Set.of("Beta", "Gamma"), fresh.getValue());
        assertTrue(fresh.isSelected("Beta"));
        assertFalse(fresh.isSelected("Alpha"));
    }

    @Test
    void multiSelectSettingCanonicalizesCaseAndRejectsUnknownOptions() {
        MultiSelectSetting setting = new MultiSelectSetting(
                "Options", List.of(), "Alpha", "Beta");

        setting.setValue(Set.of("alpha", "BETA"));
        assertEquals(Set.of("Alpha", "Beta"), setting.getValue());

        assertThrows(IllegalArgumentException.class, () -> setting.setValue(Set.of("Unknown")));
    }

    @Test
    void bindSettingRoundTrip() {
        BindSetting setting = new BindSetting("Bind");
        setting.setValue(List.of(BindSetting.key(65), BindSetting.mouse(1)));

        BindSetting fresh = new BindSetting("Bind");
        fresh.read(roundTripJson(setting));
        assertEquals(List.of(BindSetting.key(65), BindSetting.mouse(1)), fresh.getValue());
        assertTrue(fresh.matches(65));
        assertTrue(fresh.matchesMouse(1));
    }

    @Test
    void bindSettingNormalizesDuplicatesAndUnbound() {
        BindSetting setting = new BindSetting("Bind");
        setting.setValue(List.of(65, 65, BindSetting.UNBOUND, 66));
        assertEquals(List.of(65, 66), setting.getValue(),
                "duplicates and unbound entries must be dropped, order preserved");
    }

    @Test
    void inputBindSettingRoundTrip() {
        InputBindSetting setting = new InputBindSetting("Key", InputBindSetting.UNBOUND);
        setting.setKey(74);

        InputBindSetting fresh = new InputBindSetting("Key", InputBindSetting.UNBOUND);
        fresh.read(roundTripJson(setting));
        assertEquals(74, fresh.getValue());
        assertTrue(fresh.matches(74));
    }

    @Test
    void inputBindSettingNormalizesNegativeToUnbound() {
        InputBindSetting setting = new InputBindSetting("Key", 65);
        setting.setValue(-42);
        assertEquals(InputBindSetting.UNBOUND, setting.getValue());
    }

    @Test
    void buttonSettingRunsActionAndIsNotPersistent() {
        AtomicInteger presses = new AtomicInteger();
        ButtonSetting setting = new ButtonSetting(
                "Reset Position", "Reset", presses::incrementAndGet);

        setting.press();

        assertEquals(1, presses.get());
        assertFalse(setting.isPersistent());
    }
}
