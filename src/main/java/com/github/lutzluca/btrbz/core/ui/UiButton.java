package com.github.lutzluca.btrbz.core.ui;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    private final @Nullable UiIcon icon;
    private final @Nullable Component description;
    private final boolean compact;
    private @Nullable BooleanSupplier availability;
    private Component unavailable = Component.empty();
    private final RetainedText retainedText = new RetainedText();

    private boolean primary;
    private boolean selected;
    private boolean bottomAccent = true;
    private IntSupplier accent = () -> UiStyles.palette().accent();

    private @Nullable Component cachedMessage;
    private FormattedCharSequence cachedText = FormattedCharSequence.EMPTY;
    private int cachedTextWidth = -1;
    private long cachedTextRevision = -1;

    private UiButton(
        Component message,
        Consumer<ButtonComponent> action,
        @Nullable UiIcon icon,
        @Nullable Component description,
        boolean compact
    ) {
        super(message, action);
        this.icon = icon;
        this.description = description;
        this.compact = compact;
        this.textShadow(false);
        this.renderer((graphics, _, _) -> this.drawBackground(graphics));

        if (description != null) {
            this.bottomAccent = false;
            this.sizing(Sizing.fixed(compact ? 12 : 22));
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
        return new UiButton(text, action, null, null, false);
    }

    public static UiButton icon(Identifier texture, int sourceSize, Component description, Runnable action) {
        return new UiButton(Component.empty(), _ -> action.run(), new UiIcon(texture, sourceSize), description, false);
    }

    public static UiButton iconText(Identifier texture, int sourceSize, Component text, Runnable action) {
        return new UiButton(text, _ -> action.run(), new UiIcon(texture, sourceSize), null, false);
    }

    public static UiButton compactIcon(UiIcon icon, Component description, Runnable action) {
        return new UiButton(Component.empty(), _ -> action.run(), icon, description, true);
    }

    public UiButton enabledWhen(BooleanSupplier availability, Component unavailable) {
        this.availability = availability;
        this.unavailable = unavailable;
        return this;
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        if (this.availability != null) {
            boolean enabled = this.availability.getAsBoolean();
            if (this.active() != enabled) {
                this.active(enabled);
                this.tooltip(enabled ? this.description : this.unavailable);
            }
        }
        super.update(delta, mouseX, mouseY);
    }

    @Override
    public void drawFocusHighlight(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.drawFocusHighlight(graphics, mouseX, mouseY, partialTicks, delta);
        if (this.description != null && !this.isInBoundingBox(mouseX, mouseY)) {
            graphics.drawTooltip(Minecraft.getInstance().font,
                this.getRight(), this.getBottom(), this.tooltip());
        }
    }

    public UiButton primary(boolean primary) {
        this.primary = primary;
        return this;
    }

    public UiButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public UiButton accent(IntSupplier accent) {
        this.accent = accent;
        return this;
    }

    public UiButton bottomAccent(boolean visible) {
        this.bottomAccent = visible;
        return this;
    }

    private void drawBackground(OwoUIGraphics graphics) {
        if (this.compact) {
            if (this.active() && (this.isHovered() || this.isFocused())) {
                graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(),
                    UiStyles.palette().rowHover());
            }
            if (this.isFocused()) {
                graphics.drawRectOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                    UiStyles.palette().accent());
            }
            return;
        }
        var colors = UiStyles.buttons();
        boolean hover = this.isHovered();
        int color;

        if (!this.active()) {
            color = colors.disabled();
        } else if (this.primary) {
            color = hover ? colors.primaryHovered() : colors.primary();
        } else {
            color = this.selected
                ? hover ? colors.selectedHovered() : colors.selected()
                : hover ? colors.hovered() : colors.background();
        }

        int outline = this.isFocused() || this.selected && this.active()
            ? this.accent.getAsInt() : UiStyles.palette().outline();
        UiSurfaces.drawRoundedPanel(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(),
            this.active() ? outline : colors.disabled(), 3);
        UiSurfaces.drawRoundedPanel(graphics, this.getX() + 1, this.getY() + 1,
            Math.max(0, this.getWidth() - 2), Math.max(0, this.getHeight() - 2), color, 2);

        if (this.bottomAccent) {
            int border = (this.primary || this.selected) && this.active()
                ? this.accent.getAsInt()
                : hover ? colors.borderHovered() : UiStyles.palette().outline();
            graphics.fill(this.getX() + 3, this.getBottom() - 1, this.getRight() - 3, this.getBottom(), border);
        }
        if (this.isFocused()) {
            graphics.drawRectOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(),
                UiStyles.palette().accent());
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        var context = (OwoUIGraphics) graphics;
        this.renderer.draw(context, this, delta);

        var font = Minecraft.getInstance().font;
        var message = this.getMessage();
        int iconWidth = this.icon == null ? 0 : this.compact ? 7 : ICON_SIZE;
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
        var colors = UiStyles.buttons();
        int color = this.compact
            ? !this.active()
                ? UiStyles.palette().textMuted()
                : this.isHovered() ? UiStyles.palette().textPrimary() : UiStyles.palette().textSecondary()
            : !this.active() ? colors.disabledText() : this.primary ? colors.primaryText() : colors.text();

        if (this.icon != null) {
            this.icon.draw(context, contentX, this.getY() + (this.getHeight() - iconWidth) / 2, iconWidth, color);
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

}
