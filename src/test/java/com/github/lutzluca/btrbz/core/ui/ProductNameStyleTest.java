package com.github.lutzluca.btrbz.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.github.lutzluca.btrbz.core.appearance.AppearanceConfig;
import com.github.lutzluca.btrbz.core.appearance.UiColor;
import com.github.lutzluca.btrbz.core.appearance.UiTheme;

class ProductNameStyleTest {
    @AfterEach
    void restoreAppearance() {
        UiStyles.configure(new AppearanceConfig());
    }

    @Test
    void abbreviatingAndDisablingFormattingPreserveTheOriginalNameEvidence() {
        var uncommon = Style.EMPTY.withColor(0x55FF55);
        var suffix = Style.EMPTY.withColor(0xFFFF55).withBold(true);
        var original = Component.literal("Enchanted ").withStyle(uncommon)
            .append(Component.literal("Sugar").withStyle(suffix));
        var config = new AppearanceConfig();
        config.theme = new UiTheme("Test",
            config.theme.palette().withColor(UiColor.TextPrimary, 0xFFE0E0E0));
        UiStyles.configure(config);
        var shortened = UiStyles.productName(original, true);
        var segments = new ArrayList<String>();
        shortened.visit((style, text) -> {
            if (!text.isEmpty()) {
                segments.add(text + ":" + style.getColor().getValue() + ":" + style.isBold());
            }
            return Optional.empty();
        }, Style.EMPTY);
        Assertions.assertEquals(List.of("Ench. :5635925:false", "Sugar:16777045:true"), segments);
        config.formattedProductNames = false;
        UiStyles.configure(config);
        var plain = UiStyles.productName(original, true);
        Assertions.assertEquals("Ench. Sugar", plain.getString());
        Assertions.assertEquals(0xE0E0E0, plain.getStyle().getColor().getValue());
        Assertions.assertFalse(plain.getStyle().isBold());
        Assertions.assertEquals("Enchanted Sugar", original.getString());
        Assertions.assertEquals(uncommon, original.getStyle());
        Assertions.assertEquals(suffix, original.getSiblings().getFirst().getStyle());
    }
}
