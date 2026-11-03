package org.shizavisuals.menu.ps4;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.ui.AnimatedValue;
import org.shizavisuals.menu.ps5.Ps5SkinCache;
import org.shizavisuals.utils.math.Animation;
import org.shizavisuals.utils.render.gui.Render2DUtil;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 1:1 port of the PS4 Singleplayer screen (Одиночная игра): themed wallpaper,
 * PS4 top bar (back, profile, clock), horizontal carousel of world tiles with
 * edge fades, action cards and the delete confirmation modal.
 */
public final class Ps4SingleplayerScreen extends Screen {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    private final Screen parent;
    private final List<ClickZone> clickZones = new ArrayList<>();
    private final Map<String, AnimatedValue> hoverAnims = new HashMap<>();
    private final Set<String> activeHovers = new HashSet<>();

    private List<LevelSummary> worldSummaries = new ArrayList<>();
    private final Map<String, Identifier> worldIcons = new HashMap<>();
    /** Cached "dd.MM.yyyy HH:mm" strings, keyed by the world's lastPlayed. */
    private final Map<Long, String> dateCache = new HashMap<>();
    private boolean loadingWorlds = false;

    private int selectedIndex;
    private float scrollOffset;

    private boolean isDeleteConfirm = false;

    private record ClickZone(float x, float y, float w, float h, Runnable action) {
    }

    public Ps4SingleplayerScreen(Screen parent) {
        super(Component.literal("Одиночная игра"));
        this.parent = parent;
    }

    @Override
    public void added() {
        super.added();
        this.loadWorlds();
    }

    private Minecraft client() {
        return this.minecraft != null ? this.minecraft : Minecraft.getInstance();
    }

    private void loadWorlds() {
        this.loadingWorlds = true;
        Minecraft client = this.client();
        CompletableFuture.supplyAsync(() -> {
            try {
                LevelStorageSource.LevelCandidates candidates = client.getLevelSource().findLevelCandidates();
                return client.getLevelSource().loadLevelSummaries(candidates).get();
            } catch (Exception e) {
                return List.<LevelSummary>of();
            }
        }).thenAccept(summaries -> this.minecraft.execute(() -> {
            this.worldSummaries = new ArrayList<>(summaries);
            this.loadingWorlds = false;
            if (this.selectedIndex >= this.getTotalItems()) {
                this.selectedIndex = Math.max(0, this.getTotalItems() - 1);
            }
            // Decode world icons on a background thread so the render loop
            // never touches the disk; registration hops back to the main thread.
            for (LevelSummary summary : summaries) {
                this.loadWorldIconAsync(summary);
            }
        }));
    }

