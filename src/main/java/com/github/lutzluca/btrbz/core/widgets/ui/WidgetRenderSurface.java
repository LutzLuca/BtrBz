package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.braid.core.TextureSurface;
import io.wispforest.owo.braid.core.BraidGraphics;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import org.joml.Matrix3x2f;

/**
 * Persistent final-density render target for one widget.
 *
 * <p>The complete widget tree is rasterized at the density it will occupy in the
 * framebuffer. The host then maps that texture onto the widget's GUI-scaled
 * bounds, avoiding a destructive logical-resolution downscale.</p>
 */
public final class WidgetRenderSurface implements AutoCloseable {
    private TextureSurface surface;
    private int width;
    private int height;

    public void render(
        OwoUIGraphics target,
        UIComponent child,
        int logicalWidth,
        int logicalHeight,
        int mouseX,
        int mouseY,
        float partialTicks,
        float delta,
        int targetX,
        int targetY,
        double scale
    ) {
        int guiScale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        double density = renderDensity(guiScale, scale);
        this.ensureSize(surfacePixels(logicalWidth, density), surfacePixels(logicalHeight, density));

        this.surface.beginRendering();
        try {
            var extracted = this.surface.guiRenderer.newGraphics(mouseX, mouseY);
            var graphics = BraidGraphics.create(extracted, this.surface);
            double previousDensity = WidgetSurfaceRenderContext.enter(density);
            try {
                graphics.push();
                try {
                    graphics.getMatrixStack().scale((float) density, (float) density);
                    graphics.translate(-child.x(), -child.y());
                    graphics.enableScissor(child.x(), child.y(), child.x() + logicalWidth, child.y() + logicalHeight);
                    try {
                        child.draw(graphics, mouseX, mouseY, partialTicks, delta);
                    } finally {
                        graphics.disableScissor();
                    }
                } finally {
                    graphics.pop();
                }
            } finally {
                WidgetSurfaceRenderContext.restore(previousDensity);
            }
        } finally {
            // endRendering submits and clears owo's extracted render state. It
            // must run even when a component failed after partially extracting.
            this.surface.endRendering();
        }

        target.push();
        try {
            target.translate(targetX, targetY);
            target.getMatrixStack().scale((float) scale, (float) scale);
            // Render targets use a bottom-left texture origin. Reverse only the V
            // coordinates so the GUI quad keeps its normal winding and bounds.
            target.guiRenderState.addGuiElement(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(this.surface.texture(), this.surface.registeredTexture.getSampler()),
                new Matrix3x2f(target.pose()),
                0,
                0,
                logicalWidth,
                logicalHeight,
                0f,
                1f,
                1f,
                0f,
                0xFFFFFFFF,
                target.scissorStack.peek()
            ));
        } finally {
            target.pop();
        }
    }

    static double renderDensity(int guiScale, double widgetScale) {
        return Math.max(1.0, Math.max(1, guiScale) * Math.max(0.0001, widgetScale));
    }

    static int surfacePixels(int logicalSize, double density) {
        return Math.max(1, (int) Math.ceil(Math.max(1, logicalSize) * Math.max(0.0001, density)));
    }

    private void ensureSize(int requestedWidth, int requestedHeight) {
        int safeWidth = Math.max(1, requestedWidth);
        int safeHeight = Math.max(1, requestedHeight);

        if (this.surface == null) {
            this.surface = new TextureSurface(safeWidth, safeHeight);
        } else if (this.width != safeWidth || this.height != safeHeight) {
            this.surface.resize(safeWidth, safeHeight);
        }

        this.width = safeWidth;
        this.height = safeHeight;
    }

    @Override
    public void close() {
        if (this.surface == null) return;

        try {
            this.surface.guiRenderer.close();
        } finally {
            this.surface.dispose();
        }
        this.surface = null;
        this.width = 0;
        this.height = 0;
    }
}
