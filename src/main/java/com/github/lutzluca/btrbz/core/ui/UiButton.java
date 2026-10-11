package com.github.lutzluca.btrbz.core.ui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Shared rounded button with text, an icon, or an icon followed by text. */
public final class UiButton extends ButtonComponent {
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int CONTENT_INSET = 3;

    private final @Nullable Icon icon;
    private final @Nullable Component description;
    private final RetainedText retainedText = new RetainedText();

    private boolean primary;
    private boolean selected;
    private boolean bottomAccent = true;
    private int accent = UiStyles.palette().buttons().accent();

    private @Nullable Component cachedMessage;
    private FormattedCharSequence cachedText = FormattedCharSequence.EMPTY;
    private int cachedTextWidth = -1;
    private long cachedTextRevision = -1;

    private UiButton(
        Component message,
        Consumer<ButtonComponent> action,
        @Nullable Icon icon,
        @Nullable Component description
    ) {
        super(message, action);
        this.icon = icon;
        this.description = description;
        this.textShadow(false);
        this.renderer((graphics, _, _) -> this.drawBackground(graphics));

        if (description != null) {
            this.bottomAccent = false;
            this.sizing(Sizing.fixed(22));
            this.tooltip(description);
        } else {
            // owo's content width already includes text and eight pixels of padding.
            // Reserve the icon and its gap through the remaining content padding.
            int padding = 8 + (icon == null ? 0 : (ICON_SIZE + ICON_GAP) / 2);
            this.sizing(Sizing.content(padding), Sizing.fixed(22));
        }
    }

    public static UiButton text(String text, Runnable action) {
        return text(Component.literal(text), _ -> action.run());
    }

    public static UiButton text(Component text, Consumer<ButtonComponent> action) {
        return new UiButton(text, action, null, null);
    }

    public static UiButton icon(Identifier texture, int sourceSize, Component description, Runnable action) {
        return new UiButton(Component.empty(), _ -> action.run(), new Icon(texture, sourceSize), description);
    }

    public static UiButton iconText(Identifier texture, int sourceSize, Component text, Runnable action) {
        return new UiButton(text, _ -> action.run(), new Icon(texture, sourceSize), null);
    }

    public UiButton primary(boolean primary) {
        this.primary = primary;
        return this;
    }

    public UiButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public UiButton accent(int accent) {
        this.accent = accent;
        return this;
    }

    public UiButton bottomAccent(boolean visible) {
        this.bottomAccent = visible;
        return this;
    }

    private void drawBackground(OwoUIGraphics graphics) {
        var colors = UiStyles.palette().buttons();
        boolean hover = this.isHoveredOrFocused();
        int color;

        if (!this.active()) {
            color = hover ? colors.disabledHovered() : colors.disabled();
        } else if (this.primary) {
            color = hover ? colors.primaryHovered() : colors.primary();
        } else {
            color = hover ? colors.hovered() : this.selected ? colors.selected() : colors.background();
        }

        UiSurfaces.drawRoundedPanel(
            graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), color, 3);

        if (this.bottomAccent) {
            int border = (this.primary || this.selected) && this.active()
                ? this.accent
                : hover ? colors.borderHovered() : UiStyles.palette().border();
            graphics.fill(this.getX() + 3, this.getBottom() - 1, this.getRight() - 3, this.getBottom(), border);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var context = (OwoUIGraphics) graphics;
        this.renderer.draw(context, this, delta);

        var font = Minecraft.getInstance().font;
        var message = this.getMessage();
        int iconWidth = this.icon == null ? 0 : ICON_SIZE;
        int gap = this.icon == null || message.getString().isEmpty() ? 0 : ICON_GAP;
        int textWidth = Math.max(0, this.getWidth() - CONTENT_INSET * 2 - iconWidth - gap);
        long revision = TextRenderRevision.current();

        if (this.cachedMessage != message || this.cachedTextWidth != textWidth || this.cachedTextRevision != revision) {
            this.cachedMessage = message;
            this.cachedTextWidth = textWidth;
            this.cachedTextRevision = revision;
            this.cachedText = UiText.ellipsize(message, textWidth);
        }

        int contentWidth = iconWidth + gap + font.width(this.cachedText);
        int contentX = this.getX() + (this.getWidth() - contentWidth) / 2;
        var colors = UiStyles.palette().buttons();
        int color = this.active() ? colors.text() : colors.disabledText();

        if (this.icon != null) {
            context.blit(RenderPipelines.GUI_TEXTURED, this.icon.texture(), contentX,
                this.getY() + (this.getHeight() - ICON_SIZE) / 2,
                0, 0, ICON_SIZE, ICON_SIZE,
                this.icon.sourceSize(), this.icon.sourceSize(), this.icon.sourceSize(), this.icon.sourceSize(), color);
        }

        this.retainedText.draw(context, font, this.cachedText,
            contentX + iconWidth + gap, this.getY() + (this.getHeight() - 8) / 2, color, this.textShadow());
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return this.description == null
            ? super.createNarrationMessage()
            : Component.translatable("gui.narrate.button", this.description);
    }

    private record Icon(Identifier texture, int sourceSize) {}
}