    /** Loads a world's icon PNG off-thread and registers it on the main thread. */
    private void loadWorldIconAsync(LevelSummary summary) {
        String levelId = summary.getLevelId();
        if (this.worldIcons.containsKey(levelId)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try (InputStream stream = Files.newInputStream(summary.getIcon())) {
                NativeImage image = NativeImage.read(stream);
                Identifier texture = Identifier.parse(String.format("shizavisuals:dynamic/world_%08x",
                        levelId.toLowerCase(Locale.ROOT).hashCode()));
                this.client().execute(() -> {
                    try {
                        if (READY_WORLD_ICONS.remove(texture.toString())) {
                            this.client().getTextureManager().release(texture);
                        }
                        this.client().getTextureManager().register(texture,
                                new DynamicTexture(() -> texture.toString(), image));
                        READY_WORLD_ICONS.add(texture.toString());
                        // Rendered only after registration, so it is always valid.
                        this.worldIcons.put(levelId, texture);
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    /** Releases the GPU textures of the world icons, then clears the cache. */
    private void releaseWorldIcons() {
        Minecraft client = this.client();
        for (Identifier texture : this.worldIcons.values()) {
            if (texture != null && READY_WORLD_ICONS.remove(texture.toString())) {
                client.getTextureManager().release(texture);
            }
        }
        this.worldIcons.clear();
    }

    private Identifier getWorldIcon(LevelSummary summary) {
        return this.worldIcons.get(summary.getLevelId());
    }

    private int getTotalItems() {
        return 1 + this.worldSummaries.size(); // 0 is "+ Создать мир", 1..N are worlds
    }

    /** Dynamic icon ids currently registered in the TextureManager. */
    private final java.util.Set<String> READY_WORLD_ICONS = new HashSet<>();

    private void startWorld(LevelSummary summary) {
        Ps4Sound.playUiSound("ps4_select");
        Ps4Sound.stopAmbientMusic();
        this.client().createWorldOpenFlows().openWorld(summary.getLevelId(), () -> this.minecraft.setScreenAndShow(this));
    }

    private void createNewWorld() {
        Ps4Sound.playUiSound("ps4_select");
        CreateWorldScreen.openFresh(this.client(), () -> {
            this.minecraft.setScreenAndShow(this);
            this.worldSummaries.clear();
            this.releaseWorldIcons();
            this.loadWorlds();
        });
    }

    private void editWorld(LevelSummary summary) {
        Ps4Sound.playUiSound("ps4_select");
        try {
            LevelStorageSource.LevelStorageAccess access = this.client().getLevelSource().createAccess(summary.getLevelId());
            this.minecraft.setScreenAndShow(EditWorldScreen.create(this.client(), access, saved -> {
                this.minecraft.setScreenAndShow(this);
                this.worldSummaries.clear();
                this.releaseWorldIcons();
                this.loadWorlds();
            }));
        } catch (Exception ignored) {
        }
    }

    private void deleteCurrentWorld() {
        if (this.selectedIndex > 0 && this.selectedIndex <= this.worldSummaries.size()) {
            LevelSummary summary = this.worldSummaries.get(this.selectedIndex - 1);
            if (summary.canDelete()) {
                try {
                    LevelStorageSource.LevelStorageAccess access = this.client().getLevelSource().createAccess(summary.getLevelId());
                    try {
                        access.deleteLevel();
                    } finally {
                        access.close();
                    }
                    Ps4Sound.playUiSound("ps4_select");
                    this.worldSummaries.clear();
                    this.releaseWorldIcons();
                    this.loadWorlds();
                } catch (Exception ignored) {
                }
            }
        }
        this.isDeleteConfirm = false;
    }

    private void backToMenu() {
        Ps4Sound.playUiSound("ps4_scroll");
        this.client().setScreenAndShow(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.isDeleteConfirm) {
            double delta = verticalAmount != 0 ? verticalAmount : -horizontalAmount;
            if (delta > 0 && this.selectedIndex > 0) {
                this.selectedIndex--;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            } else if (delta < 0 && this.selectedIndex < this.getTotalItems() - 1) {
                this.selectedIndex++;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (this.isDeleteConfirm) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                this.isDeleteConfirm = false;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.backToMenu();
            return true;
        }

        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            if (this.selectedIndex > 0) {
                this.selectedIndex--;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
        } else if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            if (this.selectedIndex < this.getTotalItems() - 1) {
                this.selectedIndex++;
                Ps4Sound.playUiSound("ps4_scroll");
                return true;
            }
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.selectedIndex == 0) {
                this.createNewWorld();
            } else if (this.selectedIndex <= this.worldSummaries.size()) {
                this.startWorld(this.worldSummaries.get(this.selectedIndex - 1));
            }
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            float mx = (float) event.x();
            float my = (float) event.y();

            for (int i = this.clickZones.size() - 1; i >= 0; i--) {
                ClickZone zone = this.clickZones.get(i);
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

            // 1. Dynamic PS4 Theme Background.
            Ps4Helpers.coverWallpaper(Ps4Wallpapers.texture(), screenW, screenH, 1.0F);

            // Dark gradient overlay for the bottom half.
            Ps4Helpers.rect(0.0F, screenH * 0.45F, screenW, screenH * 0.55F, 0.0F, Ps4Helpers.tint(0xFF000000, 0.30F));

            // 2. Top bar (back button, profile, clock).
            this.drawTopBar(screenW, scale, mx, my);

            // 3. Carousel of worlds.
            this.drawCarousel(screenW, screenH, scale, mx, my);

            // 4. Selected world info & action cards.
            this.drawSelectedInfo(screenW, screenH, scale, mx, my);

            // 5. Delete confirmation modal.
            if (this.isDeleteConfirm) {
                this.drawDeleteConfirm(screenW, scale, mx, my);
            }

            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }
    }

    /** Top status bar: back button, profile pill (back to menu) and the clock. */
    private void drawTopBar(float screenW, float scale, float mx, float my) {
        float topY = 24.0F * scale;

        // Back button.
        float backW = 86.0F * scale;
        float backH = 22.0F * scale;
        float backX = 36.0F * scale;
        boolean backHover = Ps4Helpers.inRect(mx, my, backX, topY, backW, backH);
        float backHov = this.hoverPC("top_back", backHover);

        Ps4Helpers.rect(backX, topY, backW, backH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.15F + 0.2F * backHov));
        if (backHov > 0.05F) {
            Ps4Helpers.outline(backX, topY, backW, backH, 0.0F, 1.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, backHov));
        }
        Ps4Helpers.icon(Ps4Textures.ICON_BACK, backX + 10.0F * scale, topY + backH * 0.5F, 9.0F * scale, 9.0F * scale, Ps4Helpers.WHITE);
        Ps4Helpers.text(Ps4Fonts.UI, "Назад в меню", backX + 22.0F * scale, topY + backH * 0.5F - 3.2F * scale, 6.2F * scale, Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(backX, topY, backW, backH, this::backToMenu));

        // Clock (cached per second instead of formatting every frame).
        String timeStr = Ps4Helpers.clockString();
        float timeX = screenW - 44.0F * scale;
        float timeW = Ps4Helpers.measure(Ps4Fonts.UI, timeStr, 7.8F * scale);
        float clockLeft = timeX - timeW;
        Ps4Helpers.text(Ps4Fonts.UI, timeStr, clockLeft, topY + 7.5F * scale, 7.8F * scale, Ps4Helpers.WHITE);

        // Profile pill.
        String profileName = this.client().getUser() != null && !this.client().getUser().getName().isBlank()
                ? this.client().getUser().getName() : "Player";
        float profNameW = Ps4Helpers.measure(Ps4Fonts.UI, profileName, 7.0F * scale);
        float profW = 34.0F * scale + profNameW + 16.0F * scale;
        float profH = 24.0F * scale;
        float profX = clockLeft - profW - 24.0F * scale;

        boolean profHover = Ps4Helpers.inRect(mx, my, profX, topY - 1.0F * scale, profW, profH);
        float profHov = this.hoverPC("top_profile", profHover);

        Ps4Helpers.rect(profX, topY - 1.0F * scale, profW, profH, 0.0F, Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.22F + 0.35F * profHov));
        if (profHov > 0.05F) {
            Ps4Helpers.outline(profX, topY - 1.0F * scale, profW, profH, 0.0F, 1.2F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, profHov));
        }

        // Avatar.
        float avSize = 16.0F * scale;
        float avX = profX + 4.0F * scale;
        float avY = topY - 1.0F * scale + (profH - avSize) * 0.5F;
        Ps4Helpers.rect(avX - 1.0F * scale, avY - 1.0F * scale, avSize + 2.0F * scale, avSize + 2.0F * scale, 0.0F, 0xFF181818);
        Identifier skin = Ps5SkinCache.texture(this.client(), profileName);
        Render2DUtil.texture(avX, avY, avSize, avSize, skin).color(Ps4Helpers.WHITE)
                .uv(0.125F, 0.125F, 0.25F, 0.25F).draw();

        Ps4Helpers.text(Ps4Fonts.UI, profileName, profX + 24.0F * scale, topY - 1.0F * scale + profH * 0.5F - 3.5F * scale, 7.0F * scale, Ps4Helpers.WHITE);

        this.clickZones.add(new ClickZone(profX, topY - 1.0F * scale, profW, profH, this::backToMenu));
    }

    /** Horizontal carousel of world tiles (PS4 style with smooth edge fades). */
    private void drawCarousel(float screenW, float screenH, float scale, float mx, float my) {
        float carouselY = screenH * 0.22F;
        float tileNormalSize = 102.0F * scale;
        float tileSelectedSize = 118.0F * scale;
        float tileGap = 20.0F * scale;
        float baseStartX = 56.0F * scale;

        int total = this.getTotalItems();
        float targetScroll = this.selectedIndex * (tileNormalSize + tileGap);
        this.scrollOffset += (targetScroll - this.scrollOffset) * 0.22F;

        float curX = baseStartX - this.scrollOffset;

        for (int i = 0; i < total; i++) {
            boolean isSelected = i == this.selectedIndex;
            float curTileSize = isSelected ? tileSelectedSize : tileNormalSize;
            float tileX = curX;
            float tileY = isSelected ? carouselY - (tileSelectedSize - tileNormalSize) * 0.5F : carouselY;
            curX += curTileSize + tileGap;

            int idx = i;
            this.clickZones.add(new ClickZone(tileX, tileY, curTileSize, curTileSize, () -> {
                if (this.selectedIndex != idx) {
                    this.selectedIndex = idx;
                    Ps4Sound.playUiSound("ps4_scroll");
                } else if (idx == 0) {
                    this.createNewWorld();
                } else {
                    this.startWorld(this.worldSummaries.get(idx - 1));
                }
            }));

            // Smooth fade when scrolling off screen (smoothstep).
            float leftFadeStart = baseStartX;
            float leftFadeEnd = -curTileSize * 0.45F;
            float leftFactor = 1.0F;
            if (tileX < leftFadeStart) {
                float t = Math.max(0.0F, Math.min(1.0F, (tileX - leftFadeEnd) / (leftFadeStart - leftFadeEnd)));
                leftFactor = t * t * (3.0F - 2.0F * t);
            }

            float rightFadeStart = screenW - 85.0F * scale;
            float rightFadeEnd = screenW + curTileSize * 0.45F;
            float rightFactor = 1.0F;
            if (tileX + curTileSize > rightFadeStart) {
                float t = Math.max(0.0F, Math.min(1.0F, (rightFadeEnd - (tileX + curTileSize)) / (rightFadeEnd - rightFadeStart)));
                rightFactor = t * t * (3.0F - 2.0F * t);
            }

            float tileAlpha = Math.min(leftFactor, rightFactor);
            if (tileAlpha < 0.01F) {
                continue;
            }

            boolean hovered = Ps4Helpers.inRect(mx, my, tileX, tileY, curTileSize, curTileSize);
            float hov = this.hoverPC("world_tile_" + i, hovered);

            // Ambient soft drop shadow beneath every world card.
            Ps4Helpers.shadow(tileX + 1.5F * scale, tileY + 3.0F * scale, curTileSize, curTileSize, 0.0F,
                    10.0F * scale, Ps4Helpers.tint(0xFF000000, 0.55F * tileAlpha));

            float footerH = 22.0F * scale;
            float iconAreaH = curTileSize - footerH;

            if (i == 0) {
                // Tile 0: "+ Создать мир".
                Ps4Helpers.rect(tileX, tileY, curTileSize, curTileSize, 0.0F,
                        Ps4Helpers.tint(Ps4Helpers.ACCENT, (isSelected ? 0.45F : (0.2F + 0.15F * hov)) * tileAlpha));
                Ps4Helpers.textCentered(Ps4Fonts.UI, "+", tileX + curTileSize * 0.5F, tileY + iconAreaH * 0.5F - 14.0F * scale,
                        30.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha));
            } else {
                // World Tile 1..N.
                LevelSummary summary = this.worldSummaries.get(i - 1);
                Ps4Helpers.rect(tileX, tileY, curTileSize, curTileSize, 0.0F,
                        Ps4Helpers.tint(Ps4Helpers.ACCENT, (isSelected ? 0.40F : (0.2F + 0.12F * hov)) * tileAlpha));

                Identifier icon = this.getWorldIcon(summary);
                if (icon != null) {
                    float iconPad = 8.0F * scale;
                    float iconS = iconAreaH - iconPad * 2.0F;
                    float iconY = tileY + (iconAreaH - iconS) * 0.5F;
                    Render2DUtil.dynamicTexture(tileX + (curTileSize - iconS) * 0.5F, iconY, iconS, iconS, icon)
                            .color(Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha)).draw();
                } else {
                    float iconS = iconAreaH * 0.48F;
                    Ps4Helpers.icon(Ps4Textures.ICON_SINGLEPLAYER, tileX + curTileSize * 0.5F, tileY + iconAreaH * 0.5F,
                            iconS, iconS, Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha));
                }
            }

            // Unified footer bar across all tiles.
            Ps4Helpers.rect(tileX, tileY + curTileSize - footerH, curTileSize, footerH, 0.0F, Ps4Helpers.tint(0xB0101018, tileAlpha));
            String footerText = i == 0 ? "Создать мир" : this.worldSummaries.get(i - 1).getLevelName();
            String fitted = Ps4Helpers.fit(Ps4Fonts.UI, footerText, 6.8F * scale, curTileSize + 28.0F * scale);
            Ps4Helpers.textCentered(Ps4Fonts.UI, fitted, tileX + curTileSize * 0.5F,
                    tileY + curTileSize - footerH * 0.5F - 3.2F * scale, 6.8F * scale,
                    Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha));

