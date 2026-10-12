package com.github.lutzluca.btrbz.core.appearance;

import com.github.lutzluca.btrbz.core.productinfo.ProductInfoTooltip;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderMessages;
import com.github.lutzluca.btrbz.core.ui.UiComponents;
import com.github.lutzluca.btrbz.core.ui.UiButton;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.ui.UiSurfaces;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetChrome;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Real widget views and text formatters rendered in an isolated, read-only draft style scope. */
final class AppearancePreview extends FlowLayout {
    private final Supplier<UiTheme> appearance;
    private final WidgetRegistry registry;
    private final int contentWidth;
    private final List<WidgetView<?, ?, ?>> views = new ArrayList<>();

    AppearancePreview(
        Supplier<UiTheme> appearance,
        WidgetRegistry registry,
        int width
    ) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.appearance = appearance;
        this.registry = registry;
        this.contentWidth = width - 12;
        this.gap(10);
        this.padding(Insets.right(5));
        this.refresh();
    }

    void refresh() {
        this.closeViews();
        this.clearChildren();
        UiStyles.preview(this.appearance.get(), () -> {
            this.child(UiComponents.label("Widgets", UiStyles.palette().textSecondary()));
            this.addWidget(this.registry.find(TrackedOrdersWidgetDefinition.ID).orElseThrow());
            this.addWidget(this.registry.find(DailyLimitWidgetDefinition.ID).orElseThrow());
            this.addWidget(this.registry.find(OrderValueWidgetDefinition.ID).orElseThrow());
            this.child(UiComponents.label("Chat notifications", UiStyles.palette().textSecondary()));
            var chat = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            chat.padding(Insets.of(6));
            chat.gap(6);
            chat.surface(Surface.flat(0x99000000));
            for (var message : TrackedOrderMessages.preview()) {
                var label = UiComponents.label("", UiStyles.palette().textPrimary());
                label.text(message);
                label.maxWidth(this.contentWidth - 12);
                label.shadow(true);
                chat.child(label);
            }
            this.child(chat);
            this.child(UiComponents.label("Price tooltip", UiStyles.palette().textSecondary()));
            var tooltip = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            tooltip.padding(Insets.of(6));
            tooltip.gap(2);
            tooltip.surface(Surface.flat(0xF0100010));
            for (var line : ProductInfoTooltip.preview()) {
                var label = UiComponents.label("", 0xFFFFFFFF);
                label.text(line);
                label.maxWidth(this.contentWidth - 12);
                label.shadow(true);
                tooltip.child(label);
            }
            this.child(tooltip);
            this.child(UiComponents.label("Order highlights", UiStyles.palette().textSecondary()));
            this.child(new OrderSlots());
            this.child(UiComponents.label("Buttons", UiStyles.palette().textSecondary()));
            var buttons = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            buttons.gap(6);
            buttons.verticalAlignment(VerticalAlignment.CENTER);
            buttons.child(UiButton.text("Button", () -> {}));
            buttons.child(UiButton.text("Selected", () -> {}).selected(true));
            buttons.child(UiButton.text("Confirm", () -> {}).primary(true));
            this.child(buttons);
        });
    }

    private <D, C, A> void addWidget(WidgetDefinition<D, C, A> definition) {
        var preview = definition.getPreview().get();
        var config = definition.defaults();
        switch (config) {
            case TrackedOrdersWidgetConfig value -> value.contentWidth = this.contentWidth - 12;
            case OrderValueWidgetConfig value -> value.contentWidth = this.contentWidth - 12;
            default -> {
            }
        }
        var view = definition.getViewFactory().get();
        this.views.add(view);
        view.update(preview.data(), config, preview.session(), _ -> {});
        var panel = WidgetChrome.wrap(view.root());
        panel.surface((graphics, component) -> UiSurfaces.drawRoundedPanel(graphics,
            component.x(), component.y(), component.width(), component.height(), UiStyles.palette().panelBackground(),
            WidgetChrome.CORNER_RADIUS));
        this.child(panel);
    }

    void closeViews() {
        this.views.forEach(WidgetView::close);
        this.views.clear();
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        // The outer preview owns scrolling, including when the pointer is over a widget list.
        return false;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        UiStyles.preview(this.appearance.get(), () -> super.draw(graphics, mouseX, mouseY, partialTicks, delta));
    }

    @Override
    public void drawTooltip(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        UiStyles.preview(this.appearance.get(), () -> super.drawTooltip(graphics, mouseX, mouseY, partialTicks, delta));
    }

    private final class OrderSlots extends BaseUIComponent {
        private OrderSlots() {
            this.sizing(Sizing.fill(100), Sizing.fixed(36));
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            var palette = UiStyles.palette();
            String[] names = {"Best", "Matched", "Undercut"};
            int[] colors = {palette.best(), palette.matched(), palette.undercut()};
            for (int index = 0; index < colors.length; index++) {
                int slotX = this.x + index * this.width / 3;
                graphics.fill(slotX, this.y, slotX + 18, this.y + 18, 0xFF060606);
                graphics.fill(slotX + 1, this.y + 1, slotX + 17, this.y + 17, 0xFF282828);
                graphics.fill(slotX + 1, this.y + 1, slotX + 17, this.y + 17,
                    UiPalette.alpha(colors[index],
                        AppearancePreview.this.appearance.get().orderHighlightOpacity() / 100.0));
                graphics.text(Minecraft.getInstance().font, Component.literal(names[index]),
                    slotX, this.y + 23, palette.textSecondary(), false);
            }
        }
    }
}
