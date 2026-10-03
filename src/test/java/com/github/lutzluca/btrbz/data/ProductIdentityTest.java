package com.github.lutzluca.btrbz.data;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductIdentityTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "   ")
    void missingNameFallsBackToUnknownProduct(String name) {
        var product = ProductIdentity.fromRuntime(name, "TROUBLED_BUBBLE", null);

        Assertions.assertEquals("Unknown Product", product.strippedName());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("visualNames")
    void choosesRuntimeVisualName(String description, String formattedName, String expected) {
        var product = ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", formattedName);

        Assertions.assertEquals(expected, product.visualName());
    }

    private static Stream<Arguments> visualNames() {
        return Stream.of(
            Arguments.of("prefers formatted evidence",
                ChatFormatting.GOLD + "Troubled Bubble", ChatFormatting.GOLD + "Troubled Bubble"),
            Arguments.of("falls back to stripped name", null, "Troubled Bubble"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("marketIdentities")
    void preservesOnlySpecificMarketIds(
        String description,
        ProductIdentity product,
        String expectedId,
        String expectedVisualName
    ) {
        Assertions.assertEquals(Optional.ofNullable(expectedId), product.bazaarProductId());
        Assertions.assertEquals(expectedVisualName, product.visualName());
    }

    private static Stream<Arguments> marketIdentities() {
        return Stream.of(
            Arguments.of("indexed canonical id and formatting",
                ProductIdentity.fromIndex(new IndexedProduct(
                    "TROUBLED_BUBBLE", ChatFormatting.GOLD + "Troubled Bubble")),
                "TROUBLED_BUBBLE", ChatFormatting.GOLD + "Troubled Bubble"),
            Arguments.of("runtime raw id",
                ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null),
                "TROUBLED_BUBBLE", "Troubled Bubble"),
            Arguments.of("generic enchanted book has no specific market id",
                ProductIdentity.fromRuntime("Habanero Tactics V", "ENCHANTED_BOOK", null),
                null, "Habanero Tactics V"),
            Arguments.of("name-only evidence has no market id",
                ProductIdentity.fromName("Unknown Product"), null, "Unknown Product"));
    }
}
