package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotClickResult;
import com.github.lutzluca.btrbz.utils.slot.SlotHook;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

@Slf4j
public class BazaarOrderActions {
    public static final int CANCEL_ORDER_SLOT = 11;

    private final BazaarData bazaarData;

    private boolean shouldReopenBazaar = false;
    private @Nullable Integer remainingOrderAmount = null;

    @Nullable private CancelledOrderContext activeBuyOrderContext = null;
    @Nullable private CancelledOrderContext lastCancelledBuyOrder = null;

    private boolean hideCancelledOrderButton = false;

    public BazaarOrderActions(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.registerCloseHandlers();
        this.registerSlotHooks();
        this.registerTooltipCallback();
    }

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

    private void registerSlotHooks() {
        SlotHookRegistry.register(new CancelOrderHook());
        SlotHookRegistry.register(new ReopenOrderHook());
        SlotHookRegistry.register(new OrdersObserverHook());
    }

    private void registerCloseHandlers() {
        ScreenInfoHelper.registerOnClose(
            info -> info.inMenu(
                BazaarMenuType.SellOfferConfirmation,
                BazaarMenuType.BuyOrderConfirmation
            ),
            info -> {
                if (ConfigManager.get().orderActions.reopenBazaar && BazaarOrderActions.this.shouldReopenBazaar) {
                    GameUtils.runCommand("bz");
                }
                BazaarOrderActions.this.shouldReopenBazaar = false;
            }
        );

        ScreenInfoHelper.registerOnClose(
            info -> info.inMenu(BazaarMenuType.OrderOptions),
            _ -> {
                BazaarOrderActions.this.remainingOrderAmount = null;
                BazaarOrderActions.this.activeBuyOrderContext = null;
            }
        );
    }

    private void registerReopenCloseHandler() {
        ScreenInfoHelper.registerOnClose(
            info -> info.inMenu(BazaarMenuType.Orders),
            info -> BazaarOrderActions.this.hideCancelledOrderButton = true
        );
    }

