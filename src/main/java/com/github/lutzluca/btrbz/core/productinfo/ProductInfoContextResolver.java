package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

final class ProductInfoContextResolver {

    private final BazaarData bazaarData;

    ProductInfoContextResolver(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
    }

    @Nullable
    ResolvedProduct resolve(ItemStack stack, @Nullable MenuContext menu, boolean requireMatchingName) {
        if (stack.isEmpty()) {
            return null;
        }
        if (menu != null) {
            var item = new MenuItem(stack, menu, requireMatchingName);
            var title = menu.title();
            if (menu.bazaarMenu() == BazaarMenuType.Main) {
                return ProductInfoMatching.isBazaarProductEntry(item.lore())
                    ? this.resolveStack(stack, requireMatchingName) : null;
            }
            if (menu.bazaarMenu() == BazaarMenuType.Orders && ProductInfoMatching.isOrderStack(stack)) {
                return this.resolveOrder(item);
            }
            if (ProductInfoMatching.isAttributeMenu(title)) {
                return this.resolveAttribute(item);
            }
            if (ProductInfoMatching.isSuperpairsMenu(title) || ProductInfoMatching.isRngMenu(title)) {
                return this.resolveExperimentationReward(item);
            }
            if (ProductInfoMatching.isHuntingBoxMenu(title)) {
                return this.resolveHuntingBox(item);
            }
            if (title.equals("View Stash")) {
                return this.resolveStash(item);
            }
            if (title.equals("Composter")
                && "Collect Compost".equals(Utils.cleanDisplayName(stack.getHoverName().getString()))) {
                return this.resolveComposter(item);
            }
            if (ProductInfoMatching.isSackStack(stack)) {
                return this.resolveSack(item);
            }
        }
        return this.resolveStack(stack, requireMatchingName);
    }

    private @Nullable ResolvedProduct resolveStack(ItemStack stack, boolean requireMatchingName) {
        var product = this.bazaarData.resolveProduct(stack);
        return this.matchesName(stack.getHoverName().getString(), stack, product, requireMatchingName)
            ? new ResolvedProduct(product, stackQuantity(stack, product)) : null;
    }

    private ResolvedProduct resolveOrder(MenuItem item) {
        var order = OrderInfoParser.parseOrderInfo(item.stack(), item.menu().slotIndex(), this.bazaarData);
        if (order.isSuccess()) {
            var info = order.get();
            return new ResolvedProduct(info.product(),
                new ProductInfoQuantity(ProductInfoQuantity.Source.ORDER, OptionalInt.of(info.volume())));
        }
        return new ResolvedProduct(this.bazaarData.resolveProduct(item.stack()),
            new ProductInfoQuantity(ProductInfoQuantity.Source.ORDER, OptionalInt.empty()));
    }

    private @Nullable ResolvedProduct resolveAttribute(MenuItem item) {
        return ProductInfoContextParser.attributeShardName(item.lore())
            .flatMap(this::resolveShard)
            .map(product -> new ResolvedProduct(product,
                new ProductInfoQuantity(ProductInfoQuantity.Source.STACK, OptionalInt.of(1))))
            .orElse(null);
    }

    private @Nullable ResolvedProduct resolveHuntingBox(MenuItem item) {
        var name = Utils.cleanDisplayName(item.stack().getHoverName().getString());
        var shardName = name.endsWith(" Shard") ? name : name + " Shard";
        return this.resolveShard(shardName)
            .map(product -> new ResolvedProduct(product,
                new ProductInfoQuantity(ProductInfoQuantity.Source.HUNTING_BOX,
                    ProductInfoContextParser.ownedShards(item.lore()))))
            .orElse(null);
    }

    private Optional<ProductIdentity> resolveShard(String name) {
        var product = this.bazaarData.resolveProductName(name);
        return product.bazaarProductId().filter(id -> id.startsWith("SHARD_")).map(_ -> product);
    }

