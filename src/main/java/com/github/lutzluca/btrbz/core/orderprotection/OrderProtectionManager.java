package com.github.lutzluca.btrbz.core.orderprotection;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.core.orderprotection.OrderProtectionRule.Result;
import com.github.lutzluca.btrbz.core.orderprotection.OrderProtectionRule.Settings;
import com.github.lutzluca.btrbz.data.BazaarData;
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
        this.bazaarData.addListener(_ -> {
            this.validationCache.clear();
            this.validationFailureCache.clear();
        });
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
                Arrays
                    .stream(validation.reason().split("\n"))
                    .map(line -> Component.literal("  " + line).withStyle(ChatFormatting.YELLOW))
                    .forEach(lines::add);
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

    private void validateConfirmationStack(ItemStack rawStack, boolean force) {
        if (rawStack.isEmpty() || GameUtils.getLore(rawStack).isEmpty()) {
            return;
        }

        if (!force
            && (this.validationCache.containsKey(rawStack) || this.validationFailureCache.containsKey(rawStack))) {
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
                    pendingOrder.orderInfo().product().bazaarProductId().orElse(pendingOrder.orderInfo().productName()),
                    pendingOrder.validationResult().protect() ? "BLOCKED" : "ALLOWED");
            })
            .onFailure(err -> {
                this.validationCache.remove(rawStack);
                this.validationFailureCache.put(
                    rawStack,
                    ValidationResult.blocked(VALIDATION_FAILURE_REASON));
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
                OrderProtectionManager.this.validateConfirmationStack(ctx.view().getRawStack(), false);
            }

            return ctx.view().getRawStack();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var stack = ctx.view().getRawStack();
            var cfg = ConfigStore.get().config().orderProtection;

            // Confirmation screens can remain open while the market moves. Always make the final
            // click decision from the latest snapshot instead of trusting the render-time cache.
            if (cfg.enabled) {
                OrderProtectionManager.this.validateConfirmationStack(stack, true);
            }

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
    ) {}

    static final class OrderValidator {

        public static PendingOrderData validate(
            OutstandingOrderInfo info,
            BazaarData bazaarData,
            OrderProtectionConfig cfg
        ) {
            if (!cfg.enabled || (!cfg.blockUndercutPercentage && !cfg.blockUndercutOfOpposing)) {
                return new PendingOrderData(info, ValidationResult.allowed());
            }

            if (!bazaarData.hasMarketData()) {
                return new PendingOrderData(info, ValidationResult.blocked("Bazaar prices are unavailable."));
            }

            var product = bazaarData.resolveIndexedProduct(info.product());
            if (product.isEmpty()) {
                log.warn("Order protection blocked unresolved product '{}'", info.productName());
                return new PendingOrderData(
                    info,
                    ValidationResult.blocked("Unknown or unresolved product: " + info.productName()));
            }

            var prices = bazaarData.getMarketPrices(ProductIdentity.fromIndex(product.get()));
            var result = OrderProtectionRule.evaluate(
                info.type(),
                info.pricePerUnit(),
                prices.highestBuyOrderPrice(),
                prices.lowestSellOfferPrice(),
                Settings.from(cfg));
            var validationResult = result.blocked()
                ? ValidationResult.blocked(formatReason(info.type(), result, cfg))
                : ValidationResult.allowed();
            return new PendingOrderData(info, validationResult);
        }

        private static String formatReason(OrderType type, Result result, OrderProtectionConfig cfg) {
            var price = Utils.formatDecimal(result.proposedPrice(), 1, true);
            var reference = Utils.formatDecimal(result.referencePrice(), 1, true);
            var delta = Utils.formatDecimal(result.delta(), 1, true);
            var percentage = Utils.formatDecimal(result.percentage(), 1, true);

            return switch (result.violation().orElseThrow()) {
                case InvalidPrice -> "Invalid order price: " + price;
                case OpposingPrice -> switch (type) {
                    case Buy -> String.format(
                        "Buy Order at %s reaches the best Sell Offer (%s) and would fill instantly",
                        price,
                        reference);
                    case Sell -> String.format(
                        "Sell Offer at %s reaches the best Buy Order (%s) and would fill instantly",
                        price,
                        reference);
                };
                case Percentage -> String.format(
                    "Improves the best %s price by %s coins / %s%% (max %s%%)",
                    type == OrderType.Buy ? "Buy Order" : "Sell Offer",
                    delta,
                    percentage,
                    Utils.formatDecimal(
                        type == OrderType.Buy ? cfg.maxBuyOrderUndercut : cfg.maxSellOfferUndercut,
                        1,
                        true));
            };
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
}
