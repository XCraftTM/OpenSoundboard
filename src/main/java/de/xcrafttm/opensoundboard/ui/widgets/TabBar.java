package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Page navigation. Modern style: a vertical sidebar with icons and an accent marker on the
 * active page. Vanilla style: Minecraft's tab strip, as seen on the Create World screen.
 */
public class TabBar extends Widget {

    public record Tab(Icons icon, Component label) {
    }

    public static final int MODERN_ITEM_H = 20;

    private final List<Tab> tabs;
    private final IntConsumer onSelect;
    private int selected;

    public TabBar(List<Tab> tabs, int selected, IntConsumer onSelect) {
        this.tabs = List.copyOf(tabs);
        this.selected = selected;
        this.onSelect = onSelect;
    }

    private int vanillaTabWidth() {
        return w / Math.max(1, tabs.size());
    }

    private int vanillaTabsX() {
        return x + (w - vanillaTabWidth() * tabs.size()) / 2;
    }

    private int indexAt(double mx, double my) {
        if (!contains(mx, my)) return -1;
        if (UiStyle.useVanillaComponents()) {
            int i = (int) ((mx - vanillaTabsX()) / vanillaTabWidth());
            return i >= 0 && i < tabs.size() ? i : -1;
        }
        int i = (int) ((my - y) / (MODERN_ITEM_H + 1));
        return i >= 0 && i < tabs.size() ? i : -1;
    }

    @Override
    public void draw(UiCanvas c) {
        if (UiStyle.useVanillaComponents()) {
            drawVanilla(c);
            return;
        }
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int iy = y + i * (MODERN_ITEM_H + 1);
            boolean isSelected = i == selected;
            boolean hover = c.hovered(x, iy, w, MODERN_ITEM_H);
            if (isSelected) {
                c.fillRect(x, iy, w, MODERN_ITEM_H, Theme.surfaceHover);
                c.fillRect(x, iy, 2, MODERN_ITEM_H, Theme.accent);
            } else if (hover) {
                c.fillRect(x, iy, w, MODERN_ITEM_H, Theme.surface);
            }
            int color = isSelected ? Theme.text : (hover ? Theme.text : Theme.textMuted);
            int ix = x + 9;
            if (tab.icon() != null) {
                c.icon(tab.icon(), ix, iy + (MODERN_ITEM_H - tab.icon().height) / 2, isSelected ? Theme.accent : color);
                ix += 13;
            }
            c.text(c.trimText(tab.label().getString(), x + w - 4 - ix), ix, c.centeredTextY(iy, MODERN_ITEM_H), color);
        }
    }

    private void drawVanilla(UiCanvas c) {
        int tw = vanillaTabWidth();
        int tx = vanillaTabsX();
        for (int i = 0; i < tabs.size(); i++) {
            int bx = tx + i * tw;
            boolean isSelected = i == selected;
            boolean hover = c.hovered(bx, y, tw, h);
            String sprite = "widget/tab" + (isSelected ? "_selected" : "") + (hover ? "_highlighted" : "");
            c.sprite(sprite, bx, y, tw, h);
            if (isSelected) c.vanillaListBackground(bx + 2, y + 2, tw - 4, h - 2);
            String text = c.trimText(tabs.get(i).label().getString(), tw - 8);
            int ty = y + (h - c.lineHeight()) / 2 + (isSelected ? 0 : 2);
            c.centeredText(text, bx + tw / 2, ty, UiStyle.VANILLA_TEXT);
            if (isSelected) {
                int lineW = Math.min(c.textWidth(text), tw - 4);
                c.fillRect(bx + (tw - lineW) / 2, ty + c.lineHeight() + 1, lineW, 1, UiStyle.VANILLA_TEXT);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int i = indexAt(mx, my);
        if (i < 0) return false;
        if (i != selected) {
            UiSound.click();
            selected = i;
            onSelect.accept(i);
        }
        return true;
    }
}
