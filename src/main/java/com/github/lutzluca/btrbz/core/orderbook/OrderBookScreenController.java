package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotClickResult;
import com.github.lutzluca.btrbz.utils.slot.SlotHook;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import com.github.lutzluca.btrbz.widgets.framework.WidgetRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Gated slot hook that opens the BtrBz-owned full Order Book host screen. */
public final class OrderBookScreenController {
    private static final int SLOT = 8;
    private static final int PRODUCT_SLOT = 13;
    private static final BazaarMenuType[] MENUS = {
        BazaarMenuType.Item,
        BazaarMenuType.BuyOrderSetupVolume,
        BazaarMenuType.BuyOrderSetupPrice,
        BazaarMenuType.SellOfferSetup
    };
    private final ProductInfoProvider productInfoProvider;
    private final WidgetRuntime runtime;

    public OrderBookScreenController(ProductInfoProvider productInfoProvider, WidgetRuntime runtime) {
        this.productInfoProvider = productInfoProvider;
        this.runtime = runtime;
        SlotHookRegistry.register(new Hook());
    }

    private final class Hook implements SlotHook {
        private @Nullable ItemStack displayStack;

        @Override
        public boolean matches(SlotView view) {
            return hookEligible(
                ConfigManager.get().widgets.orderBookScreen.enabled,
                productInfoProvider.getOpenedProduct() != null,
                view.playerInventorySlot(),
                view.slotIdx(),
                view.getCurrInfo().getMenuType().orElse(null)
            );
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext context) {
            if (this.displayStack == null) {
                this.displayStack = new ItemStack(Items.BOOK);
                this.displayStack.set(
                    DataComponents.CUSTOM_NAME,
                    Component.literal("Open Order Book").withStyle(style -> style.withItalic(false))
                );
            }
            return this.displayStack.copy();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext context) {
            if (!ConfigManager.get().widgets.orderBookScreen.enabled) return SlotClickResult.Pass;
            var product = productInfoProvider.getOpenedProduct();
            if (product == null) return SlotClickResult.Pass;
            var identity = ProductIdentity.fromIndex(product);
            var icon = context.view().getCurrInfo().getItemStack(PRODUCT_SLOT)
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
            Minecraft.getInstance().setScreen(new OrderBookScreen(
                context.view().getCurrInfo().getScreen(),
                identity,
                product.formattedName(),
                icon,
                runtime.createBazaarHost()
            ));
            return SlotClickResult.Consume;
        }
    }

    static boolean hookEligible(
        boolean enabled,
        boolean productAvailable,
        boolean playerInventorySlot,
        int slot,
        @Nullable BazaarMenuType menu
    ) {
        if (!enabled || !productAvailable || playerInventorySlot || slot != SLOT || menu == null) return false;
        for (var supported : MENUS) if (supported == menu) return true;
        return false;
    }
}
