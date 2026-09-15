package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.mojang.blaze3d.platform.InputConstants;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** The same item and styled name presentation is used for results, selection and saved alerts. */
final class AlertProductRow extends FlowLayout {
    private final @Nullable Runnable select;

    AlertProductRow(
        BazaarData data,
        IndexedProduct product,
        int width,
        boolean distinguishId,
        @Nullable Runnable select
    ) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.HORIZONTAL);
        this.select = select;
        this.padding(Insets.of(6));
        this.gap(7);
        this.verticalAlignment(VerticalAlignment.CENTER);
        this.cursorStyle(select == null ? CursorStyle.POINTER : CursorStyle.HAND);
        var name = GameUtils.legacyFormattedComponent(product.formattedName());
        this.tooltip(Component.literal(product.productId()));

        var stack = data.productStack(product);
        stack.ifPresentOrElse(item -> this.child(BazaarUi.item(item, 20)),
            () -> this.child(BazaarUi.text("?", BazaarStyles.MUTED_TEXT).sizing(Sizing.fixed(20))));
        var text = UIContainers.verticalFlow(Sizing.expand(100), Sizing.content());
        text.gap(3);
        var label = BazaarUi.text("", BazaarStyles.PRIMARY_TEXT);
        label.text(name);
        label.maxWidth(Math.max(45, width - 50));
        text.child(label);
        if (distinguishId) {
            text.child(BazaarUi.text(product.productId(), BazaarStyles.MUTED_TEXT)
                .maxWidth(Math.max(45, width - 50)));
        }
        this.child(text);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        boolean highlighted = this.select != null
            && (this.isInBoundingBox(mouseX, mouseY) || this.focusHandler().focused() == this);
        WidgetSurfaces.drawRoundedPanel(graphics, this.x(), this.y(), this.width(), this.height(),
            highlighted ? BazaarStyles.ROW_HOVER : 0x18000000, 3);
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return this.select != null;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent event, boolean doubled) {
        if (this.select != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.select.run();
            return true;
        }
        return super.onMouseDown(event, doubled);
    }

    @Override
    public boolean onKeyPress(KeyEvent event) {
        if (this.select != null
            && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE
                || event.key() == InputConstants.KEY_NUMPADENTER)) {
            this.select.run();
            return true;
        }
        return super.onKeyPress(event);
    }
}
