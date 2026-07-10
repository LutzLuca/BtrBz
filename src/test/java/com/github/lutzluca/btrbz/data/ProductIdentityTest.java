package com.github.lutzluca.btrbz.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductIdentityTest {

    @Nested
    @DisplayName("strippedName")
    class StrippedName {

        @Test
        void blankNameFallsBackToUnknownProduct() {
            var product = ProductIdentity.fromRuntime("   ", "TROUBLED_BUBBLE", null);

            assertEquals("Unknown Product", product.strippedName());
        }

        @Test
        void nullNameFallsBackToUnknownProduct() {
            var product = ProductIdentity.fromRuntime(null, "TROUBLED_BUBBLE", null);

            assertEquals("Unknown Product", product.strippedName());
        }
    }

    @Nested
    @DisplayName("visualName")
    class VisualName {

        @Test
        void runtimeProductPrefersFormattedName() {
            var product = ProductIdentity.fromRuntime(
                "Troubled Bubble",
                "TROUBLED_BUBBLE",
                ChatFormatting.GOLD + "Troubled Bubble"
            );

            assertEquals(ChatFormatting.GOLD + "Troubled Bubble", product.visualName());
        }

        @Test
        void runtimeProductFallsBackToStrippedName() {
            var product = ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null);

            assertEquals("Troubled Bubble", product.visualName());
        }
    }

    @Nested
    @DisplayName("bazaarProductId")
    class BazaarProductId {

        @Test
        void fromIndexUsesCanonicalProductId() {
            var indexed = new IndexedProduct("TROUBLED_BUBBLE", ChatFormatting.GOLD + "Troubled Bubble");
            var product = ProductIdentity.fromIndex(indexed);

            assertEquals(Optional.of("TROUBLED_BUBBLE"), product.bazaarProductId());
            assertEquals(ChatFormatting.GOLD + "Troubled Bubble", product.visualName());
        }

        @Test
        void fromRuntimeUsesRawProductId() {
            var product = ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null);

            assertEquals(Optional.of("TROUBLED_BUBBLE"), product.bazaarProductId());
        }

        @Test
        void genericEnchantedBookIsNotBazaarProductId() {
            var product = ProductIdentity.fromRuntime("Habanero Tactics V", "ENCHANTED_BOOK", null);

            assertTrue(product.bazaarProductId().isEmpty());
        }

        @Test
        void fromNameHasNoMarketId() {
            var product = ProductIdentity.fromName("Unknown Product");

            assertTrue(product.bazaarProductId().isEmpty());
        }
    }
}
