package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotClickResult;
import com.github.lutzluca.btrbz.utils.slot.SlotHook;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import io.vavr.control.Try;
import java.net.URI;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ProductInfoProvider {

    private static final int CUSTOM_ITEM_IDX = 22;
    private static final int PRODUCT_IDX = 13;

    /**
     * Screens that are part of the product order flow. The opened product info
     * is preserved when transitioning between these screens
     */
    private static final BazaarMenuType[] PRODUCT_FLOW_MENUS = {
        BazaarMenuType.Item,
        BazaarMenuType.BuyOrderSetupVolume,
        BazaarMenuType.BuyOrderSetupPrice,
        BazaarMenuType.BuyOrderConfirmation,
        BazaarMenuType.SellOfferSetup,
        BazaarMenuType.SellOfferConfirmation
    };
    private final BazaarData bazaarData;
    private final ProductLookupCache productLookupCache;

    private @Nullable ItemStack cachedProductInfoItem = null;
    private @Nullable InfoProviderSite cachedProductInfoSite = null;

    @Getter
    private @Nullable IndexedProduct openedProduct;

    public ProductInfoProvider(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.productLookupCache = new ProductLookupCache();
        this.registerProductInfoListener();
        this.registerSlotHooks();
        this.registerTooltipDisplay();
        log.info("Initialized ProductInfoProvider");
    }

    private Component createPriceText(
        String label,
        @Nullable Double price,
        int stackCount,
        boolean isShiftHeld
    ) {
        var priceText = Component.literal(label).withStyle(ChatFormatting.AQUA);

        if (price != null) {
            var displayPrice = isShiftHeld && stackCount > 1 ? price * stackCount : price;
            priceText.append(Component
                .literal(Utils.formatDecimal(displayPrice, 1, true) + " coins")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            if (isShiftHeld && stackCount > 1) {
                priceText.append(Component
                    .literal(" (" + stackCount + "x)")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            priceText.append(Component.literal("Not Available").withStyle(ChatFormatting.GRAY));
        }

        return priceText;
    }

    private void registerProductInfoListener() {
        ScreenInfoHelper.registerOnLoaded(
            info -> info.inMenu(BazaarMenuType.Item), (info, inv) -> {
                var product = inv
                    .getItem(PRODUCT_IDX)
                    .map(this.bazaarData::resolveProduct)
                    .flatMap(this.bazaarData::resolveIndexedProduct);

                product.ifPresentOrElse(
                    resolved -> {
                        this.openedProduct = resolved;
                        log.debug("Opened product: {}", resolved);
                    },
                    () -> {
                        this.openedProduct = null;
                        log.warn("No product resolved for Bazaar item screen");
                    }
                );
            }
        );

        ScreenInfoHelper.registerOnSwitch(curr -> {
            this.productLookupCache.clear();
            if (this.openedProduct == null) {
                return;
            }

            var prev = ScreenInfoHelper.get().getPrevInfo();

            // Only clear when navigating to a known non-flow bazaar screen or closing
            // entirely. Transient screens (e.g. OrderBookScreen, SignEditScreen, or other
            // injected screens) are implicitly preserved since they don't match any
            // BazaarMenuType
            boolean closed = curr.getScreen() == null;
            boolean transientFlowClose = closed && prev.getScreen() instanceof SignEditScreen;
            boolean leftToNonFlowBazaar = curr.inBazaar()
                && !curr.inMenu(PRODUCT_FLOW_MENUS);

            if (transientFlowClose) {
                log.debug(
                    "Preserving product context on transient flow close: {}",
                    this.openedProduct
                );
                return;
            }

            if (closed || leftToNonFlowBazaar) {
                log.debug(
                    "Leaving product flow, clearing product: {}",
                    this.openedProduct
                );
                this.openedProduct = null;
            }
        });
    }

    private void registerSlotHooks() {
        SlotHookRegistry.register(new InfoSiteButtonHook());
        SlotHookRegistry.register(new ProductLookupHook());
    }

    private ItemStack createProductInfoItem() {
        var cfg = ConfigManager.get().productInfo;

        if (this.cachedProductInfoItem != null && this.cachedProductInfoSite == cfg.site) {
            return this.cachedProductInfoItem.copy();
        }

        var item = new ItemStack(Items.PAPER);
        item.set(
            DataComponents.CUSTOM_NAME,
            Component
                .literal("Product Info")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .withStyle(style -> style.withItalic(false))
        );

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
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
        ).<Component>map(line -> line.withStyle(style -> style.withItalic(false))).toList();

        item.set(DataComponents.LORE, new ItemLore(loreLines));

        this.cachedProductInfoItem = item;
        this.cachedProductInfoSite = cfg.site;
        return this.cachedProductInfoItem.copy();
    }

    private void registerTooltipDisplay() {
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            var cfg = ConfigManager.get().productInfo;
            if (!cfg.enabled || !cfg.ctrlShiftEnabled) {
                return;
            }

            if (!this.shouldApplyCtrlShiftClick(stack)) {
                return;
            }

            lines.add(Component.empty());
            lines.add(Component
                .literal("CTRL")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal("+").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("SHIFT").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" Click ").withStyle(ChatFormatting.GRAY))
                .append(Component
                    .literal("to view on ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .withStyle(style -> style.withBold(false)))
                .append(Component
                    .literal(cfg.site.displayName())
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));

        });

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            var cfg = ConfigManager.get().productInfo;
            if (!cfg.enabled || !cfg.priceTooltipEnabled) {
                return;
            }

            var cached = this.productLookupCache.get(stack).prices();
            if (cached == null) {
                return;
            }

            var count = stack.getItem() == Items.ENCHANTED_BOOK ? 1 : stack.getCount();
            var isShiftHeld = Minecraft.getInstance().hasShiftDown();

            lines.add(Component.empty());

            if (count > 1 && !isShiftHeld) {
                lines.add(Component
                    .literal("Hold ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("SHIFT").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                    .append(Component.literal(" to show for (").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal("x").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
            }

            if (count > 1 && isShiftHeld) {
                lines.add(Component
                    .literal("Showing price for ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal("x").withStyle(ChatFormatting.GRAY)));
            }

            lines.add(createPriceText("Buy Order: ", cached.buyOrderPrice, count, isShiftHeld));
            lines.add(createPriceText("Sell Offer: ", cached.sellOfferPrice, count, isShiftHeld));
        });
    }

    private boolean shouldApplyCtrlShiftClick(ItemStack stack) {
        if (!this.isCtrlShiftEnabled()) {
            return false;
        }

        var lookup = this.productLookupCache.get(stack);
        return this.isCtrlShiftContextEnabled(lookup.playerInventoryStack())
            && lookup.marketProductId().isPresent();
    }

    private boolean isCtrlShiftEnabled() {
        var cfg = ConfigManager.get().productInfo;
        return cfg.enabled && cfg.ctrlShiftEnabled;
    }

    private boolean isCtrlShiftContextEnabled(boolean playerInventoryStack) {
        var cfg = ConfigManager.get().productInfo;
        if (ScreenInfoHelper.inBazaar()) {
            return playerInventoryStack || cfg.ctrlShiftOnBazaarItems;
        }

        return cfg.showOutsideBazaar;
    }

    private ProductIdentity resolveProductForLookup(SlotView view) {
        var stack = view.getRawStack();
        if (this.isOrderScreenProductRow(stack) && !view.playerInventorySlot()) {
            return OrderInfoParser
                .parseOrderInfo(stack, view.slotIdx(), this.bazaarData)
                .map(order -> order.product())
                .getOrElse(() -> this.resolveProduct(stack));
        }

        return this.resolveProduct(stack);
    }

    private ProductIdentity resolveProductForLookup(ItemStack stack) {
        var hoveredSlot = this.hoveredNonPlayerSlot(stack);
        if (this.isOrderScreenProductRow(stack) && hoveredSlot.isPresent()) {
            return OrderInfoParser
                .parseOrderInfo(stack, hoveredSlot.get().getContainerSlot(), this.bazaarData)
                .map(order -> order.product())
                .getOrElse(() -> this.resolveProduct(stack));
        }

        return this.resolveProduct(stack);
    }

    private boolean isOrderScreenProductRow(ItemStack stack) {
        return ScreenInfoHelper.inMenu(BazaarMenuType.Orders)
            && GameUtils.orderScreenNonOrderItemsFilter(stack);
    }

    private Optional<Slot> hoveredNonPlayerSlot(ItemStack stack) {
        return ScreenInfoHelper
            .get()
            .getCurrInfo()
            .getGenericContainerScreen()
            .map(screen -> screen instanceof AbstractContainerScreenAccessor accessor
                ? accessor.getHoveredSlot()
                : null)
            .filter(slot -> slot != null && !GameUtils.isPlayerInventorySlot(slot) && slot.getItem() == stack);
    }

    private boolean isStackInPlayerInventory(ItemStack stack) {
        // NOTE: reference equality is intentional here
        // noinspection DataFlowIssue
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
        //? if <26.2 {
        var client = Minecraft.getInstance();
        //?} else {
        /*var client = Minecraft.getInstance().gui;
         *///?}

        client.setScreen(new ConfirmLinkScreen(
            confirmed -> {
                if (confirmed) {
                    Try
                        .run(() -> net.minecraft.util.Util.getPlatform().openUri(new URI(link)))
                        .onFailure(err -> Notifier.notifyPlayer(Component
                            .literal("Failed to open link: ")
                            .withStyle(ChatFormatting.RED)
                            .append(Component
                                .literal(link)
                                .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BLUE))));
                }

                var prev = ScreenInfoHelper.get().getPrevInfo();
                client.setScreen(prev != null ? prev.getScreen() : null);
            }, link, true
        ));
    }

    private ProductIdentity resolveProduct(ItemStack stack) {
        return this.bazaarData.resolveProduct(stack);
    }

    public enum InfoProviderSite {
        Coflnet("https://sky.coflnet.com/item/%s?range=day"),
        SkyblockBz("https://skyblock.bz/product/%s"),
        SkyblockFinance("https://skyblock.finance/items/%s");

        private final String urlFormat;

        InfoProviderSite(String urlFormat) {
            this.urlFormat = urlFormat;
        }

        public static EnumControllerBuilder<InfoProviderSite> controller(
            Option<InfoProviderSite> option
        ) {
            return EnumControllerBuilder
                .create(option)
                .enumClass(InfoProviderSite.class)
                .formatValue(site -> Component
                    .literal("Use site: ")
                    .append(Component
                        .literal(site.displayName())
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        }

        public String format(String productId) {
            return String.format(urlFormat, productId);
        }

        public String displayName() {
            return switch (this) {
                case SkyblockBz -> "Skyblock.bz";
                case SkyblockFinance -> "Skyblock.Finance";
                case Coflnet -> "Coflnet";
            };
        }
    }

    private record CachedPrice(
        @Nullable Double sellOfferPrice,
        @Nullable Double buyOrderPrice
    ) { }

    private record CachedProductLookup(
        ProductIdentity product,
        boolean playerInventoryStack,
        @Nullable CachedPrice prices
    ) {

        Optional<String> marketProductId() {
            return this.prices != null ? this.product.bazaarProductId() : Optional.empty();
        }
    }

    public final class InfoSiteButtonHook implements SlotHook {

        private InfoSiteButtonHook() { }

        @Override
        public boolean matches(SlotView view) {
            var cfg = ConfigManager.get().productInfo;
            return cfg.enabled
                && cfg.itemClickEnabled
                && ProductInfoProvider.this.openedProduct != null
                && !view.playerInventorySlot()
                && view.slotIdx() == CUSTOM_ITEM_IDX
                && view.getCurrInfo().inMenu(BazaarMenuType.Item);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            return ProductInfoProvider.this.createProductInfoItem();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var cfg = ConfigManager.get().productInfo;
            ProductInfoProvider.this.confirmAndOpen(
                cfg.site.format(ProductInfoProvider.this.openedProduct.productId())
            );
            return SlotClickResult.Consume;
        }
    }

    public final class ProductLookupHook implements SlotHook {

        private ProductLookupHook() { }

        @Override
        public boolean matches(SlotView view) {
            // Keep matching cheap; ctrl-shift eligibility may inspect inventory and only matters on click.
            return !view.getRawStack().isEmpty();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            if (!ctx.modifiers().controlDown() || !ctx.modifiers().shiftDown()) {
                return SlotClickResult.Pass;
            }

            if (!ProductInfoProvider.this.isCtrlShiftEnabled()) {
                return SlotClickResult.Pass;
            }

            var lookup = ProductInfoProvider.this.productLookupCache.get(ctx.view());
            if (!ProductInfoProvider.this.isCtrlShiftContextEnabled(lookup.playerInventoryStack())) {
                return SlotClickResult.Pass;
            }

            var productId = lookup.marketProductId();
            if (productId.isEmpty()) {
                log.warn("No Bazaar product found for {}", ctx.view().getRawStack().getHoverName().getString());
                return SlotClickResult.Pass;
            }

            var cfg = ConfigManager.get().productInfo;
            ProductInfoProvider.this.confirmAndOpen(cfg.site.format(productId.get()));
            return SlotClickResult.Consume;
        }
    }

    public static class ProductInfoProviderConfig {

        public boolean enabled = true;
        public boolean itemClickEnabled = true;
        public boolean ctrlShiftEnabled = true;
        public boolean ctrlShiftOnBazaarItems = true;
        public boolean showOutsideBazaar = false;
        public boolean priceTooltipEnabled = true;
        public InfoProviderSite site = InfoProviderSite.SkyblockBz;

        public Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Product Information"))
                .description(OptionDescription.of(Component.literal(
                    "Enable external product-page shortcuts and Bazaar price details in item tooltips.")))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<Boolean> createItemClickOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Show Product Info Paper on Product Page"))
                .description(ConfigScreen.createDescription(
                    "Open the selected product on your preferred information site when clicking the Product Info paper in its Bazaar menu.",
                    ConfigScreen.ConfigImage.PRODUCT_INFO_PAPER
                ))
                .binding(true, () -> this.itemClickEnabled, val -> this.itemClickEnabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<Boolean> createCtrlShiftOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Product Lookup Click"))
                .description(OptionDescription.of(Component.literal(
                    "Hold Ctrl+Shift and click a Bazaar product to open it on your preferred information site.")))
                .binding(true, () -> this.ctrlShiftEnabled, val -> this.ctrlShiftEnabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<Boolean> createCtrlShiftOnBazaarItemsOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Lookup Bazaar Menu Items"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Allow Product Lookup Click on items inside Bazaar menus. A normal click keeps its usual Bazaar or bookmark action."),
                    ConfigScreen.requires("Enable Product Lookup Click")
                )))
                .binding(
                    true,
                    () -> this.ctrlShiftOnBazaarItems,
                    val -> this.ctrlShiftOnBazaarItems = val
                )
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<Boolean> createShowOutsideBazaarOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Lookup Items Outside the Bazaar"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Allow Product Lookup Click in inventories and chests outside the Bazaar."),
                    ConfigScreen.requires("Enable Product Lookup Click")
                )))
                .binding(true, () -> this.showOutsideBazaar, val -> this.showOutsideBazaar = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<Boolean> createPriceTooltipOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Show Price Tooltips"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Add the best current buy-order and sell-offer prices to Bazaar product tooltips."),
                    ConfigScreen.note("Hold Shift over a stack to show its total value instead of the per-item value.")
                )))
                .binding(
                    true,
                    () -> this.priceTooltipEnabled,
                    val -> this.priceTooltipEnabled = val
                )
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<InfoProviderSite> createSiteOption() {
            return Option
                .<InfoProviderSite>createBuilder()
                .name(Component.literal("Preferred Information Site"))
                .description(OptionDescription.of(Component.literal(
                    "Choose the external website used by the Product Info paper and Product Lookup Click.")))
                .binding(
                    InfoProviderSite.SkyblockBz,
                    () -> this.site != null ? this.site : InfoProviderSite.SkyblockBz,
                    site -> this.site = site
                )
                .controller(InfoProviderSite::controller);
        }

        public OptionGroup createGroup() {
            var enabledBuilder = this.createEnabledOption();

            var ctrlShiftGroup = new OptionGrouping(this.createCtrlShiftOption()).addOptions(
                this.createCtrlShiftOnBazaarItemsOption(),
                this.createShowOutsideBazaarOption()
            );

            var rootGroup = new OptionGrouping(enabledBuilder)
                .addOptions(this.createItemClickOption())
                .addSubgroups(ctrlShiftGroup)
                .addOptions(
                    this.createPriceTooltipOption(),
                    this.createSiteOption()
                );

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Product Information"))
                .description(ConfigScreen.createDescription(
                    "View current prices in item tooltips or open a Bazaar product on an external information site.",
                    ConfigScreen.ConfigImage.PRODUCT_INFO
                ))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }

    private class ProductLookupCache {

        private final WeakHashMap<ItemStack, CachedProductLookup> cache = new WeakHashMap<>();

        ProductLookupCache() {
            log.debug("Initializing product lookup cache");
            ProductInfoProvider.this.bazaarData.addListener(products -> this.clear());
            ProductInfoProvider.this.bazaarData.addIndexChangeListener(this::clear);
        }

        CachedProductLookup get(ItemStack stack) {
            var cached = this.cache.get(stack);
            if (cached != null) {
                return cached;
            }

            return this.cache(
                stack,
                ProductInfoProvider.this.resolveProductForLookup(stack),
                ProductInfoProvider.this.isStackInPlayerInventory(stack)
            );
        }

        CachedProductLookup get(SlotView view) {
            var stack = view.getRawStack();
            var cached = this.cache.get(stack);
            if (cached != null) {
                return cached;
            }

            return this.cache(
                stack,
                ProductInfoProvider.this.resolveProductForLookup(view),
                view.playerInventorySlot()
            );
        }

        private CachedProductLookup cache(
            ItemStack stack,
            ProductIdentity product,
            boolean playerInventoryStack
        ) {
            var data = ProductInfoProvider.this.bazaarData;
            CachedPrice prices = null;
            if (data.contains(product)) {
                prices = new CachedPrice(
                    data.lowestSellOfferPrice(product).orElse(null),
                    data.highestBuyOrderPrice(product).orElse(null)
                );
            }

            var cached = new CachedProductLookup(product, playerInventoryStack, prices);
            this.cache.put(stack, cached);
            return cached;
        }

        void clear() {
            log.trace("Clearing product lookup cache with {} mappings", this.cache.size());
            this.cache.clear();
        }
    }
}
