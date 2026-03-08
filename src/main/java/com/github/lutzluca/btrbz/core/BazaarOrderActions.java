package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

@Slf4j
public class BazaarOrderActions {

    private static boolean shouldReopenBazaar = false;
    private static Integer remainingOrderAmount = null;

    public static void init() {
        ScreenActionManager.register(new ScreenActionManager.ScreenClickRule() {

            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get().orderActions;
                if (!cfg.enabled || !cfg.copyRemaining) {
                    return false;
                }

                if (GameUtils.isPlayerInventorySlot(slot)) {
                    return false;
                }

                return info.inMenu(BazaarMenuType.OrderOptions) && isCancelOrderSlot(slot);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var prev = ScreenInfoHelper.get().getPrevInfo();
                if (!prev.inMenu(BazaarMenuType.Orders)) {
                    return false;
                }

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
            info -> info.inMenu(BazaarMenuType.OrderOptions), info -> {
                remainingOrderAmount = null;
            }
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

    // NOTE: this could (and probably should) also be done inside the `OrderOptions` screen
    // parsing the `Cancel Order` item's lore "You will be refunded {rounded total} coins from
    // {remaining}x missing items."
    public static void onOrderClick(OrderInfo info) {
        if (info.unclaimed() != 0 || info.type() != OrderType.Buy) {
            return;
        }

        remainingOrderAmount = info.volume() - info.filledAmount();
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
        return slot != null && slot.getContainerSlot() == 11 && slot
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
                .name(Component.literal("Order Cancel Router"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Component.literal(
                    "Master switch for actions on order cancel")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var copyGroup = new OptionGrouping(this.createCopyRemainingOption())
                .addOptions(this.createCopyRemainingModifierOption());

            var rootGroup = new OptionGrouping(this.createEnabledOption())
                .addOptions(this.createReopenBazaarOption())
                .addSubgroups(copyGroup);

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
