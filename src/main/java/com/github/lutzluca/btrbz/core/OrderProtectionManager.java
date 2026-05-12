package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.OrderPriceInfo;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ClickOutcome;
import com.github.lutzluca.btrbz.utils.ScreenInventoryTracker.Inventory;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.slot.SlotInterceptorManager;
import com.github.lutzluca.btrbz.utils.slot.SlotInterceptorRegistration;
import com.github.lutzluca.btrbz.utils.SoundUtil;
import com.github.lutzluca.btrbz.utils.Utils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import java.util.Arrays;
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

    private final BazaarData bazaarData;
    private final WeakHashMap<ItemStack, PendingOrderData> validationCache = new WeakHashMap<>();
    private final WeakHashMap<ItemStack, ValidationResult> validationFailureCache = new WeakHashMap<>();

    private @Nullable BiConsumer<ItemStack, Optional<PendingOrderData>> setOrderCallback = null;

    public OrderProtectionManager(BazaarData bazaarData) {
        this.bazaarData = bazaarData;

        ScreenInfoHelper.registerOnLoaded(
            info -> info.inMenu(
                BazaarMenuType.BuyOrderConfirmation,
                BazaarMenuType.SellOfferConfirmation
            ), this::precomputeValidation
        );

        SlotInterceptorManager.register(
            SlotInterceptorRegistration
                .named("order-protection.confirmation-slot")
                .matches(ctx ->
                    ctx.inMenu(
                        BazaarMenuType.BuyOrderConfirmation,
                        BazaarMenuType.SellOfferConfirmation
                    ) && !ctx.isPlayerInventorySlot() &&
                        ctx.containerSlot() == CONFIRMATION_SLOT_INDEX
                )
                .onClick(ctx -> {
                    var stack = ctx.rawItem();
                    var cfg = ConfigManager.get().orderProtection;
                    var pending = this.validationCache.get(stack);
                    if (!cfg.enabled) {
                        this.dispatchSetOrder(stack, Optional.ofNullable(pending));
                        return ClickOutcome.Handled;
                    }

                    if (!this.hasCachedValidation(stack)) {
                        this.computeValidation(stack);
                    }

                    pending = this.validationCache.get(stack);
                    var validation = this.getValidationResult(stack)
                        .orElseGet(() -> {
                            log.warn("No cached validation for confirmation item");
                            return ValidationResult.blocked(VALIDATION_UNAVAILABLE_REASON);
                        });

                    if (validation.protect() && !ctx.modifiers().controlDown()) {
                        if (cfg.showChatMessage) {
                            Notifier.sendBlockedOrderMessage(validation);
                        }
                        SoundUtil.playSoundIf(cfg.soundOnBlocked, SoundEvents.VILLAGER_NO, 0.6f, 1);
                        return ClickOutcome.Cancel;
                    }

                    if (pending == null) {
                        this.dispatchSetOrder(stack, Optional.empty());
                        return ClickOutcome.Handled;
                    }

                    this.dispatchSetOrder(stack, Optional.of(pending));
                    return ClickOutcome.Handled;
                })
                .build()
        );

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!ConfigManager.get().orderProtection.enabled) {
                return;
            }

            var validation = this.getOrComputeVisualValidation(stack).orElse(null);
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

                if (validation.reason() != null) {
                    lines.add(Component.literal("Reason:").withStyle(ChatFormatting.GRAY));
                    Arrays
                        .stream(validation.reason().split("\n"))
                        .map(line -> Component.literal("  " + line).withStyle(ChatFormatting.YELLOW))
                        .forEach(lines::add);
                }

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

            if (validation.reason() != null) {
                lines.add(Component.literal("Reason:").withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("  " + validation.reason()).withStyle(ChatFormatting.YELLOW));
            }

            lines.add(Component.literal("Hold Ctrl to override").withStyle(ChatFormatting.DARK_GRAY));
        });
    }

    private void precomputeValidation(ScreenInfo info, Inventory inventory) {
        if (!ConfigManager.get().orderProtection.enabled) {
            return;
        }

        resolvePrecomputedValidationStack(info, inventory)
            .ifPresent(this::computeValidation);
    }

    static Optional<ItemStack> resolvePrecomputedValidationStack(ScreenInfo info, Inventory inventory) {
        return info.getItemStack(CONFIRMATION_SLOT_INDEX)
            .filter(stack -> !stack.isEmpty())
            .or(() -> inventory.getItem(CONFIRMATION_SLOT_INDEX).filter(stack -> !stack.isEmpty()));
    }

    private boolean hasCachedValidation(ItemStack stack) {
        return this.validationCache.containsKey(stack) || this.validationFailureCache.containsKey(stack);
    }

    private void computeValidation(ItemStack stack) {
        if (stack.isEmpty() || this.hasCachedValidation(stack)) {
            return;
        }

        OrderInfoParser
            .parseSetOrderItem(stack)
            .map(orderInfo ->
                OrderValidator.validate(
                    orderInfo,
                    this.bazaarData,
                    ConfigManager.get().orderProtection
                )
            )
            .onSuccess(pendingOrder -> {
                this.validationCache.put(stack, pendingOrder);
                this.validationFailureCache.remove(stack);

                log.trace(
                    "Validated: {} - {}",
                    pendingOrder.orderInfo().productName(),
                    pendingOrder.validationResult().protect() ? "BLOCKED" : "ALLOWED"
                );
            })
            .onFailure(err -> {
                this.validationCache.remove(stack);
                this.validationFailureCache.put(
                    stack,
                    ValidationResult.blocked(VALIDATION_FAILURE_REASON)
                );
                log.warn(
                    "Failed to parse or validate confirmation item '{}'",
                    stack.getHoverName().getString(),
                    err
                );
            });
    }

    public void onSetOrder(BiConsumer<ItemStack, Optional<PendingOrderData>> cb) {
        this.setOrderCallback = cb;
    }

    private Optional<ValidationResult> getValidationResult(ItemStack stack) {
        return Optional
            .ofNullable(this.validationCache.get(stack))
            .map(PendingOrderData::validationResult)
            .or(() -> Optional.ofNullable(this.validationFailureCache.get(stack)));
    }

    private Optional<ValidationResult> getOrComputeVisualValidation(ItemStack stack) {
        var cached = this.getValidationResult(stack);
        if (cached.isPresent()) {
            return cached;
        }

        if (!isConfirmationItem(stack)) {
            return Optional.empty();
        }

        if (!ScreenInfoHelper.get().getCurrInfo().inMenu(
            BazaarMenuType.BuyOrderConfirmation,
            BazaarMenuType.SellOfferConfirmation
        )) {
            return Optional.empty();
        }

        this.computeValidation(stack);
        return this.getValidationResult(stack);
    }

    private static boolean isConfirmationItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        var title = stack.getHoverName().getString();
        return title.equals("Buy Order") || title.equals("Sell Offer");
    }

    private void dispatchSetOrder(ItemStack stack, Optional<PendingOrderData> data) {
        if (this.setOrderCallback != null) {
            this.setOrderCallback.accept(stack, data);
        }
    }

    public Optional<Pair<ValidationResult, Boolean>> getVisualOrderInfo(ItemStack stack) {
        if (!ConfigManager.get().orderProtection.enabled) {
            return Optional.empty();
        }

        return this.getOrComputeVisualValidation(stack).map(data -> Pair.of(data, false));
    }

    public record PendingOrderData(
        OutstandingOrderInfo orderInfo, ValidationResult validationResult
    ) { }

    private static final class OrderValidator {

        public static PendingOrderData validate(OutstandingOrderInfo info, BazaarData bazaarData, OrderProtectionConfig cfg) {
            if (!cfg.enabled || (!cfg.blockUndercutPercentage && !cfg.blockUndercutOfOpposing)) {
                return new PendingOrderData(info, ValidationResult.allowed());
            }

            var productId = bazaarData.nameToId(info.productName());
            if (productId.isEmpty()) {
                log.trace("No product ID found for {}, allowing order", info.productName());
                return new PendingOrderData(info, ValidationResult.allowed());
            }

            var prices = bazaarData.getOrderPrices(productId.get());
            var validationResult = validateOrder(info, prices, cfg);
            return new PendingOrderData(info, validationResult);
        }

        private static ValidationResult validateOrder(
            OutstandingOrderInfo info,
            OrderPriceInfo prices,
            OrderProtectionConfig cfg
        ) {
            var bestSell = prices.sellOfferPrice();
            var bestBuy = prices.buyOrderPrice();

            return switch (info.type()) {
                case Sell -> validateSellOffer(info, bestSell, bestBuy, cfg);
                case Buy -> validateBuyOrder(info, bestSell, bestBuy, cfg);
            };
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static ValidationResult validateSellOffer(
            OutstandingOrderInfo info,
            Optional<Double> bestSell,
            Optional<Double> bestBuy,
            OrderProtectionConfig cfg
        ) {
            if (bestBuy.isPresent() && info.pricePerUnit() <= bestBuy.get() && cfg.blockUndercutOfOpposing) {
                return ValidationResult.blocked(String.format(
                    "Your Sell Offer price of (%s) is \nbelow the insta sell price of (%s)",
                    Utils.formatDecimal(info.pricePerUnit(), 1, true),
                    Utils.formatDecimal(bestBuy.get(), 1, true)
                ));
            }

            if (bestSell.isPresent() && cfg.blockUndercutPercentage) {
                double undercut = (bestSell.get() - info.pricePerUnit()) / bestSell.get() * 100;
                if (undercut >= cfg.maxSellOfferUndercut) {
                    return ValidationResult.blocked(String.format(
                        "Undercuts by %s%% (max %s%%)",
                        Utils.formatDecimal(undercut, 1, true),
                        Utils.formatDecimal(cfg.maxSellOfferUndercut, 1, true)
                    ));
                }
            }

            return ValidationResult.allowed();
        }

        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private static ValidationResult validateBuyOrder(
            OutstandingOrderInfo info,
            Optional<Double> bestSell,
            Optional<Double> bestBuy,
            OrderProtectionConfig cfg
        ) {
            if (bestSell.isPresent() && info.pricePerUnit() >= bestSell.get() && cfg.blockUndercutOfOpposing) {
                return ValidationResult.blocked(String.format(
                    "Your Buy Order price of (%s) is \nabove the insta buy price of (%s)",
                    Utils.formatDecimal(info.pricePerUnit(), 1, true),
                    Utils.formatDecimal(bestSell.get(), 1, true)
                ));
            }

            if (bestBuy.isPresent() && cfg.blockUndercutPercentage) {
                double undercut = (info.pricePerUnit() - bestBuy.get()) / bestBuy.get() * 100;

                if (undercut >= cfg.maxBuyOrderUndercut) {
                    return ValidationResult.blocked(String.format(
                        "Undercuts by %s%% (max %s%%)",
                        Utils.formatDecimal(undercut, 1, true),
                        Utils.formatDecimal(cfg.maxBuyOrderUndercut, 1, true)
                    ));
                }
            }

            return ValidationResult.allowed();
        }
    }

    public record ValidationResult(boolean protect, @Nullable String reason) {

        static ValidationResult blocked(String reason) {
            return new ValidationResult(true, reason);
        }

        static ValidationResult allowed() {
            return new ValidationResult(false, null);
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
                .description(OptionDescription.of(Component.literal(
                    "Master switch to enable or disable protection against accidental order mistakes.")))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createShowChatMessageOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Show Chat Messages"))
                .description(OptionDescription.of(Component.literal(
                    "Show system chat messages when protections are triggered.")))
                .binding(true, () -> this.showChatMessage, val -> this.showChatMessage = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createSoundOnBlockedOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Sound - Order Blocked"))
                .description(OptionDescription.of(Component.literal(
                    "Play a sound when an order is blocked by protection.")))
                .binding(true, () -> this.soundOnBlocked, val -> this.soundOnBlocked = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createBlockUndercutPercentageOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Block Percentage Undercut"))
                .description(OptionDescription.of(Component.literal(
                    "Blocks creating orders that undercut existing ones by more than a specified percentage.")))
                .binding(
                    true,
                    () -> this.blockUndercutPercentage,
                    val -> this.blockUndercutPercentage = val
                )
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Double> createMaxBuyOrderUndercutOption() {
            return Option
                .<Double>createBuilder()
                .name(Component.literal("Max Buy Order Undercut (%)"))
                .description(OptionDescription.of(Component.literal(
                    "Maximum allowed undercut percentage for creating Buy Orders.")))
                .binding(
                    this.maxBuyOrderUndercut,
                    () -> this.maxBuyOrderUndercut,
                    val -> this.maxBuyOrderUndercut = val
                )
                .controller(opt -> DoubleSliderControllerBuilder
                    .create(opt)
                    .range(0.0, 100.0)
                    .step(0.5));
        }

        public Option.Builder<Double> createMaxSellOfferUndercutOption() {
            return Option
                .<Double>createBuilder()
                .name(Component.literal("Max Sell Offer Undercut (%)"))
                .description(OptionDescription.of(Component.literal(
                    "Maximum allowed undercut percentage for creating Sell Offers.")))
                .binding(
                    this.maxSellOfferUndercut,
                    () -> this.maxSellOfferUndercut,
                    val -> this.maxSellOfferUndercut = val
                )
                .controller(opt -> DoubleSliderControllerBuilder
                    .create(opt)
                    .range(0.0, 100.0)
                    .step(0.5));
        }

        public Option.Builder<Boolean> createBlockUndercutOfOpposingOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Block Opposing Order Undercuts"))
                .description(OptionDescription.of(Component.literal(
                    "Prevents creating Sell Offers below existing Buy Orders, and Buy Orders above existing Sell Offers")))
                .binding(
                    true,
                    () -> this.blockUndercutOfOpposing,
                    val -> this.blockUndercutOfOpposing = val
                )
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var undercutGroup = new OptionGrouping(this.createBlockUndercutPercentageOption()).addOptions(
                this.createMaxSellOfferUndercutOption(),
                this.createMaxBuyOrderUndercutOption()
            );

            var rootGroup = new OptionGrouping(this.createEnabledOption())
                .addOptions(
                    this.createShowChatMessageOption(),
                    this.createSoundOnBlockedOption(),
                    this.createBlockUndercutOfOpposingOption()
                )
                .addSubgroups(undercutGroup);

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Order Protection"))
                .description(OptionDescription.of(Component.literal(
                    "Settings that guard against creating orders with unsafe or unintended pricing")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}