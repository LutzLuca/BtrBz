package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

@Slf4j
public class BazaarOrderActions {
    public static final int CANCEL_ORDER_SLOT = 11;

    private static boolean shouldReopenBazaar = false;
    private static @Nullable Integer remainingOrderAmount = null;

    @Nullable private static CancelledOrderContext activeBuyOrderContext = null;
    @Nullable private static CancelledOrderContext lastCancelledBuyOrder = null;

    private static boolean hideCancelledOrderButton = false;

    public record CancelledOrderContext(ItemStack displayItem, String productName) {
        public static CancelledOrderContext buildDisplayContext(ItemStack originalItem, String productName) {
            var display = originalItem.copy();
            display.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Reopen: ")
                    .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.YELLOW))
                    .append(
                        Component.literal(productName)
                            .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD))
                    )
            );

            var loreLines = new ArrayList<Component>();
            loreLines.add(Component.empty());
            loreLines.add(
                Component.literal("Click to reopen this product's Bazaar page")
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle(style -> style.withItalic(false))
            );
            display.set(DataComponents.LORE, new ItemLore(loreLines));
            
            return new CancelledOrderContext(display, productName);
        }
    }

    public static void init() {
        ScreenActionManager.register(new ScreenActionManager.ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get().orderActions;
                if (!cfg.enabled) {
                    return false;
                }

                if (GameUtils.isPlayerInventorySlot(slot)) {
                    return false;
                }

                var prev = ScreenInfoHelper.get().getPrevInfo();
                return info.inMenu(BazaarMenuType.OrderOptions) && 
                    prev.inMenu(BazaarMenuType.Orders) && 
                    isCancelOrderSlot(slot);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                if (activeBuyOrderContext != null) {
                    lastCancelledBuyOrder = activeBuyOrderContext;
                    hideCancelledOrderButton = false;
                    log.debug(
                        "Cancelled buy order for productName='{}', setting as last cancelled buy order",
                        lastCancelledBuyOrder.productName()
                    );
                }
                activeBuyOrderContext = null;

                var cfg = ConfigManager.get().orderActions;
                if (cfg.copyRemaining && cfg.copyRemainingModifier.isDown() && remainingOrderAmount != null) {
                    log.debug("Copying remaining order amount '{}' to clipboard", remainingOrderAmount);
                    GameUtils.copyToClipboard(remainingOrderAmount);
                    remainingOrderAmount = null;
                }

                return false;
            }
        });

        ScreenInfoHelper.registerOnClose(
            info -> info.inMenu(
                BazaarMenuType.SellOfferConfirmation,
                BazaarMenuType.BuyOrderConfirmation
            ),
            info -> {
                if (ConfigManager.get().orderActions.reopenBazaar && shouldReopenBazaar) {
                    GameUtils.runCommand("bz");
                }
                shouldReopenBazaar = false;
            }
        );

        ScreenInfoHelper.registerOnClose(
            info -> info.inMenu(BazaarMenuType.OrderOptions),
            ignored -> {
                remainingOrderAmount = null;
                activeBuyOrderContext = null;
            }
        );

        ItemOverrideManager.register((info, slot, original) -> {
            var cfg = ConfigManager.get().orderActions;
            if (!cfg.enabled || !cfg.reopenLastBuyOrderEnabled || (cfg.clearOnClose && hideCancelledOrderButton) ||
                lastCancelledBuyOrder == null) {
                return Optional.empty();
            }

            if (GameUtils.isPlayerInventorySlot(slot) || !info.inMenu(BazaarMenuType.Orders)) {
                return Optional.empty();
            }

            var targetSlotIdx = info.getGenericContainerScreen()
                .map(gcs -> gcs.getMenu().getContainer().getContainerSize() - 6)
                .orElse(-1);

            if (slot.getContainerSlot() != targetSlotIdx) {
                return Optional.empty();
            }

            return Optional.of(lastCancelledBuyOrder.displayItem().copy());
        });

        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get().orderActions;
                if (!cfg.enabled || !cfg.reopenLastBuyOrderEnabled || (cfg.clearOnClose && hideCancelledOrderButton) ||
                    lastCancelledBuyOrder == null) {
                    return false;
                }

                if (GameUtils.isPlayerInventorySlot(slot) || !info.inMenu(BazaarMenuType.Orders)) {
                    return false;
                }

                var targetSlotIdx = info.getGenericContainerScreen()
                    .map(gcs -> gcs.getMenu().getContainer().getContainerSize() - 6)
                    .orElse(-1);

                return slot.getContainerSlot() == targetSlotIdx;
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                log.debug("Reopening bazaar page for product '{}'", lastCancelledBuyOrder.productName());
                GameUtils.runCommand("bz " + lastCancelledBuyOrder.productName());
                return true;
            }
        });

        ScreenInfoHelper.registerOnClose(
            info -> info.inMenu(BazaarMenuType.Orders),
            info -> hideCancelledOrderButton = true
        );

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            var cfg = ConfigManager.get().orderActions;
            if (!cfg.enabled || !cfg.copyRemaining || remainingOrderAmount == null) {
                return;
            }

            var screenInfo = ScreenInfoHelper.get().getCurrInfo();
            if (!screenInfo.inMenu(BazaarMenuType.OrderOptions)) {
                return;
            }

            var isCancelOrderSlot = screenInfo.getGenericContainerScreen()
                .map(screen -> screen instanceof AbstractContainerScreenAccessor accessor ? accessor.getHoveredSlot() : null)
                .filter(BazaarOrderActions::isCancelOrderSlot)
                .isPresent();

            if (!isCancelOrderSlot) {
                return;
            }

            lines.add(Component.empty());
            lines.add(Component.literal("[BtrBz]").withStyle(ChatFormatting.AQUA));
            var modifier = cfg.copyRemainingModifier;
            var keyName = switch (modifier) {
                case Ctrl -> "Ctrl";
                case Alt -> "Alt";
                case None -> null;
            };

            var hint = keyName != null
                ? String.format("Hold %s to copy the remaining amount.", keyName)
                : "Copies the remaining amount.";

            lines.add(Component.literal(hint).withStyle(ChatFormatting.GRAY));
        });
    }

    public static void onOrderClick(OrderInfo info, ItemStack slotItem) {
        if (info.type() != OrderType.Buy) {
            log.debug("Order is not a buy order, clearing `activeBuyOrderContext` and `remainingOrderAmount`");
            activeBuyOrderContext = null;
            remainingOrderAmount = null;
            return;
        }

        if (info.unclaimed() != 0) {
            // NOTE: This should never happen, if you have a partially filled order, clicking it will claim
            // the filled items first instead of opening the order options.
            log.warn("Order has unclaimed items, resetting state");
            activeBuyOrderContext = null;
            remainingOrderAmount = null;
            return;
        }

        activeBuyOrderContext = CancelledOrderContext.buildDisplayContext(slotItem, info.productName());
        log.debug(
            "Set active order context for transition: productName='{}'",
            activeBuyOrderContext.productName()
        );

        remainingOrderAmount = info.volume() - info.filledAmountSnapshot();
        log.debug(
            "Setting remainingOrderAmount to {} from order info {}",
            remainingOrderAmount,
            info
        );
    }

    public static void setReopenBazaar() {
        shouldReopenBazaar = true;
    }

    private static boolean isCancelOrderSlot(@Nullable Slot slot) {
        return slot != null && slot.getContainerSlot() == CANCEL_ORDER_SLOT && slot
            .getItem()
            .getHoverName()
            .getString()
            .equals("Cancel Order");
    }

    public static class OrderActionsConfig {

        public boolean enabled = true;
        public boolean copyRemaining = true;
        public Modifier copyRemainingModifier = Modifier.Ctrl;
        public boolean reopenBazaar = false;
        public boolean reopenLastBuyOrderEnabled = true;
        public boolean clearOnClose = true;

        public enum Modifier {
            None,
            Ctrl,
            Alt;

            public static EnumControllerBuilder<Modifier> controller(Option<Modifier> option) {
                return EnumControllerBuilder
                    .create(option)
                    .enumClass(Modifier.class)
                    .formatValue(modifier -> switch (modifier) {
                        case None -> Component.literal("None");
                        case Ctrl -> Component.literal("Ctrl");
                        case Alt -> Component.literal("Alt");
                    });
            }

            public boolean isDown() {
                var mc = Minecraft.getInstance();
                return switch (this) {
                    case None -> true;
                    case Ctrl -> mc.hasControlDown();
                    case Alt -> mc.hasAltDown();
                };
            }
        }

        public Option.Builder<Boolean> createReopenBazaarOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Return to Bazaar After Order Setup"))
                .binding(false, () -> this.reopenBazaar, val -> this.reopenBazaar = val)
                .description(OptionDescription.of(GameUtils.join(
                    List.of(
                        Component.literal(
                            "Automatically reopens the main Bazaar menu after placing a buy/sell order."),
                        Component.literal(
                            "\nNote: This executes '/bz' which requires a server round-trip."),
                        Component.literal(
                            "You may experience brief mouse unlock during the transition, which may feel a bit clunky.")
                    ), " "
                )))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createCopyRemainingOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Copy Remaining "))
                .binding(true, () -> this.copyRemaining, enabled -> this.copyRemaining = enabled)
                .description(OptionDescription.of(Component.literal(
                    "Automatically copies the remaining amount of items from a cancelled order")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Modifier> createCopyRemainingModifierOption() {
            return Option
                .<Modifier>createBuilder()
                .name(Component.literal("Copy Remaining Modifier"))
                .binding(
                    Modifier.Ctrl,
                    () -> this.copyRemainingModifier != null ? this.copyRemainingModifier : Modifier.Ctrl,
                    val -> this.copyRemainingModifier = val
                )
                .description(OptionDescription.of(Component.literal(
                    "The modifier key that must be held down to copy the remaining amount")))
                .controller(Modifier::controller);
        }

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Order Actions Toggle"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Component.literal(
                    "Master switch for actions on order cancel")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createReopenLastBuyOrderEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Reopen Last Cancelled Buy Order"))
                .binding(true, () -> this.reopenLastBuyOrderEnabled, val -> this.reopenLastBuyOrderEnabled = val)
                .description(OptionDescription.of(Component.literal(
                    "Show a button in the Manage Orders screen to quickly reopen the Bazaar page "
                    + "of the last cancelled buy order.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createClearOnCloseOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Hide Button After Closing Orders"))
                .binding(true, () -> this.clearOnClose, val -> this.clearOnClose = val)
                .description(OptionDescription.of(Component.literal(
                    "When enabled, the reopen button is hidden after you close the Manage Orders screen. "
                    + "It reappears automatically the next time you cancel a buy order.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var copyGroup = new OptionGrouping(this.createCopyRemainingOption())
                .addOptions(this.createCopyRemainingModifierOption());

            var reopenGroup = new OptionGrouping(this.createReopenLastBuyOrderEnabledOption())
                .addOptions(this.createClearOnCloseOption());

            var rootGroup = new OptionGrouping(this.createEnabledOption())
                .addOptions(this.createReopenBazaarOption())
                .addSubgroups(copyGroup, reopenGroup);

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Order Cancel Actions"))
                .description(OptionDescription.of(Component.literal(
                    "Settings for actions after canceling an order")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}
