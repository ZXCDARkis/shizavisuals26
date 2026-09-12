package org.shizavisuals.utils.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.shizavisuals.utils.render.Theme;

import java.util.List;

/**
 * Draws the inventory-manager overlay inside the player inventory screen:
 * chest button (opens the preset GUI), slot ghosts, missing-items panel and
 * the arrange button. Refreshes the click rects on InventoryManager.
 */
public final class InventoryOverlay {
    private InventoryOverlay() {
    }

    public static void render(int guiLeft, int guiTop, int guiWidth, int guiHeight,
                               int mouseX, int mouseY, GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (g == null || mc.player == null) {
            return;
        }
        InventoryManager m = InventoryManager.get();
        InventoryPreset active = m.store().active();
        Font font = mc.font;

        // Chest button above the inventory top-right corner.
        int bw = 24, bh = 12;
        int bx = guiLeft + guiWidth - bw - 7;
        int by = guiTop - bh + 3;
        boolean chestHover = InventoryManager.inside(mouseX, mouseY, bx, by, bw, bh);
        g.fill(bx, by, bx + bw, by + bh, chestHover ? 0xFFCFCFCF : 0xFFC6C6C6);
        g.fill(bx, by, bx + bw, by + 1, 0xFFFFFFFF);
        g.fill(bx, by, bx + 1, by + bh, 0xFFFFFFFF);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFF8B8B8B);
        InventoryManager.rect(m.chestBtnRect, bx, by, bw, bh);
        if (chestHover) {
            g.setTooltipForNextFrame(font,
                    List.of(Component.literal("Inventory Manager")), java.util.Optional.empty(), mouseX, mouseY);
        }

        InventoryManager.clearRect(m.arrangeBtnRect);
        InventoryManager.clearRect(m.missingPanelRect);
        m.missingRowRects.clear();
        m.missingRowNames.clear();
        if (active == null || !m.overlayHints()) {
            return;
        }

        InventoryDiff d = m.diff();
        boolean arranging = m.arranger().isRunning();
        int accent = Theme.getAccent() | 0xFF000000;
        int red = 0xFFFF5555;
        int white = 0xFFF0F0F0;
        int green = 0xFF55FF7A;
        int yellow = 0xFFFFD23F;
        int dim = 0xFFA0A0AA;

        int px = guiLeft + guiWidth + 6;
        int py = guiTop;
        int pad = 6;
        int fh = font.lineHeight;

        if (arranging) {
            String t1 = "Расстановка...";
            String t2 = "Остановить";
            int w = Math.max(font.width(t1), font.width(t2)) + pad * 2;
            int h = pad * 2 + fh + 4 + fh;
            panel(g, px, py, w, h);
            g.text(font, t1, px + pad, py + pad, accent);
            int sy = py + pad + fh + 4;
            boolean hStop = InventoryManager.inside(mouseX, mouseY, px + pad, sy, font.width(t2), fh + 2);
            g.text(font, t2, px + pad, sy, hStop ? white : dim);
            InventoryManager.rect(m.arrangeBtnRect, px + pad, sy, font.width(t2), fh + 2);
            drawGhosts(g, guiLeft, guiTop, m, active, d);
            return;
        }

        if (d.assembled) {
            String s = "Инвентарь собран";
            int w = font.width(s) + pad * 2;
            int h = pad * 2 + fh;
            panel(g, px, py, w, h);
            g.text(font, s, px + pad, py + pad, green);
            drawGhosts(g, guiLeft, guiTop, m, active, d);
            return;
        }

        int afterY = py;

