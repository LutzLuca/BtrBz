package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.utils.ClientTickDispatcher;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

public class OrderCancelRouter {

    public static void init() {
        ScreenActionManager.register(new ScreenActionManager.ScreenClickRule() {

            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                if (!ConfigManager.get().orderCancel.enabled) {
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

                ClientTickDispatcher.submit(client -> Util.runCommand("managebazaarorders"), 5);
                return false;
            }
        });
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

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Order Cancel Router"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Text.literal(
                    "Automatically return to the Orders screen after cancelling an order")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var enabledBuilder = this.createEnabledOption();

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order Cancel Router"))
                .description(OptionDescription.of(Text.literal(
                    "Automatically return to the Orders screen after cancelling an order")))
                .option(enabledBuilder.build())
                .collapsed(false)
                .build();
        }
    }
}
