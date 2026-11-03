package org.shizavisuals.menu.ps4;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.core.MenuConfigStore;
import org.shizavisuals.menu.ps5.Ps5SkinCache;
import org.shizavisuals.menu.ui.AnimatedValue;
import org.shizavisuals.utils.AccountSwitcher;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.gui.Render2DUtil;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1:1 port of the PS4 (ShizaVisuals PS4) main menu: themed wallpaper with drifting
 * light particles, the top status bar (vanilla-menu switch, profile pill,
 * clock, volume widget), the horizontal tile carousel with sub-cards and the
 * same keyboard navigation flow, plus the accounts overlay.
 */
public final class Ps4MainMenuScreen extends Screen {
    private boolean accountsOpen;
    private final AnimatedValue accountsAnim = new AnimatedValue(260L, Animation.Easing.EASE_OUT_QUAD);
    private final Map<String, AnimatedValue> hoverAnims = new HashMap<>();
    private final List<ClickZone> clickZones = new ArrayList<>();
    private boolean fastIntro;
    private long screenOpenTime = System.currentTimeMillis();
    private final Set<String> activeHovers = new HashSet<>();

    private List<PsAccount> accounts = new ArrayList<>();
    private boolean accountAddMode;
    private String accountInput = "";
    private float accountsScroll;

    private int selectedRow = 1; // 0 = top bar, 1 = main ribbon, 2 = sub cards
    private int selectedTile = 1; // Default to Сетевая игра like console UX, PS4 default Одиночная (0)
    private int selectedSub;
    private int selectedTop;
    private float currentScroll;

    private boolean isDraggingVolume;
    private float volumeTrackX;
    private float volumeTrackW;
    private float drawStateAlpha = 1.0F;

    private record SubCard(String title, String desc, net.minecraft.resources.Identifier icon, Runnable action) {
    }

    private record Ps4Tile(String title, String subtitle, net.minecraft.resources.Identifier icon,
                           Runnable primaryAction, List<SubCard> subCards) {
    }

    private record ClickZone(float x, float y, float w, float h, Runnable action) {
    }

    private record PsAccount(String name, String activity, int avatarIndex) {
    }

    public Ps4MainMenuScreen(boolean fastIntro) {
        super(Component.literal("ShizaVisuals"));
        this.fastIntro = fastIntro;
    }

    public Ps4MainMenuScreen() {
        this(false);
    }

    private String welcomeName() {
        Minecraft client = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        String name = client.getUser() != null ? client.getUser().getName() : "";
        return name.isBlank() ? "Player" : name;
    }

    private int paint(int color) {
        return ColorUtil.multiplyAlpha(color, this.drawStateAlpha);
    }

    private int paint(int color, float extra) {
        return ColorUtil.multiplyAlpha(color, this.drawStateAlpha * Math.max(0.0F, Math.min(1.0F, extra)));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        MenuSettings.load();
        Ps4Sound.startAmbientMusic();
        if (System.currentTimeMillis() - this.screenOpenTime > 600L) {
            this.screenOpenTime = System.currentTimeMillis();
            this.fastIntro = false;
            this.accountsOpen = false;
            this.accountAddMode = false;
            this.accountsAnim.toward(0.0F);
        }
        if (this.accounts.isEmpty()) {
            this.loadAccounts();
        }
    }

