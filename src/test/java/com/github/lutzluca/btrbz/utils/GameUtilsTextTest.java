package com.github.lutzluca.btrbz.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
//? if >=26.2 {
/*import net.minecraft.network.chat.TextColor;
*///?}

class GameUtilsTextTest {

    @Nested
    @DisplayName("legacy formatted text")
    class LegacyFormattedText {

        @Test
        void parsesLegacyFormattingIntoComponentStyles() {
            var text = GameUtils.legacyFormattedComponent("§d§lThunderlord VII");

            assertEquals("Thunderlord VII", text.getString());
            //? if <26.2 {
            assertEquals(ChatFormatting.LIGHT_PURPLE.getColor(), text.getStyle().getColor().getValue());
            //?} else {
            /*assertEquals(TextColor.LIGHT_PURPLE.getValue(), text.getStyle().getColor().getValue());
            *///?}
            assertTrue(text.getStyle().isBold());
            assertEquals("§d§lThunderlord VII", GameUtils.legacyFormattedText(text));
        }

        @Test
        void preservesNamedColors() {
            var text = Component.literal("Troubled Bubble").withStyle(ChatFormatting.GOLD);

            assertEquals(ChatFormatting.GOLD + "Troubled Bubble", GameUtils.legacyFormattedText(text));
        }

        @Test
        void preservesBasicStyles() {
            var text = Component.literal("Habanero Tactics")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD, ChatFormatting.ITALIC);

            assertEquals(
                ChatFormatting.LIGHT_PURPLE
                    + ChatFormatting.BOLD.toString()
                    + ChatFormatting.ITALIC
                    + "Habanero Tactics",
                GameUtils.legacyFormattedText(text));
        }

        @Test
        void extractsMatchingFormattedSuffix() {
            var text = Component.empty()
                .append(Component.literal("BUY ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("Bank III").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));

            assertEquals(
                Optional.of(ChatFormatting.LIGHT_PURPLE + ChatFormatting.BOLD.toString() + "Bank III"),
                GameUtils.matchingLegacySuffix(text, "Bank III"));
        }
    }
}