    private @Nullable ResolvedProduct resolveStash(MenuItem item) {
        var entry = ProductInfoContextParser.stashEntry(item.stack().getHoverName().getString());
        var product = this.bazaarData.resolveProduct(item.stack(), entry.productName());
        if (!this.matchesName(entry.productName(), item.stack(), product, item.requireMatchingName())) {
            return null;
        }
        var count = entry.hasQuantitySuffix() ? entry.count() : stackQuantity(item.stack(), product).count();
        return new ResolvedProduct(product, new ProductInfoQuantity(ProductInfoQuantity.Source.STASH, count));
    }

    private ResolvedProduct resolveComposter(MenuItem item) {
        return new ResolvedProduct(this.bazaarData.resolveProduct("COMPOST", "Compost"),
            new ProductInfoQuantity(ProductInfoQuantity.Source.COMPOSTER,
                ProductInfoContextParser.availableCompost(item.lore())));
    }

    private @Nullable ResolvedProduct resolveSack(MenuItem item) {
        var product = this.bazaarData.resolveProduct(item.stack());
        return this.matchesName(item.stack().getHoverName().getString(), item.stack(), product,
            item.requireMatchingName())
                ? new ResolvedProduct(product, new ProductInfoQuantity(ProductInfoQuantity.Source.SACK,
                    ProductInfoContextParser.sackQuantity(item.lore())))
                : null;
    }

    private @Nullable ResolvedProduct resolveExperimentationReward(MenuItem item) {
        var stack = item.stack();
        var product = this.bazaarData.resolveProduct(stack);
        var indexed = this.bazaarData.resolveIndexedProduct(product).orElse(null);
        var identifiedBook = indexed != null && isIdentifiedEnchantmentBook(stack, indexed.productId());
        var displayedName = stack.getHoverName().getString();
        var genericBook = ProductInfoMatching.isSuperpairsMenu(item.menu().title())
            && "Enchanted Book".equals(Utils.cleanDisplayName(displayedName));
        var rewardName = genericBook && !identifiedBook
            ? ProductInfoContextParser.superpairsEnchantmentName(item.lore()).orElse(null) : displayedName;
        if (rewardName == null) {
            return null;
        }
        if (indexed == null) {
            product = this.bazaarData.resolveProductName(rewardName);
            indexed = this.bazaarData.resolveIndexedProduct(product).orElse(null);
        }
        if (genericBook && (indexed == null || !indexed.productId().startsWith("ENCHANTMENT_"))) {
            return null;
        }
        if (item.requireMatchingName()
            && (indexed == null || !ProductInfoMatching.matchesDisplayedName(rewardName, indexed, identifiedBook))) {
            return null;
        }
        return new ResolvedProduct(product, stackQuantity(stack, product));
    }

    private boolean matchesName(String name, ItemStack stack, ProductIdentity product, boolean requireMatchingName) {
        if (!requireMatchingName) {
            return true;
        }
        var indexed = this.bazaarData.resolveIndexedProduct(product).orElse(null);
        return indexed != null && ProductInfoMatching.matchesDisplayedName(name, indexed,
            isIdentifiedEnchantmentBook(stack, indexed.productId()));
    }

    private static boolean isIdentifiedEnchantmentBook(ItemStack stack, String productId) {
        return stack.getItem() == Items.ENCHANTED_BOOK && productId.startsWith("ENCHANTMENT_");
    }

    private static ProductInfoQuantity stackQuantity(ItemStack stack, ProductIdentity product) {
        return ProductInfoQuantity.stack(stack.getCount(), product, stack.getItem() == Items.ENCHANTED_BOOK);
    }

    record MenuContext(String title, @Nullable BazaarMenuType bazaarMenu, int slotIndex) {}

    record ResolvedProduct(ProductIdentity product, ProductInfoQuantity quantity) {}

    private record MenuItem(ItemStack stack, MenuContext menu, boolean requireMatchingName) {
        List<Component> lore() {
            return this.stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
        }
    }
}
