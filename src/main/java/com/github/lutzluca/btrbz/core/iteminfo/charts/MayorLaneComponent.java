package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedTextRow;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.github.lutzluca.coflnet.MayorTerm;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Recorded terms occupy their own lane, without marking the price plot as causal. */
public final class MayorLaneComponent extends BaseUIComponent {
    private final HistoryViewport viewport;
    private final Supplier<List<MayorTerm>> mayors;
    private final RetainedTextRow text = new RetainedTextRow();
    private final FormattedCharSequence title = Component.literal("Mayor").getVisualOrderText();
    private List<MayorTerm> cachedTerms = List.of();
    private List<Region> regions = List.of();
    private long revision = -1;
    private int cachedWidth = -1;
    private MayorTerm tooltipTerm;

    public MayorLaneComponent(HistoryViewport viewport, Supplier<List<MayorTerm>> mayors) {
        this.viewport = viewport;
        this.mayors = mayors;
        this.sizing(Sizing.fill(100), Sizing.fixed(18));
        this.cursorStyle(CursorStyle.POINTER);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var terms = this.mayors.get();
        if (this.revision != this.viewport.revision() || this.cachedWidth != this.width
            || !terms.equals(this.cachedTerms)) {
            this.rebuild(terms);
        }
        var font = Minecraft.getInstance().font;
        int labelY = this.y + (this.height - font.lineHeight) / 2;
        this.text.begin();
        this.text.draw(graphics, font, this.title, this.x + 2, labelY, UiStyles.palette().muted(), false);
        MayorTerm hovered = null;
        var root = this.root();
        boolean visibleHover = root != null && root.childAt(mouseX, mouseY) == this;
        for (var region : this.regions) {
            graphics.fill(this.x + region.left(), this.y + 2, this.x + region.right(), this.y + this.height - 2,
                region.color());
            if (region.name() != null) {
                this.text.draw(graphics, font, region.name(), this.x + region.left() + 4, labelY, 0xFFA69E91, false);
            }
            if (visibleHover && mouseX >= this.x + region.left() && mouseX < this.x + region.right()) {
                hovered = region.term();
            }
        }
        if (!Objects.equals(hovered, this.tooltipTerm)) {
            this.tooltipTerm = hovered;
            this.updateTooltip(hovered);
        }
    }

    private void rebuild(List<MayorTerm> terms) {
        this.cachedTerms = List.copyOf(terms);
        this.revision = this.viewport.revision();
        this.cachedWidth = this.width;
        var sorted = terms.stream().sorted(Comparator.comparing(MayorTerm::start)).toList();
        var result = new ArrayList<Region>();
        var font = Minecraft.getInstance().font;
        for (int index = 0; index < sorted.size(); index++) {
            var term = sorted.get(index);
            if (!term.end().isAfter(this.viewport.start()) || !term.start().isBefore(this.viewport.end())) {
                continue;
            }
            int left = this.position(term.start());
            int right = this.position(term.end());
            var name = Component.literal(term.name()).getVisualOrderText();
            result.add(new Region(left, right, font.width(name) + 8 <= right - left ? name : null,
                index % 2 == 0 ? 0x44514D45 : 0x44302E2B, term));
        }
        this.regions = List.copyOf(result);
    }

    private int position(Instant time) {
        double fraction = (double) (time.toEpochMilli() - this.viewport.start().toEpochMilli())
            / (this.viewport.end().toEpochMilli() - this.viewport.start().toEpochMilli());
        int plotWidth = Math.max(1, this.width - HistoryChartComponent.LEFT - HistoryChartComponent.RIGHT);
        return HistoryChartComponent.LEFT + (int) Math.round(Math.clamp(fraction, 0, 1) * plotWidth);
    }

    private void updateTooltip(MayorTerm term) {
        if (term == null) {
            this.tooltip(List.<Component>of());
            return;
        }
        var lines = new ArrayList<Component>();
        lines.add(Component.literal("Recorded mayor: ").withStyle(UiStyles.muted())
            .append(Component.literal(term.name()).withStyle(UiStyles.primary())));
        for (var perk : term.perks()) {
            lines.add(Component.literal(perk.name()).withStyle(UiStyles.label())
                .append(Component.literal(perk.description() == null || perk.description().isBlank()
                    ? "" : ": " + perk.description()).withStyle(UiStyles.muted())));
        }
        this.tooltip(WidgetTooltips.wrapped(lines));
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        var root = this.root();
        return root != null && root.childAt((int) mouseX, (int) mouseY) == this
            && super.shouldDrawTooltip(mouseX, mouseY);
    }

    @Override
    public void drawTooltip(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.shouldDrawTooltip(mouseX, mouseY)) {
            graphics.tooltip(Minecraft.getInstance().font, this.tooltip(), mouseX, mouseY,
                HistoryInspectionTooltip.POSITIONER, null);
        }
    }

    private record Region(int left, int right, FormattedCharSequence name, int color, MayorTerm term) {}
}
