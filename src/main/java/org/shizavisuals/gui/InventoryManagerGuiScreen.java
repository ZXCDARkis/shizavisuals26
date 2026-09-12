
package org.shizavisuals.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.shizavisuals.context.RenderContext;
import org.shizavisuals.menu.core.MenuAppearance;
import org.shizavisuals.utils.ColorUtil;
import org.shizavisuals.utils.inventory.InventoryManager;
import org.shizavisuals.utils.inventory.InventoryPreset;
import org.shizavisuals.utils.inventory.InvItem;
import org.shizavisuals.utils.render.Theme;
import org.shizavisuals.utils.render.gui.MsdfFont;
import org.shizavisuals.utils.render.gui.Render2DUtil;
import org.shizavisuals.utils.render.gui.TextAlign;
import org.shizavisuals.utils.render.gui.UiFonts;
import org.shizavisuals.utils.render.gui.UiFontStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Полноценный порт InventoryManagerScreen из rocksnow на рендер Silient.
 * Панель рисуется через шейдеры (glass/blur + анимированный фон menu_background.fsh),
 * как главное меню. Есть анимации: открытие/закрытие (fade+слайд), слайд превью
 * пресета, плавные ховеры кнопок/строк.
 */
public final class InventoryManagerGuiScreen extends Screen {
    private static final int PANEL_W = 452;
    private static final int PANEL_H = 272;
    private static final int RADIUS = 12;
    private static final int PAD = 12;
    private static final int GAP = 10;
    private static final int RW = 180;
    private static final int LW = PANEL_W - PAD * 2 - GAP - RW;
    private static final int CELL = 17;
    private static final int ROW_H = 34;
    private static final int ROW_GAP = 5;
    private static final int BTN_H = 18;
    private static final int OPEN_MS = 240;
    private static final int CLOSE_MS = 200;
    private static final int SLIDE_MS = 320;

    private final Screen parent;
    private final InventoryManager manager = InventoryManager.get();
    private final MsdfFont uiFont = UiFonts.sfProDisplay();
    private static final float FONT_SIZE = 9.0F;
    private static final float FONT_LS = FONT_SIZE * UiFontStyle.REGULAR.letterSpacingEm();

    private String nameValue = "";
    private boolean nameFocused;
    private int cursorTick;

    private float scroll;
    private float maxScroll;

    // hit rects rebuilt each frame
    private final float[] backRect = {-999, -999, 0, 0};
    private final float[] saveRect = {-999, -999, 0, 0};
    private final float[] nameRect = {-999, -999, 0, 0};
    private final float[] disableRect = {-999, -999, 0, 0};
    private final List<float[]> rowRects = new ArrayList<>();
    private final List<float[]> loadRects = new ArrayList<>();
    private final List<float[]> penRects = new ArrayList<>();
    private final List<float[]> delRects = new ArrayList<>();
    private final List<String> rowNames = new ArrayList<>();
    private final List<float[]> cellRects = new ArrayList<>();
    private final List<String> cellNames = new ArrayList<>();

    private String hoverPreview;
    private String shownPreview;
    private String prevPreview;
    private float previewT = 1.0f;
    private int previewDir = 1;
    private String confirmTarget;
    private boolean confirmOpen;

    // animation state
    private long openStart = -1;
    private boolean closing;
    private long closeStart;
    private long lastTime = -1;
    private float fade = 0.0f;
    private float dt;
    private final Map<String, Float> anims = new HashMap<>();

    // derived
    private float px;
    private float py;
    private float contentTop;
    private float contentBottom;