        if (m.showMissing() && !d.missing.isEmpty()) {
            List<InvItem> rows = d.missing;
            int total = rows.size();
            int maxVisible = 8;
            int visible = Math.min(total, maxVisible);
            m.missingScrollMax = Math.max(0, total - maxVisible);
            m.missingScroll = Math.max(0, Math.min(m.missingScroll, m.missingScrollMax));
            boolean scroll = m.missingScrollMax > 0;

            int rowMax = 0;
            for (InvItem it : rows) {
                rowMax = Math.max(rowMax, 20 + font.width("×" + it.count));
            }
            String title = "Отсутствует:";
            int contentW = Math.max(font.width(title), rowMax);
            int w = contentW + pad * 2 + (scroll ? 6 : 0);
            int headerH = fh + 4;
            int listH = visible * 18;
            int h = pad + headerH + listH + pad - 4;
            panel(g, px, py, w, h);
            InventoryManager.rect(m.missingPanelRect, px, py, w, h);
            g.text(font, title, px + pad, py + pad, red);

            int listTop = py + pad + headerH;
            int rowW = w - pad - (scroll ? 6 : 0);
            g.enableScissor(px + pad - 2, listTop, px + pad - 2 + rowW, listTop + listH);
            int ry0 = listTop - m.missingScroll * 18;
            for (int i = 0; i < total; i++) {
                int ry = ry0 + i * 18;
                if (ry + 16 < listTop || ry > listTop + listH) {
                    continue;
                }
                InvItem it = rows.get(i);
                boolean rowHov = mouseX >= px + pad - 2 && mouseX <= px + pad - 2 + rowW
                        && mouseY >= ry - 1 && mouseY <= ry + 16
                        && mouseY >= listTop && mouseY <= listTop + listH;
                if (rowHov) {
                    g.fill(px + pad - 2, ry - 1, px + pad - 2 + rowW, ry + 16, 0x33FFFFFF);
                }
                ItemStack icon = it.toStack();
                if (!icon.isEmpty()) {
                    g.item(icon, px + pad, ry);
                    g.itemDecorations(font, icon, px + pad, ry);
                }
                g.text(font, "×" + it.count, px + pad + 20, ry + 4, white);

                String nm = it.name;
                if ((nm == null || nm.isEmpty()) && !icon.isEmpty()) {
                    nm = icon.getHoverName().getString();
                }
                m.missingRowRects.add(new float[]{px + pad - 2, ry - 1, rowW, 17});
                m.missingRowNames.add(nm == null ? "" : nm);
            }
            g.disableScissor();

            if (scroll) {
                int tx = px + w - 4;
                g.fill(tx, listTop, tx + 2, listTop + listH, 0x40FFFFFF);
                int thumbH = Math.max(12, listH * visible / total);
                int thumbY = listTop + (listH - thumbH) * m.missingScroll / m.missingScrollMax;
                g.fill(tx, thumbY, tx + 2, thumbY + thumbH, 0xC0FFFFFF);
            }
            afterY = py + h + 5;
        } else {
            m.missingScrollMax = 0;
            m.missingScroll = 0;
            if (d.missing.isEmpty()) {
                String s = "Не на своих местах:";
                int w = font.width(s) + pad * 2;
                int h = pad * 2 + fh;
                panel(g, px, py, w, h);
                g.text(font, s, px + pad, py + pad, yellow);
                afterY = py + h + 5;
            }
        }

        if (!d.arrangeableSlots.isEmpty()) {
            String label = "Расставить всё";
            boolean hov = InventoryManager.inside(mouseX, mouseY, px, afterY, font.width(label), fh + 2);
            g.text(font, label, px, afterY, hov ? accent : white);
            InventoryManager.rect(m.arrangeBtnRect, px, afterY, font.width(label), fh + 2);
        }

        drawGhosts(g, guiLeft, guiTop, m, active, d);
    }

    private static void drawGhosts(GuiGraphicsExtractor g, int guiLeft, int guiTop,
                                   InventoryManager m, InventoryPreset active, InventoryDiff d) {
        if (!m.highlightSlots()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var menu = mc.player.inventoryMenu;

        for (int slot : d.wrongSlots) {
            if (slot >= menu.slots.size()) {
                continue;
            }
            InvItem want = active.items.get(slot);
            if (want == null) {
                continue;
            }
            ItemStack desired = want.toStack();
            if (desired.isEmpty()) {
                continue;
            }
            boolean arrangeable = d.arrangeableSlots.contains(slot);
            if (!arrangeable && !m.showMissing()) {
                continue;
            }
            boolean empty = menu.getSlot(slot).getItem().isEmpty();
            int sx = guiLeft + menu.getSlot(slot).x;
            int sy = guiTop + menu.getSlot(slot).y;

            int bc = arrangeable ? 0xC0FFD23F : 0xC0FF5555;
            g.fill(sx - 1, sy - 1, sx + 17, sy, bc);
            g.fill(sx - 1, sy + 16, sx + 17, sy + 17, bc);
            g.fill(sx - 1, sy, sx, sy + 16, bc);
            g.fill(sx + 16, sy, sx + 17, sy + 16, bc);

            if (empty) {
                g.item(desired, sx, sy);
                g.fill(sx, sy, sx + 16, sy + 16, 0x808B8B8B);
                g.itemDecorations(mc.font, desired, sx, sy);
            } else {
                // Half-size ghost in the corner of an occupied slot.
                g.fakeItem(desired, sx + 6, sy + 6);
            }
        }
    }

    /** Vanilla-style tooltip panel. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        final int BG = 0xF0100010;
        final int B1 = 0x505000FF;
        final int B2 = 0x5028007F;
        int x2 = x + w, y2 = y + h;
        g.fill(x + 1, y, x2 - 1, y + 1, BG);
        g.fill(x + 1, y2 - 1, x2 - 1, y2, BG);
        g.fill(x, y + 1, x2, y2 - 1, BG);
        g.fillGradient(x + 1, y + 2, x + 2, y2 - 2, B1, B2);
        g.fillGradient(x2 - 2, y + 2, x2 - 1, y2 - 2, B1, B2);
        g.fill(x + 2, y + 1, x2 - 2, y + 2, B1);
        g.fill(x + 2, y2 - 2, x2 - 2, y2 - 1, B2);
    }
}
