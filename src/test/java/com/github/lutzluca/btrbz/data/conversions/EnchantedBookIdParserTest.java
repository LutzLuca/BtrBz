package com.github.lutzluca.btrbz.data.conversions;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class EnchantedBookIdParserTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("customData")
    void requiresOneCustomDataEnchantment(
        String description,
        Map<String, Integer> levels,
        String expectedId
    ) {
        var customData = new CompoundTag();
        var enchantments = new CompoundTag();
        levels.forEach(enchantments::putInt);
        customData.put("enchantments", enchantments);

        Assertions.assertEquals(
            Optional.ofNullable(expectedId), EnchantedBookIdParser.fromCustomData(customData));
    }

    private static Stream<Arguments> customData() {
        return Stream.of(
            Arguments.of("single enchantment", Map.of("quick_bite", 5), "ENCHANTMENT_QUICK_BITE_5"),
            Arguments.of("empty enchantments", Map.of(), null),
            Arguments.of("multiple enchantments", Map.of("growth", 6, "protection", 6), null));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "Quick Bite V, ENCHANTMENT_QUICK_BITE_5",
        "SELL Quick Bite V, ENCHANTMENT_QUICK_BITE_5",
        "Counter-Strike 5, ENCHANTMENT_COUNTER_STRIKE_5",
        "Growth 6-7,"
    })
    void derivesOnlyAnUnambiguousDisplayNameId(String displayName, String expectedId) {
        Assertions.assertEquals(
            Optional.ofNullable(expectedId), EnchantedBookIdParser.fromDisplayName(displayName));
    }

    @Test
    void canonicalizesArabicDisplayLevel() {
        Assertions.assertEquals(
            Optional.of("Turbo-Cacti V"), EnchantedBookIdParser.canonicalDisplayName("Turbo-Cacti 5"));
    }
}
