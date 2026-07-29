package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHostOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** BtrBz-owned host screen for only the full Order Book widget. */
public final class OrderBookScreen extends Screen {
    private final Screen parent;
    private final ProductIdentity product;
    private final String productName;
    private final WidgetHost host;

    public OrderBookScreen(
        Screen parent,
        ProductIdentity product,
        String productName,
        WidgetHost host
    ) {
        super(Component.literal(productName + " Order Book"));
        this.parent = parent;
        this.product = product;
        this.productName = productName;
        this.host = host;
    }

    public ProductIdentity product() {
        return this.product;
    }

    public String productId() {
        return this.product.bazaarProductId().orElse(this.product.strippedName());
    }

    public String productName() {
        return this.productName;
    }

    @Override
    public void onClose() {
        this.host.dispose();
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void removed() {
        this.host.dispose();
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The screen deliberately supplies its own dimmed background.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        this.host.render(
            graphics,
            mouseX,
            mouseY,
            delta,
            new WidgetCanvas(0, 0, this.width, this.height),
            WidgetHostOptions.runtime(true),
            this
        );
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.host.mouseClicked(event, doubleClick) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return this.host.mouseReleased(event) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return this.host.mouseDragged(event, deltaX, deltaY)
            || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return this.host.mouseScrolled(mouseX, mouseY, horizontal, vertical)
            || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return this.host.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
