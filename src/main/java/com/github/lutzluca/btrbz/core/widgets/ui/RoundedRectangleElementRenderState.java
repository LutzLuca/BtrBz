package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.WidgetMath;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wispforest.owo.ui.core.OwoUIPipelines;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

final class RoundedRectangleElementRenderState implements GuiElementRenderState {
    private final Matrix3x2f pose;
    private final @Nullable ScreenRectangle scissorArea;

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private final int color;
    private final float[] perimeter;

    RoundedRectangleElementRenderState(
        Matrix3x2f pose,
        @Nullable ScreenRectangle scissorArea,
        int x,
        int y,
        int width,
        int height,
        int radius,
        int cornerSegments,
        int color
    ) {
        this.pose = pose;
        this.scissorArea = scissorArea;

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.color = color;
        this.perimeter = buildPerimeter(x, y, width, height, radius, cornerSegments);
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        vertices.addVertexWith2DPose(
            this.pose,
            this.x + this.width / 2f,
            this.y + this.height / 2f
        ).setColor(this.color);

        for (int i = 0; i < this.perimeter.length; i += 2) {
            vertices.addVertexWith2DPose(this.pose, this.perimeter[i], this.perimeter[i + 1])
                .setColor(this.color);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return OwoUIPipelines.GUI_TRIANGLE_FAN;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return this.scissorArea;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        var transformedBounds = new ScreenRectangle(this.x, this.y, this.width, this.height)
            .transformMaxBounds(this.pose);
        return this.scissorArea == null
            ? transformedBounds
            : this.scissorArea.intersection(transformedBounds);
    }

    static float[] buildPerimeter(
        int x,
        int y,
        int width,
        int height,
        int radius,
        int cornerSegments
    ) {
        int safeRadius = WidgetMath.clamp(radius, 0, Math.max(0, Math.min(width, height) / 2));
        int safeSegments = Math.max(1, cornerSegments);

        float[] points = new float[(5 + safeSegments * 4) * 2];
        int offset = 0;

        offset = put(points, offset, x + safeRadius, y);
        offset = putArc(points, offset, x + safeRadius, y + safeRadius, safeRadius, 270, 180, safeSegments);

        offset = put(points, offset, x, y + height - safeRadius);
        offset = putArc(points, offset, x + safeRadius, y + height - safeRadius, safeRadius, 180, 90, safeSegments);

        offset = put(points, offset, x + width - safeRadius, y + height);
        offset = putArc(points, offset, x + width - safeRadius, y + height - safeRadius, safeRadius, 90, 0, safeSegments);

        offset = put(points, offset, x + width, y + safeRadius);
        offset = putArc(points, offset, x + width - safeRadius, y + safeRadius, safeRadius, 0, -90, safeSegments);
        put(points, offset, x + safeRadius, y);
        return points;
    }

    private static int putArc(
        float[] points,
        int offset,
        int centerX,
        int centerY,
        int radius,
        int startDegrees,
        int endDegrees,
        int segments
    ) {
        double angleStep = Math.toRadians(endDegrees - startDegrees) / segments;

        for (int i = 1; i <= segments; i++) {
            double angle = Math.toRadians(startDegrees) + angleStep * i;
            offset = put(
                points,
                offset,
                (float) (centerX + Math.cos(angle) * radius),
                (float) (centerY + Math.sin(angle) * radius)
            );
        }

        return offset;
    }

    private static int put(float[] points, int offset, float x, float y) {
        points[offset] = x;
        points[offset + 1] = y;

        return offset + 2;
    }
}
