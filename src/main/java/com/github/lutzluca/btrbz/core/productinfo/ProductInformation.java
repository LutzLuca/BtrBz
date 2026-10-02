package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.screen.BazaarProductContext;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.screen.slot.SlotClickContext;
import com.github.lutzluca.btrbz.screen.slot.SlotClickResult;
import com.github.lutzluca.btrbz.screen.slot.SlotHook;
import com.github.lutzluca.btrbz.screen.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.screen.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.screen.slot.SlotView;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import io.vavr.control.Try;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ProductInformation {

    private static final int CUSTOM_ITEM_IDX = 22;
    private final BazaarData bazaarData;
    private final BazaarProductContext productContext;
    private final Supplier<ProductInfoConfig> config;
    private final ProductInfoContextResolver contextResolver;
    private final PriceCache priceCache;

    private @Nullable ItemStack cachedProductInfoItem = null;
    private @Nullable ProductInfoConfig.Site cachedProductInfoSite = null;

    public ProductInformation(
        BazaarData bazaarData,
        BazaarProductContext productContext,
        Supplier<ProductInfoConfig> config
    ) {
        this.bazaarData = bazaarData;
        this.productContext = productContext;
        this.config = config;
        this.contextResolver = new ProductInfoContextResolver(bazaarData);
        this.priceCache = new PriceCache();
        ScreenTracker.registerOnSwitch(_ -> this.priceCache.clear());
        this.registerSlotHooks();
        this.registerTooltipDisplay();
    }

    private void registerSlotHooks() {
        SlotHookRegistry.register(new InfoSiteButtonHook());
        SlotHookRegistry.register(new ProductLookupHook());
    }

    private ItemStack createProductInfoItem() {
        var cfg = this.config.get();

        if (this.cachedProductInfoItem != null && this.cachedProductInfoSite == cfg.site) {
            return this.cachedProductInfoItem.copy();
        }

        var item = new ItemStack(Items.PAPER);
        item.set(
            DataComponents.CUSTOM_NAME,
            Component
                .literal("Product Info")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .withStyle(style -> style.withItalic(false)));

        var loreLines = Stream.of(
            Component.literal("View detailed Bazaar statistics").withStyle(ChatFormatting.GRAY),
            Component.literal("and live market data for this item.").withStyle(ChatFormatting.GRAY),
            Component.empty(),
            Component
                .literal("➤ Click to open ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .withStyle(style -> style.withItalic(false))
                .append(Component
                    .literal(cfg.site.displayName())
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)))
            .<Component>map(line -> line.withStyle(style -> style.withItalic(false))).toList();

        item.set(DataComponents.LORE, new ItemLore(loreLines));

        this.cachedProductInfoItem = item;
        this.cachedProductInfoSite = cfg.site;
        return this.cachedProductInfoItem.copy();
    }

    private void registerTooltipDisplay() {
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!BtrBz.isActive()) {
                return;
            }
            var cfg = this.config.get();
            if (!cfg.enabled || !cfg.ctrlShiftEnabled || !this.shouldApplyCtrlShiftClick(stack)) {
                return;
            }
            lines.add(Component.empty());
            lines.add(Component.literal("CTRL").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("+").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("SHIFT").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" Click ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("to view on ").withStyle(ChatFormatting.DARK_GRAY)
                    .withStyle(style -> style.withBold(false)))
                .append(Component.literal(cfg.site.displayName()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        });
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!BtrBz.isActive()) {
                return;
            }
            var cfg = this.config.get();
            if (!cfg.enabled || !cfg.priceTooltipEnabled) {
                return;
            }
            var lookup = this.lookup(stack);
            if (lookup == null || lookup.prices() == null) {
                return;
            }
            ProductInfoTooltip.append(lines, lookup.prices(), lookup.quantity(),
                Minecraft.getInstance().hasShiftDown());
        });
    }

    private boolean shouldApplyCtrlShiftClick(ItemStack stack) {
        if (!this.isCtrlShiftEnabled()) {
            return false;
        }

        var lookup = this.lookup(stack);
        return lookup != null
            && this.isCtrlShiftContextEnabled(this.isStackInPlayerInventory(stack))
            && lookup.marketProductId().isPresent();
    }

    private boolean isCtrlShiftEnabled() {
        var cfg = this.config.get();
        return cfg.enabled && cfg.ctrlShiftEnabled;
    }

    private boolean isCtrlShiftContextEnabled(boolean playerInventoryStack) {
        var cfg = this.config.get();
        if (ScreenTracker.inBazaar()) {
            return playerInventoryStack || cfg.ctrlShiftOnBazaarItems;
        }

        return cfg.showOutsideBazaar;
    }

    private @Nullable ProductLookup lookup(SlotView view) {
        // Clicks and tooltips resolve the displayed stack, which may be projected.
        return this.lookup(view.getSlot().getItem(), view.playerInventorySlot() ? null : view.getSlot());
    }

    private @Nullable ProductLookup lookup(ItemStack stack) {
        return this.lookup(stack, this.hoveredSlot(stack)
            .filter(slot -> !GameUtils.isPlayerInventorySlot(slot)).orElse(null));
    }

    private @Nullable ProductLookup lookup(ItemStack stack, @Nullable Slot menuSlot) {
        var info = ScreenTracker.get().getCurrInfo();
        var screen = info.getGenericContainerScreen().orElse(null);
        ProductInfoContextResolver.MenuContext menu = null;
        if (menuSlot != null && screen != null && menuSlot.container == screen.getMenu().getContainer()) {
            menu = new ProductInfoContextResolver.MenuContext(screen.getTitle().getString(),
                info.getMenuType().orElse(null), menuSlot.getContainerSlot());
        }
        var resolved = this.contextResolver.resolve(stack, menu, this.config.get().requireMatchingName);
        return resolved != null
            ? new ProductLookup(resolved.product(), this.priceCache.get(resolved.product()),
                resolved.quantity())
            : null;
    }

    private Optional<Slot> hoveredSlot(ItemStack stack) {
        // Some mods give the tooltip code a copy of the item.
        // Check that it matches the item in the hovered slot.
        // Use that slot for menu lookup and player inventory checks.
        return ScreenTracker
            .get()
            .getCurrInfo()
            .getGenericContainerScreen()
            .map(screen -> screen instanceof AbstractContainerScreenAccessor accessor
                ? accessor.getHoveredSlot()
                : null)
            .filter(slot -> slot != null && ItemStack.matches(slot.getItem(), stack));
    }

    private boolean isStackInPlayerInventory(ItemStack stack) {
        var hoveredSlot = this.hoveredSlot(stack);
        if (hoveredSlot.isPresent()) {
            return GameUtils.isPlayerInventorySlot(hoveredSlot.get());
        }

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }

        for (var playerStack : player.getInventory()) {
            if (playerStack == stack) {
                return true;
            }
        }

        return false;
    }

    private void confirmAndOpen(String link) {
        GameUtils.setScreen(new ConfirmLinkScreen(
            confirmed -> {
                if (confirmed) {
                    Try
                        .run(() -> Util.getPlatform().openUri(new URI(link)))
                        .onFailure(err -> Notifier.notifyPlayer(Component
                            .literal("Failed to open link: ")
                            .withStyle(ChatFormatting.RED)
                            .append(Component
                                .literal(link)
                                .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BLUE))));
                }

                var prev = ScreenTracker.get().getPrevInfo();
                GameUtils.setScreen(prev != null ? prev.getScreen() : null);
            }, link, true));
    }

    private record ProductLookup(
        ProductIdentity product,
        @Nullable ProductInfoTooltip.Prices prices,
        ProductInfoQuantity quantity
    ) {
        Optional<String> marketProductId() {
            return this.prices != null ? this.product.bazaarProductId() : Optional.empty();
        }
    }

    private final class InfoSiteButtonHook implements SlotHook {

        private InfoSiteButtonHook() {}

        @Override
        public boolean matches(SlotView view) {
            var cfg = ProductInformation.this.config.get();
            return cfg.enabled
                && cfg.itemClickEnabled
                && ProductInformation.this.productContext.openedProduct() != null
                && !view.playerInventorySlot()
                && view.slotIdx() == CUSTOM_ITEM_IDX
                && view.getCurrInfo().inMenu(BazaarMenuType.Item);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            return ProductInformation.this.createProductInfoItem();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var cfg = ProductInformation.this.config.get();
            ProductInformation.this.confirmAndOpen(
                cfg.site.format(ProductInformation.this.productContext.openedProduct().productId()));
            return SlotClickResult.Consume;
        }
    }

    private final class ProductLookupHook implements SlotHook {

        private ProductLookupHook() {}

        @Override
        public boolean matches(SlotView view) {
            return true; // view displayed stack on click
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            if (!ctx.modifiers().controlDown() || !ctx.modifiers().shiftDown()) {
                return SlotClickResult.Pass;
            }

            if (!ProductInformation.this.isCtrlShiftEnabled()) {
                return SlotClickResult.Pass;
            }

            if (!ProductInformation.this.isCtrlShiftContextEnabled(ctx.view().playerInventorySlot())) {
                return SlotClickResult.Pass;
            }
            var lookup = ProductInformation.this.lookup(ctx.view());
            if (lookup == null) {
                return SlotClickResult.Pass;
            }

            var productId = lookup.marketProductId();
            if (productId.isEmpty()) {
                log.warn("No Bazaar product found for {}", ctx.view().getSlot().getItem().getHoverName().getString());
                return SlotClickResult.Pass;
            }

            var cfg = ProductInformation.this.config.get();
            ProductInformation.this.confirmAndOpen(cfg.site.format(productId.get()));
            return SlotClickResult.Consume;
        }
    }

    private final class PriceCache {
        private final Map<ProductIdentity, ProductInfoTooltip.Prices> cache = new HashMap<>();

        PriceCache() {
            ProductInformation.this.bazaarData.addListener(_ -> this.clear());
            ProductInformation.this.bazaarData.addIndexChangeListener(this::clear);
        }

        @Nullable
        ProductInfoTooltip.Prices get(ProductIdentity product) {
            if (this.cache.containsKey(product)) {
                return this.cache.get(product);
            }
            var data = ProductInformation.this.bazaarData;
            var prices = data.contains(product)
                ? new ProductInfoTooltip.Prices(data.lowestSellOfferPrice(product).orElse(null),
                    data.highestBuyOrderPrice(product).orElse(null))
                : null;
            this.cache.put(product, prices);
            return prices;
        }

        void clear() {
            this.cache.clear();
        }
    }
}