    private void loadAccounts() {
        List<PsAccount> loaded = new ArrayList<>();
        JsonArray saved = MenuConfigStore.getArray("accounts");
        if (saved != null) {
            for (JsonElement element : saved) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject account = element.getAsJsonObject();
                String name = account.has("name") ? account.get("name").getAsString() : "";
                if (!name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                    continue;
                }
                String activity = account.has("activity") ? account.get("activity").getAsString() : "Профиль";
                int avatar = account.has("avatar") ? account.get("avatar").getAsInt() : loaded.size();
                loaded.add(new PsAccount(name, activity, avatar));
            }
        }
        if (loaded.isEmpty()) {
            loaded.add(new PsAccount(this.welcomeName(), "Играет сейчас", 0));
        }
        this.accounts = loaded;
    }

    private String selectedAccountName() {
        String saved = MenuConfigStore.getString("selectedAccount", "");
        if (!saved.isBlank()) {
            return saved;
        }
        return this.welcomeName();
    }

    private void persistAccounts() {
        JsonArray array = new JsonArray();
        for (PsAccount account : this.accounts) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", account.name());
            obj.addProperty("activity", account.activity());
            obj.addProperty("avatar", account.avatarIndex());
            array.add(obj);
        }
        String selected = MenuConfigStore.getString("selectedAccount", "");
        MenuConfigStore.save(data -> {
            data.add("accounts", array);
            data.addProperty("selectedAccount", selected);
        });
    }

    private void switchAccount(PsAccount account) {
        MenuConfigStore.save(data -> data.addProperty("selectedAccount", account.name()));
        AccountSwitcher.switchTo(account.name());
        AccountSwitcher.applyStartupAccount();
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // Persist once per drag instead of writing the config file on every mouse move.
        if (this.isDraggingVolume) {
            this.isDraggingVolume = false;
            MenuSettings.captureMenuVolume(Ps4Sound.getMenuVolume());
            MenuSettings.save();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.isDraggingVolume && this.volumeTrackW > 0.0F) {
            float mx = (float) event.x();
            float frac = Math.max(0.0F, Math.min(1.0F, (mx - this.volumeTrackX) / this.volumeTrackW));
            Ps4Sound.updateMenuVolume(frac);
            if (Ps4Sound.isAmbientMuted() && frac > 0.0F) {
                Ps4Sound.toggleAmbientMuted();
            }
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            float mx = (float) event.x();
            float my = (float) event.y();

            if (this.accountsOpen && this.accountsAnim.value() > 0.5F) {
                this.handleAccountsClick(mx, my);
                return true;
            }

            if (this.volumeTrackW > 0.0F
                    && mx >= this.volumeTrackX - 3.0F && mx <= this.volumeTrackX + this.volumeTrackW + 3.0F
                    && my >= 10.0F && my <= 38.0F) {
                this.isDraggingVolume = true;
                float frac = Math.max(0.0F, Math.min(1.0F, (mx - this.volumeTrackX) / this.volumeTrackW));
                MenuSettings.setMenuVolume(frac);
                if (Ps4Sound.isAmbientMuted() && frac > 0.0F) {
                    Ps4Sound.toggleAmbientMuted();
                }
                Ps4Sound.playUiSound("ps4_select");
                return true;
            }

            for (int index = this.clickZones.size() - 1; index >= 0; index--) {
                ClickZone zone = this.clickZones.get(index);
                if (Ps4Helpers.inRect(mx, my, zone.x(), zone.y(), zone.w(), zone.h())) {
                    Ps4Sound.playUiSound("ps4_select");
                    zone.action().run();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float mx = (float) mouseX;
        float my = (float) mouseY;

        if (this.accountsOpen && this.accountsAnim.value() > 0.5F) {
            this.scrollAccounts(verticalAmount);
            return true;
        }

        if (this.volumeTrackW > 0.0F
                && mx >= this.volumeTrackX - 25.0F && mx <= this.volumeTrackX + this.volumeTrackW + 35.0F
                && my >= 10.0F && my <= 45.0F) {
            float cur = Ps4Sound.getMenuVolume();
            float delta = verticalAmount > 0 ? 0.05F : -0.05F;
            float next = Math.max(0.0F, Math.min(1.0F, cur + delta));
            Ps4Sound.setMenuVolume(next);
            if (Ps4Sound.isAmbientMuted() && next > 0.0F) {
                Ps4Sound.toggleAmbientMuted();
            }
            Ps4Sound.playUiSound("ps4_scroll");
            return true;
        }

        List<Ps4Tile> tiles = this.getTiles();
        if (verticalAmount > 0.2) {
            this.selectTile(Math.max(0, this.selectedTile - 1));
            return true;
        } else if (verticalAmount < -0.2) {
            this.selectTile(Math.min(tiles.size() - 1, this.selectedTile + 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void scrollAccounts(double dir) {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        float scale = Math.min(this.width / 960.0F, this.height / 540.0F);
        float rowH = 34.0F * scale;
        float visible = this.accountsListHeight(scale) / rowH;
        float maxScroll = Math.max(0.0F, this.accounts.size() + 1 - visible) * rowH;
        this.accountsScroll = Math.max(0.0F, Math.min(maxScroll, this.accountsScroll - (float) dir * rowH));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();

        if (this.accountsOpen) {
            if (this.accountAddMode) {
                if (k == GLFW.GLFW_KEY_ESCAPE) {
                    this.accountAddMode = false;
                    this.accountInput = "";
                    Ps4Sound.playUiSound("ps4_scroll");
                    return true;
                }
                if (k == GLFW.GLFW_KEY_BACKSPACE && !this.accountInput.isEmpty()) {
                    this.accountInput = this.accountInput.substring(0, this.accountInput.length() - 1);
                    return true;
                }
                if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
                    this.saveNewAccount();
                    return true;
                }
                return true;
            }
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                this.accountsOpen = false;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
            return true;
        }

        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        List<Ps4Tile> tiles = this.getTiles();

        if (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_A) {
            if (this.selectedRow == 1) {
                this.selectTile(Math.max(0, this.selectedTile - 1));
            } else if (this.selectedRow == 2) {
                this.selectSub(Math.max(0, this.selectedSub - 1));
            } else if (this.selectedRow == 0) {
                this.selectTop(Math.max(0, this.selectedTop - 1));
            }
            return true;
        }

        if (k == GLFW.GLFW_KEY_RIGHT || k == GLFW.GLFW_KEY_D) {
            if (this.selectedRow == 1) {
                this.selectTile(Math.min(tiles.size() - 1, this.selectedTile + 1));
            } else if (this.selectedRow == 2) {
                int maxSub = tiles.get(this.selectedTile).subCards().size() - 1;
                this.selectSub(Math.min(maxSub, this.selectedSub + 1));
            } else if (this.selectedRow == 0) {
                this.selectTop(Math.min(2, this.selectedTop + 1));
            }
            return true;
        }

        if (k == GLFW.GLFW_KEY_DOWN || k == GLFW.GLFW_KEY_S) {
            if (this.selectedRow == 0) {
                this.selectedRow = 1;
                Ps4Sound.playUiSound("ps4_scroll");
            } else if (this.selectedRow == 1) {
                this.selectedRow = 2;
                this.selectedSub = 0;
                Ps4Sound.playUiSound("ps4_scroll");
            }
            return true;
        }

        if (k == GLFW.GLFW_KEY_UP || k == GLFW.GLFW_KEY_W) {
            if (this.selectedRow == 2) {
                this.selectedRow = 1;
                Ps4Sound.playUiSound("ps4_scroll");
            } else if (this.selectedRow == 1) {
                this.selectedRow = 0;
                Ps4Sound.playUiSound("ps4_scroll");
            }
            return true;
        }

        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER || k == GLFW.GLFW_KEY_SPACE) {
            Ps4Sound.playUiSound("ps4_select");
            if (this.selectedRow == 1 && this.selectedTile >= 0 && this.selectedTile < tiles.size()) {
                tiles.get(this.selectedTile).primaryAction().run();
            } else if (this.selectedRow == 2 && this.selectedTile >= 0 && this.selectedTile < tiles.size()) {
                List<SubCard> subs = tiles.get(this.selectedTile).subCards();
                if (this.selectedSub >= 0 && this.selectedSub < subs.size()) {
                    subs.get(this.selectedSub).action().run();
                }
            } else if (this.selectedRow == 0) {
                this.runTopAction(mc, this.selectedTop);
            }
            return true;
        }

        if (k == GLFW.GLFW_KEY_ESCAPE && this.selectedRow != 1) {
            this.selectedRow = 1;
            Ps4Sound.playUiSound("ps4_scroll");
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.accountsOpen && this.accountAddMode) {
            int codePoint = event.codepoint();
            if (codePoint >= 32 && codePoint != 127 && this.accountInput.length() < 16) {
                this.accountInput += new String(Character.toChars(codePoint));
                return true;
            }
            return true;
        }
        return super.charTyped(event);
    }

    private void saveNewAccount() {
        String name = this.accountInput.trim();
        if (!name.matches("^[a-zA-Z0-9_]{3,16}$")) {
            return;
        }
        boolean exists = this.accounts.stream().anyMatch(a -> a.name().equalsIgnoreCase(name));
        if (!exists) {
            this.accounts.add(new PsAccount(name, "Профиль", this.accounts.size()));
        }
        MenuConfigStore.save(data -> data.addProperty("selectedAccount", name));
        AccountSwitcher.switchTo(name);
        this.accountAddMode = false;
        this.accountInput = "";
        Ps4Sound.playUiSound("ps4_select");
    }

    private void runTopAction(Minecraft mc, int index) {
        switch (index) {
            case 0 -> this.switchToVanillaMenu(mc);
            case 1 -> this.toggleAccounts();
            case 2 -> {
                float next = nextVolumeStep();
                Ps4Sound.setMenuVolume(next);
                if (next > 0.0F && Ps4Sound.isAmbientMuted()) {
                    Ps4Sound.toggleAmbientMuted();
                }
            }
            default -> {
            }
        }
    }

    private float nextVolumeStep() {
        float cur = Ps4Sound.getMenuVolume();
        return cur >= 0.95F ? 0.75F : (cur >= 0.70F ? 0.50F : (cur >= 0.45F ? 0.25F : (cur >= 0.20F ? 0.0F : 1.0F)));
    }

    private void switchToVanillaMenu(Minecraft mc) {
        MenuSettings.setUseVanillaMenu(true);
        Ps4Sound.stopAmbientMusic();
        mc.setScreenAndShow(new TitleScreen());
    }

    private void selectTile(int index) {
        if (this.selectedTile != index) {
            this.selectedTile = index;
            this.selectedSub = 0;
            Ps4Sound.playUiSound("ps4_scroll");
        }
    }

    private void selectSub(int index) {
        if (this.selectedSub != index) {
            this.selectedSub = index;
            Ps4Sound.playUiSound("ps4_scroll");
        }
    }

    private void selectTop(int index) {
        if (this.selectedTop != index) {
            this.selectedTop = index;
            Ps4Sound.playUiSound("ps4_scroll");
        }
    }

    private List<Ps4Tile> cachedTiles;
    private String cachedWallLabel;
    private float cachedMenuVol = -1.0F;

    private List<Ps4Tile> getTiles() {
        String wallLabel = Ps4Wallpapers.label();
        float menuVol = Ps4Sound.getMenuVolume();
        if (this.cachedTiles == null
                || !wallLabel.equals(this.cachedWallLabel)
                || menuVol != this.cachedMenuVol) {
            this.cachedWallLabel = wallLabel;
            this.cachedMenuVol = menuVol;
            this.cachedTiles = buildTiles(wallLabel, (int) Math.round(menuVol * 100.0F));
        }
        return this.cachedTiles;
    }

    private List<Ps4Tile> buildTiles(String wallLabel, int menuVolPct) {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        List<Ps4Tile> list = new ArrayList<>();

        // 1. ОДИНОЧНАЯ ИГРА
        list.add(new Ps4Tile(
                "ОДИНОЧНАЯ ИГРА",
                "Локальный мир, одиночное выживание и творчество",
                Ps4Textures.ICON_SINGLEPLAYER,
                () -> mc.setScreenAndShow(new Ps4SingleplayerScreen(this)),
                List.of(
                        new SubCard("Список миров", "Все сохраненные миры", Ps4Textures.ICON_SINGLEPLAYER, () -> mc.setScreenAndShow(new Ps4SingleplayerScreen(this))),
                        new SubCard("Создать мир", "Новое приключение", Ps4Textures.ICON_PLUS, () -> CreateWorldScreen.openFresh(mc, () -> mc.setScreenAndShow(this))),
                        new SubCard("Параметры графики", "Настройки видео", Ps4Textures.ICON_SETTINGS, () -> mc.setScreenAndShow(new VideoSettingsScreen(this, mc, mc.options)))
                )
        ));

        // 2. СЕТЕВАЯ ИГРА
        list.add(new Ps4Tile(
                "СЕТЕВАЯ ИГРА",
                "Подключайтесь к серверам и играйте с другими",
                Ps4Textures.ICON_GRASS,
                () -> mc.setScreenAndShow(new Ps4MultiplayerScreen(this)),
                List.of(
                        new SubCard("Список серверов", "Все добавленные серверы", Ps4Textures.ICON_GRASS, () -> mc.setScreenAndShow(new Ps4MultiplayerScreen(this))),
                        new SubCard("Добавить сервер", "Ввести новый адрес", Ps4Textures.ICON_PLUS, () -> mc.setScreenAndShow(new Ps4MultiplayerScreen(this, true)))
                )
        ));

        // 3. УЧЁТНЫЕ ЗАПИСИ
        list.add(new Ps4Tile(
                "УЧЁТНЫЕ ЗАПИСИ",
                "Быстрое переключение профилей и добавление новых аккаунтов",
                Ps4Textures.ICON_ACCOUNTS,
                this::toggleAccounts,
                List.of(
                        new SubCard("Сменить игрока", "Открыть менеджер аккаунтов", Ps4Textures.ICON_ACCOUNTS, this::toggleAccounts),
                        new SubCard("Случайный ник", "Добавить аккаунт со случайным ником", Ps4Textures.ICON_DICE, () -> {
                            this.accountInput = randomNickname();
                            this.accountAddMode = true;
                            this.accountsOpen = true;
                        })
                )
        ));

        // 4. НАСТРОЙКИ
        list.add(new Ps4Tile(
                "НАСТРОЙКИ",
                "Параметры графики, звука, интерфейса и управления",
                Ps4Textures.ICON_SETTINGS,
                () -> mc.setScreenAndShow(new Ps4SettingsScreen(this)),
                List.of(
                        new SubCard("Настройки системы", "Полный список настроек PS4", Ps4Textures.ICON_SETTINGS, () -> mc.setScreenAndShow(new Ps4SettingsScreen(this))),
                        new SubCard("Обои: " + wallLabel, "Клик для смены обоев меню", Ps4Textures.ICON_PERSON, () -> {
                            Ps4Wallpapers.next();
                            Ps4Sound.playUiSound("ps4_scroll");
                        }),
                        new SubCard("Свои обои", "Листает обои из папки пользователя", Ps4Textures.ICON_PLUS, () -> {
                            if (Ps4Wallpapers.customCount() == 0 || !Ps4Wallpapers.nextCustom()) {
                                Ps4Wallpapers.openCustomFolder();
                            }
                        }),
                        new SubCard("Папка с обоями", "config/shizavisuals/backgrounds", Ps4Textures.ICON_GRASS, Ps4Wallpapers::openCustomFolder),
                        new SubCard("Параметры графики", "Видео и шейдеры", Ps4Textures.ICON_SETTINGS, () -> mc.setScreenAndShow(new VideoSettingsScreen(this, mc, mc.options))),
                        new SubCard("Настройки звука", "Громкость и каналы", Ps4Textures.ICON_MUSIC_ON, () -> mc.setScreenAndShow(new SoundOptionsScreen(this, mc.options))),
                        new SubCard("Громкость меню: " + menuVolPct + "%", "Клик: 100% -> 75% -> 50% -> 25% -> 0%", Ps4Textures.ICON_MUSIC_ON, () -> {
                            Ps4Sound.playUiSound("ps4_select");
                            float next = this.nextVolumeStep();
                            Ps4Sound.setMenuVolume(next);
                            if (next > 0.0F && Ps4Sound.isAmbientMuted()) {
                                Ps4Sound.toggleAmbientMuted();
                            }
                        })
                )
        ));

        // 5. ВЫХОД ИЗ ИГРЫ
        list.add(new Ps4Tile(
                "ВЫХОД ИЗ ИГРЫ",
                "Завершить работу клиента и закрыть Minecraft",
                Ps4Textures.ICON_EXIT,
                mc::stop,
                List.of(
                        new SubCard("Выключить систему", "Закрыть Minecraft", Ps4Textures.ICON_EXIT, mc::stop),
                        new SubCard("Свернуть окно", "Свернуть в панель задач", Ps4Textures.ICON_CHEVRON, () ->
                                GLFW.glfwIconifyWindow(mc.getWindow().handle()))
                )
        ));

        return list;
    }

    private void toggleAccounts() {
        this.loadAccounts();
        this.accountsOpen = !this.accountsOpen;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float screenW = this.width;
        float screenH = this.height;
        float scale = Math.min(screenW / 960.0F, screenH / 540.0F);
        float mx = (float) mouseX;
        float my = (float) mouseY;

        this.clickZones.clear();
        RenderContext.enter2D(this.minecraft.gui, graphics, this.minecraft.getDeltaTracker());
        try {
            Render2DUtil.beginFrame();
            Render2DUtil.setBackdropBlurScale(1.0F);

            this.accountsAnim.toward(this.accountsOpen ? 1.0F : 0.0F);
            float accountsAnim = this.accountsAnim.value();

            float introAlpha = Ps4Helpers.easeOut(
                    Ps4Helpers.progress(this.screenOpenTime, this.fastIntro ? 150L : 0L, this.fastIntro ? 500L : 350L));
            this.drawStateAlpha = introAlpha;

            // 1. Wallpaper (PS4 wave, customizable).
            Ps4Helpers.coverWallpaper(Ps4Wallpapers.texture(), screenW, screenH, 1.0F);

            // 2. Subtle vignette.
            Ps4Helpers.rect(0.0F, 0.0F, screenW, 36.0F * scale, 0.0F, paint(Ps4Helpers.tint(0xFF000000, 0.22F)));
            Ps4Helpers.rect(0.0F, screenH - 42.0F * scale, screenW, 42.0F * scale, 0.0F, paint(Ps4Helpers.tint(0xFF000000, 0.32F)));

            // 3. PS4 ambient flowing light particles (fewer, cheaper).
            if (introAlpha > 0.02F) {
                long now = System.currentTimeMillis();
                for (int p = 0; p < 16; p++) {
                    float seed = (float) (p * 93.172);
                    float speed = 0.00018F + (p % 6) * 0.00007F;
                    float px = ((seed * 127.3F + now * speed * screenW) % screenW + screenW) % screenW;
                    float py = ((seed * 289.1F + (float) Math.sin((now * 0.0011) + seed) * 32.0F) % screenH + screenH) % screenH;
                    float pSize = (1.2F + (p % 3) * 0.9F) * scale;
                    float pAlpha = (0.20F + 0.25F * (float) Math.sin((now * 0.002) + seed * 2.0F)) * introAlpha;
                    Ps4Helpers.glow(px - pSize * 2.5F, py - pSize * 2.5F, pSize * 6.0F, pSize * 6.0F, pSize * 3.0F, pSize * 2.5F,
                            paint(Ps4Helpers.tint(Ps4Helpersrgba(0x00, 0xD2, 0xFF), pAlpha * 0.85F)));
                }
            }

            if (accountsAnim < 0.999F) {
                // Top function status bar.
                this.drawPs4TopBar(screenW, scale, mx, my);

                // Main horizontal ribbon.
                this.drawTileCarousel(screenW, screenH, scale, mx, my);
            }

            if (accountsAnim > 0.001F) {
                this.drawAccountsOverlay(screenW, screenH, scale, accountsAnim, mx, my);
            }

            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }
    }

    private static int Ps4Helpersrgba(int red, int green, int blue) {
        return Ps4Helpers.rgba(red, green, blue, 255);
    }

    /**
     * Top status bar: vanilla menu switch on the left, profile pill, real time
     * clock and volume widget on the right.
     */
    private void drawPs4TopBar(float screenW, float scale, float mouseX, float mouseY) {
        float topY = 20.0F * scale;

        // --- Left: return to vanilla Minecraft menu ---
        float leftX = 46.0F * scale;
        float vBtnW = 124.0F * scale;
        float vBtnH = 18.0F * scale;
        float vBtnX = leftX;
        float vBtnY = topY - 1.0F * scale;
        boolean vBtnHov = Ps4Helpers.inRect(mouseX, mouseY, vBtnX, vBtnY, vBtnW, vBtnH);
        float vHov = this.hoverPC("topVanillaBtn", vBtnHov);
        if (vHov > 0.01F || (this.selectedRow == 0 && this.selectedTop == 0)) {
            Ps4Helpers.rect(vBtnX, vBtnY, vBtnW, vBtnH, 2.0F * scale, paint(Ps4Helpers.tint(0xFFFFFFFF, 0.20F * 0.6F)));
            Ps4Helpers.outline(vBtnX, vBtnY, vBtnW, vBtnH, 2.0F * scale, 1.2F * scale,
                    paint(Ps4Helpers.tint(Ps4Helpers.WHITE, Math.max(vHov, 0.8F))));
        } else {
            Ps4Helpers.rect(vBtnX, vBtnY, vBtnW, vBtnH, 2.0F * scale, paint(Ps4Helpers.tint(0xFFFFFFFF, 0.133F * 0.4F)));
        }
        Ps4Helpers.icon(Ps4Textures.ICON_GRASS, vBtnX + 10.0F * scale, vBtnY + vBtnH * 0.5F,
                11.0F * scale, 11.0F * scale, paint(Ps4Helpers.WHITE));
        Ps4Helpers.text(Ps4Fonts.UI, "Стандартное меню", vBtnX + 20.0F * scale,
                vBtnY + vBtnH * 0.5F - 3.4F * scale, 7.5F * scale,
                paint(ColorUtil.multiplyAlpha(Ps4Helpers.tint(Ps4Helpers.WHITE, 0xCC / 255.0F), Math.max(0.6F, vHov))));
        if (vHov > 0.1F) {
            String tip = "Переключиться на стандартное меню Minecraft";
            Ps4Helpers.text(Ps4Fonts.UI, tip, vBtnX, vBtnY + vBtnH + 4.0F * scale, 5.8F * scale,
                    paint(Ps4Helpers.tint(Ps4Helpers.WHITE, vHov)));
        }
        this.clickZones.add(new ClickZone(vBtnX, vBtnY, vBtnW, vBtnH, () -> {
            Ps4Sound.playUiSound("ps4_select");
            this.switchToVanillaMenu(Minecraft.getInstance());
        }));

        // --- Right ---
        float rightEdge = screenW - 46.0F * scale;

        // 1. Menu volume widget.
        float volW = 95.0F * scale;
        float volH = 18.0F * scale;
        float volX = rightEdge - volW;
        float volY = topY - 1.0F * scale;
        boolean volHov = Ps4Helpers.inRect(mouseX, mouseY, volX, volY, volW, volH);
        float vHover = this.hoverPC("topVolume", volHov);
        if (vHover > 0.01F || (this.selectedRow == 0 && this.selectedTop == 2)) {
            Ps4Helpers.rect(volX, volY, volW, volH, 2.0F * scale, paint(Ps4Helpers.tint(0xFFFFFFFF, 0.20F * 0.6F)));
            Ps4Helpers.outline(volX, volY, volW, volH, 2.0F * scale, 1.2F * scale,
                    paint(Ps4Helpers.tint(Ps4Helpers.WHITE, Math.max(vHover, 0.8F))));
        } else {
            Ps4Helpers.rect(volX, volY, volW, volH, 2.0F * scale, paint(Ps4Helpers.tint(0xFFFFFFFF, 0.133F * 0.4F)));
        }

        boolean isMuted = Ps4Sound.isAmbientMuted();
        float curVol = Ps4Sound.getMenuVolume();

        // Speaker icon.
        float spkSize = 12.0F * scale;
        float spkX = volX + 5.0F * scale;
        float spkY = volY + (volH - spkSize) * 0.5F;
        int mCol = (isMuted || curVol <= 0.001F) ? paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0x88 / 255.0F))
                : paint(ColorUtil.multiplyAlpha(Ps4Helpers.tint(Ps4Helpers.WHITE, 0xCC / 255.0F), Math.max(0.6F, vHover)));
        Ps4Helpers.icon(isMuted || curVol <= 0.001F ? Ps4Textures.ICON_MUSIC_OFF : Ps4Textures.ICON_MUSIC_ON,
                spkX + spkSize * 0.5F, spkY + spkSize * 0.5F, spkSize, spkSize, mCol);
        this.clickZones.add(new ClickZone(spkX - 2.0F * scale, volY, spkSize + 4.0F * scale, volH, Ps4Sound::toggleAmbientMuted));

        // Slider track.
        float trackX = spkX + spkSize + 6.0F * scale;
        float trackW = 44.0F * scale;
        float trackH = 4.0F * scale;
        float trackY = volY + (volH - trackH) * 0.5F;
        this.volumeTrackX = trackX;
        this.volumeTrackW = trackW;

        Ps4Helpers.rect(trackX, trackY, trackW, trackH, 2.0F * scale, paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0x44 / 255.0F)));
        float fillRatio = isMuted ? 0.0F : curVol;
        float fillW = trackW * fillRatio;
        if (fillW > 0.0F) {
            Ps4Helpers.rect(trackX, trackY, fillW, trackH, 2.0F * scale, paint(Ps4Helpers.ACCENT));
        }
        float knobX = trackX + fillW;
        Ps4Helpers.rect(knobX - 2.5F * scale, trackY - 1.5F * scale, 5.0F * scale, 7.0F * scale, 2.0F * scale, paint(Ps4Helpers.WHITE));

        this.clickZones.add(new ClickZone(trackX - 2.0F * scale, volY, trackW + 4.0F * scale, volH, () -> {
            float frac = Math.max(0.0F, Math.min(1.0F, ((float) mouseX - trackX) / trackW));
            Ps4Sound.setMenuVolume(frac);
            if (Ps4Sound.isAmbientMuted()) {
                Ps4Sound.toggleAmbientMuted();
            }
        }));

        // Percentage text.
        String pctStr = isMuted ? "0%" : ((int) Math.round(curVol * 100.0F) + "%");
        Ps4Helpers.text(Ps4Fonts.UI, pctStr, trackX + trackW + 5.0F * scale, volY + volH * 0.5F - 3.2F * scale,
                6.5F * scale, paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0xEE / 255.0F)));

        if (vHover > 0.1F) {
            String volTip = "Громкость меню: " + ((int) Math.round(curVol * 100.0F)) + "% (Колесо мыши)";
            float vTipW = Ps4Helpers.measure(Ps4Fonts.UI, volTip, 5.8F * scale);
            Ps4Helpers.text(Ps4Fonts.UI, volTip, volX + volW - vTipW, volY + volH + 4.0F * scale, 5.8F * scale,
                    paint(Ps4Helpers.tint(Ps4Helpers.WHITE, vHover)));
        }

        // 2. Real-time digital clock (cached per second).
        String timeStr = Ps4Helpers.clockString();
        float timeW = Ps4Helpers.measure(Ps4Fonts.UI, timeStr, 8.0F * scale);
        float timeX = volX - timeW - 18.0F * scale;
        Ps4Helpers.text(Ps4Fonts.UI, timeStr, timeX, topY + 6.2F * scale, 8.0F * scale,
                paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0xEE / 255.0F)));

        // 3. User profile pill.
        String playerName = this.welcomeName();
        float nameW = Ps4Helpers.measure(Ps4Fonts.UI, playerName, 8.0F * scale);
        float profW = 24.0F * scale + nameW + 16.0F * scale;
        float profH = 18.0F * scale;
        float profX = timeX - profW - 18.0F * scale;
        float profY = topY - 1.0F * scale;

        boolean profHov = Ps4Helpers.inRect(mouseX, mouseY, profX, profY, profW, profH);
        float uHov = this.hoverPC("topProfile", profHov);

        if (uHov > 0.01F || (this.selectedRow == 0 && this.selectedTop == 1)) {
            Ps4Helpers.rect(profX, profY, profW, profH, 2.0F * scale, paint(Ps4Helpers.tint(0xFFFFFFFF, 0.20F * 0.6F)));
            Ps4Helpers.outline(profX, profY, profW, profH, 2.0F * scale, 1.2F * scale,
                    paint(Ps4Helpers.tint(Ps4Helpers.WHITE, Math.max(uHov, 0.8F))));
        } else {
            Ps4Helpers.rect(profX, profY, profW, profH, 2.0F * scale, paint(Ps4Helpers.tint(0xFFFFFFFF, 0.133F * 0.4F)));
        }

        // Square avatar inside the pill.
        float avSize = 13.0F * scale;
        float avX = profX + 4.0F * scale;
        float avY = profY + (profH - avSize) * 0.5F;
        this.drawNamedAvatar(this.welcomeName(), avX, avY, avSize);

        // Username.
        float txtX = avX + avSize + 6.0F * scale;
        Ps4Helpers.text(Ps4Fonts.UI, playerName, txtX, profY + profH * 0.5F - 3.4F * scale, 8.0F * scale, paint(Ps4Helpers.WHITE));

        // Gold plus badge right beside the username.
        float plusBadgeX = txtX + nameW + 4.0F * scale;
        int goldColor = Ps4Helpersrgba(0xF1, 0xC4, 0x0F);
        Ps4Helpers.icon(Ps4Textures.ICON_PLUS, plusBadgeX + 4.5F * scale, profY + profH * 0.5F,
                9.0F * scale, 9.0F * scale, goldColor);

        if (uHov > 0.1F) {
            String altTip = "Сменить профиль (Альт)";
            float aTipW = Ps4Helpers.measure(Ps4Fonts.UI, altTip, 5.8F * scale);
            Ps4Helpers.text(Ps4Fonts.UI, altTip, profX + profW * 0.5F - aTipW * 0.5F, profY + profH + 4.0F * scale,
                    5.8F * scale, paint(Ps4Helpers.tint(Ps4Helpers.WHITE, uHov)));
        }

        this.clickZones.add(new ClickZone(profX, profY, profW, profH, this::toggleAccounts));
    }

    private void drawNamedAvatar(String owner, float x, float y, float size) {
        net.minecraft.resources.Identifier skin = Ps5SkinCache.texture(
                this.minecraft != null ? this.minecraft : Minecraft.getInstance(), owner);
        Ps4Helpers.rect(x, y, size, size, 1.5F, paint(Ps4Helpersrgba(0x18, 0x1C, 0x26)));
        Render2DUtil.texture(x, y, size, size, skin).color(paint(Ps4Helpers.WHITE))
                .uv(0.125F, 0.125F, 0.25F, 0.25F).radius(size * 0.15F).draw();
        Ps4Helpers.outline(x, y, size, size, 1.5F, 0.8F, paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0x40 / 255.0F)));
    }

    /** Tile carousel: PS4 square tiles with focus frame and sub cards below. */
    private void drawTileCarousel(float screenW, float screenH, float scale, float mouseX, float mouseY) {
        List<Ps4Tile> tiles = this.getTiles();
        float startBaseX = 46.0F * scale;
        float ribbonY = 82.0F * scale;
        float normalTileSize = 78.0F * scale;
        float selectedTileSize = 92.0F * scale;
        float tileGap = 12.0F * scale;

        float targetScroll = this.selectedTile * (normalTileSize + tileGap);
        this.currentScroll += (targetScroll - this.currentScroll) * 0.22F;

        float curX = startBaseX - this.currentScroll;

        float focusedX = 0.0F;
        float focusedY = 0.0F;
        float focusedSize = selectedTileSize;

        for (int i = 0; i < tiles.size(); i++) {
            Ps4Tile tile = tiles.get(i);
            boolean isSelected = i == this.selectedTile;
            float curSize = isSelected ? selectedTileSize : normalTileSize;
            float tileX = curX;
            float tileY = ribbonY;
            curX += curSize + tileGap;

            if (isSelected) {
                focusedX = tileX;
                focusedY = tileY;
                focusedSize = curSize;
            }

            if (tileX + curSize < -50.0F || tileX > screenW + 50.0F) {
                continue;
            }

            boolean isHovered = Ps4Helpers.inRect(mouseX, mouseY, tileX, tileY, curSize, curSize);
            float hov = this.hoverPC("main_tile_" + i, isHovered);

            // Soft ambient drop shadow underneath the tile.
            Ps4Helpers.shadow(tileX + 2.0F * scale, tileY + 4.0F * scale, curSize, curSize, 0.0F,
                    12.0F * scale, paint(Ps4Helpers.tint(0xFF000000, 0.65F)));

            // Tile background (PS4 slate).
            int bgCol = isSelected ? Ps4Helpersrgba(0x14, 0x1E, 0x34)
                    : Ps4Helpers.lerp(Ps4Helpersrgba(0x10, 0x16, 0x26), Ps4Helpersrgba(0x1A, 0x24, 0x3A), hov);
            Ps4Helpers.rect(tileX, tileY, curSize, curSize, 0.0F, paint(bgCol));

            // Centered icon.
            float icSize = isSelected ? 38.0F * scale : 32.0F * scale;
            if (tile.icon() != null) {
                Ps4Helpers.icon(tile.icon(), tileX + curSize * 0.5F, tileY + curSize * 0.5F,
                        icSize, icSize, isSelected ? paint(Ps4Helpers.WHITE) : paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0xDD / 255.0F)));
            }

            // PS4 focus frame and downward chevron on the selected tile.
            if (isSelected) {
                long now = System.currentTimeMillis();
                float breath = 0.32F + 0.14F * (float) Math.sin(now * 0.0035);

                Ps4Helpers.glow(tileX, tileY, curSize, curSize, 0.0F, 18.0F * scale,
                        paint(Ps4Helpers.tint(Ps4Helpersrgba(0x00, 0x84, 0xFF), breath)));
                Ps4Helpers.glow(tileX, tileY, curSize, curSize, 0.0F, 8.0F * scale,
                        paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0.45F)));
                Ps4Helpers.outline(tileX, tileY, curSize, curSize, 0.0F, 2.6F * scale, paint(Ps4Helpers.WHITE));

                float bob = (float) Math.sin(now * 0.005) * 1.5F * scale;
                float chevSize = 9.0F * scale;
                float chevX = tileX + curSize * 0.5F - chevSize * 0.5F;
                float chevY = tileY + curSize + 7.0F * scale + bob - chevSize * 0.5F;
                Render2DUtil.texture(chevX, chevY, chevSize, chevSize, Ps4Textures.ICON_CHEVRON)
                        .color(paint(Ps4Helpers.WHITE)).transpose().draw();
            } else if (hov > 0.05F) {
                Ps4Helpers.outline(tileX, tileY, curSize, curSize, 0.0F, 1.5F * scale,
                        paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0.6F * hov)));
            }

            final int idx = i;
            this.clickZones.add(new ClickZone(tileX, tileY, curSize, curSize, () -> {
                if (this.selectedTile == idx) {
                    this.runTile(tile);
                } else {
                    this.selectedRow = 1;
                    this.selectTile(idx);
                }
            }));
        }

        // Title & subtitle below the carousel ribbon.
        if (this.selectedTile >= 0 && this.selectedTile < tiles.size()) {
            Ps4Tile selTile = tiles.get(this.selectedTile);
            float titleX = focusedX;
            float titleY = ribbonY + focusedSize + 16.0F * scale;

            Ps4Helpers.text(Ps4Fonts.UI, selTile.title(), titleX, titleY, 14.5F * scale, paint(Ps4Helpers.WHITE));
            String subtitle = Ps4Helpers.fit(Ps4Fonts.UI, selTile.subtitle(), 7.2F * scale, screenW - titleX - 40.0F * scale);
            Ps4Helpers.text(Ps4Fonts.UI, subtitle, titleX, titleY + 14.0F * scale, 7.2F * scale,
                    paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0x99 / 255.0F)));

            this.drawSubCards(selTile, titleX, titleY + 28.0F * scale, scale, mouseX, mouseY);
        }
    }

    private void runTile(Ps4Tile tile) {
        if (tile.primaryAction() != null) {
            tile.primaryAction().run();
        }
    }

    /** Sub-cards row displayed below the focused item. */
    private void drawSubCards(Ps4Tile tile, float startX, float subY, float scale, float mouseX, float mouseY) {
        List<SubCard> subs = tile.subCards();
        if (subs == null || subs.isEmpty()) {
            return;
        }

        float cardW = 110.0F * scale;
        float cardH = 58.0F * scale;
        float cardGap = 12.0F * scale;

        for (int i = 0; i < subs.size(); i++) {
            SubCard sub = subs.get(i);
            float cardX = startX + i * (cardW + cardGap);
            if (cardX > this.width) {
                break;
            }
            boolean isHovered = Ps4Helpers.inRect(mouseX, mouseY, cardX, subY, cardW, cardH);
            boolean isKeyboardFocused = this.selectedRow == 2 && this.selectedSub == i;
            float hov = this.hoverPC("sub_card_" + this.selectedTile + "_" + i, isHovered || isKeyboardFocused);

            // Soft drop shadow.
            Ps4Helpers.shadow(cardX + 1.5F * scale, subY + 3.0F * scale, cardW, cardH, 0.0F,
                    8.0F * scale, paint(Ps4Helpers.tint(0xFF000000, 0.5F)));

            // Dark card background.
            int cardBg = Ps4Helpers.lerp(Ps4Helpersrgba(0x10, 0x16, 0x26), Ps4Helpersrgba(0x1A, 0x24, 0x38), hov);
            Ps4Helpers.rect(cardX, subY, cardW, cardH, 0.0F, paint(cardBg));

            // Focus border on hover or keyboard focus.
            if (hov > 0.01F) {
                Ps4Helpers.glow(cardX, subY, cardW, cardH, 0.0F, 8.0F * scale,
                        paint(Ps4Helpers.tint(Ps4Helpers.WHITE, hov * 0.45F)));
                Ps4Helpers.outline(cardX, subY, cardW, cardH, 0.0F, 2.0F * scale, paint(Ps4Helpers.WHITE));
            } else {
                Ps4Helpers.outline(cardX, subY, cardW, cardH, 0.0F, 0.8F * scale,
                        paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0x40 / 255.0F)));
            }

            // Sub-card icon.
            float icSize = 15.0F * scale;
            if (sub.icon() != null) {
                Ps4Helpers.icon(sub.icon(), cardX + 14.0F * scale, subY + 14.0F * scale, icSize, icSize,
                        hov > 0.1F ? paint(Ps4Helpers.WHITE) : paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0xCC / 255.0F)));
            }

            // Title (clipped so it can not escape the card).
            String title = Ps4Helpers.fit(Ps4Fonts.UI, sub.title(), 7.0F * scale, cardW - (26.0F + 5.0F) * scale);
            Ps4Helpers.text(Ps4Fonts.UI, title, cardX + 26.0F * scale, subY + 10.5F * scale, 7.0F * scale, paint(Ps4Helpers.WHITE));

            // Description (clipped so it can not escape the card).
            String desc = Ps4Helpers.fit(Ps4Fonts.UI, sub.desc(), 5.8F * scale, cardW - 18.0F * scale);
            Ps4Helpers.text(Ps4Fonts.UI, desc, cardX + 10.0F * scale, subY + 32.0F * scale, 5.8F * scale,
                    paint(Ps4Helpers.tint(Ps4Helpers.WHITE, 0xAA / 255.0F)));

            final int subIdx = i;
            this.clickZones.add(new ClickZone(cardX, subY, cardW, cardH, () -> {
                this.selectedRow = 2;
                this.selectedSub = subIdx;
                sub.action().run();
            }));
        }
    }

    private float hoverPC(String key, boolean hovered) {
        if (hovered) {
            if (this.activeHovers.add(key)) {
                Ps4Sound.playUiSound("ps4_scroll");
            }
        } else {
            this.activeHovers.remove(key);
        }
        AnimatedValue anim = this.hoverAnims.computeIfAbsent(key, k -> new AnimatedValue(140L, Animation.Easing.EASE_OUT_QUAD));
        anim.toward(hovered ? 1.0F : 0.0F);
        return anim.value();
    }

    /* ------------------------------------------------------------------
     * Accounts overlay (УЧЁТНЫЕ ЗАПИСИ)
     * ------------------------------------------------------------------ */

    private static final String[] NICK_PREFIXES = {
            "Shadow", "Frost", "Void", "Pixel", "Aqua", "Night", "Storm", "Ember", "Ghost", "Nova",
            "Cyber", "Lunar", "Rapid", "Toxic", "Magma", "Blaze", "Astro", "Neon", "Grim", "Hyper"
    };
    private static final String[] NICK_SUFFIXES = {
            "Byte", "Craft", "Rush", "Fox", "Zap", "Wing", "Strike", "Core", "Drift", "Wolf",
            "Hawk", "Reign", "Spark", "Flux", "Dash", "Rage", "Snipe", "King", "Lord", "Vibe"
    };

    private String randomNickname() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 30; attempt++) {
            String prefix = NICK_PREFIXES[random.nextInt(NICK_PREFIXES.length)];
            String suffix = NICK_SUFFIXES[random.nextInt(NICK_SUFFIXES.length)];
            String name = switch (random.nextInt(5)) {
                case 0 -> prefix + suffix + random.nextInt(10, 100);
                case 1 -> prefix + "_" + suffix;
                case 2 -> prefix.toLowerCase(Locale.ROOT) + suffix + random.nextInt(100, 1000);
                case 3 -> prefix + suffix;
                default -> suffix + prefix + random.nextInt(10, 100);
            };
            if (name.length() <= 16 && !name.matches(".*\\d{5,}.*")) {
                return name;
            }
        }
        return NICK_PREFIXES[0] + random.nextInt(1000, 10000);
    }

    private float accountsListHeight(float scale) {
        return Math.min(5.0F, this.accounts.size() + 1) * (34.0F * scale + 8.0F * scale);
    }

    private float accountsPanelH(float scale) {
        return 108.0F * scale + Math.min(5.0F, this.accounts.size() + 1) * (42.0F * scale);
    }

    private void drawAccountsOverlay(float screenW, float screenH, float scale, float amount, float mx, float my) {
        Paint paintHelper = alphaFrom(amount);

        // Dim the background menu.
        Ps4Helpers.rect(0.0F, 0.0F, screenW, screenH, 0.0F, paintHelper.multiply(Ps4Helpers.tint(0xFF000000, 0.92F * amount)));

        float panelW = Math.min(480.0F * scale, screenW - 60.0F * scale);
        float panelH = this.accountsPanelH(scale);
        float panelX = (screenW - panelW) * 0.5F;
        float panelY = (screenH - panelH) * 0.5F;

        Ps4Helpers.shadow(panelX + 3.0F * scale, panelY + 6.0F * scale, panelW, panelH, 0.0F,
                18.0F * scale, paintHelper.multiply(Ps4Helpers.tint(0xFF000000, 0.6F)));
        Ps4Helpers.rect(panelX, panelY, panelW, panelH, 3.0F * scale, paintHelper.multiply(Ps4Helpersrgba(0x0F, 0x14, 0x20)));
        Ps4Helpers.outline(panelX, panelY, panelW, panelH, 3.0F * scale, 1.6F * scale, paintHelper.multiply(Ps4Helpers.tint(Ps4Helpers.WHITE, 0.5F)));

        // Header.
        Ps4Helpers.text(Ps4Fonts.UI, "УЧЁТНЫЕ ЗАПИСИ", panelX + 18.0F * scale, panelY + 14.0F * scale,
                9.0F * scale, paintHelper.multiply(Ps4Helpers.WHITE));
        Ps4Helpers.text(Ps4Fonts.UI, "Выберите профиль, добавьте новый или удалите лишний",
                panelX + 18.0F * scale, panelY + 26.0F * scale, 5.8F * scale,
                paintHelper.multiply(Ps4Helpers.tint(Ps4Helpers.WHITE, 0x88 / 255.0F)));

        float listX = panelX + 18.0F * scale;
        float listY = panelY + 42.0F * scale;
        float listW = panelW - 36.0F * scale;
        float rowH = 34.0F * scale;
        float rowGap = 8.0F * scale;
        int addRowIndex = this.accountAddMode ? this.accounts.size() : -1;

        // Push accounts scissor.
        float listClipH = Math.min(this.accounts.size() + (this.accountAddMode ? 1 : 0), 5) * (rowH + rowGap);
        Render2DUtil.pushScissor(listX, listY, listW, listClipH);

        for (int i = 0; i <= this.accounts.size(); i++) {
            boolean isAddRow = i == this.accounts.size() + (this.accountAddMode ? -1 : 0) || (this.accountAddMode && i == this.accounts.size());
            boolean isEntryRow = !this.accountAddMode ? i < this.accounts.size() : i <= this.accounts.size() && i != this.accounts.size();

            if (this.accountAddMode) {
                isAddRow = i == this.accounts.size();
                isEntryRow = i < this.accounts.size();
            }

            float rowY = listY + i * (rowH + rowGap) - this.accountsScroll;
            if (rowY + rowH < listY - rowH || rowY > listY + listClipH) {
                continue;
            }

            if (isEntryRow && i < this.accounts.size()) {
                this.drawAccountRow(listX, listY, rowY, rowH, listW, scale, this.accounts.get(i), i, paintHelper, mx, my);
            } else if (isAddRow) {
                this.drawAddAccountRow(listX, rowY, rowH, listW, scale, paintHelper, mx, my);
            }
        }

        Render2DUtil.popScissor();

        // Bottom hint.
        Ps4Helpers.text(Ps4Fonts.UI, "Текущий ник: " + selectedAccountName() + "   •   Esc — закрыть",
                panelX + 18.0F * scale, panelY + panelH - 16.0F * scale, 5.5F * scale,
                paintHelper.multiply(Ps4Helpers.tint(Ps4Helpers.WHITE, 0x77 / 255.0F)));
    }

    private Paint alphaFrom(float amount) {
        return new Paint(amount, this.drawStateAlpha);
    }

    private final class Paint {
        private final float overlay;
        private final float intro;

        private Paint(float overlay, float intro) {
            this.overlay = overlay;
            this.intro = intro;
        }

        private int multiply(int color) {
            return ColorUtil.multiplyAlpha(color, this.overlay * this.intro);
        }
    }

    private void drawAccountRow(float listX, float listY, float rowY, float rowH, float listW, float scale,
                                PsAccount account, int index, Paint paintHelper, float mx, float my) {
        if (rowY + rowH < listY - rowH || rowY > listY + this.accountsListHeight(scale)) {
            return;
        }

        String selectedName = selectedAccountName();
        boolean isSelected = account.name().equals(selectedName);

        boolean rowHov = Ps4Helpers.inRect(mx, my, listX, rowY, listW, rowH);
        float hov = this.hoverPC("account_" + index, rowHov);

        int bgCol = isSelected ? Ps4Helpersrgba(0x1E, 0x2A, 0x44) : Ps4Helpersrgba(0x14, 0x1B, 0x2A);
        bgCol = Ps4Helpers.lerp(bgCol, Ps4Helpersrgba(0x22, 0x30, 0x50), hov);
        Ps4Helpers.rect(listX, rowY, listW, rowH, 2.0F * scale, paintHelper.multiply(bgCol));

        if (isSelected) {
            Ps4Helpers.glow(listX, rowY, listW, rowH, 2.0F * scale, 6.0F * scale,
                    paintHelper.multiply(Ps4Helpers.tint(Ps4Helpersrgba(0x00, 0x84, 0xFF), 0.5F)));
            Ps4Helpers.outline(listX, rowY, listW, rowH, 2.0F * scale, 1.4F * scale, paintHelper.multiply(Ps4Helpers.WHITE));
        } else if (hov > 0.05F) {
            Ps4Helpers.outline(listX, rowY, listW, rowH, 2.0F * scale, 1.0F * scale,
                    paintHelper.multiply(Ps4Helpers.tint(Ps4Helpers.WHITE, 0.6F * hov)));
        }

        // Avatar.
        float avSize = 22.0F * scale;
        this.drawNamedAvatar(account.name(), listX + 6.0F * scale, rowY + (rowH - avSize) * 0.5F, avSize);

        // Name & status.
        float nameX = listX + 36.0F * scale;
        Ps4Helpers.text(Ps4Fonts.UI, account.name(), nameX, rowY + 7.0F * scale, 7.2F * scale, paintHelper.multiply(Ps4Helpers.WHITE));
        String status = isSelected ? "Играет сейчас" : account.activity();
        Ps4Helpers.text(Ps4Fonts.UI, status, nameX, rowY + 18.0F * scale, 5.5F * scale,
                paintHelper.multiply(Ps4Helpers.tint(Ps4Helpers.WHITE, isSelected ? 0x66 / 255.0F : 0x99 / 255.0F)));

        // Right action: select / current.
        String action = isSelected ? "Сейчас" : "Выбрать";
        float actW = 64.0F * scale;
        float actX = listX + listW - actW - 30.0F * scale;
        String fittedAction = Ps4Helpers.fit(Ps4Fonts.UI, action, 5.8F * scale, actW - 6.0F * scale);
        Ps4Helpers.textCentered(Ps4Fonts.UI, fittedAction, actX + actW * 0.5F, rowY + rowH * 0.5F - 2.9F * scale,
                5.8F * scale, paintHelper.multiply(isSelected ? Ps4Helpers.tint(Ps4Helpers.WHITE, 0x66 / 255.0F) : Ps4Helpers.WHITE));
        if (!isSelected) {
            this.clickZones.add(new ClickZone(actX - 3.0F * scale, rowY, actW + 6.0F * scale, rowH, () -> {
                this.switchAccount(account);
            }));
        }

        // Delete button.
        int delIconSize = 12;
        float delSize = 20.0F * scale;
        float delX = listX + listW - delSize - 8.0F * scale;
        float delY = rowY + (rowH - delSize) * 0.5F;
        boolean delHov = Ps4Helpers.inRect(mx, my, delX, delY, delSize, delSize);
        Ps4Helpers.rect(delX, delY, delSize, delSize, 2.0F * scale,
                paintHelper.multiply(Ps4Helpers.tint(0xFFE74C3C, 0.18F + (delHov ? 0.45F : 0.0F))));
        int delColor = Ps4Helpersrgba(0xE7, 0x4C, 0x3C);
        Ps4Helpers.icon(Ps4Textures.ICON_DELETE, delX + delSize * 0.5F, delY + delSize * 0.5F,
                delIconSize * scale, delIconSize * scale, paintHelper.multiply(delColor));
        if (this.accounts.size() > 1) {
            this.clickZones.add(new ClickZone(delX, delY, delSize, delSize, () -> {
                this.accounts.remove(account);
                if (account.name().equals(selectedAccountName())) {
                    MenuConfigStore.save(data -> {
                        if (!this.accounts.isEmpty()) {
                            data.addProperty("selectedAccount", this.accounts.get(0).name());
                        }
                        AccountSwitcher.switchTo(this.accounts.get(0).name());
                    });
                }
                this.persistAccounts();
            }));
        }
    }

    private void drawAddAccountRow(float listX, float rowY, float rowH, float listW, float scale, Paint paintHelper, float mx, float my) {
        // "+ Добавить аккаунт" toggle or the nickname input.
        boolean inputMode = this.accountAddMode;

        int bg = Ps4Helpersrgba(0x14, 0x1B, 0x2A);
        Ps4Helpers.rect(listX, rowY, listW, rowH, 2.0F * scale, paintHelper.multiply(bg));
        Ps4Helpers.outline(listX, rowY, listW, rowH, 2.0F * scale, inputMode ? 1.4F * scale : 0.9F * scale,
                paintHelper.multiply(Ps4Helpers.tint(inputMode ? Ps4Helpers.ACCENT : Ps4Helpers.WHITE, inputMode ? 0.9F : 0.35F)));

        if (!inputMode) {
            Ps4Helpers.icon(Ps4Textures.ICON_PLUS, listX + 17.0F * scale, rowY + rowH * 0.5F,
                    14.0F * scale, 14.0F * scale, paintHelper.multiply(Ps4Helpers.ACCENT));
            Ps4Helpers.text(Ps4Fonts.UI, "Добавить аккаунт", listX + 30.0F * scale, rowY + rowH * 0.5F - 3.2F * scale,
                    6.6F * scale, paintHelper.multiply(Ps4Helpers.WHITE));
            this.clickZones.add(new ClickZone(listX, rowY, listW, rowH, () -> {
                this.accountInput = "";
                this.accountAddMode = true;
            }));
            return;
        }

        // Input field content.
        String display = this.accountInput + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        Ps4Helpers.text(Ps4Fonts.UI, Ps4Helpers.fit(Ps4Fonts.UI, "Ник: " + display, 6.6F * scale, listW - 90.0F * scale),
                listX + 10.0F * scale, rowY + rowH * 0.5F - 3.2F * scale, 6.6F * scale, paintHelper.multiply(Ps4Helpers.WHITE));

        // Confirm button.
        float okW = 64.0F * scale;
        float okX = listX + listW - okW - 8.0F * scale;
        boolean okHov = Ps4Helpers.inRect(mx, my, okX, rowY + 4.0F * scale, okW, rowH - 8.0F * scale);
        boolean valid = this.accountInput.trim().matches("^[a-zA-Z0-9_]{3,16}$");
        Ps4Helpers.rect(okX, rowY + 4.0F * scale, okW, rowH - 8.0F * scale, 2.0F * scale,
                paintHelper.multiply(valid || okHov ? Ps4Helpersrgba(0x1E, 0x3A, 0x6E) : Ps4Helpersrgba(0x1B, 0x22, 0x30)));
        Ps4Helpers.textCentered(Ps4Fonts.UI, Ps4Helpers.fit(Ps4Fonts.UI, "Добавить", 5.8F * scale, okW - 6.0F * scale),
                okX + okW * 0.5F, rowY + rowH * 0.5F - 2.9F * scale, 5.8F * scale,
                paintHelper.multiply(valid ? Ps4Helpers.WHITE : Ps4Helpers.tint(Ps4Helpers.WHITE, 0.5F)));
        if (valid) {
            this.clickZones.add(new ClickZone(okX, rowY, okW, rowH, this::saveNewAccount));
        }

        // Dice (random nick).
        float diceSize = 20.0F * scale;
        float diceX = okX - diceSize - 6.0F * scale;
        float diceY = rowY + (rowH - diceSize) * 0.5F;
        boolean diceHov = Ps4Helpers.inRect(mx, my, diceX, diceY, diceSize, diceSize);
        Ps4Helpers.icon(Ps4Textures.ICON_DICE, diceX + diceSize * 0.5F, diceY + diceSize * 0.5F,
                diceSize * 0.7F, diceSize * 0.7F, paintHelper.multiply(diceHov ? Ps4Helpers.WHITE : Ps4Helpers.ACCENT));
        if (diceHov) {
            this.clickZones.add(new ClickZone(diceX, diceY, diceSize, diceSize, () -> this.accountInput = randomNickname()));
        }
    }

    private void handleAccountsClick(float mx, float my) {
        // Clicks outside the panel close the overlay.
        float scale = Math.min(this.width / 960.0F, this.height / 540.0F);
        float panelW = Math.min(480.0F * scale, this.width - 60.0F * scale);
        float panelH = this.accountsPanelH(scale);
        float panelX = (this.width - panelW) * 0.5F;
        float panelY = (this.height - panelH) * 0.5F;
        if (!Ps4Helpers.inRect(mx, my, panelX, panelY, panelW, panelH)) {
            this.accountsOpen = false;
            return;
        }
        Ps4Sound.playUiSound("ps4_select");
    }
}
