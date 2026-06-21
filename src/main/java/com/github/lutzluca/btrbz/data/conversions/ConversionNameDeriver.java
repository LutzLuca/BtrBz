package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;

final class ConversionNameDeriver {

    private ConversionNameDeriver() { }

    static DerivedDisplayName deriveDisplayName(String productId) {
        return deriveDisplayName(productId, null);
    }

    static DerivedDisplayName deriveDisplayName(String productId, String neuId) {
        if (neuId != null && neuId.contains(";")) {
            var parts = neuId.split(";", 2);
            var level = Try
                .of(() -> Utils.intToRoman(Integer.parseInt(parts[1])))
                .getOrElse(parts[1]);
            return new DerivedDisplayName(Utils.titleCase(parts[0].replace('_', ' ')) + " " + level, false);
        }

        if (productId.startsWith("ENCHANTMENT_")) {
            var withoutPrefix = productId.substring("ENCHANTMENT_".length());
            var delimiter = withoutPrefix.lastIndexOf('_');
            if (delimiter > 0) {
                var name = Utils.titleCase(withoutPrefix.substring(0, delimiter).replace('_', ' '));
                var level = Try
                    .of(() -> Utils.intToRoman(Integer.parseInt(withoutPrefix.substring(delimiter + 1))))
                    .getOrElse(withoutPrefix.substring(delimiter + 1));
                return new DerivedDisplayName(name + " " + level, false);
            }
        }

        if (productId.startsWith("SHARD_")) {
            return new DerivedDisplayName(
                Utils.titleCase(productId.substring("SHARD_".length()).replace('_', ' ')) + " Shard",
                false
            );
        }

        if (productId.startsWith("ESSENCE_")) {
            return new DerivedDisplayName(
                Utils.titleCase(productId.substring("ESSENCE_".length()).replace('_', ' ')) + " Essence",
                false
            );
        }

        return switch (productId) {
            case "BAZAAR_COOKIE" -> new DerivedDisplayName("Bazaar Cookie", false);
            case "SLEEPY_HOLLOW" -> new DerivedDisplayName("Sleepy Hollow", false);
            default -> new DerivedDisplayName(Utils.titleCase(productId.replace('_', ' ')), true);
        };
    }
}
