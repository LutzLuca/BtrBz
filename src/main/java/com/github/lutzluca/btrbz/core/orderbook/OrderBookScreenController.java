package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoController;
import com.github.lutzluca.btrbz.core.bazaariteminfo.InitialMode;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotClickResult;
import com.github.lutzluca.btrbz.utils.slot.SlotHook;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Bazaar slot hook that opens the unified Item Info screen in Order Book mode. */
public final class OrderBookScreenController {
    private static final int CONTROLLER_SLOT = 8;
    private static final BazaarMenuType[] SUPPORTED_MENUS = {
        BazaarMenuType.Item,
        BazaarMenuType.BuyOrderSetupVolume,
        BazaarMenuType.BuyOrderSetupPrice,
        BazaarMenuType.SellOfferSetup
    };

    private final ProductInfoProvider productInfoProvider;
    private final BazaarData bazaarData;
    private final BazaarItemInfoController itemInfo;

    public OrderBookScreenController(
        ProductInfoProvider productInfoProvider,
        BazaarData bazaarData,
        BazaarItemInfoController itemInfo
    ) {
        this.productInfoProvider = productInfoProvider;
        this.bazaarData = bazaarData;
        this.itemInfo = itemInfo;
        SlotHookRegistry.register(new ControllerHook());
    }

    private final class ControllerHook implements SlotHook {
        private @Nullable ItemStack displayStack;

        @Override
        public boolean matches(SlotView view) {
            return hookEligible(
                ConfigManager.get().bazaarItemInfo.showBazaarEntry,
                productInfoProvider.getOpenedProduct() != null,
                view.playerInventorySlot(),
                view.slotIdx(),
                view.getCurrInfo().getMenuType().orElse(null));
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext context) {
            if (this.displayStack == null) {
                this.displayStack = new ItemStack(Items.BOOK);
                this.displayStack.set(
                    DataComponents.CUSTOM_NAME,
                    Component.literal("Open Order Book").withStyle(style -> style.withItalic(false)));
            }

            return this.displayStack.copy();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext context) {
            if (!ConfigManager.get().bazaarItemInfo.showBazaarEntry) {
                return SlotClickResult.Pass;
            }

            var product = productInfoProvider.getOpenedProduct();
            if (product == null) {
                return SlotClickResult.Pass;
            }
            var identity = ProductIdentity.fromIndex(product);

            var stack = bazaarData.productStack(identity).orElseGet(() -> new ItemStack(Items.BOOK));
            return itemInfo.open(
                context.view().getCurrInfo().getScreen(), identity, stack, InitialMode.OrderBook)
                    ? SlotClickResult.Consume
                    : SlotClickResult.Pass;
        }
    }

    static boolean hookEligible(
        boolean enabled,
        boolean productAvailable,
        boolean playerInventorySlot,
        int slot,
        @Nullable BazaarMenuType menu
    ) {
        if (!enabled || !productAvailable || playerInventorySlot || slot != CONTROLLER_SLOT || menu == null) {
            return false;
        }

        for (var supported : SUPPORTED_MENUS) {
            if (supported == menu) {
                return true;
            }
        }

        return false;
    }
}
