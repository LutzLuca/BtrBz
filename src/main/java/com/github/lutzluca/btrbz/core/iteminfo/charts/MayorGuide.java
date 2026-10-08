package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.github.lutzluca.coflnet.MayorTerm;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Matching term shading for the plots with details confined to the name inset. */
final class MayorGuide {
    static final int HEIGHT = 14;
    private final HistoryViewport viewport;
    private final Supplier<List<MayorTerm>> mayors;
    private final RetainedTextRow text = new RetainedTextRow();
    private List<MayorTerm> cachedTerms = List.of();
    private List<Region> regions = List.of();
    private List<ClientTooltipComponent> tooltip = List.of();
    private long revision = -1;
    private int cachedWidth = -1;
    private MayorTerm tooltipTerm;

    MayorGuide(HistoryViewport viewport, Supplier<List<MayorTerm>> mayors) {
        this.viewport = viewport;
        this.mayors = mayors;
    }

    void drawShading(OwoUIGraphics graphics, int x, int top, int width, int bottom) {
        this.prepare(width);
        for (var region : this.regions) {
            graphics.fill(x + region.left(), top, x + region.right(), bottom, region.color());
            if (region.boundary()) {
                graphics.fill(x + region.left(), top, x + region.left() + 1, bottom, 0x285C6773);
            }
        }
    }

    void drawNames(
        OwoUIGraphics graphics,
        int x,
        int y,
        int width,
        int mouseX,
        int mouseY,
        boolean unobstructed
    ) {
        this.prepare(width);
        this.text.begin();
        MayorTerm hovered = null;
        for (var region : this.regions) {
            if (region.boundary()) {
                graphics.fill(x + region.left(), y + HEIGHT - 5, x + region.left() + 1, y + HEIGHT - 1,
                    0xFF66655F);
            }
            if (region.name() != null) {
                this.text.draw(graphics, Minecraft.getInstance().font, region.name(), x + region.left() + 3, y + 1,
                    UiStyles.palette().muted(), false);
            }
            if (unobstructed && mouseY >= y
                && mouseY < y + HEIGHT
                && mouseX >= x + region.left()
                && mouseX < x + region.right()) {
                hovered = region.term();
            }
        }
        if (!Objects.equals(hovered, this.tooltipTerm)) {
            this.tooltipTerm = hovered;
            this.tooltip = this.details(hovered);
        }
    }

    List<ClientTooltipComponent> tooltip() {
        return this.tooltip;
    }

    private void prepare(int width) {
        var terms = this.mayors.get();
        if (this.revision != this.viewport.revision() || this.cachedWidth != width
            || !terms.equals(this.cachedTerms)) {
            this.rebuild(terms, width);
        }
    }

    private void rebuild(List<MayorTerm> terms, int width) {
        this.cachedTerms = List.copyOf(terms);
        this.revision = this.viewport.revision();
        this.cachedWidth = width;
        var result = new ArrayList<Region>();
        var ordered = terms.stream().sorted(Comparator.comparing(MayorTerm::start)).toList();
        for (int index = 0; index < ordered.size(); index++) {
            var term = ordered.get(index);
            if (!term.end().isAfter(this.viewport.start()) || !term.start().isBefore(this.viewport.end())) {
                continue;
            }
            int left = this.position(term.start(), width);
            int right = this.position(term.end(), width);
            var name = Component.literal(term.name()).getVisualOrderText();
            boolean boundary = !term.start().isBefore(this.viewport.start());
            result.add(new Region(left, right, Minecraft.getInstance().font.width(name) + 6 <= right - left
                ? name : null, boundary, index % 2 == 0 ? 0xFF30283D : 0xFF20242A, term));
        }
        this.regions = List.copyOf(result);
    }

    private int position(Instant time, int width) {
        double fraction = (double) (time.toEpochMilli() - this.viewport.start().toEpochMilli())
            / (this.viewport.end().toEpochMilli() - this.viewport.start().toEpochMilli());
        return (int) Math.round(Math.clamp(fraction, 0, 1) * width);
    }

    private List<ClientTooltipComponent> details(MayorTerm term) {
        if (term == null) {
            return List.of();
        }
        var lines = new ArrayList<Component>();
        lines.add(Component.literal("Recorded mayor: ").withStyle(UiStyles.muted())
            .append(Component.literal(term.name()).withStyle(UiStyles.primary())));
        lines.add(Component.literal("From " + HistoryTime.detailed(term.start())).withStyle(UiStyles.muted()));
        lines.add(Component.literal("Until " + HistoryTime.detailed(term.end())).withStyle(UiStyles.muted()));
        for (var perk : term.perks()) {
            lines.add(Component.literal(perk.name()).withStyle(UiStyles.label())
                .append(Component.literal(perk.description() == null || perk.description().isBlank()
                    ? "" : ": " + perk.description()).withStyle(UiStyles.muted())));
        }
        return WidgetTooltips.wrapped(lines);
    }

    private record Region(
        int left, int right, FormattedCharSequence name, boolean boundary, int color, MayorTerm term
    ) {}
}
