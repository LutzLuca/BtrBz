package com.github.lutzluca.btrbz.data.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import net.minecraft.nbt.CompoundTag;

class EnchantedBookIdParserTest {

    @Nested
    @DisplayName("custom data")
    class CustomData {

        @Test
        void derivesIdFromSingleEnchantment() {
            var customData = new CompoundTag();
            var enchantments = new CompoundTag();
            enchantments.putInt("quick_bite", 5);
            customData.put("enchantments", enchantments);

            assertEquals(
                "ENCHANTMENT_QUICK_BITE_5",
                EnchantedBookIdParser.fromCustomData(customData).orElseThrow()
            );
        }

        @Test
        void rejectsEmptyEnchantments() {
            var customData = new CompoundTag();
            customData.put("enchantments", new CompoundTag());

            assertTrue(EnchantedBookIdParser.fromCustomData(customData).isEmpty());
        }

        @Test
        void rejectsMultipleEnchantments() {
            var customData = new CompoundTag();
            var enchantments = new CompoundTag();
            enchantments.putInt("growth", 6);
            enchantments.putInt("protection", 6);
            customData.put("enchantments", enchantments);

            assertTrue(EnchantedBookIdParser.fromCustomData(customData).isEmpty());
        }
    }

    @Nested
    @DisplayName("display names")
    class DisplayNames {

        @Test
        void derivesIdFromRomanLevel() {
            assertEquals(
                "ENCHANTMENT_QUICK_BITE_5",
                EnchantedBookIdParser.fromDisplayName("Quick Bite V").orElseThrow()
            );
        }

        @Test
        void stripsBazaarActionPrefix() {
            assertEquals(
                "ENCHANTMENT_QUICK_BITE_5",
                EnchantedBookIdParser.fromDisplayName("SELL Quick Bite V").orElseThrow()
            );
        }

        @Test
        void derivesIdFromArabicLevel() {
            assertEquals(
                "ENCHANTMENT_COUNTER_STRIKE_5",
                EnchantedBookIdParser.fromDisplayName("Counter-Strike 5").orElseThrow()
            );
        }

        @Test
        void derivesCanonicalNameFromArabicLevel() {
            assertEquals(
                "Turbo-Cacti V",
                EnchantedBookIdParser.canonicalDisplayName("Turbo-Cacti 5").orElseThrow()
            );
        }

        @Test
        void rejectsLevelRanges() {
            assertTrue(EnchantedBookIdParser.fromDisplayName("Growth 6-7").isEmpty());
        }
    }
}
