package com.github.lutzluca.btrbz.core.order_book;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.utils.Notifier;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.Optional;
import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

public class OrderBookScreenController {

    private static final int CUSTOM_ORDER_BOOK_IDX = 8;
    private static OrderBookScreenController instance;

    private OrderBookScreenController() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (slot.getIndex() != CUSTOM_ORDER_BOOK_IDX || !info.inMenu(BazaarMenuType.Item)) {
                return Optional.empty();
            }

            if (!ConfigManager.get().orderBook.enabled) {
                return Optional.empty();
            }

            var book = new ItemStack(Items.BOOK);
            book.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal("Open Order Book").styled(style -> style.withItalic(false))
            );

            return Optional.of(book);
        });

        ScreenActionManager.register(new ScreenClickRule() {

            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                return slot.getIndex() == CUSTOM_ORDER_BOOK_IDX && info.inMenu(BazaarMenuType.Item) && ConfigManager.get().orderBook.enabled;
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var productNameInfo = ProductInfoProvider.get().getOpenedProductNameInfo();
                if (productNameInfo == null) {
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Text.literal("Failed to determine the opened product name")));
                    return false;
                }

                var orders = BtrBz.bazaarData().getOrderLists(productNameInfo.productId());
                var orderBookScreen = new OrderBookScreen(
                    info.getScreen(),
                    productNameInfo.productName(),
                    orders
                );
                MinecraftClient.getInstance().setScreen(orderBookScreen);

                return true;
            }

        });
    }

    public static OrderBookScreenController get() {
        if (instance == null) {
            instance = new OrderBookScreenController();
        }
        return instance;
    }

    public static class OrderBookConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.of("Order Book"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var root = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Text.of("Order Book"))
                .options(root.build())
                .collapsed(false)
                .build();
        }
    }
}
