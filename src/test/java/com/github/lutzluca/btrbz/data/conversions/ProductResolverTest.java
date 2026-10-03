package com.github.lutzluca.btrbz.data.conversions;

import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductResolverTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "Wither Essence, ESSENCE_WITHER",
        "Suspicious Scrap,"
    })
    void derivesOnlyEssenceProductIds(String displayName, String expectedId) {
        Assertions.assertEquals(Optional.ofNullable(expectedId), ProductResolver.essenceProductId(displayName));
    }

    @ParameterizedTest(name = "{0} idless shard={1}")
    @CsvSource({
        "Lapis Zombie Shard, true",
        "Suspicious Scrap, false"
    })
    void recognizesIdlessMenuShards(String displayName, boolean expected) {
        Assertions.assertEquals(expected, ProductResolver.isPossibleShardStack(null, displayName));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "Phanpyre Shard, SHARD_PHANPYRE",
        "Unknown Shard,"
    })
    void resolvesShardIdentityOnlyWhenIndexed(String displayName, String expectedId) {
        var resolver = new ProductResolver(serviceWithProducts());
        var product = resolver.resolveShardIdentity(displayName, null, "ATTRIBUTE_SHARD");

        Assertions.assertEquals(Optional.ofNullable(expectedId), product.bazaarProductId());
        Assertions.assertEquals(displayName, product.visualName());
    }

    @ParameterizedTest(name = "{0} with display evidence {1}")
    @CsvSource({
        "REDSTONE, Growth 6-7, REDSTONE, Redstone",
        "TROUBLED_BUBBLE, Troubled Bubble, TROUBLED_BUBBLE, Troubled Bubble"
    })
    void preservesAuthoritativeRawProductIds(
        String rawId,
        String displayName,
        String expectedId,
        String expectedVisualName
    ) {
        var product = serviceWithProducts().resolveProduct(rawId, displayName);

        Assertions.assertEquals(Optional.of(expectedId), product.bazaarProductId());
        Assertions.assertEquals(expectedVisualName, product.visualName());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "SELL Quick Bite V, ENCHANTMENT_QUICK_BITE_5, Quick Bite V",
        "Habanero Tactics V,, Habanero Tactics V",
        "Growth 6-7,, Growth 6-7",
        "Enchanted Book,, Enchanted Book",
        "Turbo-Cacti 5, ENCHANTMENT_TURBO_CACTUS_5, Turbo-Cacti V"
    })
    void resolvesGenericBookEvidenceWithCanonicalNamePrecedence(
        String displayName,
        String expectedId,
        String expectedVisualName
    ) {
        var product = serviceWithProducts().resolveProduct("ENCHANTED_BOOK", displayName);

        Assertions.assertEquals(Optional.ofNullable(expectedId), product.bazaarProductId());
        Assertions.assertEquals(expectedVisualName, product.visualName());
    }

    private static ConversionIndexService serviceWithProducts() {
        var products = new LinkedHashMap<String, ConversionProductEntry>();
        products.put("REDSTONE", new ConversionProductEntry("Redstone", new ProductNameSource.Neu("REDSTONE")));
        products.put(
            "ENCHANTMENT_QUICK_BITE_5",
            new ConversionProductEntry("Quick Bite V", new ProductNameSource.Neu("ENCHANTMENT_QUICK_BITE_5")));
        products.put(
            "ENCHANTMENT_TURBO_CACTUS_5",
            new ConversionProductEntry("Turbo-Cacti V", new ProductNameSource.Neu("TURBO_CACTUS;5")));
        products.put(
            "SHARD_PHANPYRE",
            new ConversionProductEntry(
                "Phanpyre Shard",
                new ProductNameSource.Neu("ATTRIBUTE_SHARD_NOCTURNAL_ANIMAL;1")));
        return new ConversionIndexService(new ConversionIndex(
            ConversionIndex.SCHEMA_VERSION, "now", null, products));
    }
}
