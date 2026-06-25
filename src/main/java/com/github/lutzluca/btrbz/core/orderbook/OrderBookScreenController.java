package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotClickResult;
import com.github.lutzluca.btrbz.utils.slot.SlotHook;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import org.jetbrains.annotations.Nullable;

public class OrderBookScreenController {

    private static final int CUSTOM_ORDER_BOOK_IDX = 8;
    private final BazaarData bazaarData;
    private final ProductInfoProvider productInfoProvider;

    public OrderBookScreenController(BazaarData bazaarData, ProductInfoProvider productInfoProvider) {
        this.bazaarData = bazaarData;
        this.productInfoProvider = productInfoProvider;
        SlotHookRegistry.register(new OrderBookButtonHook());
    }

    public final class OrderBookButtonHook implements SlotHook {
        private @Nullable ItemStack cachedDisplayStack = null;

        private OrderBookButtonHook() { }

        @Override
        public boolean matches(SlotView view) {
            return ConfigManager.get().orderBook.enabled
                && !view.playerInventorySlot()
                && view.slotIdx() == CUSTOM_ORDER_BOOK_IDX
                && view.currInfo().inMenu(
                    BazaarMenuType.Item,
                    BazaarMenuType.BuyOrderSetupVolume,
                    BazaarMenuType.BuyOrderSetupPrice,
                    BazaarMenuType.SellOfferSetup
                );
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            if (cachedDisplayStack != null) {
                return cachedDisplayStack;
            }

            var book = new ItemStack(Items.BOOK);
            book.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Open Order Book").withStyle(style -> style.withItalic(false))
            );

            cachedDisplayStack = book;
            return cachedDisplayStack.copy();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var productNameInfo = OrderBookScreenController.this.productInfoProvider.getOpenedProductNameInfo();
            if (productNameInfo == null) {
                Notifier.notifyPlayer(Notifier
                    .prefix()
                    .append(Component.literal("Failed to determine the opened product name")));
                return SlotClickResult.Consume;
            }

            var orders = OrderBookScreenController.this.bazaarData.getOrderLists(productNameInfo.productId());
            var orderBookScreen = new OrderBookScreen(
                ctx.view().currInfo().getScreen(),
                productNameInfo.productName(),
                orders
            );
            Minecraft.getInstance().setScreen(orderBookScreen);
            return SlotClickResult.Consume;
        }
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
