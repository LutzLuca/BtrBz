package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener.Event;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

@Slf4j
public class BazaarOrderActions {

    private static boolean shouldReopenOrders = false;
    private static boolean shouldReopenBazaar = false;
    private static Integer remainingOrderAmount = null;

    public static void init() {
        ScreenActionManager.register(new ScreenActionManager.ScreenClickRule() {

            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get().orderActions;
                if (!cfg.enabled) {
                    return false;
                }

                if (!cfg.reopenOrders && !cfg.copyRemaining) {
                    return false;
                }

                var player = MinecraftClient.getInstance().player;
                if (player == null || slot == null || slot.inventory == player.getInventory()) {
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
                if (cfg.copyRemaining && remainingOrderAmount != null) {
                    GameUtils.copyIntToClipboard(remainingOrderAmount);
                    remainingOrderAmount = null;
                }
                if (cfg.reopenOrders) {
                    shouldReopenOrders = true;
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
                if (shouldReopenOrders) {
                    GameUtils.runCommand("managebazaarorders");
                }

                shouldReopenOrders = false;
                remainingOrderAmount = null;
            }
        );
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

    private static boolean isCancelOrderSlot(Slot slot) {
        return (slot.getIndex() == 11 || slot.getIndex() == 13) && slot
            .getStack()
            .getName()
            .getString()
            .equals("Cancel Order");
    }

    public static class OrderActionsConfig {

        public boolean enabled = true;
        public boolean reopenOrders = true;
        public boolean copyRemaining = false;
        public boolean reopenBazaar = false;

        public Option.Builder<Boolean> createReopenBazaarOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Return to Bazaar After Order Setup"))
                .binding(false, () -> this.reopenBazaar, val -> this.reopenBazaar = val)
                .description(OptionDescription.of(GameUtils.join(
                    List.of(
                        Text.literal(
                            "Automatically reopens the main Bazaar menu after placing a buy/sell order."),
                        Text.literal(
                            "\nNote: This executes '/bz' which requires a server round-trip."),
                        Text.literal(
                            "You may experience brief mouse unlock during the transition, which may feel a bit clunky.")
                    ), " "
                )))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createCopyRemainingOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Copy Remaining "))
                .binding(true, () -> this.copyRemaining, enabled -> this.copyRemaining = enabled)
                .description(OptionDescription.of(Text.literal(
                    "Automatically copies the remaining amount of items from a cancelled order")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createReopenOrdersOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Go back to Order Screen"))
                .binding(true, () -> this.reopenOrders, enabled -> this.reopenOrders = enabled)
                .description(OptionDescription.of(Text.literal(
                    "Automatically opens the Bazaar order screen after cancelling an order")))
                .controller(ConfigScreen::createBooleanController);
        }


        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Order Cancel Router"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Text.literal(
                    "Master switch for actions on order cancel")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var options = List.of(
                this.createReopenOrdersOption().build(),
                this.createCopyRemainingOption().build(),
                this.createReopenBazaarOption().build()
            );
            var enabledBuilder = this.createEnabledOption();

            enabledBuilder.addListener((option, event) -> {
                if (event == Event.STATE_CHANGE) {
                    options.forEach(opt -> opt.setAvailable(option.pendingValue()));
                }
            });

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order Cancel Actions"))
                .description(OptionDescription.of(Text.literal(
                    "Automatically return to the Orders screen after cancelling an order")))
                .option(enabledBuilder.build())
                .options(options)
                .collapsed(false)
                .build();
        }
    }
}
