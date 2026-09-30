package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.screen.slot.SlotClickContext;
import com.github.lutzluca.btrbz.screen.slot.SlotClickResult;
import com.github.lutzluca.btrbz.screen.slot.SlotHook;
import com.github.lutzluca.btrbz.screen.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.screen.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.screen.slot.SlotView;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Slf4j
public class FlipHelper {

    private static final int FLIP_ORDER_ITEM_SLOT_IDX = 15;
    private static final int CUSTOM_HELPER_ITEM_SLOT_IDX = 16;

    private final BazaarData bazaarData;
    private final FlipProductContext flipProductContext;
    private final FlipSubmissionTracker flipSubmissionTracker;
    private final TrackedOrderManager orderManager;

    private boolean pendingFlip = false;
    private CachedHelperDisplay cachedHelperDisplay = null;

    public FlipHelper(
        BazaarData bazaarData,
        FlipProductContext flipProductContext,
        FlipSubmissionTracker flipSubmissionTracker,
        TrackedOrderManager orderManager
    ) {
        this.bazaarData = bazaarData;
        this.flipProductContext = flipProductContext;
        this.flipSubmissionTracker = flipSubmissionTracker;
        this.orderManager = orderManager;
        this.registerSlotHooks();
        this.registerFlipProductContextHandler();
        this.registerFlipPriceScreenHandler();
    }

    public void onOrderClick(OrderInfo info) {
        this.clearPendingFlipState();
        this.flipProductContext.selectOrder(info);
        this.flipProductContext.getSelectedProduct()
            .ifPresent(product -> log.debug("Selected flip product: {}", product));
    }

    private void registerSlotHooks() {
        SlotHookRegistry.register(new OrderFlipHook());
        SlotHookRegistry.register(new OrderProductObserverHook());
    }

    private void registerFlipProductContextHandler() {
        ScreenTracker.registerOnSwitch(curr -> {
            if (this.flipProductContext.getSelectedProduct().isEmpty()) {
                return;
            }

            var prev = ScreenTracker.get().getPrevInfo();
            boolean inOrderOptions = curr.inMenu(BazaarMenuType.OrderOptions);
            boolean inFlipPriceSign = curr.getScreen() instanceof SignEditScreen
                && prev.inMenu(BazaarMenuType.OrderOptions);
            if (inOrderOptions || inFlipPriceSign) {
                return;
            }

            log.debug("Leaving flip flow, clearing selected product context");
            this.cancelPendingFlip();
        });
    }