            // Visual effects for the selected or hovered tile.
            if (isSelected) {
                long now = System.currentTimeMillis();
                float breath = 0.30F + 0.14F * (float) Math.sin(now * 0.0035);

                // 1. Breathing aura bloom.
                Ps4Helpers.glow(tileX, tileY, curTileSize, curTileSize, 0.0F, 16.0F * scale,
                        Ps4Helpers.tint(Ps4Helpers.ACCENT, breath * tileAlpha));
                // 2. White outer focus glow.
                Ps4Helpers.glow(tileX, tileY, curTileSize, curTileSize, 0.0F, 8.0F * scale,
                        Ps4Helpers.tint(Ps4Helpers.WHITE, 0.45F * tileAlpha));
                // 3. Crisp white PS4 frame.
                Ps4Helpers.outline(tileX, tileY, curTileSize, curTileSize, 0.0F, 2.6F * scale,
                        Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha));

                // 4. Subtle shimmer sweep across the selected card.
                float sweepProg = (now % 3200L) / 3200.0F;
                if (sweepProg < 0.32F) {
                    float sx = tileX + (sweepProg / 0.32F) * (curTileSize + 24.0F * scale) - 12.0F * scale;
                    Ps4Helpers.glow(sx, tileY + 3.0F * scale, 10.0F * scale, curTileSize - 6.0F * scale, 0.0F, 6.0F * scale,
                            Ps4Helpers.tint(Ps4Helpers.WHITE, 0.22F * tileAlpha));
                }

