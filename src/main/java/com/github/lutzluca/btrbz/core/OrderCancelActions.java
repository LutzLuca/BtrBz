package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
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
public class OrderCancelActions {

    private static boolean returnToOrderScreen = false;
    private static Integer remainingOrderAmount = null;

    public static void init() {
        ScreenActionManager.register(new ScreenActionManager.ScreenClickRule() {

            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                var cfg = ConfigManager.get().orderCancelActions;
                if (!cfg.enabled) {
                    return false;
                }

                if (!cfg.goToOrderScreen && !cfg.copyRemaining) {
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

                var cfg = ConfigManager.get().orderCancelActions;
                if (cfg.copyRemaining && remainingOrderAmount != null) {
                    Util.copyIntToClipboard(remainingOrderAmount);
                    remainingOrderAmount = null;
                }
                if (cfg.goToOrderScreen) {
                    returnToOrderScreen = true;
                }

                return false;
            }
        });

        ScreenInfoHelper.registerOnClose(
            info -> info.inMenu(BazaarMenuType.OrderOptions), info -> {
                if (returnToOrderScreen) {
                    Util.runCommand("managebazaarorders");
                }
                returnToOrderScreen = false;
                remainingOrderAmount = null;
            }
        );
    }

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

    private static boolean isCancelOrderSlot(Slot slot) {
        return (slot.getIndex() == 11 || slot.getIndex() == 13) && slot
            .getStack()
            .getName()
            .getString()
            .equals("Cancel Order");
    }

    public static class OrderCancelConfig {

        public boolean enabled = true;
        public boolean copyRemaining = true;
        public boolean goToOrderScreen = true;

        public Option.Builder<Boolean> createCopyRemainingOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Copy Remaining "))
                .binding(true, () -> this.copyRemaining, enabled -> this.copyRemaining = enabled)
                .description(OptionDescription.of(Text.literal(
                    "Automatically copies the remaining amount of items from a cancelled order")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createGoToOrderScreenOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Go back to Order Screen"))
                .binding(
                    true,
                    () -> this.goToOrderScreen,
                    enabled -> this.goToOrderScreen = enabled
                )
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
                this.createGoToOrderScreenOption().build(),
                this.createCopyRemainingOption().build()
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
