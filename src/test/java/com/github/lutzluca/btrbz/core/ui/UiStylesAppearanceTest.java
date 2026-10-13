package com.github.lutzluca.btrbz.core.ui;

import com.github.lutzluca.btrbz.core.appearance.AppearanceConfig;
import com.github.lutzluca.btrbz.core.appearance.ThemePreset;
import com.github.lutzluca.btrbz.core.appearance.UiPalette;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class UiStylesAppearanceTest {
    @AfterEach
    void restoreAppearance() {
        UiStyles.configure(ThemePreset.BtrBzDark.theme());
    }

    @Test
    void softerNamesPreservePerSpanFormattingAndNavigationWhileAbbreviating() {
        var config = new AppearanceConfig();
        config.theme = config.theme.withProductNames(true, 75);
        UiStyles.configure(config.theme);
        var link = new ClickEvent.RunCommand("/bz Enchanted Diamond");
        var name = Component.literal("Enchanted ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal("Diamond").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD))
            .withStyle(style -> style.withClickEvent(link));
        var result = UiStyles.productName(name, true);
        var styles = new ArrayList<Style>();
        result.visit((style, text) -> {
            if (!text.isEmpty()) {
                styles.add(style);
            }
            return Optional.empty();
        }, Style.EMPTY);
        Assertions.assertEquals("Ench. Diamond", result.getString());
        Assertions.assertEquals("Enchanted Diamond", name.getString());
        Assertions.assertEquals(2, styles.size());
        Assertions.assertEquals(UiPalette.blend(config.theme.palette().textSecondary(),
            0x55FF55, 0.75) & 0xFFFFFF, styles.getFirst().getColor().getValue());
        Assertions.assertEquals(UiPalette.blend(config.theme.palette().textSecondary(),
            0x5555FF, 0.75) & 0xFFFFFF, styles.getLast().getColor().getValue());
        Assertions.assertTrue(styles.getLast().isBold());
        styles.forEach(style -> Assertions.assertEquals(link, style.getClickEvent()));

        config.theme = config.theme.withProductNames(true, 100);
        UiStyles.configure(config.theme);
        Assertions.assertEquals(name, UiStyles.productName(name, false));
        config.theme = config.theme.withProductNames(false, 100);
        UiStyles.configure(config.theme);
        Assertions.assertEquals(config.theme.palette().textPrimary() & 0xFFFFFF,
            UiStyles.productName(name, true).getStyle().getColor().getValue());
    }

    @Test
    void draftScopesRestoreAppliedStylesAfterNestedAndFailingPreviewsWithoutInvalidation() {
        var applied = new AppearanceConfig();
        UiStyles.configure(applied.theme);
        var draft = new AppearanceConfig();
        draft.theme = ThemePreset.Nord.theme();
        draft.theme = draft.theme.withProductNames(true, 0);
        long appearanceRevision = UiStyles.appearanceRevision();
        long textRevision = TextRenderRevision.current();
        UiStyles.configure(applied.theme.withName("Renamed").withOrderHighlightOpacity(11));
        Assertions.assertEquals(appearanceRevision, UiStyles.appearanceRevision());
        Assertions.assertEquals(textRevision, TextRenderRevision.current());
        Assertions.assertThrows(IllegalStateException.class, () -> UiStyles.preview(draft.theme, () -> {
            Assertions.assertEquals(draft.theme.palette(), UiStyles.palette());
            Assertions.assertEquals(draft.theme.palette().textSecondary() & 0xFFFFFF,
                UiStyles.productName("§aDiamond").getSiblings().getFirst().getStyle().getColor().getValue());
            UiStyles.preview(applied.theme, () -> Assertions.assertEquals(applied.theme.palette(), UiStyles.palette()));
            Assertions.assertEquals(draft.theme.palette(), UiStyles.palette());
            throw new IllegalStateException("draft render failed");
        }));
        Assertions.assertEquals(applied.theme.palette(), UiStyles.palette());
        Assertions.assertEquals(applied.theme.palette().buttons(), UiStyles.buttons());
        Assertions.assertEquals(appearanceRevision, UiStyles.appearanceRevision());
        Assertions.assertEquals(textRevision, TextRenderRevision.current());
        Assertions.assertEquals(0x55FF55,
            UiStyles.productName(Component.literal("Diamond").withStyle(ChatFormatting.GREEN), false)
                .getStyle().getColor().getValue());
        applied.theme = applied.theme.withProductNames(true, 75);
        UiStyles.configure(applied.theme);
        Assertions.assertTrue(UiStyles.appearanceRevision() > appearanceRevision);
        Assertions.assertTrue(TextRenderRevision.current() > textRevision);
    }

}
