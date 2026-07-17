package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.SoundUtil;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotClickResult;
import com.github.lutzluca.btrbz.utils.slot.SlotHook;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
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
    private static final BazaarMenuType[] CONFIRMATION_MENUS = {
        BazaarMenuType.BuyOrderConfirmation,
        BazaarMenuType.SellOfferConfirmation
    };

    private final BazaarData bazaarData;
    private final WeakHashMap<ItemStack, PendingOrderData> validationCache = new WeakHashMap<>();
    private final WeakHashMap<ItemStack, ValidationResult> validationFailureCache = new WeakHashMap<>();

    private @Nullable BiConsumer<ItemStack, Optional<PendingOrderData>> setOrderCallback = null;

    public OrderProtectionManager(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        SlotHookRegistry.register(new ConfirmationHook());

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!ConfigManager.get().orderProtection.enabled) {
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

    public void onSetOrder(BiConsumer<ItemStack, Optional<PendingOrderData>> cb) {
        this.setOrderCallback = cb;
    }

    private Optional<ValidationResult> getValidationResult(ItemStack stack) {
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
                ConfigManager.get().orderProtection
            ))
            .onSuccess(pendingOrder -> {
                this.validationCache.put(rawStack, pendingOrder);
                this.validationFailureCache.remove(rawStack);

                log.trace(
                    "Validated: {} - {}",
                    pendingOrder.orderInfo().product().bazaarProductId().orElse(pendingOrder.orderInfo().productName()),
                    pendingOrder.validationResult().protect() ? "BLOCKED" : "ALLOWED"
                );
            })
            .onFailure(err -> {
                this.validationCache.remove(rawStack);
                this.validationFailureCache.put(
                    rawStack,
                    ValidationResult.blocked(VALIDATION_FAILURE_REASON)
                );
                log.warn(
                    "Failed to parse or validate confirmation item '{}'",
                    rawStack.getHoverName().getString(),
                    err
                );
            });
    }

    public Optional<Pair<ValidationResult, Boolean>> getVisualOrderInfo(ItemStack stack) {
        if (!ConfigManager.get().orderProtection.enabled) {
            return Optional.empty();
        }

        return this.getValidationResult(stack).map(data -> Pair.of(data, false));
    }

    public final class ConfirmationHook implements SlotHook {

        private ConfirmationHook() { }

        @Override
        public boolean matches(SlotView view) {
            return !view.playerInventorySlot()
                && view.slotIdx() == CONFIRMATION_SLOT_INDEX
                && view.getCurrInfo().inMenu(CONFIRMATION_MENUS);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            if (ConfigManager.get().orderProtection.enabled) {
                OrderProtectionManager.this.validateConfirmationStack(ctx.view().getRawStack());
            }

            return ctx.view().getRawStack();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var stack = ctx.view().getRawStack();
            var cfg = ConfigManager.get().orderProtection;
            var pending = OrderProtectionManager.this.validationCache.get(stack);
            var validation = OrderProtectionManager.this.getValidationResult(stack)
                .orElseGet(() -> {
                    log.warn("No cached validation for confirmation item");
                    return ValidationResult.blocked(VALIDATION_UNAVAILABLE_REASON);
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
    ) { }

    private static final class OrderValidator {

        public static PendingOrderData validate(OutstandingOrderInfo info, BazaarData bazaarData, OrderProtectionConfig cfg) {
            if (!cfg.enabled || (!cfg.blockUndercutPercentage && !cfg.blockUndercutOfOpposing)) {
                return new PendingOrderData(info, ValidationResult.allowed());
            }

            var product = bazaarData.resolveIndexedProduct(info.product());
            if (product.isEmpty()) {
                log.warn("Order protection blocked unresolved product '{}'", info.productName());
                return new PendingOrderData(
                    info,
                    ValidationResult.blocked("Unknown or unresolved product: " + info.productName())
                );
            }

            var prices = bazaarData.getMarketPrices(ProductIdentity.fromIndex(product.get()));
            var validationResult = validateOrder(info, prices, cfg);
            return new PendingOrderData(info, validationResult);
        }

        private static ValidationResult validateOrder(
            OutstandingOrderInfo info,
            MarketPrices prices,
            OrderProtectionConfig cfg
        ) {
            var bestSell = prices.lowestSellOfferPrice();
            var bestBuy = prices.highestBuyOrderPrice();

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
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Check new order prices and stop submissions that match an enabled safety rule."),
                    ConfigScreen.note("Hold Ctrl while confirming to override a block.")
                )))
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
                        "Block prices that improve on the current best order by the allowed percentage or more. The buy-order and sell-offer limits below apply only while this is enabled."),
                    ConfigScreen.example(
                        "At a best price of 15M, a 100K change is about 0.67%. With a 15% limit, the blocked difference begins at 2.25M."),
                    ConfigScreen.note(
                        "Known limitation: fixed 0.1-coin price steps make percentage checks unreliable for very cheap items. Moving from 0.5 to 0.6 coins is already a 20% increase.")
                )))
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
                .name(Component.literal("Maximum Buy-Order Increase (%)"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Block a buy order when it is this percentage or more above the best current buy-order price."),
                    ConfigScreen.example(
                        "With a best price of 15M and a 5% limit, 15.75M or more is blocked.")
                )))
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
                .name(Component.literal("Maximum Sell-Offer Reduction (%)"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Block a sell offer when it is this percentage or more below the best current sell-offer price."),
                    ConfigScreen.example(
                        "With a best price of 15M and a 5% limit, 14.25M or less is blocked.")
                )))
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
                .name(Component.literal("Block Orders at Instant-Trade Prices"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Block buy orders priced at or above the best sell offer, and sell offers priced at or below the best buy order. Use an instant trade instead."),
                    ConfigScreen.example(
                        "If the best buy order is 100 and the best sell offer is 105, a sell offer of 100 or less and a buy order of 105 or more are blocked.")
                )))
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
                .description(ConfigScreen.createDescription(
                    "Prevent accidental orders at unusually aggressive prices before they are submitted to the Bazaar.",
                    ConfigScreen.ConfigImage.ORDER_PROTECTION
                ))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }
}
