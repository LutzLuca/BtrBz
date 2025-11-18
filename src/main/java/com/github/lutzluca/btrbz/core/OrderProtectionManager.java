package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.data.BazaarData.OrderPriceInfo;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener.Event;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import java.util.List;
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
import org.jetbrains.annotations.Nullable;

@Slf4j
public class OrderProtectionManager {


    private static OrderProtectionManager instance;
    private final WeakHashMap<ItemStack, PendingOrderData> validationCache = new WeakHashMap<>();

    private @Nullable BiConsumer<ItemStack, Optional<PendingOrderData>> setOrderCallback = null;

    private OrderProtectionManager() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (!info.inMenu(
                BazaarMenuType.BuyOrderConfirmation,
                BazaarMenuType.SellOfferConfirmation
            )) {
                return Optional.empty();
            }
            if (slot == null || slot.getIndex() != 13 || slot.getStack().isEmpty()) {
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
                var pendingOrder = validationCache.get(slot.getStack());

                if (pendingOrder == null) {
                    log.warn("No cached validation for confirmation item");

                    if (OrderProtectionManager.this.setOrderCallback != null) {
                        OrderProtectionManager.this.setOrderCallback.accept(
                            slot.getStack(),
                            Optional.empty()
                        );
                    }
                    return false;
                }

                var validation = pendingOrder.validationResult();
                boolean strgHeld = Screen.hasControlDown();

                if (validation.protect() && !strgHeld) {
                    if (ConfigManager.get().orderProtection.showChatMessage) {
                        var builder = Text.literal("Order blocked: ").formatted(Formatting.RED);
                        if (validation.reason() != null) {
                            builder.append(Text
                                .literal(validation.reason())
                                .formatted(Formatting.YELLOW));
                        } else {
                            log.warn("Order blocked without a reason in validation result");
                        }

                        builder.append(Text
                            .literal("\nHold STRG to override")
                            .formatted(Formatting.GRAY));
                        Notifier.notifyPlayer(builder);
                    }

                    return true;
                }

                if (OrderProtectionManager.this.setOrderCallback != null) {
                    OrderProtectionManager.this.setOrderCallback.accept(
                        slot.getStack(),
                        Optional.of(pendingOrder)
                    );
                }
                return false;
            }
        });

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            var pendingOrder = validationCache.get(stack);
            if (pendingOrder == null) {
                return;
            }

            var validation = pendingOrder.validationResult();

            lines.add(Text.empty());

            if (!validation.protect()) {
                lines.add(Text.literal("✓ Order validated").formatted(Formatting.GREEN));
            } else {
                lines.add(Text.literal("✗ Order blocked").formatted(Formatting.RED));
                lines.add(Text.literal("  " + validation.reason()).formatted(Formatting.GRAY));
                lines.add(Text
                    .literal("Hold STRG to override")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
            }
        });
    }

    public static OrderProtectionManager getInstance() {
        if (instance == null) {
            instance = new OrderProtectionManager();
        }
        return instance;
    }

    public void onSetOrder(BiConsumer<ItemStack, Optional<PendingOrderData>> cb) {
        this.setOrderCallback = cb;
    }

    public @Nullable PendingOrderData getValidationState(ItemStack stack) {
        return this.validationCache.get(stack);
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

        private static ValidationResult validateSellOffer(
            OutstandingOrderInfo info,
            Optional<Double> bestSell,
            Optional<Double> bestBuy,
            OrderProtectionConfig cfg
        ) {
            if (bestBuy.isPresent() && info.pricePerUnit() <= bestBuy.get() && cfg.blockUndercutOfOpposing) {
                return ValidationResult.blocked(String.format(
                    "Your sell offer at %.1f undercuts the current buy order of %.1f (use insta-sell)",
                    info.pricePerUnit(),
                    bestBuy.get()
                ));
            }

            if (bestSell.isPresent() && cfg.blockUndercutPercentage) {
                double undercut = (bestSell.get() - info.pricePerUnit()) / bestSell.get() * 100;

                if (undercut >= cfg.maxSellOfferUndercut) {
                    return ValidationResult.blocked(String.format(
                        "Undercuts by %.1f%% (max %.1f%%)",
                        undercut,
                        cfg.maxSellOfferUndercut
                    ));
                }
            }

            return ValidationResult.allowed();
        }

        private static ValidationResult validateBuyOrder(
            OutstandingOrderInfo info,
            Optional<Double> bestSell,
            Optional<Double> bestBuy,
            OrderProtectionConfig cfg
        ) {
            if (bestSell.isPresent() && info.pricePerUnit() >= bestSell.get() && cfg.blockUndercutOfOpposing) {
                return ValidationResult.blocked(String.format(
                    "Your buy order at %.1f meets/exceeds the sell offer of %.1f (use insta-buy)",
                    info.pricePerUnit(),
                    bestSell.get()
                ));
            }

            if (bestBuy.isPresent() && cfg.blockUndercutPercentage) {
                double undercut = (info.pricePerUnit() - bestBuy.get()) / bestBuy.get() * 100;
                log.debug("Undercut {} for {}", undercut, info.productName());

                if (undercut >= cfg.maxBuyOrderUndercut) {
                    return ValidationResult.blocked(String.format(
                        "Undercuts by %.1f%% (max %.1f%%)",
                        undercut,
                        cfg.maxBuyOrderUndercut
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

        public Option<Boolean> createShowChatMessageOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Show Chat Messages"))
                .description(OptionDescription.of(Text.literal(
                    "Show system chat messages when protections are triggered.")))
                .binding(true, () -> this.showChatMessage, val -> this.showChatMessage = val)
                .controller(ConfigScreen::createBooleanController)
                .build();
        }

        public Option<Boolean> createBlockUndercutPercentageOption() {
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
                .controller(ConfigScreen::createBooleanController)
                .build();
        }

        public Option<Double> createMaxBuyOrderUndercutOption() {
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
                    .step(0.5))
                .build();
        }

        public Option<Double> createMaxSellOfferUndercutOption() {
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
                    .step(0.5))
                .build();
        }

        public Option<Boolean> createBlockUndercutOfOpposingOption() {
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
                .controller(ConfigScreen::createBooleanController)
                .build();
        }

        public OptionGroup createGroup() {
            var enabledBuilder = this.createEnabledOption();

            var options = List.of(
                this.createShowChatMessageOption(),
                this.createBlockUndercutPercentageOption(),
                this.createMaxBuyOrderUndercutOption(),
                this.createMaxSellOfferUndercutOption(),
                this.createBlockUndercutOfOpposingOption()
            );

            enabledBuilder.addListener((option, event) -> {
                if (event == Event.STATE_CHANGE) {
                    boolean val = option.pendingValue();
                    options.forEach(opt -> opt.setAvailable(val));
                }
            });

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order Protection"))
                .description(OptionDescription.of(Text.literal(
                    "Settings that guard against creating orders with unsafe or unintended pricing")))
                .option(enabledBuilder.build())
                .options(options)
                .collapsed(false)
                .build();
        }
    }

}