    private void registerTooltipCallback() {
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            var cfg = ConfigManager.get().orderActions;
            if (!cfg.enabled || !cfg.copyRemaining || BazaarOrderActions.this.remainingOrderAmount == null) {
                return;
            }

            var screenInfo = ScreenInfoHelper.get().getCurrInfo();
            if (!screenInfo.inMenu(BazaarMenuType.OrderOptions)) {
                return;
            }

            var isCancelOrderSlot = screenInfo.getGenericContainerScreen()
                .map(screen -> screen instanceof AbstractContainerScreenAccessor accessor ? accessor.getHoveredSlot() : null)
                .filter(BazaarOrderActions.this::isCancelOrderSlot)
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

    public void onOrderClick(OrderInfo info, ItemStack slotItem) {
        if (info.type() != OrderType.Buy) {
            log.debug("Order is not a buy order, clearing `activeBuyOrderContext` and `remainingOrderAmount`");
            this.activeBuyOrderContext = null;
            this.remainingOrderAmount = null;
            return;
        }

        if (info.unclaimed() != 0) {
            log.warn("Order has unclaimed items, resetting state");
            this.activeBuyOrderContext = null;
            this.remainingOrderAmount = null;
            return;
        }

        this.activeBuyOrderContext = CancelledOrderContext.buildDisplayContext(slotItem, info.productName());
        log.debug(
            "Set active order context for transition: productName='{}'",
            this.activeBuyOrderContext.productName()
        );

        this.remainingOrderAmount = info.volume() - info.filledAmountSnapshot();
        log.debug(
            "Setting remainingOrderAmount to {} from order info {}",
            this.remainingOrderAmount,
            info
        );
    }

    public void setReopenBazaar() {
        this.shouldReopenBazaar = true;
    }

    private int getReopenTargetSlotIdx(SlotView slot) {
        return slot.getCurrInfo()
            .getGenericContainerScreen()
            .map(gcs -> gcs.getMenu().getContainer().getContainerSize() - 6)
            .orElse(-1);
    }

    private boolean isCancelOrderSlot(@Nullable net.minecraft.world.inventory.Slot slot) {
        return slot != null && slot.getContainerSlot() == CANCEL_ORDER_SLOT && slot
            .getItem()
            .getHoverName()
            .getString()
            .equals("Cancel Order");
    }

    public final class CancelOrderHook implements SlotHook {

        private CancelOrderHook() { }

        @Override
        public boolean matches(SlotView view) {
            var cfg = ConfigManager.get().orderActions;
            return cfg.enabled
                && !view.playerInventorySlot()
                && view.getCurrInfo().inMenu(BazaarMenuType.OrderOptions)
                && view.getPrevInfo().inMenu(BazaarMenuType.Orders)
                && BazaarOrderActions.this.isCancelOrderSlot(view.getSlot());
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            if (BazaarOrderActions.this.activeBuyOrderContext != null) {
                BazaarOrderActions.this.lastCancelledBuyOrder = BazaarOrderActions.this.activeBuyOrderContext;
                BazaarOrderActions.this.hideCancelledOrderButton = false;
                log.debug(
                    "Cancelled buy order for productName='{}', setting as last cancelled buy order",
                    BazaarOrderActions.this.lastCancelledBuyOrder.productName()
                );
            }
            BazaarOrderActions.this.activeBuyOrderContext = null;

            var cfg = ConfigManager.get().orderActions;
            if (cfg.copyRemaining && cfg.copyRemainingModifier.isDown() && BazaarOrderActions.this.remainingOrderAmount != null) {
                log.debug("Copying remaining order amount '{}' to clipboard", BazaarOrderActions.this.remainingOrderAmount);
                GameUtils.copyToClipboard(BazaarOrderActions.this.remainingOrderAmount);
                BazaarOrderActions.this.remainingOrderAmount = null;
            }

            return SlotClickResult.Pass;
        }
    }

    public final class ReopenOrderHook implements SlotHook {

        private ReopenOrderHook() {
            BazaarOrderActions.this.registerReopenCloseHandler();
        }

        @Override
        public boolean matches(SlotView view) {
            var cfg = ConfigManager.get().orderActions;
            
            return cfg.enabled
                && cfg.reopenLastBuyOrderEnabled
                && (!cfg.clearOnClose || !BazaarOrderActions.this.hideCancelledOrderButton)
                && BazaarOrderActions.this.lastCancelledBuyOrder != null
                && !view.playerInventorySlot()
                && view.getCurrInfo().inMenu(BazaarMenuType.Orders)
                && view.slotIdx() == BazaarOrderActions.this.getReopenTargetSlotIdx(view);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            return BazaarOrderActions.this.lastCancelledBuyOrder.displayItem().copy();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            log.debug("Reopening bazaar page for product '{}'", BazaarOrderActions.this.lastCancelledBuyOrder.productName());
            GameUtils.runCommand("bz " + BazaarOrderActions.this.lastCancelledBuyOrder.productName());
            return SlotClickResult.Consume;
        }
    }

    public final class OrdersObserverHook implements SlotHook {

        private OrdersObserverHook() { }

        @Override
        public boolean matches(SlotView view) {
            return ConfigManager.get().orderActions.enabled
                && view.getCurrInfo().inMenu(BazaarMenuType.Orders)
                && !view.playerInventorySlot();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var slot = ctx.view();
            var orderInfo = OrderInfoParser.parseOrderInfo(
                slot.getRawStack(),
                slot.slotIdx(),
                BazaarOrderActions.this.bazaarData
            );
            if (orderInfo.isSuccess()) {
                BazaarOrderActions.this.onOrderClick(orderInfo.get(), slot.getRawStack());
            }

            return SlotClickResult.Pass;
        }
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
                .name(Component.literal("Return to Bazaar After Placing an Order"))
                .binding(false, () -> this.reopenBazaar, val -> this.reopenBazaar = val)
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    Component
                        .literal("Run ")
                        .append(ConfigScreen.command("/bz"))
                        .append(Component.literal(" after placing a buy order or sell offer.")),
                    ConfigScreen.note(
                        "The menu may briefly close and unlock the mouse while the server reopens it.")
                )))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createCopyRemainingOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Copy Remaining Amount"))
                .binding(true, () -> this.copyRemaining, enabled -> this.copyRemaining = enabled)
                .description(OptionDescription.of(Component.literal(
                    "Copy the unfilled amount when cancelling a buy order, ready to paste into the next order.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Modifier> createCopyRemainingModifierOption() {
            return Option
                .<Modifier>createBuilder()
                .name(Component.literal("Copy Amount Modifier"))
                .binding(
                    Modifier.Ctrl,
                    () -> this.copyRemainingModifier != null ? this.copyRemainingModifier : Modifier.Ctrl,
                    val -> this.copyRemainingModifier = val
                )
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Choose which modifier key must be held while cancelling to copy the unfilled amount."),
                    ConfigScreen.requires("Copy Remaining Amount")
                )))
                .controller(Modifier::controller);
        }

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Cancelled Order Actions"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Component.literal(
                    "Enable shortcuts for cancelled buy orders.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createReopenLastBuyOrderEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Reopen Last Cancelled Buy Order"))
                .binding(true, () -> this.reopenLastBuyOrderEnabled, val -> this.reopenLastBuyOrderEnabled = val)
                .description(OptionDescription.of(Component.literal(
                    "Show a shortcut on the Bazaar Orders page to reopen the product page of the last cancelled buy order.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createClearOnCloseOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Hide Button After Closing Orders"))
                .binding(true, () -> this.clearOnClose, val -> this.clearOnClose = val)
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Hide the reopen button when you leave the Bazaar Orders page. It returns after another buy order is cancelled."),
                    ConfigScreen.requires("Reopen Last Cancelled Buy Order")
                )))
                .controller(ConfigScreen::createBooleanController);
        }

        public List<OptionGroup> createGroups() {
            var copyGroup = new OptionGrouping(this.createCopyRemainingOption())
                .addOptions(this.createCopyRemainingModifierOption());

            var reopenGroup = new OptionGrouping(this.createReopenLastBuyOrderEnabledOption())
                .addOptions(this.createClearOnCloseOption());

            var rootGroup = new OptionGrouping(this.createEnabledOption())
                .addSubgroups(copyGroup, reopenGroup);

            return List.of(
                OptionGroup
                    .createBuilder()
                    .name(Component.literal("After Placing an Order"))
                    .description(ConfigScreen.createDescription(
                        "Configure what happens after placing a buy order or sell offer."))
                    .options(List.of(this.createReopenBazaarOption().build()))
                    .collapsed(true)
                    .build(),
                OptionGroup
                    .createBuilder()
                    .name(Component.literal("Cancelled Order Actions"))
                    .description(ConfigScreen.createDescription(
                        "Copy the remaining amount or reopen the product page of the last cancelled buy order.",
                        ConfigScreen.ConfigImage.REOPEN_LAST_ORDER
                    ))
                    .options(rootGroup.build())
                    .collapsed(true)
                    .build()
            );
        }
    }
}
