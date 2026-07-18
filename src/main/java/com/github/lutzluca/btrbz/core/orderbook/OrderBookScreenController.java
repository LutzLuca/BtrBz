package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
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
import dev.isxander.yacl3.api.OptionDescription;
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
    private static final BazaarMenuType[] ORDER_BOOK_MENUS = {
        BazaarMenuType.Item,
        BazaarMenuType.BuyOrderSetupVolume,
        BazaarMenuType.BuyOrderSetupPrice,
        BazaarMenuType.SellOfferSetup
    };

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
                && view.getCurrInfo().inMenu(ORDER_BOOK_MENUS);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            if (this.cachedDisplayStack != null) {
                return this.cachedDisplayStack.copy();
            }

            var book = new ItemStack(Items.BOOK);
            book.set(
                DataComponents.CUSTOM_NAME,
                Component.literal("Open Order Book").withStyle(style -> style.withItalic(false))
            );

            this.cachedDisplayStack = book;
            return this.cachedDisplayStack.copy();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var product = OrderBookScreenController.this.productInfoProvider.getOpenedProduct();
            if (product == null) {
                Notifier.notifyPlayer(Notifier
                    .prefix()
                    .append(Component.literal("Failed to determine the opened product name")));
                return SlotClickResult.Consume;
            }

            var orders = OrderBookScreenController.this.bazaarData.getOrderLists(ProductIdentity.fromIndex(product));
            var title = Component.empty()
                .append(Component.literal(product.formattedName()))
                .append(Component.literal(" Order Book"));
            var orderBookScreen = new OrderBookScreen(
                ctx.view().getCurrInfo().getScreen(),
                title,
                orders
            );
            //? if <26.2 {
            Minecraft.getInstance().setScreen(orderBookScreen);
            //?} else {
            /*Minecraft.getInstance().gui.setScreen(orderBookScreen);
             *///?}
            return SlotClickResult.Consume;
        }
    }

    public static class OrderBookConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Enable Order Book"))
                .description(OptionDescription.of(Component.literal(
                    "Add an Open Order Book button to product, buy-order amount, buy-order price, and sell-offer screens.")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var root = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.nullToEmpty("Order Book"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Open a detailed view of current buy orders and sell offers for the selected product."),
                    ConfigScreen.note(
                        "Click a listed price to copy it and return to the Bazaar screen.")
                ),
                    ConfigScreen.ConfigImage.ORDER_BOOK
                ))
                .options(root.build())
                .collapsed(true)
                .build();
        }
    }
}
