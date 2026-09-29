package com.github.lutzluca.btrbz.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** Generic content toast; it has no input handling or alert state. */
final class BtrBzToast implements Toast {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/system");
    private static final int WIDTH = 240;
    private static final int CONTENT_X = 18;
    private static final int DETAILS_Y = 19;
    private static final int LINE_HEIGHT = 12;
    private static final int ICON_SIZE = 16;
    private static final int MAX_DETAIL_LINES = 11;
    private static final long VISIBLE_MS = 5_000L;

    private final List<TextLine> textLines;
    private final Optional<ItemStack> icon;
    private final BooleanSupplier valid;
    private final int height;
    private Toast.Visibility wantedVisibility = Toast.Visibility.HIDE;

    BtrBzToast(Font font, Component title, List<Component> details, Optional<ItemStack> icon, BooleanSupplier valid) {
        this.icon = icon;
        this.valid = valid;

        var wrapped = new ArrayList<TextLine>();
        if (details.isEmpty() && this.icon.isEmpty()) {
            var lines = font.split(title, WIDTH - CONTENT_X - 12);
            int lineCount = Math.min(lines.size(), MAX_DETAIL_LINES);
            int textHeight = font.lineHeight + Math.max(0, lineCount - 1) * LINE_HEIGHT;
            this.height = Math.max(24, textHeight + 14);
            int top = (this.height - textHeight) / 2;

            for (int line = 0; line < lineCount; line++) {
                var text = lines.size() > lineCount && line == lineCount - 1
                    ? Component.literal("…").getVisualOrderText() : lines.get(line);
                wrapped.add(new TextLine(text, CONTENT_X, top + line * LINE_HEIGHT));
            }
            this.textLines = List.copyOf(wrapped);
            return;
        }

        int y = DETAILS_Y;
        for (int index = 0; index < details.size() && wrapped.size() < MAX_DETAIL_LINES; index++) {
            boolean besideIcon = index == 0 && this.icon.isPresent();
            int textX = CONTENT_X + (besideIcon ? ICON_SIZE + 4 : 0);
            var lines = font.split(details.get(index), WIDTH - textX - 12);
            int room = MAX_DETAIL_LINES - wrapped.size() - (details.size() - index - 1);
            if (room <= 0) {
                break;
            }
            int lineCount = Math.min(lines.size(), room);
            for (int line = 0; line < lineCount; line++) {
                var text = lines.size() > room && line == lineCount - 1
                    ? Component.literal("…").getVisualOrderText() : lines.get(line);
                wrapped.add(new TextLine(text, textX, y + (besideIcon ? 3 : 0) + line * LINE_HEIGHT));
            }
            y += besideIcon ? Math.max(ICON_SIZE, lineCount * LINE_HEIGHT) + 1 : lineCount * LINE_HEIGHT;
        }

        wrapped.addFirst(new TextLine(title.getVisualOrderText(), CONTENT_X, 7));
        this.textLines = List.copyOf(wrapped);
        this.height = Math.max(y, DETAILS_Y + (this.icon.isPresent() ? ICON_SIZE : LINE_HEIGHT)) + 3;
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return this.wantedVisibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        double displayTime = VISIBLE_MS * manager.getNotificationDisplayTimeMultiplier();
        this.wantedVisibility = this.valid.getAsBoolean() && fullyVisibleForMs < displayTime
            ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    @Override
    public float yPos(int firstSlotIndex) {
        return firstSlotIndex * Toast.SLOT_HEIGHT;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, this.width(), this.height());
        this.icon.ifPresent(stack -> graphics.fakeItem(stack, CONTENT_X, DETAILS_Y));
        for (var line : this.textLines) {
            graphics.text(font, line.text(), line.x(), line.y(), 0xFFAAAAAA, false);
        }
    }

    private record TextLine(FormattedCharSequence text, int x, int y) {}
}
