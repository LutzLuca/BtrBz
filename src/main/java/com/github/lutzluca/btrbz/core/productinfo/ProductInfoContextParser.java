package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;

final class ProductInfoContextParser {

    private ProductInfoContextParser() {}

    static Optional<String> attributeShardName(List<Component> lore) {
        // Source: <shard name> (<shard identifier>)
        for (var line : lore) {
            var text = Utils.cleanDisplayName(line.getString());
            if (text.startsWith("Source:")) {
                var identifierStart = text.lastIndexOf(" (");
                if (identifierStart < "Source:".length()) {
                    return Optional.empty();
                }

                var name = text.substring("Source:".length(), identifierStart).trim();
                return name.isEmpty() ? Optional.empty() : Optional.of(name);
            }
        }

        return Optional.empty();
    }

    static Optional<String> superpairsEnchantmentName(List<Component> lore) {
        if (lore.size() < 3) {
            return Optional.empty();
        }
        var name = Utils.cleanDisplayName(lore.get(2).getString());
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    static StashEntry stashEntry(String displayedName) {
        var name = Utils.cleanDisplayName(displayedName);
        var separator = name.lastIndexOf(" x");
        if (separator < 0) {
            return new StashEntry(name, OptionalInt.empty(), false);
        }
        return new StashEntry(name.substring(0, separator).trim(),
            parseQuantity(name.substring(separator + 2)), true);
    }

    static OptionalInt sackQuantity(List<Component> lore) {
        var gemstoneEntry = !lore.isEmpty()
            && Utils.cleanDisplayName(lore.getFirst().getString()).equals("Gemstones");
        String storedQuantity = null;
        for (var component : lore) {
            var line = Utils.cleanDisplayName(component.getString());
            if (line.startsWith("Amount:")) {
                // Stored is a rough-equivalent total; Amount identifies the selected gemstone tier.
                return parseQuantity(line.substring("Amount:".length()));
            }
            if (!gemstoneEntry && line.startsWith("Stored:")) {
                var separator = line.indexOf('/');
                if (separator >= 0) {
                    storedQuantity = line.substring("Stored:".length(), separator);
                }
            }
        }
        return storedQuantity != null ? parseQuantity(storedQuantity) : OptionalInt.empty();
    }

    static OptionalInt ownedShards(List<Component> lore) {
        return loreValue(lore, "Owned:").map(value -> {
            var suffix = value.endsWith(" Shards") ? " Shards" : " Shard";
            return value.endsWith(suffix)
                ? parseQuantity(value.substring(0, value.length() - suffix.length()))
                : OptionalInt.empty();
        }).orElseGet(OptionalInt::empty);
    }

    static OptionalInt availableCompost(List<Component> lore) {
        return loreValue(lore, "Compost Available:")
            .map(ProductInfoContextParser::parseQuantity)
            .orElseGet(OptionalInt::empty);
    }

    static OptionalInt essenceBalance(List<Component> lore, String type) {
        return loreValue(lore, "Your " + type + " Essence:")
            .map(ProductInfoContextParser::parseQuantity)
            .orElseGet(OptionalInt::empty);
    }

    static OptionalInt ownedEssence(List<Component> lore, String type) {
        var text = Utils.cleanDisplayName(lore.stream().map(Component::getString).collect(Collectors.joining(" ")));
        var prefix = "You currently own ";
        var end = text.indexOf(" " + type + " Essence.", prefix.length());
        if (!text.startsWith(prefix) || end < 0) {
            return OptionalInt.empty();
        }
        return Utils.parseUsFormattedNumber(text.substring(prefix.length(), end))
            .map(BigDecimal::intValueExact)
            .filter(count -> count >= 0)
            .map(OptionalInt::of)
            .getOrElse(OptionalInt.empty());
    }

    static OptionalInt essenceCost(List<Component> lore, String type) {
        var suffix = " " + type + " Essence";
        var costSection = false;
        for (var component : lore) {
            var line = Utils.cleanDisplayName(component.getString());
            if (line.equals("Cost") || line.equals("Cost:")) {
                costSection = true;
            } else if (costSection && line.endsWith(suffix)) {
                return parseQuantity(line.substring(0, line.length() - suffix.length()));
            }
        }
        return OptionalInt.empty();
    }

    private static Optional<String> loreValue(List<Component> lore, String prefix) {
        for (var component : lore) {
            var line = Utils.cleanDisplayName(component.getString());
            if (line.startsWith(prefix)) {
                return Optional.of(line.substring(prefix.length()).trim());
            }
        }
        return Optional.empty();
    }

    private static OptionalInt parseQuantity(String value) {
        return Try.of(() -> Integer.parseInt(value.replace(",", "").trim()))
            .filter(count -> count >= 0)
            .map(OptionalInt::of)
            .getOrElse(OptionalInt.empty());
    }

    record StashEntry(String productName, OptionalInt count, boolean hasQuantitySuffix) {}
}
