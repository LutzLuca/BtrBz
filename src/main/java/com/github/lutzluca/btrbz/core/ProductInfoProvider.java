package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
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
import java.util.Set;
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
import java.util.stream.StreamSupport;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ProductInfoProvider {

    private static final int CUSTOM_ITEM_IDX = 22;
    private static final int PRODUCT_IDX = 13;

    /**
     * Screens that are part of the product order flow. The opened product info
     * is preserved when transitioning between these screens
     */
    private static final Set<BazaarMenuType> PRODUCT_FLOW_MENUS = Set.of(
        BazaarMenuType.Item,
        BazaarMenuType.BuyOrderSetupVolume,
        BazaarMenuType.BuyOrderSetupPrice,
        BazaarMenuType.BuyOrderConfirmation,
        BazaarMenuType.SellOfferSetup,
        BazaarMenuType.SellOfferConfirmation
    );

    private static final BazaarMenuType[] PRODUCT_FLOW_MENUS_ARRAY = PRODUCT_FLOW_MENUS.toArray(BazaarMenuType[]::new);

    private final BazaarData bazaarData;
    private final PriceCache priceCache;
    @Getter
    private @Nullable ProductNameInfo openedProductNameInfo;

    public ProductInfoProvider(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.priceCache = new PriceCache();
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
                var productName = inv
                    .getItem(PRODUCT_IDX)
                    .map(ItemStack::getHoverName)
                    .map(Component::getString)
                    .orElse("<empty>");

                this.bazaarData.nameToId(productName).ifPresentOrElse(
                    productId -> {
                        this.openedProductNameInfo = new ProductNameInfo(productId, productName);
                        log.debug("Opened product: {} ({})", productName, productId);
                    }, () -> {
                        this.openedProductNameInfo = null;
                        log.warn("No product id found for {}", productName);
                    }
                );
            }
        );

        ScreenInfoHelper.registerOnSwitch(curr -> {
            if (this.openedProductNameInfo == null) {
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
                && !curr.inMenu(PRODUCT_FLOW_MENUS_ARRAY);

            if (transientFlowClose) {
                log.debug(
                    "Preserving product context on transient flow close: {} ({})",
                    this.openedProductNameInfo.productName,
                    this.openedProductNameInfo.productId
                );
                return;
            }

            if (closed || leftToNonFlowBazaar) {
                log.debug(
                    "Leaving product flow, clearing product: {} ({})",
                    this.openedProductNameInfo.productName,
                    this.openedProductNameInfo.productId
                );
                this.openedProductNameInfo = null;
            }
        });
    }

    private void registerSlotHooks() {
        SlotHookRegistry.register(new InfoSiteButtonHook());
        SlotHookRegistry.register(new ProductLookupHook());
    }

    private ItemStack createProductInfoItem() {
        var cfg = ConfigManager.get().productInfo;
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
        return item;
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

            var priceInfo = this.priceCache.get(stack);
            if (priceInfo.isEmpty()) {
                return;
            }

            var cached = priceInfo.get();
            var count = stack.getCount();
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
        var cfg = ConfigManager.get().productInfo;
        if (!cfg.enabled || !cfg.ctrlShiftEnabled) {
            return false;
        }

        var productName = stack.getHoverName().getString();
        if (this.resolveProductId(stack, productName).isEmpty()) {
            return false;
        }

        if (ScreenInfoHelper.inMenu(BazaarMenuType.Main, BazaarMenuType.Item)) {
            return this.isStackInPlayerInventory(stack);
        }

        if (cfg.showOutsideBazaar) {
            return true;
        }

        return ScreenInfoHelper.inBazaar();
    }

    private boolean isStackInPlayerInventory(ItemStack stack) {
        // NOTE: reference equality is intentional here
        // noinspection DataFlowIssue
        return Try
            .of(() -> StreamSupport
                .stream(Minecraft.getInstance().player.getInventory().spliterator(), false)
                .anyMatch(playerStack -> playerStack == stack))
            .getOrElse(false);
    }

    private void confirmAndOpen(String link) {
        var client = Minecraft.getInstance();
        client.setScreen(new ConfirmLinkScreen(
            confirmed -> {
                if (confirmed) {
                    Try
                        //? if >=1.21.11 {
                        /*.run(() -> net.minecraft.util.Util.getPlatform().openUri(new URI(link)))
                        *///?} else {
                        .run(() -> net.minecraft.Util.getPlatform().openUri(new URI(link)))
                        //?}
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

    private Optional<String> resolveProductId(ItemStack stack, String name) {
        var direct = this.bazaarData.nameToId(name);
        if (direct.isPresent()) {
            return direct;
        }

        if ("Enchanted Book".equals(name)) {
            var ids = OrderInfoParser
                .getLore(stack)
                .stream()
                .map(this.bazaarData::nameToId)
                .flatMap(Optional::stream)
                .distinct()
                .toList();

            return ids.size() == 1 ? Optional.of(ids.getFirst()) : Optional.empty();
        }

        var delimiter = name.lastIndexOf(' ');
        if (delimiter == -1 || !Utils.isValidRomanNumeral(name.substring(delimiter).trim())) {
            return Optional.empty();
        }

        return this.bazaarData.nameToId(name.substring(0, delimiter).trim());
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

    public record ProductNameInfo(@NotNull String productId, @NotNull String productName) { }

    private record CachedPrice(@Nullable Double sellOfferPrice, @Nullable Double buyOrderPrice) { }

    public final class InfoSiteButtonHook implements SlotHook {

        private InfoSiteButtonHook() { }

        @Override
        public boolean matches(SlotView view) {
            var cfg = ConfigManager.get().productInfo;
            return cfg.enabled
                && cfg.itemClickEnabled
                && ProductInfoProvider.this.openedProductNameInfo != null
                && !view.playerInventorySlot()
                && view.slotIdx() == CUSTOM_ITEM_IDX
                && view.currInfo().inMenu(BazaarMenuType.Item);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            return ProductInfoProvider.this.createProductInfoItem();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var cfg = ConfigManager.get().productInfo;
            ProductInfoProvider.this.confirmAndOpen(
                cfg.site.format(ProductInfoProvider.this.openedProductNameInfo.productId)
            );
            return SlotClickResult.Consume;
        }
    }

    public final class ProductLookupHook implements SlotHook {

        private ProductLookupHook() { }

        @Override
        public boolean matches(SlotView view) {
            return !view.rawStack().isEmpty() && ProductInfoProvider.this.shouldApplyCtrlShiftClick(view.rawStack());
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            if (!ctx.modifiers().controlDown() || !ctx.modifiers().shiftDown()) {
                return SlotClickResult.Pass;
            }

            var cfg = ConfigManager.get().productInfo;
            var stack = ctx.view().rawStack();
            var name = stack.getHoverName().getString();
            var id = ProductInfoProvider.this.resolveProductId(stack, name);
            if (id.isEmpty()) {
                log.warn("No product id found for {}", name);
                return SlotClickResult.Pass;
            }

            ProductInfoProvider.this.confirmAndOpen(cfg.site.format(id.get()));
            return SlotClickResult.Consume;
        }
    }

    public static class ProductInfoProviderConfig {

        public boolean enabled = true;
        public boolean itemClickEnabled = true;
        public boolean ctrlShiftEnabled = true;
        public boolean showOutsideBazaar = false;
        public boolean priceTooltipEnabled = true;
        public InfoProviderSite site = InfoProviderSite.SkyblockBz;

        public Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Product Info System"))
                .description(OptionDescription.of(Component.literal(
                    "Master switch that enables or disables the entire product information feature.")))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<Boolean> createItemClickOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Product Info Click"))
                .description(OptionDescription.of(Component.literal(
                    "Allows clicking the 'Product Info' paper item in the Bazaar Item menu to open the product page.")))
                .binding(true, () -> this.itemClickEnabled, val -> this.itemClickEnabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<Boolean> createCtrlShiftOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable CTRL+SHIFT Click Shortcut"))
                .description(OptionDescription.of(Component.literal(
                    "Allows viewing Bazaar product info by holding CTRL+SHIFT and clicking the item.\n" + "Disabled in the Bazaar Item menu to avoid conflicts with bookmarks.")))
                .binding(true, () -> this.ctrlShiftEnabled, val -> this.ctrlShiftEnabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<Boolean> createShowOutsideBazaarOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Show Product Info Outside of the Bazaar"))
                .description(OptionDescription.of(Component.literal(
                    "Allows the CTRL+SHIFT Click shortcut to work outside the Bazaar (e.g., in chests or player inventory).")))
                .binding(true, () -> this.showOutsideBazaar, val -> this.showOutsideBazaar = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Builder<Boolean> createPriceTooltipOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Show Price Tooltips"))
                .description(OptionDescription.of(Component.literal(
                    "Display current Buy Order and Sell Offer prices in item tooltips for Bazaar items.")))
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
                .name(Component.literal("Preferred Product Info Site"))
                .description(OptionDescription.of(Component.literal(
                    "Select which external website to open for product information.")))
                .binding(
                    InfoProviderSite.SkyblockBz,
                    () -> this.site != null ? this.site : InfoProviderSite.SkyblockBz,
                    site -> this.site = site
                )
                .controller(InfoProviderSite::controller);
        }

        public OptionGroup createGroup() {
            var enabledBuilder = this.createEnabledOption();

            var rootGroup = new OptionGrouping(enabledBuilder).addOptions(
                this.createItemClickOption(),
                this.createCtrlShiftOption(),
                this.createShowOutsideBazaarOption(),
                this.createPriceTooltipOption(),
                this.createSiteOption()
            );

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Product Info"))
                .description(OptionDescription.of(Component.literal(
                    "Settings for the product information helper (tooltips, click-to-open, site selection)")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }

    private class PriceCache {

        private final WeakHashMap<ItemStack, @Nullable CachedPrice> cache = new WeakHashMap<>();

        PriceCache() {
            log.debug("Initializing Price Cache");
            ProductInfoProvider.this.bazaarData.addListener(products -> {
                log.trace(
                    "Bazaar data updated, clearing price cache with {} mappings",
                    this.cache.size()
                );

                cache.clear();
            });
        }

        Optional<CachedPrice> get(ItemStack stack) {
            if (cache.containsKey(stack)) {
                return Optional.ofNullable(cache.get(stack));
            }
            var name = stack.getHoverName().getString();
            var productId = resolveProductId(stack, name);

            if (productId.isEmpty()) {
                cache.put(stack, null);
                return Optional.empty();
            }

            var data = ProductInfoProvider.this.bazaarData;

            var sellOfferPrice = data.lowestSellPrice(productId.get()).orElse(null);
            var buyOrderPrice = data.highestBuyPrice(productId.get()).orElse(null);

            var cached = new CachedPrice(sellOfferPrice, buyOrderPrice);
            cache.put(stack, cached);

            log.trace(
                "Cached price for '{}' (id: {}): buy={}, sell={}",
                name,
                productId.get(),
                buyOrderPrice,
                sellOfferPrice
            );

            return Optional.of(cached);
        }
    }
}