                // Small "Запуск" button under the selected tile.
                float startBtnW = curTileSize;
                float startBtnH = 16.0F * scale;
                float startBtnY = tileY + curTileSize + 6.0F * scale;
                boolean sBtnHov = Ps4Helpers.inRect(mx, my, tileX, startBtnY, startBtnW, startBtnH);

                if (sBtnHov) {
                    Ps4Helpers.glow(tileX, startBtnY, startBtnW, startBtnH, 0.0F, 8.0F * scale,
                            Ps4Helpers.tint(0xFF3498DB, 0.55F * tileAlpha));
                }
                Ps4Helpers.rect(tileX, startBtnY, startBtnW, startBtnH, 0.0F,
                        Ps4Helpers.tint(sBtnHov ? 0xDD3498DB : 0x95000000, tileAlpha));
                Ps4Helpers.outline(tileX, startBtnY, startBtnW, startBtnH, 0.0F, 0.8F * scale,
                        Ps4Helpers.tint(sBtnHov ? Ps4Helpers.WHITE : 0x88FFFFFF, tileAlpha));
                String btnText = i == 0 ? "▶  Создать" : "▶  Запустить";
                Ps4Helpers.textCentered(Ps4Fonts.UI, btnText, tileX + startBtnW * 0.5F, startBtnY + startBtnH * 0.5F - 3.2F * scale,
                        6.5F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, tileAlpha));

                this.clickZones.add(new ClickZone(tileX, startBtnY, startBtnW, startBtnH, () -> {
                    if (idx == 0) {
                        this.createNewWorld();
                    } else if (idx - 1 < this.worldSummaries.size()) {
                        this.startWorld(this.worldSummaries.get(idx - 1));
                    }
                }));
            } else if (hov > 0.05F) {
                Ps4Helpers.glow(tileX, tileY, curTileSize, curTileSize, 0.0F, 8.0F * scale,
                        Ps4Helpers.tint(Ps4Helpers.ACCENT, 0.35F * hov * tileAlpha));
                Ps4Helpers.outline(tileX, tileY, curTileSize, curTileSize, 0.0F, 1.4F * scale,
                        Ps4Helpers.tint(Ps4Helpers.WHITE, hov * 0.6F * tileAlpha));
            }
        }
    }

    /** Lower section: world title, details and action cards. */
    private void drawSelectedInfo(float screenW, float screenH, float scale, float mx, float my) {
        float leftX = 56.0F * scale;
        float infoY = screenH * 0.54F;

        if (this.selectedIndex == 0) {
            // "+ Создать мир" selected.
            if (this.loadingWorlds) {
                Ps4Helpers.text(Ps4Fonts.UI, "ЗАГРУЗКА МИРОВ...", leftX, infoY, 15.0F * scale, Ps4Helpers.WHITE);
            } else {
                Ps4Helpers.text(Ps4Fonts.UI, "СОЗДАТЬ НОВЫЙ МИР", leftX, infoY, 15.0F * scale, Ps4Helpers.WHITE);
            }
            Ps4Helpers.text(Ps4Fonts.UI, "Сгенерируйте новое одиночное приключение, настройте сид и правила игры",
                    leftX, infoY + 20.0F * scale, 7.5F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, 0xCC / 255.0F));

            float cardsY = infoY + 38.0F * scale;
            float cardW = 120.0F * scale;
            float cardH = 68.0F * scale;
            float cardGap = 12.0F * scale;

            this.drawActionCard(leftX, cardsY, cardW, cardH, scale, mx, my,
                    "▶  Создать мир", "Открыть мастер создания", Ps4Textures.ICON_PLUS, this::createNewWorld);
            this.drawActionCard(leftX + (cardW + cardGap), cardsY, cardW, cardH, scale, mx, my,
                    "Обновить список", "Пересканировать папку saves", Ps4Textures.ICON_SEARCH,
                    () -> {
                        Ps4Sound.playUiSound("ps4_select");
                        this.loadWorlds();
                    });
            return;
        }

        int worldIdx = this.selectedIndex - 1;
        if (worldIdx >= this.worldSummaries.size()) {
            return;
        }
        LevelSummary summary = this.worldSummaries.get(worldIdx);

        // 1. World name.
        Ps4Helpers.text(Ps4Fonts.UI, summary.getLevelName(), leftX, infoY, 15.0F * scale, Ps4Helpers.WHITE);

        // 2. World details.
        String modeStr;
        if (summary.isHardcore()) {
            modeStr = "Хардкор";
        } else if (summary.getGameMode() != null) {
            modeStr = switch (summary.getGameMode()) {
                case CREATIVE -> "Творчество";
                case SURVIVAL -> "Выживание";
                case ADVENTURE -> "Приключение";
                case SPECTATOR -> "Наблюдатель";
                default -> "Выживание";
            };
        } else {
            modeStr = "Выживание";
        }

        String dateStr = this.dateCache.computeIfAbsent(summary.getLastPlayed(),
                lastPlayed -> DATE_FORMAT.format(new Date(lastPlayed)));
        String verStr = summary.getWorldVersionName() != null ? summary.getWorldVersionName().getString() : "";
        String fullSub = Ps4Helpers.fit(Ps4Fonts.UI, modeStr + "  •  " + dateStr + "  •  " + verStr,
                7.2F * scale, screenW - leftX - 40.0F * scale);
        Ps4Helpers.text(Ps4Fonts.UI, fullSub, leftX, infoY + 20.0F * scale, 7.2F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, 0xCC / 255.0F));

        // 3. Action cards.
        float cardsY = infoY + 38.0F * scale;
        float cardW = 120.0F * scale;
        float cardH = 68.0F * scale;
        float cardGap = 12.0F * scale;

        this.drawActionCard(leftX, cardsY, cardW, cardH, scale, mx, my,
                "▶  Играть в мире", "Загрузить сохранение", Ps4Textures.ICON_SINGLEPLAYER, () -> this.startWorld(summary));
        this.drawActionCard(leftX + (cardW + cardGap), cardsY, cardW, cardH, scale, mx, my,
                "⚙  Настроить мир", "Переименовать / бэкап", Ps4Textures.ICON_RENAME, () -> this.editWorld(summary));
        this.drawActionCard(leftX + (cardW + cardGap) * 2.0F, cardsY, cardW, cardH, scale, mx, my,
                "＋  Создать новый", "Сгенерировать другой мир", Ps4Textures.ICON_PLUS, this::createNewWorld);
        this.drawActionCard(leftX + (cardW + cardGap) * 3.0F, cardsY, cardW, cardH, scale, mx, my,
                "🗑  Удалить мир", "Безвозвратное удаление", Ps4Textures.ICON_DELETE, () -> {
                    Ps4Sound.playUiSound("ps4_select");
                    this.isDeleteConfirm = true;
                });
    }

    private void drawActionCard(float x, float y, float w, float h, float scale, float mx, float my,
                                String title, String desc, Identifier icon, Runnable action) {
        boolean hovered = Ps4Helpers.inRect(mx, my, x, y, w, h);
        int bgCol = hovered ? 0xCC202028 : 0x80101016;

        Ps4Helpers.rect(x, y, w, h, 0.0F, bgCol);
        if (hovered) {
            Ps4Helpers.glow(x, y, w, h, 0.0F, 10.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, 0.35F));
            Ps4Helpers.outline(x, y, w, h, 0.0F, 1.8F * scale, Ps4Helpers.WHITE);
        } else {
            Ps4Helpers.outline(x, y, w, h, 0.0F, 0.8F * scale, 0x40FFFFFF);
        }

        float iconSize = 14.0F * scale;
        Ps4Helpers.icon(icon, x + 12.0F * scale, y + 14.0F * scale, iconSize, iconSize,
                hovered ? Ps4Helpers.WHITE : Ps4Helpers.tint(Ps4Helpers.WHITE, 0xAA / 255.0F));
        Ps4Helpers.text(Ps4Fonts.UI, Ps4Helpers.fit(Ps4Fonts.UI, title, 6.8F * scale, w - 30.0F * scale),
                x + 24.0F * scale, y + 10.0F * scale, 6.8F * scale, Ps4Helpers.WHITE);
        Ps4Helpers.text(Ps4Fonts.UI, Ps4Helpers.fit(Ps4Fonts.UI, desc, 5.8F * scale, w - 16.0F * scale),
                x + 10.0F * scale, y + 36.0F * scale, 5.8F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, 0xBB / 255.0F));

        this.clickZones.add(new ClickZone(x, y, w, h, action));
    }

    /** PS4 styled confirmation dialog for deleting a world. */
    private void drawDeleteConfirm(float screenW, float scale, float mx, float my) {
        float screenH = this.height;
        Ps4Helpers.rect(0.0F, 0.0F, screenW, screenH, 0.0F, 0xAA000000);

        float modalW = 280.0F * scale;
        float modalH = 135.0F * scale;
        float modalX = (screenW - modalW) * 0.5F;
        float modalY = (screenH - modalH) * 0.5F;

        Ps4Helpers.rect(modalX, modalY, modalW, modalH, 0.0F, 0xF0151520);
        Ps4Helpers.outline(modalX, modalY, modalW, modalH, 0.0F, 1.5F * scale, 0xFFE74C3C);

        Ps4Helpers.textCentered(Ps4Fonts.UI, "Удаление мира", modalX + modalW * 0.5F, modalY + 16.0F * scale,
                9.5F * scale, Ps4Helpers.tint(0xFFE74C3C, 1.0F));

        String worldName = this.selectedIndex > 0 && this.selectedIndex <= this.worldSummaries.size()
                ? this.worldSummaries.get(this.selectedIndex - 1).getLevelName() : "";
        String prompt = Ps4Helpers.fit(Ps4Fonts.UI, "Вы уверены, что хотите удалить мир '" + worldName + "'?",
                6.5F * scale, modalW - 24.0F * scale);
        Ps4Helpers.textCentered(Ps4Fonts.UI, prompt, modalX + modalW * 0.5F, modalY + 38.0F * scale, 6.5F * scale,
                Ps4Helpers.WHITE);
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Мир будет удален безвозвратно!", modalX + modalW * 0.5F,
                modalY + 52.0F * scale, 6.0F * scale, Ps4Helpers.tint(Ps4Helpers.WHITE, 0xAA / 255.0F));

        float btnW = 96.0F * scale;
        float btnH = 22.0F * scale;
        float btnY = modalY + modalH - 32.0F * scale;

        // Delete button.
        float delX = modalX + 32.0F * scale;
        boolean delHov = Ps4Helpers.inRect(mx, my, delX, btnY, btnW, btnH);
        Ps4Helpers.rect(delX, btnY, btnW, btnH, 0.0F, delHov ? 0xFFE74C3C : 0x88E74C3C);
        Ps4Helpers.outline(delX, btnY, btnW, btnH, 0.0F, 1.0F * scale, delHov ? Ps4Helpers.WHITE : Ps4Helpers.tint(Ps4Helpers.WHITE, 0xAA / 255.0F));
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Удалить", delX + btnW * 0.5F, btnY + btnH * 0.5F - 3.2F * scale, 6.5F * scale,
                Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(delX, btnY, btnW, btnH, this::deleteCurrentWorld));

        // Cancel button.
        float canX = modalX + modalW - btnW - 32.0F * scale;
        boolean canHov = Ps4Helpers.inRect(mx, my, canX, btnY, btnW, btnH);
        Ps4Helpers.rect(canX, btnY, btnW, btnH, 0.0F, canHov ? 0x88555555 : 0x55333333);
        Ps4Helpers.outline(canX, btnY, btnW, btnH, 0.0F, 1.0F * scale, canHov ? Ps4Helpers.WHITE : Ps4Helpers.tint(Ps4Helpers.WHITE, 0xAA / 255.0F));
        Ps4Helpers.textCentered(Ps4Fonts.UI, "Отмена", canX + btnW * 0.5F, btnY + btnH * 0.5F - 3.2F * scale, 6.5F * scale,
                Ps4Helpers.WHITE);
        this.clickZones.add(new ClickZone(canX, btnY, btnW, btnH, () -> {
            Ps4Sound.playUiSound("ps4_scroll");
            this.isDeleteConfirm = false;
        }));
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
}