    private ItemStack createHelperDisplayStack(double price) {
        var formatted = Utils.formatDecimal(price, 1, true);

        var customHelperItem = new ItemStack(Items.NETHER_STAR);
        customHelperItem.set(
            DataComponents.CUSTOM_NAME,
            Component
                .literal("Flip for ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatted).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" coins each").withStyle(ChatFormatting.GRAY))
                .withStyle(style -> style.withItalic(false)));
        return customHelperItem;
    }

    private ItemStack getCachedHelperDisplayStack() {
        var cachedPrice = this.flipProductContext.getFlipPrice(this.bazaarData);
        if (cachedPrice.isEmpty()) {
            this.cachedHelperDisplay = null;
            return null;
        }

        var displayPrice = cachedPrice.get();

        if (this.cachedHelperDisplay != null
            && Double.compare(this.cachedHelperDisplay.displayPrice(), displayPrice) == 0) {
            return this.cachedHelperDisplay.display().copy();
        }

        var display = this.createHelperDisplayStack(displayPrice);
        this.cachedHelperDisplay = new CachedHelperDisplay(displayPrice, display.copy());
        return display;
    }

    private void registerFlipPriceScreenHandler() {
        ScreenTracker.registerOnSwitch(curr -> {
            if (!ConfigStore.get().config().flipHelper.enabled || !this.pendingFlip) {
                this.clearPendingFlipState();
                return;
            }

            this.clearPendingFlipState();
            var prev = ScreenTracker.get().getPrevInfo();
            if (!prev.inMenu(BazaarMenuType.OrderOptions)) {
                return;
            }

            if (!(curr.getScreen() instanceof SignEditScreen signEditScreen)) {
                log.warn("""
                        Expected screen transition from OrderOptions to a SignEditScreen while pendingFlip is set,
                        but switched to a non-SignEditScreen; resetting flip state
                    """);
                return;
            }

            var flipPrice = this.flipProductContext.getFlipPrice(this.bazaarData);

            if (flipPrice.isEmpty()) {
                log.warn(
                    "Could not resolve price for product {}",
                    this.flipProductContext.getSelectedProduct());
                return;
            }

            var formatted = Utils.formatDecimal(flipPrice.get(), 1, false);
            this.flipSubmissionTracker.recordSubmittedFlip(
                this.flipProductContext.getSelectedProduct().orElseThrow(),
                flipPrice.get());
            GameUtils.submitSignValue(signEditScreen, formatted);
        });
    }

    public void handleFlipped(BazaarMessage.OrderFlipped flipped) {
        var match = this.flipSubmissionTracker.consume(ProductIdentity.fromName(flipped.productName()));

        if (match.isEmpty()) {
            log.debug(
                "Flip completed without a recorded price for {}x {}; relying on Bazaar Orders sync",
                flipped.volume(),
                flipped.productName());
            return;
        }

        var entry = match.get();
        double pricePerUnit = entry.pricePerUnit();

        var orderInfo = new OrderInfo.UnfilledOrderInfo(
            entry.product(),
            flipped.productName(),
            OrderType.Sell,
            flipped.volume(),
            pricePerUnit,
            0,
            0,
            -1);
        this.orderManager.addTrackedOrder(new TrackedOrder(orderInfo, entry.product()));

        log.debug(
            "Added tracked Sell order from flipped chat: {}x {} at {} per unit",
            flipped.volume(),
            entry.product(),
            Utils.formatDecimal(pricePerUnit, 1, true));
    }

    public void cancelPendingFlip() {
        this.clearPendingFlipState();
        this.flipProductContext.clearProduct();
    }

    private void clearPendingFlipState() {
        this.cachedHelperDisplay = null;
        this.pendingFlip = false;
    }

    public final class OrderFlipHook implements SlotHook {

        private OrderFlipHook() {}

        @Override
        public boolean matches(SlotView view) {
            return ConfigStore.get().config().flipHelper.enabled
                && !view.playerInventorySlot()
                && view.slotIdx() == CUSTOM_HELPER_ITEM_SLOT_IDX
                && view.getCurrInfo().inMenu(BazaarMenuType.OrderOptions)
                && FlipHelper.this.flipProductContext.getSelectedProduct().isPresent();
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext ctx) {
            return FlipHelper.this.getCachedHelperDisplayStack();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var client = Minecraft.getInstance();
            var gcsOpt = ctx.view().getCurrInfo().getGenericContainerScreen();
            if (gcsOpt.isEmpty()) {
                return SlotClickResult.Pass;
            }

            var handler = gcsOpt.get().getMenu();
            var player = client.player;
            var interactionManager = client.gameMode;
            if (player == null || interactionManager == null) {
                return SlotClickResult.Pass;
            }

            if (FlipHelper.this.flipProductContext.getFlipPrice(FlipHelper.this.bazaarData).isEmpty()) {
                log.debug("Ignoring flip execution click because its price is not available");
                return SlotClickResult.Pass;
            }

            FlipHelper.this.pendingFlip = true;
            interactionManager.handleContainerInput(
                handler.containerId,
                FLIP_ORDER_ITEM_SLOT_IDX,
                ctx.button(),
                ContainerInput.PICKUP,
                player);
            return SlotClickResult.Consume;
        }
    }

    public final class OrderProductObserverHook implements SlotHook {

        private OrderProductObserverHook() {}

        @Override
        public boolean matches(SlotView view) {
            var cfg = ConfigStore.get().config();
            boolean tracksFlipProduct = cfg.widgets.orderBookPrice.frame.enabled;
            return (cfg.flipHelper.enabled || tracksFlipProduct)
                && view.getCurrInfo().inMenu(BazaarMenuType.Orders)
                && !view.playerInventorySlot();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var orderInfo = OrderInfoParser.parseOrderInfo(
                ctx.view().getRawStack(),
                ctx.view().slotIdx(),
                FlipHelper.this.bazaarData);
            if (orderInfo.isSuccess()) {
                FlipHelper.this.onOrderClick(orderInfo.get());
            } else {
                FlipHelper.this.cancelPendingFlip();
            }

            return SlotClickResult.Pass;
        }
    }

    private record CachedHelperDisplay(double displayPrice, ItemStack display) {}

    public static class FlipHelperConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Flip Helper"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(ConfigScreen.createDescription(
                    "Add a quick-flip action to filled buy orders and suggest a sell-offer price 0.1 coins below "
                        + "the current lowest offer."))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Flip Helper"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text("Turn a filled buy order into a sell offer with fewer clicks."),
                    ConfigScreen.example(
                        "If the best sell offer is 1,000 coins, the suggested price is 999.9 coins.")),
                    ConfigImages.FlipHelper))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }
}
