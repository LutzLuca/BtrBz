package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.utils.ClickOutcome;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.slot.SlotInterceptorManager;
import com.github.lutzluca.btrbz.utils.slot.SlotInterceptorRegistration;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class OrderBookScreenController {

    private static final int CUSTOM_ORDER_BOOK_IDX = 8;
    private final BazaarData bazaarData;
    private final ProductInfoProvider productInfoProvider;

    public OrderBookScreenController(BazaarData bazaarData, ProductInfoProvider productInfoProvider) {
        this.bazaarData = bazaarData;
        this.productInfoProvider = productInfoProvider;
        this.registerSlotInterceptor();
    }

    private boolean isOrderSetupMenu(ScreenInfo info) {
        return info.inMenu(
            BazaarMenuType.Item,
            BazaarMenuType.BuyOrderSetupVolume,
            BazaarMenuType.BuyOrderSetupPrice,
            BazaarMenuType.SellOfferSetup
        );
    }

    private void registerSlotInterceptor() {
        SlotInterceptorManager.register(
            SlotInterceptorRegistration
                .named("order-book.button")
                .matches(ctx ->
                    !ctx.isPlayerInventorySlot() &&
                        ctx.containerSlot() == CUSTOM_ORDER_BOOK_IDX &&
                        this.isOrderSetupMenu(ctx.currInfo()) &&
                        ConfigManager.get().orderBook.enabled
                )
                .overrideItem(ctx -> {
                    var book = new ItemStack(Items.BOOK);
                    book.set(
                        DataComponents.CUSTOM_NAME,
                        Component.literal("Open Order Book").withStyle(style -> style.withItalic(false))
                    );

                    return Optional.of(book);
                })
                .onClick(ctx -> {
                    var productNameInfo = this.productInfoProvider.getOpenedProductNameInfo();
                    if (productNameInfo == null) {
                        Notifier.notifyPlayer(Notifier
                            .prefix()
                            .append(Component.literal("Failed to determine the opened product name")));
                        return ClickOutcome.Cancel;
                    }

                    var orders = this.bazaarData.getOrderLists(productNameInfo.productId());
                    var orderBookScreen = new OrderBookScreen(
                        ctx.currInfo().getScreen(),
                        productNameInfo.productName(),
                        orders
                    );
                    Minecraft.getInstance().setScreen(orderBookScreen);

                    return ClickOutcome.Cancel;
                })
                .build()
        );
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
