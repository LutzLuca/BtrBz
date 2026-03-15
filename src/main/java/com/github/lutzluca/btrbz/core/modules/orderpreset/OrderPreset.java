package com.github.lutzluca.btrbz.core.modules.orderpreset;

import com.github.lutzluca.btrbz.widgets.Renderable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public sealed interface OrderPreset permits OrderPreset.Volume, OrderPreset.Max, OrderPreset.Clipboard {

    record Volume(int amount) implements OrderPreset {

        @Override
        public @NotNull String toString() {
            return String.valueOf(this.amount);
        }
    }

    record Clipboard(int amount) implements OrderPreset {

        @Override
        public @NotNull String toString() {
            return String.valueOf(this.amount);
        }
    }

    record Max() implements OrderPreset {
        
        @Override
        public @NotNull String toString() {
            return "Max";
        }
    }

    class RenderableEntry implements Renderable {
        @Getter
        private final OrderPreset preset;
        @Getter
        @Setter
        private boolean disabled = false;
        private List<Component> tooltipLines = null;
        private final Component displayText;
        private final int backgroundColor;

        public RenderableEntry(OrderPreset preset) {
            this.preset = preset;
            this.displayText = Component.literal(preset.toString());
            this.backgroundColor = switch (preset) {
                case OrderPreset.Max() -> 0x80404020;
                case OrderPreset.Clipboard(int amount) -> 0x80204080;
                default -> 0x80000000;
            };
        }

        public void setTooltipLines(List<Component> lines) {
            this.tooltipLines = lines;
        }

        @Override
        public void render(
            net.minecraft.client.gui.GuiGraphics graphics,
            int x, int y, int width, int height,
            int mouseX, int mouseY, float delta,
            boolean hovered
        ) {
            var font = Minecraft.getInstance().font;

            int bgColor = hovered && !disabled ? 0x60FFFFFF : this.backgroundColor;
            graphics.fill(x, y, x + width, y + height, bgColor);

            int textColor = disabled ? 0xFF888888 : 0xFFFFFFFF;
            int textY = y + (height - font.lineHeight) / 2;
            graphics.drawString(font, this.displayText, x + 4, textY, textColor);
        }

        @Override
        public List<Component> getTooltip() {
            return this.tooltipLines;
        }
    }
}
