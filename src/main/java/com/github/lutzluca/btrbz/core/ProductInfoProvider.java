package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
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
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ProductInfoProvider {

    private static final int CUSTOM_ITEM_IDX = 22;
    private static final int PRODUCT_IDX = 13;

    private static ProductInfoProvider instance;
    private final PriceCache priceCache;
    private String openedProductId;

    private ProductInfoProvider() {
        this.priceCache = new PriceCache();
        this.registerProductInfoListener();
        this.registerInfoProviderItemOverride();
        this.registerInfoProviderClick();
        this.registerTooltipDisplay();
    }

    public static void init() {
        if (instance == null) {
            instance = new ProductInfoProvider();
            log.info("Initialized ProductInfoProvider");
        }
    }

    private static Optional<String> extractProductName(GenericContainerScreen screen) {
        var handler = screen.getScreenHandler();
        var inv = handler.getInventory();
        return Try
            .of(() -> inv.getStack(PRODUCT_IDX))
            .map(ItemStack::getName)
            .map(Text::getString)
            .toJavaOptional();
    }

    private static Text createPriceText(
        String label,
        @Nullable Double price,
        int stackCount,
        boolean isShiftHeld
    ) {
        var priceText = Text.literal(label).formatted(Formatting.AQUA);

        if (price != null) {
            var displayPrice = isShiftHeld && stackCount > 1 ? price * stackCount : price;
            priceText.append(Text
                .literal(Utils.formatDecimal(displayPrice, 1, true) + " coins")
                .formatted(Formatting.GOLD, Formatting.BOLD));

            if (isShiftHeld && stackCount > 1) {
                priceText.append(Text
                    .literal(" (" + stackCount + "x)")
                    .formatted(Formatting.DARK_GRAY));
            }
        } else {
            priceText.append(Text.literal("Not Available").formatted(Formatting.GRAY));
        }

        return priceText;
    }


    private void registerProductInfoListener() {
        ScreenInfoHelper.registerOnLoaded(
            info -> info.inMenu(BazaarMenuType.Item), (info, inv) -> {
                var cfg = ConfigManager.get().productInfo;
                if (!cfg.enabled) {
                    return;
                }

                var productName = inv
                    .getItem(PRODUCT_IDX)
                    .map(ItemStack::getName)
                    .map(Text::getString)
                    .orElse("<empty>");

                BtrBz.bazaarData().nameToId(productName).ifPresentOrElse(
                    id -> {
                        this.openedProductId = id;
                        log.debug("Opened product: {}", id);
                    }, () -> {
                        this.openedProductId = null;
                        log.warn("No product id found for {}", productName);
                    }
                );
            }
        );

        ScreenInfoHelper.registerOnClose(
            ignored -> true, ignored -> {
                if (this.openedProductId != null) {
                    log.debug("Closing product: {}", this.openedProductId);
                }
                this.openedProductId = null;
            }
        );

        ScreenInfoHelper.registerOnSwitch(info -> {
            if (!info.inMenu(BazaarMenuType.Item)) {
                return;
            }

            var productName = info
                .getGenericContainerScreen()
                .flatMap(ProductInfoProvider::extractProductName);

            productName.flatMap(BtrBz.bazaarData()::nameToId).ifPresentOrElse(
                id -> {
                    this.openedProductId = id;
                    log.debug("Switched to product: {}", id);
                }, () -> {
                    this.openedProductId = null;
                    log.warn(
                        "Failed to determine opened product id when switching to loaded Item menu product name: '{}'",
                        productName
                    );
                }
            );
        });
    }

    private void registerInfoProviderItemOverride() {
        ItemOverrideManager.register((info, slot, original) -> {
            var cfg = ConfigManager.get().productInfo;
            if (!cfg.enabled || !cfg.itemClickEnabled) {
                return Optional.empty();
            }
            if (this.openedProductId == null || slot.getIndex() != CUSTOM_ITEM_IDX) {
                return Optional.empty();
            }

            if (GameUtils.isPlayerInventorySlot(slot)) {
                return Optional.empty();
            }

            var item = new ItemStack(Items.PAPER);
            item.set(
                DataComponentTypes.CUSTOM_NAME,
                Text
                    .literal("Product Info")
                    .formatted(Formatting.AQUA, Formatting.BOLD)
                    .styled(style -> style.withItalic(false))
            );

            var loreLines = Stream.of(
                Text.literal("View detailed Bazaar statistics").formatted(Formatting.GRAY),

                Text.literal("and live market data for this item.").formatted(Formatting.GRAY),

                Text.empty(),

                Text
                    .literal("➤ Click to open ")
                    .formatted(Formatting.DARK_GRAY)
                    .styled(style -> style.withItalic(false))
                    .append(Text
                        .literal(cfg.site.displayName())
                        .formatted(Formatting.AQUA, Formatting.BOLD))
            ).<Text>map(line -> line.styled(style -> style.withItalic(false))).toList();

            item.set(DataComponentTypes.LORE, new LoreComponent(loreLines));
            return Optional.of(item);
        });
    }

    private void registerInfoProviderClick() {
        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get().productInfo;
                if (!cfg.enabled || !cfg.itemClickEnabled || openedProductId == null || slot == null) {
                    return false;
                }

                if (GameUtils.isPlayerInventorySlot(slot)) {
                    return false;
                }

                return slot.getIndex() == CUSTOM_ITEM_IDX && info.inMenu(BazaarMenuType.Item);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get().productInfo;
                confirmAndOpen(cfg.site.format(openedProductId));
                return true;
            }
        });

        ScreenActionManager.register(new ScreenClickRule() {
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get().productInfo;
                if (!cfg.enabled || !cfg.ctrlShiftEnabled || slot == null) {
                    return false;
                }

                var stack = slot.getStack();
                return !stack.isEmpty() && shouldApplyCtrlShiftClick(stack) && Screen.hasControlDown() && Screen.hasShiftDown();
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get().productInfo;
                var stack = slot.getStack();
                var name = stack.getName().getString();
                var id = resolveProductId(stack, name);
                if (id.isEmpty()) {
                    log.warn("No product id found for {}", name);
                    return false;
                }

                confirmAndOpen(cfg.site.format(id.get()));
                return true;
            }
        });
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

            lines.add(Text.empty());
            lines.add(Text
                .literal("CTRL")
                .formatted(Formatting.AQUA, Formatting.BOLD)
                .append(Text.literal("+").formatted(Formatting.DARK_GRAY))
                .append(Text.literal("SHIFT").formatted(Formatting.AQUA, Formatting.BOLD))
                .append(Text.literal(" Click ").formatted(Formatting.GRAY))
                .append(Text
                    .literal("to view on ")
                    .formatted(Formatting.DARK_GRAY)
                    .styled(style -> style.withBold(false)))
                .append(Text
                    .literal(cfg.site.displayName())
                    .formatted(Formatting.AQUA, Formatting.BOLD)));

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
            var isShiftHeld = Screen.hasShiftDown();

            lines.add(Text.empty());

            if (count > 1 && !isShiftHeld) {
                lines.add(Text
                    .literal("Hold ")
                    .formatted(Formatting.DARK_GRAY)
                    .append(Text.literal("SHIFT").formatted(Formatting.AQUA, Formatting.BOLD))
                    .append(Text.literal(" to show for (").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(String.valueOf(count)).formatted(Formatting.LIGHT_PURPLE))
                    .append(Text.literal("x").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(")").formatted(Formatting.DARK_GRAY)));
            }

            if (count > 1 && isShiftHeld) {
                lines.add(Text
                    .literal("Showing price for ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(String.valueOf(count)).formatted(Formatting.LIGHT_PURPLE))
                    .append(Text.literal("x").formatted(Formatting.GRAY)));
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

        var productName = stack.getName().getString();
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
                .stream(MinecraftClient.getInstance().player.getInventory().spliterator(), false)
                .anyMatch(playerStack -> playerStack == stack))
            .getOrElse(false);
    }

    private void confirmAndOpen(String link) {
        var client = MinecraftClient.getInstance();
        client.setScreen(new ConfirmLinkScreen(
            confirmed -> {
                if (confirmed) {
                    Try
                        .run(() -> net.minecraft.util.Util.getOperatingSystem().open(new URI(link)))
                        .onFailure(err -> Notifier.notifyPlayer(Text
                            .literal("Failed to open link: ")
                            .formatted(Formatting.RED)
                            .append(Text
                                .literal(link)
                                .formatted(Formatting.UNDERLINE, Formatting.BLUE))));
                }

                var prev = ScreenInfoHelper.get().getPrevInfo();
                client.setScreen(prev.inMenu(BazaarMenuType.Item) ? prev.getScreen() : null);
            }, link, true
        ));
    }

    private Optional<String> resolveProductId(ItemStack stack, String name) {
        var direct = BtrBz.bazaarData().nameToId(name);
        if (direct.isPresent()) {
            return direct;
        }

        if ("Enchanted Book".equals(name)) {
            var ids = OrderInfoParser
                .getLore(stack)
                .stream()
                .map(potentialProduct -> BtrBz.bazaarData().nameToId(potentialProduct))
                .flatMap(Optional::stream)
                .distinct()
                .toList();

            return ids.size() == 1 ? Optional.of(ids.getFirst()) : Optional.empty();
        }

        var delimiter = name.lastIndexOf(' ');
        if (delimiter == -1 || !Utils.isValidRomanNumeral(name.substring(delimiter).trim())) {
            return Optional.empty();
        }

        return BtrBz.bazaarData().nameToId(name.substring(0, delimiter).trim());
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
                .formatValue(site -> Text
                    .literal("Use site: ")
                    .append(Text
                        .literal(site.displayName())
                        .formatted(Formatting.AQUA, Formatting.BOLD)));
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

    private record CachedPrice(@Nullable Double sellOfferPrice, @Nullable Double buyOrderPrice) { }

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
                .name(Text.literal("Enable Product Info System"))
                .description(OptionDescription.of(Text.literal(
                    "Master switch that enables or disables the entire product information feature.")))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createItemClickOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Enable Product Info Click"))
                .description(OptionDescription.of(Text.literal(
                    "Allows clicking the 'Product Info' paper item in the Bazaar Item menu to open the product page.")))
                .binding(true, () -> this.itemClickEnabled, val -> this.itemClickEnabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createCtrlShiftOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Enable CTRL+SHIFT Click Shortcut"))
                .description(OptionDescription.of(Text.literal(
                    "Allows viewing Bazaar product info by holding CTRL+SHIFT and clicking the item.\n" + "Disabled in the Bazaar Item menu to avoid conflicts with bookmarks.")))
                .binding(true, () -> this.ctrlShiftEnabled, val -> this.ctrlShiftEnabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createShowOutsideBazaarOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Show Product Info Outside of the Bazaar"))
                .description(OptionDescription.of(Text.literal(
                    "Allows the CTRL+SHIFT Click shortcut to work outside the Bazaar (e.g., in chests or player inventory).")))
                .binding(true, () -> this.showOutsideBazaar, val -> this.showOutsideBazaar = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createPriceTooltipOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Show Price Tooltips"))
                .description(OptionDescription.of(Text.literal(
                    "Display current Buy Order and Sell Offer prices in item tooltips for Bazaar items.")))
                .binding(
                    true,
                    () -> this.priceTooltipEnabled,
                    val -> this.priceTooltipEnabled = val
                )
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<InfoProviderSite> createSiteOption() {
            return Option
                .<InfoProviderSite>createBuilder()
                .name(Text.literal("Preferred Product Info Site"))
                .description(OptionDescription.of(Text.literal(
                    "Select which external website to open for product information.")))
                .binding(this.site, () -> this.site, site -> this.site = site)
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
                .name(Text.literal("Product Info"))
                .description(OptionDescription.of(Text.literal(
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
            BtrBz.bazaarData().addListener(products -> {
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
            var name = stack.getName().getString();
            var productId = resolveProductId(stack, name);

            if (productId.isEmpty()) {
                cache.put(stack, null);
                return Optional.empty();
            }

            var data = BtrBz.bazaarData();

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