    public InventoryManagerGuiScreen(Screen parent) {
        super(Component.literal("Inventory Manager"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        if (openStart < 0) openStart = now;
        if (lastTime < 0) lastTime = now;
        dt = Math.max(0.0f, Math.min(0.1f, (now - lastTime) / 1000.0f));
        lastTime = now;
        cursorTick++;

        float openT = clamp((now - openStart) / (float) OPEN_MS, 0.0f, 1.0f);
        if (closing) {
            float closeT = clamp((now - closeStart) / (float) CLOSE_MS, 0.0f, 1.0f);
            fade = 1.0f - ease(closeT);
            if (closeT >= 1.0f) {
                onClose();
                return;
            }
        } else {
            fade = ease(openT);
        }

        // dim behind
        g.fill(0, 0, this.width, this.height, A(0xB0000000));

        // layout
        float oy = (1.0f - fade) * 26.0f;
        px = (this.width - PANEL_W) / 2.0f;
        py = (this.height - PANEL_H) / 2.0f + oy;
        contentTop = py + 56.0f;
        contentBottom = py + PANEL_H - PAD;
        hoverPreview = null;

        RenderContext.enter2D(this.minecraft.gui, g, this.minecraft.getDeltaTracker());
        try {
            Render2DUtil.beginFrame();
            Render2DUtil.setBackdropBlurScale(MenuAppearance.glassBlurScale());
            drawPanelShaders(g, mouseX, mouseY);
            drawPreviewPanelShaders(g);
            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }

        // content (drawn on top; text uses the asset MSDF font via the 2D pipeline)
        RenderContext.enter2D(this.minecraft.gui, g, this.minecraft.getDeltaTracker());
        try {
            Render2DUtil.beginFrame();
            renderHeader(g, mouseX, mouseY);
            renderLeft(g, mouseX, mouseY);
            renderRight(g, mouseX, mouseY);
            Render2DUtil.flush();
        } finally {
            RenderContext.exit2D();
        }

        // sync preview animation
        String want = hoverPreview != null ? hoverPreview : manager.store().activeName();
        if (want != null && want.isEmpty()) want = null;
        if (want != null && !want.equals(shownPreview)) {
            if (shownPreview != null) {
                prevPreview = shownPreview;
                previewDir = manager.store().presets().indexOf(new InventoryPreset(want))
                        > manager.store().presets().indexOf(new InventoryPreset(shownPreview)) ? 1 : -1;
            }
            shownPreview = want;
            previewT = 0.0f;
        }
        if (want == null) shownPreview = null;
        previewT = Math.min(1.0f, previewT + dt * 1000.0f / SLIDE_MS);
    }

    private void drawPanelShaders(GuiGraphicsExtractor g, int mx, int my) {
        // shadow
        Render2DUtil.rect(px, py, PANEL_W, PANEL_H)
                .color(ColorUtil.TRANSPARENT)
                .radius(RADIUS)
                .shadow(A(Theme.Colors.PANEL_SHADOW_STRONG), 30)
                .draw();
        // glass backdrop blur
        Render2DUtil.rect(px, py, PANEL_W, PANEL_H)
                .color(ColorUtil.TRANSPARENT)
                .radius(RADIUS)
                .blur(22.0f, 0.85f * fade)
                .draw();
        // solid tint
        Render2DUtil.rect(px, py, PANEL_W, PANEL_H)
                .color(A(Theme.Colors.PANEL))
                .radius(RADIUS)
                .draw();
        // border
        Render2DUtil.rect(px, py, PANEL_W, PANEL_H)
                .color(ColorUtil.TRANSPARENT)
                .radius(RADIUS)
                .border(1.0f, A(Theme.Colors.PANEL_BORDER))
                .draw();
    }

    private void drawPreviewPanelShaders(GuiGraphicsExtractor g) {
        float rx = px + PAD + LW + GAP;
        float ct = contentTop;
        float cb = contentBottom;
        float h = cb - ct;

        Render2DUtil.rect(rx, ct, RW, h)
                .color(ColorUtil.TRANSPARENT)
                .radius(8.0f)
                .blur(14.0f, 0.8f * fade)
                .draw();
        Render2DUtil.rect(rx, ct, RW, h)
                .color(A(Theme.Colors.BACKGROUND_SURFACE_M, 0.45f))
                .radius(8.0f)
                .draw();

        var background = MenuAppearance.background();
        int a = 150;
        int primary = (Theme.getAccent() & 0x00FFFFFF) | (a << 24);
        int secondary = (accentComplement() & 0x00FFFFFF) | (a << 24);
        float seconds = (System.currentTimeMillis() % 3_600_000L) / 1000.0F;
        Render2DUtil.menuBackground(rx + 1, ct + 1, RW - 2, h - 2, 8.0f,
                seconds, background.shaderMode(), primary, secondary);
        Render2DUtil.rect(rx, ct, RW, h)
                .color(A(ColorUtil.BLACK, 150))
                .radius(8.0f)
                .draw();
        Render2DUtil.rect(rx, ct, RW, h)
                .color(ColorUtil.TRANSPARENT)
                .radius(8.0f)
                .border(1.0f, A(Theme.Colors.PANEL_BORDER))
                .draw();
    }

    private void renderHeader(GuiGraphicsExtractor g, int mx, int my) {
        int textColor = Theme.Colors.TEXT;
        // back
        String back = "< Назад";
        int bx = (int) (px + PAD);
        int by = (int) (py + 12);
        float bHov = anim("back", hit(mx, my, backRect), dt);
        int backCol = mix(0x80FFFFFF, 0xFFFFFFFF, bHov);
        drawText(back, bx, by, A(backCol), TextAlign.LEFT);
        set(backRect, bx - 2, by - 2, measure(back) + 4, 12);

        // title with chest
        int titleY = (int) (py + 28);
        g.item(new ItemStack(Items.CHEST), (int) (px + PAD), titleY);
        String title = "Inventory Manager";
        int titleX = (int) (px + PAD + 18);
        drawText(title, titleX, titleY + 4, A(0xFFFFFFFF), TextAlign.LEFT);
        int titleEnd = titleX + (int) measure(title);

        // save button
        String saveLabel = "+ Сохранить";
        int sbW = (int) measure(saveLabel) + 16;
        int sbH = BTN_H;
        int sbX = titleEnd + 12;
        int sbY = titleY - 1;
        boolean canSave = !nameValue.isBlank();
        float sHov = anim("save", hit(mx, my, saveRect), dt);
        int bg = canSave ? mix(0x2AFFFFFF, 0x3FFFFFFF, sHov) : 0x1AFFFFFF;
        int fg = canSave ? mix(0xDDFFFFFF, 0xFFFFFFFF, sHov) : 0x66FFFFFF;
        g.fill(sbX, sbY, sbX + sbW, sbY + sbH, A(bg));
        drawText(saveLabel, sbX + 8, sbY + 5, A(fg), TextAlign.LEFT);
        set(saveRect, sbX, sbY, sbW, sbH);

        // count
        int n = manager.store().presets().size();
        String cnt = n + " сохр.";
        int cntW = (int) measure(cnt);
        drawText(cnt, (int) (px + PANEL_W - PAD - cntW), titleY + 4, A(0x80FFFFFF), TextAlign.LEFT);

        // divider
        g.fill((int) (px + PAD), (int) (py + 48), (int) (px + PANEL_W - PAD), (int) (py + 49), A(0x18FFFFFF));
    }

    private void renderLeft(GuiGraphicsExtractor g, int mx, int my) {
        float lx = px + PAD;
        float ct = contentTop;

        // name field
        int niX = (int) lx;
        int niY = (int) ct;
        int niW = LW;
        int niH = 20;
        float nfHov = anim("name", nameFocused, dt);
        int fieldBg = mix(0x1AFFFFFF, 0x33FFFFFF, nfHov);
        g.fill(niX, niY, niX + niW, niY + niH, A(fieldBg));
        g.fill(niX, niY + niH - 1, niX + niW, niY + niH, A(nameFocused ? (Theme.getAccent() | 0xFF000000) : 0x18FFFFFF));
        set(nameRect, niX, niY, niW, niH);
        String hint = "имя пресета";
        String shown = nameValue.isEmpty() ? hint : nameValue;
        int col = nameValue.isEmpty() ? 0x66FFFFFF : 0xFFFFFFFF;
        int tx = niX + 6;
        int ty = niY + 6;
        if (nameFocused && (cursorTick / 12) % 2 == 0 && !nameValue.isEmpty()) {
            drawText(nameValue + "_", tx, ty, A(col), TextAlign.LEFT);
        } else {
            drawText(shown, tx, ty, A(col), TextAlign.LEFT);
        }

        float listY = ct + niH + 8.0f;
        float statusH = 16.0f;
        float listH = (contentBottom - statusH) - listY;

        List<InventoryPreset> presets = manager.store().presets();
        String activeName = manager.store().activeName();

        rowRects.clear(); loadRects.clear(); penRects.clear(); delRects.clear(); rowNames.clear();

        if (presets.isEmpty()) {
            int ecx = (int) (lx + LW / 2);
            int ecy = (int) (listY + listH / 2);
            g.item(new ItemStack(Items.CHEST), ecx - 8, ecy - 16);
            String e1 = "Пресетов нет";
            String e2 = "сохрани текущий инвентарь";
            drawText(e1, ecx, ecy + 6, A(0x72FFFFFF), TextAlign.CENTER);
            drawText(e2, ecx, ecy + 16, A(0x4CFFFFFF), TextAlign.CENTER);
            maxScroll = 0;
        } else {
            float contentH = presets.size() * (ROW_H + ROW_GAP);
            maxScroll = Math.max(0, contentH - listH);
            scroll = clamp(scroll, 0, maxScroll);

            g.enableScissor((int) lx, (int) listY, (int) (lx + LW), (int) (listY + listH));
            Render2DUtil.pushScissor(lx, listY, LW, listH);
            float yy = listY - scroll;
            for (InventoryPreset p : presets) {
                renderRow(g, p, lx, yy, LW, mx, my, p.name.equals(activeName));
                yy += ROW_H + ROW_GAP;
            }
            Render2DUtil.popScissor();
            g.disableScissor();

            if (contentH > listH + 0.5f) {
                float sbX = lx + LW + 3;
                float thumbH = Math.max(18, listH * listH / contentH);
                float thumbY = listY + (listH - thumbH) * (maxScroll <= 0 ? 0 : scroll / maxScroll);
                g.fill((int) sbX, (int) listY, (int) (sbX + 2), (int) (listY + listH), A(0x18FFFFFF));
                g.fill((int) sbX, (int) thumbY, (int) (sbX + 2), (int) (thumbY + thumbH), A(0x55FFFFFF));
            }
        }

        // status + disable
        float rowCy = contentBottom - statusH / 2.0f;
        boolean hasActive = manager.store().active() != null;
        String status = hasActive ? "Активен: " + activeName : "Выключено";
        String trimmed = trimToWidth(status, (int) (LW - 70));
        drawText(trimmed, (int) (lx + 8), (int) (rowCy - 3), A(0x99FFFFFF), TextAlign.LEFT);

        if (hasActive) {
            String dl = "Выключить";
            int dW = (int) measure(dl) + 14;
            int dH = 14;
            int dX = (int) (lx + LW - dW);
            int dY = (int) (rowCy - dH / 2.0f);
            float dHov = anim("disable", hit(mx, my, disableRect), dt);
            int bg = mix(0x22FF5555, 0x33FF5555, dHov);
            int fg = mix(0xFFFF8888, 0xFFFFAAAA, dHov);
            g.fill(dX, dY, dX + dW, dY + dH, A(bg));
            drawText(dl, dX + 7, dY + 3, A(fg), TextAlign.LEFT);
            set(disableRect, dX, dY, dW, dH);
        } else {
            clear(disableRect);
        }
    }

    private void renderRow(GuiGraphicsExtractor g, InventoryPreset p, float rx, float ry, float rw, int mx, int my, boolean active) {
        boolean visible = ry + ROW_H > contentTop && ry < contentBottom;
        boolean rowHov = visible && hit(mx, my, (int) rx, (int) ry, (int) rw, ROW_H);
        if (rowHov && hoverPreview == null) hoverPreview = p.name;

        float hov = anim("row:" + p.name, rowHov, dt);
        int base = active ? 0x3344AA66 : 0x1E202022;
        int bg = mix(base, 0x28303035, hov);
        g.fill((int) rx, (int) ry, (int) (rx + rw), (int) (ry + ROW_H), A(bg));
        if (active) {
            g.fill((int) rx, (int) ry, (int) (rx + 2), (int) (ry + ROW_H), A(Theme.getAccent() | 0xFF000000));
        }

        float bw = BTN_H, bh = BTN_H;
        float gap = 6.0f;
        float delX = rx + rw - 10 - bw;
        float penX = delX - gap - bw;

        String loadLabel = active ? "Заменить" : "Загрузить";
        int loadW = (int) measure(loadLabel) + 12;
        int loadH = BTN_H;
        float loadX = penX - gap - loadW;
        float loadY = ry + ROW_H / 2 - bh / 2;

        String nameTrim = trimToWidth(p.name, (int) (loadX - 10 - (rx + 9)));
        drawText(nameTrim, (int) (rx + 9), (int) (ry + 7), A(0xFFFFFFFF), TextAlign.LEFT);
        drawText(p.size() + " предм.", (int) (rx + 9), (int) (ry + 19), A(0x80FFFFFF), TextAlign.LEFT);

        float lHov = anim("load:" + p.name, rowHov && hit(mx, my, (int) loadX, (int) loadY, loadW, loadH), dt);
        g.fill((int) loadX, (int) loadY, (int) (loadX + loadW), (int) (loadY + loadH), A(mix(0x1EFFffff, 0x33FFFFFF, lHov)));
        drawText(loadLabel, (int) (loadX + 6), (int) (loadY + 5), A(mix(0xCCFFFFFF, 0xFFFFFFFF, lHov)), TextAlign.LEFT);

        float penHov = anim("pen:" + p.name, rowHov && hit(mx, my, (int) penX, (int) (ry + ROW_H / 2 - bh / 2), (int) bw, (int) bh), dt);
        g.fill((int) penX, (int) (ry + ROW_H / 2 - bh / 2), (int) (penX + bw), (int) (ry + ROW_H / 2 + bh / 2), A(mix(0x1AFFFFFF, 0x33FFFFFF, penHov)));
        drawText("✎", (int) (penX + 5), (int) (ry + 11), A(mix(0xAAFFFFFF, 0xFFFFFFFF, penHov)), TextAlign.LEFT);

        float delHov = anim("del:" + p.name, rowHov && hit(mx, my, (int) delX, (int) (ry + ROW_H / 2 - bh / 2), (int) bw, (int) bh), dt);
        g.fill((int) delX, (int) (ry + ROW_H / 2 - bh / 2), (int) (delX + bw), (int) (ry + ROW_H / 2 + bh / 2), A(mix(0x1AFFFFFF, 0x33FF4444, delHov)));
        drawText("✕", (int) (delX + 5), (int) (ry + 11), A(mix(0xAAFFFFFF, 0xFFFF6666, delHov)), TextAlign.LEFT);

        rowRects.add(new float[]{rx, ry, rw, ROW_H});
        loadRects.add(new float[]{loadX, loadY, loadW, loadH});
        penRects.add(new float[]{penX, ry + ROW_H / 2 - bh / 2, bw, bh});
        delRects.add(new float[]{delX, ry + ROW_H / 2 - bh / 2, bw, bh});
        rowNames.add(p.name);
    }

    private void renderRight(GuiGraphicsExtractor g, int mx, int my) {
        float rx = px + PAD + LW + GAP;
        float ct = contentTop;
        float cb = contentBottom;

        String head = "Превью";
        drawText(head, (int) (rx + 8), (int) (ct + 7), A(0xCCFFFFFF), TextAlign.LEFT);

        String name = shownPreview;
        if (name != null) {
            String shown = trimToWidth(name, RW - 80);
            drawText(shown, (int) (rx + 8 + measure(head) + 6), (int) (ct + 7), A(0x66FFFFFF), TextAlign.LEFT);
        }

        cellRects.clear();
        cellNames.clear();

        InventoryPreset preset = name == null ? null : manager.store().byName(name);
        if (preset == null) {
            String h1 = "Наведи на пресет";
            String h2 = "или выбери активный";
            int cx = (int) (rx + RW / 2);
            int cy = (int) ((ct + cb) / 2);
            drawText(h1, cx, cy - 6, A(0x66FFFFFF), TextAlign.CENTER);
            drawText(h2, cx, cy + 4, A(0x44FFFFFF), TextAlign.CENTER);
            return;
        }

        float slideX = (1.0f - ease(previewT)) * previewDir * (RW + 30.0f);
        float gw = 9 * CELL;
        float gx0 = rx + (RW - gw) / 2.0f + slideX;
        float gridTop = ct + 26.0f;
        float blockH = CELL + 8 + 3 * CELL + 8 + CELL;
        float gy0 = gridTop + ((cb - gridTop) - blockH) / 2.0f - 3.0f;

        g.enableScissor((int) rx, (int) ct, (int) (rx + RW), (int) cb);
        Render2DUtil.pushScissor(rx, ct, RW, cb - ct);
        try {
            for (int i = 0; i < 4; i++) drawCell(g, preset, 5 + i, gx0 + i * CELL, gy0, mx, my);
            drawCell(g, preset, 45, gx0 + 8 * CELL, gy0, mx, my);

            float mainY = gy0 + CELL + 8;
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 9; col++)
                    drawCell(g, preset, 9 + row * 9 + col, gx0 + col * CELL, mainY + row * CELL, mx, my);

            float hotY = mainY + 3 * CELL + 8;
            for (int col = 0; col < 9; col++) drawCell(g, preset, 36 + col, gx0 + col * CELL, hotY, mx, my);
        } finally {
            Render2DUtil.popScissor();
            g.disableScissor();
        }
    }

    private void drawCell(GuiGraphicsExtractor g, InventoryPreset preset, int slot, float cx, float cy, int mx, int my) {
        float c = CELL - 2;
        InvItem it = preset.items.get(slot);
        ItemStack st = it == null ? ItemStack.EMPTY : it.toStack();
        boolean has = !st.isEmpty();
        boolean hov = has && hit(mx, my, (int) cx, (int) cy, (int) c, (int) c);

        g.fill((int) cx, (int) cy, (int) (cx + c), (int) (cy + c), A(hov ? 0x33FFFFFF : 0x1A000000));
        g.fill((int) cx, (int) cy, (int) (cx + c), (int) (cy + 1), A(0x0FFFFFFF));
        g.fill((int) cx, (int) (cy + c - 1), (int) (cx + c), (int) (cy + c), A(0x0FFFFFFF));

        if (!has) return;
        cellRects.add(new float[]{cx, cy, c, c});
        String nm = (it.name == null || it.name.isEmpty()) ? st.getHoverName().getString() : it.name;
        cellNames.add(nm);

        g.item(st, (int) (cx + 1), (int) (cy + 1));
        if (st.getCount() > 1) {
            String cnt = String.valueOf(st.getCount());
            float cs = 8.0F;
            float cw = this.uiFont.measureWidth(cnt, cs, cs * UiFontStyle.REGULAR.letterSpacingEm());
            float ct = (cy + 1) + c - this.uiFont.textHeight(cs) - 1.0F;
            drawText(cnt, (cx + 1) + c - cw - 1.0F, ct, A(0xFFFFFFFF), TextAlign.LEFT);
        }
    }

    private void renderConfirm(GuiGraphicsExtractor g, int mx, int my) {
        if (!confirmOpen || confirmTarget == null) return;
        float cw = 226, ch = 98;
        float cx = px + (PANEL_W - cw) / 2;
        float cy = py + (PANEL_H - ch) / 2;
        g.fill((int) cx, (int) cy, (int) (cx + cw), (int) (cy + ch), A(0x88000000));
        g.fill((int) (cx - 1), (int) (cy - 1), (int) (cx + cw + 1), (int) (cy + ch + 1), A(0x44FFFFFF));
        g.fill((int) cx, (int) cy, (int) (cx + cw), (int) (cy + ch), A(Theme.Colors.PANEL));
        String t = "Перезаписать пресет?";
        drawText(t, (int) (cx + cw / 2), (int) (cy + 16), A(0xFFFFFFFF), TextAlign.CENTER);
        String nameLine = "\"" + trimToWidth(confirmTarget, (int) (cw - 24)) + "\"";
        drawText(nameLine, (int) (cx + cw / 2), (int) (cy + 32), A(0xAAFFFFFF), TextAlign.CENTER);

        float bh = BTN_H;
        float bw = (cw - 24 - 8) / 2;
        float by = cy + ch - 14 - bh;
        float noX = cx + 12, yesX = noX + bw + 8;
        float noHov = anim("cno", hit(mx, my, (int) noX, (int) by, (int) bw, (int) bh), dt);
        float yesHov = anim("cyes", hit(mx, my, (int) yesX, (int) by, (int) bw, (int) bh), dt);
        g.fill((int) noX, (int) by, (int) (noX + bw), (int) (by + bh), A(mix(0x1AFFFFFF, 0x33FFFFFF, noHov)));
        g.fill((int) yesX, (int) by, (int) (yesX + bw), (int) (by + bh), A(mix(0x22FF5555, 0x33FF5555, yesHov)));
        String noL = "Отмена";
        String yesL = "Заменить";
        drawText(noL, (int) (noX + bw / 2), (int) (by + 5), A(0xFFFFFFFF), TextAlign.CENTER);
        drawText(yesL, (int) (yesX + bw / 2), (int) (by + 5), A(0xFFFFFFFF), TextAlign.CENTER);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        if (closing) return true;
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (button != 0) return true;

        if (confirmOpen) {
            float cw = 226, ch = 98;
            float cx = px + (PANEL_W - cw) / 2;
            float cy = py + (PANEL_H - ch) / 2;
            float bh = BTN_H;
            float bw = (cw - 24 - 8) / 2;
            float by = cy + ch - 14 - bh;
            float noX = cx + 12, yesX = noX + bw + 8;
            if (hit(mx, my, noX, by, bw, bh)) { confirmOpen = false; return true; }
            if (hit(mx, my, yesX, by, bw, bh)) { manager.replaceCurrent(confirmTarget); confirmOpen = false; return true; }
            if (mx < cx || mx > cx + cw || my < cy || my > cy + ch) { confirmOpen = false; return true; }
            return true;
        }

        if (hit(mx, my, backRect)) { startClose(); return true; }
        if (hit(mx, my, saveRect) && !nameValue.isBlank()) { manager.saveCurrent(nameValue.trim()); nameValue = ""; return true; }
        if (hit(mx, my, disableRect)) { manager.clearActive(); return true; }
        if (hit(mx, my, nameRect)) { nameFocused = true; return true; }
        if (nameFocused && !hit(mx, my, nameRect)) nameFocused = false;

        for (int i = 0; i < cellRects.size(); i++) {
            if (hit(mx, my, cellRects.get(i))) { manager.searchAuction(cellNames.get(i)); return true; }
        }
        for (int i = 0; i < rowNames.size(); i++) {
            String n = rowNames.get(i);
            if (hit(mx, my, delRects.get(i))) { manager.store().delete(n); return true; }
            if (hit(mx, my, penRects.get(i))) { confirmTarget = n; confirmOpen = true; return true; }
            if (hit(mx, my, loadRects.get(i))) {
                if (n.equals(manager.store().activeName())) { confirmTarget = n; confirmOpen = true; }
                else manager.setActive(n);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (closing || confirmOpen) return true;
        float lx = px + PAD;
        float listY = contentTop + 28;
        float listH = (contentBottom - 16) - listY;
        if (mouseX >= lx && mouseX <= lx + LW && mouseY >= listY && mouseY <= listY + listH && maxScroll > 0) {
            scroll = clamp(scroll - (float) v * 18, 0, maxScroll);
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (closing) return true;
        int key = input.key();
        if (confirmOpen) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { manager.replaceCurrent(confirmTarget); confirmOpen = false; return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { confirmOpen = false; return true; }
            return true;
        }
        if (nameFocused) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                if (!nameValue.isBlank()) { manager.saveCurrent(nameValue.trim()); nameValue = ""; }
                nameFocused = false; return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { nameFocused = false; return true; }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                if (!nameValue.isEmpty()) nameValue = nameValue.substring(0, nameValue.length() - 1);
                return true;
            }
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { startClose(); return true; }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (closing || confirmOpen) return true;
        char c = (char) input.codepoint();
        if (nameFocused) {
            if (c < ' ' || c == '/' || c == '\\' || c == ':' || c == '"') return true;
            if (nameValue.length() < 24) nameValue += c;
            return true;
        }
        return true;
    }

    private void startClose() {
        if (!closing) {
            closing = true;
            closeStart = System.currentTimeMillis();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }

    // animation helpers
    private float anim(String key, boolean target, float dtSec) {
        Float v = anims.get(key);
        if (v == null) v = 0.0f;
        float goal = target ? 1.0f : 0.0f;
        v += (goal - v) * Math.min(1.0f, 14.0f * dtSec);
        if (v < 0.001f) v = 0.0f;
        if (v > 0.999f) v = 1.0f;
        anims.put(key, v);
        return v;
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float ease(float t) { return t * t * (3.0f - 2.0f * t); }

    private static int A(int color) { return A(color, 1.0f); }
    private static int A(int color, float m) {
        int a = (color >> 24) & 0xFF;
        int na = (int) (a * m) & 0xFF;
        return (na << 24) | (color & 0x00FFFFFF);
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int b2 = (int) (ab + (bb - ab) * t);
        int al = (int) (aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | b2;
    }

    private static int accentComplement() {
        float[] hsv = ColorUtil.hsv(Theme.getAccent());
        float hue = (hsv[0] + 0.42F) % 1.0F;
        return ColorUtil.fromHsv(hue, Math.max(0.55F, hsv[1]), Math.max(0.75F, hsv[2]), 255);
    }

    private String trimToWidth(String s, int maxW) {
        if (measure(s) <= maxW) return s;
        String ell = "...";
        float ellW = measure(ell);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (measure(sb.toString() + s.charAt(i) + ell) > maxW) break;
            sb.append(s.charAt(i));
        }
        return sb + ell;
    }

    private void drawText(String text, float x, float topY, int color, TextAlign align) {
        Render2DUtil.text(x, topY, FONT_SIZE, text)
                .font(uiFont)
                .style(UiFontStyle.REGULAR)
                .color(color)
                .align(align)
                .draw();
    }

    private float measure(String s) {
        return uiFont.measureWidth(s, FONT_SIZE, FONT_LS);
    }

    private static boolean hit(double mx, double my, float x, float y, float w, float h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    private static boolean hit(double mx, double my, float[] r) { return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]; }
    private static void set(float[] r, float x, float y, float w, float h) { r[0]=x; r[1]=y; r[2]=w; r[3]=h; }
    private static void clear(float[] r) { r[0]=-999; r[1]=-999; r[2]=0; r[3]=0; }
}
