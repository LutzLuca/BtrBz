package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData.OrderPriceInfo;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class OrderProtectionManager {


    private static OrderProtectionManager instance;
    private final WeakHashMap<ItemStack, PendingOrderData> validationCache = new WeakHashMap<>();

    private @Nullable BiConsumer<ItemStack, Optional<PendingOrderData>> setOrderCallback = null;

    private OrderProtectionManager() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (!ConfigManager.get().orderProtection.enabled) {
                return Optional.empty();
            }
            if (!info.inMenu(
                BazaarMenuType.BuyOrderConfirmation,
                BazaarMenuType.SellOfferConfirmation
            )) {
                return Optional.empty();
            }

            if (slot == null || slot.getIndex() != 13 || original == ItemStack.EMPTY) {
                return Optional.empty();
            }

            if (validationCache.containsKey(original)) {
                return Optional.of(original);
            }

            OrderInfoParser
                .parseSetOrderItem(original)
                .map(OrderValidator::validate)
                .onSuccess(pendingOrder -> {
                    validationCache.put(original, pendingOrder);

                    log.trace(
                        "Validated: {} - {}",
                        pendingOrder.orderInfo().productName(),
                        pendingOrder.validationResult().protect() ? "BLOCKED" : "ALLOWED"
                    );
                });

            return Optional.of(original);
        });

        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                return info.inMenu(
                    BazaarMenuType.BuyOrderConfirmation,
                    BazaarMenuType.SellOfferConfirmation
                ) && slot != null && slot.getIndex() == 13;
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var stack = slot.getStack();
                var cfg = ConfigManager.get().orderProtection;
                var pending = validationCache.get(stack);

                if (pending == null) {
                    log.warn("No cached validation for confirmation item");
                    OrderProtectionManager.this.dispatchSetOrder(stack, Optional.empty());
                    return false;
                }

                if (!cfg.enabled) {
                    OrderProtectionManager.this.dispatchSetOrder(stack, Optional.of(pending));
                    return false;
                }

                var validation = pending.validationResult();
                boolean isBlocked = validation.protect();
                boolean overrideActive = Screen.hasControlDown();

                if (isBlocked && !overrideActive) {
                    if (cfg.showChatMessage) {
                        sendBlockedOrderMessage(validation);
                    }
                    return true;
                }

                OrderProtectionManager.this.dispatchSetOrder(stack, Optional.of(pending));
                return false;
            }
        });

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!ConfigManager.get().orderProtection.enabled) {
                return;
            }

            var pending = validationCache.get(stack);
            if (pending == null) { return; }

            var validation = pending.validationResult();
            boolean blocked = validation.protect();
            boolean ctrlHeld = Screen.hasControlDown();

            lines.add(Text.empty());

            if (!blocked) {
                lines.add(Text
                    .literal("✓ ")
                    .formatted(Formatting.GREEN)
                    .append(Text.literal("Order Protection: ").formatted(Formatting.GRAY))
                    .append(Text.literal("Safe").formatted(Formatting.GREEN)));
                return;
            }

            if (ctrlHeld) {
                lines.add(Text
                    .literal("⚠ ")
                    .formatted(Formatting.GOLD)
                    .append(Text.literal("Order Protection: ").formatted(Formatting.GRAY))
                    .append(Text.literal("Overridden").formatted(Formatting.GOLD)));

                if (validation.reason() != null) {
                    lines.add(Text.literal("Reason:").formatted(Formatting.GRAY));
                    Arrays
                        .stream(validation.reason().split("\n"))
                        .map(line -> Text.literal("  " + line).formatted(Formatting.YELLOW))
                        .forEach(lines::add);
                }

                lines.add(Text
                    .literal("Release Ctrl to cancel override")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
                return;
            }

            lines.add(Text
                .literal("✗ ")
                .formatted(Formatting.RED)
                .append(Text.literal("Order Protection: ").formatted(Formatting.GRAY))
                .append(Text.literal("Blocked").formatted(Formatting.RED)));

            if (validation.reason() != null) {
                lines.add(Text.literal("Reason:").formatted(Formatting.GRAY));
                lines.add(Text.literal("  " + validation.reason()).formatted(Formatting.YELLOW));
            }

            lines.add(Text.literal("Hold Ctrl to override").formatted(Formatting.DARK_GRAY));
        });
    }

    private static void sendBlockedOrderMessage(
        ValidationResult validation
    ) {
        var reason = validation.reason() == null ? "Order blocked."
            : "Order blocked: " + validation.reason();

        var msg = Text
            .literal(reason)
            .formatted(Formatting.RED)
            .append(Text.literal(" Hold Ctrl to override.").formatted(Formatting.GRAY));

        Notifier.notifyPlayer(msg);
    }

    public static OrderProtectionManager getInstance() {
        if (instance == null) {
            instance = new OrderProtectionManager();
        }
        return instance;
    }

    private void dispatchSetOrder(ItemStack stack, Optional<PendingOrderData> data) {
        if (this.setOrderCallback != null) {
            this.setOrderCallback.accept(stack, data);
        }
    }

    public void onSetOrder(BiConsumer<ItemStack, Optional<PendingOrderData>> cb) {
        this.setOrderCallback = cb;
    }


    public Optional<Pair<PendingOrderData, Boolean>> getVisualOrderInfo(ItemStack stack) {
        if (!ConfigManager.get().orderProtection.enabled) {
            return Optional.empty();
        }

        return Optional
            .ofNullable(validationCache.get(stack))
            .map(data -> Pair.of(data, Screen.hasControlDown()));
    }

    public record PendingOrderData(
        OutstandingOrderInfo orderInfo, ValidationResult validationResult
    ) { }

    private static final class OrderValidator {

        public static PendingOrderData validate(OutstandingOrderInfo info) {
            var cfg = ConfigManager.get().orderProtection;

            if (!cfg.enabled || !cfg.blockUndercutPercentage) {
                return new PendingOrderData(info, ValidationResult.allowed());
            }

            var data = BtrBz.bazaarData();
            var productId = data.nameToId(info.productName());
            if (productId.isEmpty()) {
                log.trace("No product ID found for {}, allowing order", info.productName());
                return new PendingOrderData(info, ValidationResult.allowed());
            }

            var prices = data.getOrderPrices(productId.get());
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

        public boolean blockUndercutPercentage = true;
        public double maxBuyOrderUndercut = 15.0;
        public double maxSellOfferUndercut = 15.0;

        public boolean blockUndercutOfOpposing = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Enable Order Protection"))
                .description(OptionDescription.of(Text.literal(
                    "Master switch to enable or disable protection against accidental order mistakes.")))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createShowChatMessageOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Show Chat Messages"))
                .description(OptionDescription.of(Text.literal(
                    "Show system chat messages when protections are triggered.")))
                .binding(true, () -> this.showChatMessage, val -> this.showChatMessage = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createBlockUndercutPercentageOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Block Percentage Undercut"))
                .description(OptionDescription.of(Text.literal(
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
                .name(Text.literal("Max Buy Order Undercut (%)"))
                .description(OptionDescription.of(Text.literal(
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
                .name(Text.literal("Max Sell Offer Undercut (%)"))
                .description(OptionDescription.of(Text.literal(
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
                .name(Text.literal("Block Opposing Order Undercuts"))
                .description(OptionDescription.of(Text.literal(
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
                    this.createBlockUndercutOfOpposingOption()
                )
                .addSubgroups(undercutGroup);

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order Protection"))
                .description(OptionDescription.of(Text.literal(
                    "Settings that guard against creating orders with unsafe or unintended pricing")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}