package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetRuntime;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.screen.BazaarProductContext;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.screen.slot.SlotClickContext;
import com.github.lutzluca.btrbz.screen.slot.SlotClickResult;
import com.github.lutzluca.btrbz.screen.slot.SlotHook;
import com.github.lutzluca.btrbz.screen.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.screen.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.screen.slot.SlotView;
import com.github.lutzluca.btrbz.utils.GameUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** Gated slot hook that opens the BtrBz-owned full Order Book host screen. */
public final class OrderBookScreenController {
    private static final int CONTROLLER_SLOT = 8;
    private static final BazaarMenuType[] SUPPORTED_MENUS = {
        BazaarMenuType.Item,
        BazaarMenuType.BuyOrderSetupVolume,
        BazaarMenuType.BuyOrderSetupPrice,
        BazaarMenuType.SellOfferSetup
    };

    private final BazaarProductContext productContext;
    private final WidgetRuntime runtime;

    public OrderBookScreenController(
        BazaarProductContext productContext,
        WidgetRuntime runtime
    ) {
        this.productContext = productContext;
        this.runtime = runtime;
        SlotHookRegistry.register(new ControllerHook());
    }

    private final class ControllerHook implements SlotHook {
        private @Nullable ItemStack displayStack;

        @Override
        public boolean matches(SlotView view) {
            return hookEligible(
                ConfigManager.get().widgets.orderBookScreen.frame.enabled,
                OrderBookScreenController.this.productContext.openedProduct() != null,
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
            if (!ConfigManager.get().widgets.orderBookScreen.frame.enabled) {
                return SlotClickResult.Pass;
            }

            var product = OrderBookScreenController.this.productContext.openedProduct();
            if (product == null) {
                return SlotClickResult.Pass;
            }
            var identity = ProductIdentity.fromIndex(product);

            GameUtils.setScreen(new OrderBookScreen(
                context.view().getCurrInfo().getScreen(),
                identity,
                product.formattedName(),
                OrderBookScreenController.this.runtime.createScreenHost()));

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
