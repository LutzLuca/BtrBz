package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.SoundUtil;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.screen.slot.SlotClickContext;
import com.github.lutzluca.btrbz.screen.slot.SlotClickResult;
import com.github.lutzluca.btrbz.screen.slot.SlotHook;
import com.github.lutzluca.btrbz.screen.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.screen.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.screen.slot.SlotView;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class OrderProtectionManager {

    private static final int CONFIRMATION_SLOT_INDEX = 13;
    private static final String VALIDATION_FAILURE_REASON = "Could not validate this order.";
    private static final String VALIDATION_UNAVAILABLE_REASON = "Order validation unavailable.";
    private static final BazaarMenuType[] CONFIRMATION_MENUS = {
        BazaarMenuType.BuyOrderConfirmation,
        BazaarMenuType.SellOfferConfirmation
    };

    private final BazaarData bazaarData;
    private final WeakHashMap<ItemStack, PendingOrderData> validationCache = new WeakHashMap<>();
    private final WeakHashMap<ItemStack, ValidationResult> validationFailureCache = new WeakHashMap<>();
    private @Nullable ValidationSettings validationSettings;

    private @Nullable BiConsumer<ItemStack, Optional<PendingOrderData>> setOrderCallback = null;

    public OrderProtectionManager(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.bazaarData.addListener(_ -> this.clearValidationCache());
        this.bazaarData.addIndexChangeListener(this::clearValidationCache);
        SlotHookRegistry.register(new ConfirmationHook());

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!BtrBz.isActive()) {
                return;
            }
            if (!ConfigStore.get().config().orderProtection.enabled) {
                return;
            }

            var validation = this.getValidationResult(stack).orElse(null);
            if (validation == null) {
                return;
            }

            boolean blocked = validation.protect();
            boolean ctrlHeld = Minecraft.getInstance().hasControlDown();

            lines.add(Component.empty());

            if (!blocked) {
                lines.add(Component
                    .literal("✓ ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("Order Protection: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Safe").withStyle(ChatFormatting.GREEN)));
                return;
            }

            if (ctrlHeld) {
                lines.add(Component
                    .literal("⚠ ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Order Protection: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Overridden").withStyle(ChatFormatting.GOLD)));

                appendReasonLines(lines, validation);

                lines.add(Component
                    .literal("Release Ctrl to cancel override")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                return;
            }

            lines.add(Component
                .literal("✗ ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("Order Protection: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Blocked").withStyle(ChatFormatting.RED)));

            appendReasonLines(lines, validation);

            lines.add(Component.literal("Hold Ctrl to override").withStyle(ChatFormatting.DARK_GRAY));
        });
    }

    public void onSetOrder(BiConsumer<ItemStack, Optional<PendingOrderData>> cb) {
        this.setOrderCallback = cb;
    }

    private static void appendReasonLines(List<Component> lines, ValidationResult validation) {
        lines.add(Component.literal("Reason:").withStyle(ChatFormatting.GRAY));
        for (var reason : validation.reasonLines()) {
            lines.add(Component.literal("  ").append(reason).withStyle(ChatFormatting.YELLOW));
        }
    }

    private void clearValidationCache() {
        this.validationCache.clear();
        this.validationFailureCache.clear();
    }

    private void invalidateChangedSettings() {
        var settings = ValidationSettings.from(ConfigStore.get().config().orderProtection);

        if (!settings.equals(this.validationSettings)) {
            this.clearValidationCache();
            this.validationSettings = settings;
        }
    }

    private Optional<ValidationResult> getValidationResult(ItemStack stack) {
        this.invalidateChangedSettings();
        return Optional
            .ofNullable(this.validationCache.get(stack))
            .map(PendingOrderData::validationResult)
            .or(() -> Optional.ofNullable(this.validationFailureCache.get(stack)));
    }

    private void dispatchSetOrder(ItemStack stack, Optional<PendingOrderData> data) {
        if (this.setOrderCallback != null) {
            this.setOrderCallback.accept(stack, data);
        }
    }

    private void validateConfirmationStack(ItemStack rawStack) {
        this.invalidateChangedSettings();
        if (rawStack.isEmpty() || GameUtils.getLore(rawStack).isEmpty()) {
            return;
        }

        if (this.validationCache.containsKey(rawStack) || this.validationFailureCache.containsKey(rawStack)) {
            return;
        }

        OrderInfoParser
            .parseSetOrderItem(rawStack, this.bazaarData)
            .map(orderInfo -> OrderValidator.validate(
                orderInfo,
                this.bazaarData,
                ConfigStore.get().config().orderProtection))
            .onSuccess(pendingOrder -> {
                this.validationCache.put(rawStack, pendingOrder);
                this.validationFailureCache.remove(rawStack);

                log.trace(
                    "Validated: {} - {}",
                    pendingOrder.orderInfo().product().bazaarProductId()
                        .orElse(pendingOrder.orderInfo().productName()),
                    pendingOrder.validationResult().protect() ? "BLOCKED" : "ALLOWED");
            })
            .onFailure(err -> {
                this.validationCache.remove(rawStack);
                this.validationFailureCache.put(
                    rawStack,
                    new ValidationUnavailable(VALIDATION_FAILURE_REASON));
                log.warn(
                    "Failed to parse or validate confirmation item '{}'",
                    rawStack.getHoverName().getString(),
                    err);
            });
    }

    public Optional<Pair<ValidationResult, Boolean>> getVisualOrderInfo(ItemStack stack) {
        if (!ConfigStore.get().config().orderProtection.enabled) {
            return Optional.empty();
        }

        return this.getValidationResult(stack)
            .map(data -> Pair.of(data, data.protect() && Minecraft.getInstance().hasControlDown()));
    }

    public final class ConfirmationHook implements SlotHook {

        private ConfirmationHook() {}

        @Override
        public boolean matches(SlotView view) {
            return !view.playerInventorySlot()
                && view.slotIdx() == CONFIRMATION_SLOT_INDEX
                && view.getCurrInfo().inMenu(CONFIRMATION_MENUS);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            if (ConfigStore.get().config().orderProtection.enabled) {
                OrderProtectionManager.this.validateConfirmationStack(ctx.view().getRawStack());
            }

            return ctx.view().getRawStack();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var stack = ctx.view().getRawStack();
            var cfg = ConfigStore.get().config().orderProtection;

            // The server does not update the screen, re-check the latest market snapshot on
            // the final click for the best available estimate.
            OrderProtectionManager.this.validationCache.remove(stack);
            OrderProtectionManager.this.validationFailureCache.remove(stack);
            OrderProtectionManager.this.validateConfirmationStack(stack);

            var pending = OrderProtectionManager.this.validationCache.get(stack);
            var validation = OrderProtectionManager.this.getValidationResult(stack)
                .orElseGet(() -> {
                    log.warn("No cached validation for confirmation item");
                    return new ValidationUnavailable(VALIDATION_UNAVAILABLE_REASON);
                });

            if (!cfg.enabled) {
                OrderProtectionManager.this.dispatchSetOrder(stack, Optional.ofNullable(pending));
                return SlotClickResult.Pass;
            }

            boolean isBlocked = validation.protect();
            if (isBlocked && !ctx.modifiers().controlDown()) {
                if (cfg.showChatMessage) {
                    Notifier.sendBlockedOrderMessage(validation);
                }
                SoundUtil.playSoundIf(cfg.soundOnBlocked, SoundEvents.VILLAGER_NO, 0.6f, 1);
                return SlotClickResult.Consume;
            }

            if (pending == null) {
                OrderProtectionManager.this.dispatchSetOrder(stack, Optional.empty());
                return SlotClickResult.Pass;
            }

            OrderProtectionManager.this.dispatchSetOrder(stack, Optional.of(pending));
            return SlotClickResult.Pass;
        }
    }

    public record PendingOrderData(
        OutstandingOrderInfo orderInfo, ValidationResult validationResult
    ) {}

    private record ValidationSettings(
        boolean enabled,
        boolean blockPercentage,
        boolean blockOpposing,
        double maxBuyPercentage,
        double maxSellPercentage
    ) {

        static ValidationSettings from(OrderProtectionConfig cfg) {
            return new ValidationSettings(
                cfg.enabled,
                cfg.blockUndercutPercentage,
                cfg.blockUndercutOfOpposing,
                cfg.maxBuyOrderUndercut,
                cfg.maxSellOfferUndercut);
        }
    }

    static final class OrderValidator {

        public static PendingOrderData validate(
            OutstandingOrderInfo info,
            BazaarData bazaarData,
            OrderProtectionConfig cfg
        ) {
            if (!cfg.enabled || (!cfg.blockUndercutPercentage && !cfg.blockUndercutOfOpposing)) {
                return new PendingOrderData(info, new Allowed());
            }

            var market = bazaarData.currentSnapshot();
            if (!market.available()) {
                return new PendingOrderData(info, new ValidationUnavailable("Bazaar prices are unavailable."));
            }

            var product = bazaarData.resolveIndexedProduct(info.product());
            if (product.isEmpty()) {
                log.warn("Order protection blocked unresolved product '{}'", info.productName());
                return new PendingOrderData(
                    info,
                    new ValidationUnavailable("Unknown or unresolved product: " + info.productName()));
            }

            var identity = ProductIdentity.fromIndex(product.get());
            if (!market.contains(identity)) {
                return new PendingOrderData(
                    info,
                    new ValidationUnavailable("Product is missing from Bazaar market data: " + info.productName()));
            }

            var prices = market.getMarketPrices(identity);
            var validationResult = validateOrder(info, prices, cfg);
            return new PendingOrderData(info, validationResult);
        }

        static ValidationResult validateOrder(
            OutstandingOrderInfo info,
            MarketPrices prices,
            OrderProtectionConfig cfg
        ) {
            var proposedTicks = priceTicks(info.pricePerUnit());
            var opposingPrice = info.type() == OrderType.Buy
                ? prices.lowestSellOfferPrice()
                : prices.highestBuyOrderPrice();
            if (cfg.blockUndercutOfOpposing && opposingPrice.isPresent()) {
                int comparison = proposedTicks.compareTo(priceTicks(opposingPrice.get()));
                boolean crossesSpread = info.type() == OrderType.Buy ? comparison >= 0 : comparison <= 0;
                if (crossesSpread) {
                    return new SpreadCrossing(info.type(), info.pricePerUnit(), opposingPrice.get());
                }
            }

            var referencePrice = info.type() == OrderType.Buy
                ? prices.highestBuyOrderPrice()
                : prices.lowestSellOfferPrice();
            if (cfg.blockUndercutPercentage && referencePrice.isPresent()) {
                var referenceTicks = priceTicks(referencePrice.get());
                var improvementTicks = info.type() == OrderType.Buy
                    ? proposedTicks.subtract(referenceTicks)
                    : referenceTicks.subtract(proposedTicks);
                // Whole 0.1-coin ticks avoid classifying binary rounding noise as a larger
                // improvement.
                if (improvementTicks.compareTo(BigDecimal.ONE) <= 0) {
                    return new Allowed();
                }

                double percentageLimit = info.type() == OrderType.Buy
                    ? cfg.maxBuyOrderUndercut
                    : cfg.maxSellOfferUndercut;
                if (improvementTicks.multiply(BigDecimal.valueOf(100))
                    .compareTo(referenceTicks.multiply(BigDecimal.valueOf(percentageLimit))) >= 0) {
                    return new PercentageExceeded(
                        info.type(), info.pricePerUnit(), referencePrice.get(), percentageLimit);
                }
            }

            return new Allowed();
        }

        private static BigDecimal priceTicks(double price) {
            return BigDecimal.valueOf(price).movePointRight(1).setScale(0, RoundingMode.HALF_UP);
        }
    }

    public sealed interface ValidationResult
        permits Allowed, SpreadCrossing, PercentageExceeded, ValidationUnavailable {

        default boolean protect() {
            return !(this instanceof Allowed);
        }

        List<Component> reasonLines();
    }

    record Allowed() implements ValidationResult {

        @Override
        public List<Component> reasonLines() {
            return List.of();
        }
    }

    record SpreadCrossing(OrderType type, double proposedPrice, double opposingPrice) implements ValidationResult {

        @Override
        public List<Component> reasonLines() {
            var orderName = this.type == OrderType.Buy ? "Buy Order" : "Sell Offer";
            var comparison = this.type == OrderType.Buy ? "at or above" : "at or below";
            var instantTrade = this.type == OrderType.Buy ? "insta buy" : "insta sell";
            return List.of(
                Component.literal(String.format(
                    "Your %s price of %s coins is %s",
                    orderName, Utils.formatDecimal(this.proposedPrice, 1, true), comparison)),
                Component.literal(String.format(
                    "the %s price of %s coins.", instantTrade,
                    Utils.formatDecimal(this.opposingPrice, 1, true))));
        }
    }

    record PercentageExceeded(
        OrderType type, double proposedPrice, double referencePrice, double limitPercentage
    )
        implements ValidationResult {

        @Override
        public List<Component> reasonLines() {
            double improvement = this.type == OrderType.Buy
                ? this.proposedPrice - this.referencePrice
                : this.referencePrice - this.proposedPrice;
            var change = this.type == OrderType.Buy
                ? "Increases the best buy-order price"
                : "Reduces the best sell-offer price";

            return List.of(Component.literal(String.format(
                "%s by %s%% (limit %s%%).",
                change,
                Utils.formatDecimal(improvement / this.referencePrice * 100, 1, true),
                Utils.formatDecimal(this.limitPercentage, 1, true))));
        }
    }

    record ValidationUnavailable(String reason) implements ValidationResult {

        @Override
        public List<Component> reasonLines() {
            return List.of(Component.literal(this.reason));
        }
    }

    public static class OrderProtectionConfig {

        public boolean enabled = true;
        public boolean showChatMessage = true;
        public boolean soundOnBlocked = true;

        public boolean blockUndercutPercentage = true;
        public double maxBuyOrderUndercut = 15.0;
        public double maxSellOfferUndercut = 15.0;

        public boolean blockUndercutOfOpposing = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Order Protection"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Check new order prices and stop submissions that match an enabled safety rule."),
                    ConfigScreen.note("Hold Ctrl while confirming to override a block."))))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createShowChatMessageOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Show Chat Messages"))
                .description(OptionDescription.of(Component.literal(
                    "Explain in chat which safety rule blocked an order and which price caused the warning.")))
                .binding(true, () -> this.showChatMessage, val -> this.showChatMessage = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createSoundOnBlockedOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Play Blocked-Order Sound"))
                .description(OptionDescription.of(Component.literal(
                    "Play a warning sound whenever Order Protection stops a submission.")))
                .binding(true, () -> this.soundOnBlocked, val -> this.soundOnBlocked = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createBlockUndercutPercentageOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Limit Price Undercutting"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Block buy-order price increases and sell-offer price reductions at the configured percentage "
                            + "or more relative to the best price on the same side."),
                    ConfigScreen.example(
                        "At a best price of 15M, a 100K change is about 0.67%. With a 15% limit, "
                            + "the blocked difference begins at 2.25M."),
                    ConfigScreen.note(
                        "A one-tick improvement of 0.1 coins is exempt from percentage protection. "
                            + "Protection against orders at instant-trade prices still applies."))))
                .binding(
                    true,
                    () -> this.blockUndercutPercentage,
                    val -> this.blockUndercutPercentage = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Double> createMaxBuyOrderUndercutOption() {
            return Option
                .<Double>createBuilder()
                .name(Component.literal("Maximum Buy-Order Increase (%)"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Block a buy order when it is this percentage or more above the best current buy-order price. "
                            + "A 0.1-coin increase passes this check."),
                    ConfigScreen.example(
                        "With a best price of 15M and a 5% limit, 15.75M or more is blocked."))))
                .binding(
                    this.maxBuyOrderUndercut,
                    () -> this.maxBuyOrderUndercut,
                    val -> this.maxBuyOrderUndercut = val)
                .controller(opt -> DoubleSliderControllerBuilder
                    .create(opt)
                    .range(0.0, 100.0)
                    .step(0.5));
        }

        public Option.Builder<Double> createMaxSellOfferUndercutOption() {
            return Option
                .<Double>createBuilder()
                .name(Component.literal("Maximum Sell-Offer Reduction (%)"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Block a sell offer when it is this percentage or more below the best current "
                            + "sell-offer price. A 0.1-coin reduction passes this check."),
                    ConfigScreen.example(
                        "With a best price of 15M and a 5% limit, 14.25M or less is blocked."))))
                .binding(
                    this.maxSellOfferUndercut,
                    () -> this.maxSellOfferUndercut,
                    val -> this.maxSellOfferUndercut = val)
                .controller(opt -> DoubleSliderControllerBuilder
                    .create(opt)
                    .range(0.0, 100.0)
                    .step(0.5));
        }

        public Option.Builder<Boolean> createBlockUndercutOfOpposingOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Block Orders at Instant-Trade Prices"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Block buy orders priced at or above the best sell offer, and sell offers priced at or below "
                            + "the best buy order. Use an instant trade instead."),
                    ConfigScreen.example(
                        "If the best buy order is 100 and the best sell offer is 105, a sell offer of 100 or less "
                            + "and a buy order of 105 or more are blocked."))))
                .binding(
                    true,
                    () -> this.blockUndercutOfOpposing,
                    val -> this.blockUndercutOfOpposing = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var undercutGroup = new OptionGrouping(this.createBlockUndercutPercentageOption()).addOptions(
                this.createMaxSellOfferUndercutOption(),
                this.createMaxBuyOrderUndercutOption());

            var rootGroup = new OptionGrouping(this.createEnabledOption())
                .addOptions(
                    this.createShowChatMessageOption(),
                    this.createSoundOnBlockedOption(),
                    this.createBlockUndercutOfOpposingOption())
                .addSubgroups(undercutGroup);

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Order Protection"))
                .description(ConfigScreen.createDescription(
                    "Prevent accidental orders at unusually aggressive prices before they are submitted to the Bazaar.",
                    ConfigImages.OrderProtection))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }
}
