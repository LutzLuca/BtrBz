package com.github.lutzluca.btrbz.core.order_book;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.utils.Notifier;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;

public class OrderBookScreenController {

    private static final int CUSTOM_ORDER_BOOK_IDX = 8;
    private final BazaarData bazaarData;
    private final ProductInfoProvider productInfoProvider;

    public OrderBookScreenController(BazaarData bazaarData, ProductInfoProvider productInfoProvider) {
        this.bazaarData = bazaarData;
        this.productInfoProvider = productInfoProvider;
        this.registerItemOverride();
        this.registerClickAction();
    }

    private boolean isOrderSetupMenu(ScreenInfo info) {
        return info.inMenu(
            BazaarMenuType.Item,
            BazaarMenuType.BuyOrderSetupVolume,
            BazaarMenuType.BuyOrderSetupPrice,
            BazaarMenuType.SellOfferSetup
        );
    }

    private void registerItemOverride() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (!ConfigManager.get().orderBook.enabled) {
                return Optional.empty();
            }

            if (slot.getContainerSlot() != CUSTOM_ORDER_BOOK_IDX || 
                !isOrderSetupMenu(info) || 
                GameUtils.isPlayerInventorySlot(slot)
            ) {
                return Optional.empty();
            }

            var book = new ItemStack(Items.BOOK);
            book.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Open Order Book").withStyle(style -> style.withItalic(false))
            );

            return Optional.of(book);
        });
    }

    private void registerClickAction() {
        ScreenActionManager.register(new ScreenClickRule() {

            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                return !GameUtils.isPlayerInventorySlot(slot) &&
                    slot.getContainerSlot() == CUSTOM_ORDER_BOOK_IDX && 
                    isOrderSetupMenu(info) && 
                    ConfigManager.get().orderBook.enabled;
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var productNameInfo = OrderBookScreenController.this.productInfoProvider.getOpenedProductNameInfo();
                if (productNameInfo == null) {
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Component.literal("Failed to determine the opened product name")));
                    return false;
                }

                var orders = OrderBookScreenController.this.bazaarData.getOrderLists(productNameInfo.productId());
                var orderBookScreen = new OrderBookScreen(
                    info.getScreen(),
                    productNameInfo.productName(),
                    orders
                );
                Minecraft.getInstance().setScreen(orderBookScreen);

                return true;
            }

        });
    }

    public static class OrderBookConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Order Book"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var root = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.nullToEmpty("Order Book"))
                .options(root.build())
                .collapsed(false)
                .build();
        }
    }
}
