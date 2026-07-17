package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.TrackedProduct;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.TimedStore;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.utils.slot.SlotClickContext;
import com.github.lutzluca.btrbz.utils.slot.SlotClickResult;
import com.github.lutzluca.btrbz.utils.slot.SlotHook;
import com.github.lutzluca.btrbz.utils.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.utils.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.utils.slot.SlotView;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
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

    private final TimedStore<FlipEntry> pendingFlips = new TimedStore<>(15_000L);
    private final BazaarData bazaarData;

    private TrackedProduct potentialFlipProduct = null;
    private boolean pendingFlip = false;
    private CachedHelperDisplay cachedHelperDisplay = null;

    public FlipHelper(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.registerSlotHooks();
        this.registerFlipPriceScreenHandler();
    }

    public void onOrderClick(OrderInfo info) {
        if (info.type() != OrderType.Buy) {
            this.clearPendingFlipState();
            return;
        }

        if (info instanceof OrderInfo.UnfilledOrderInfo) {
            this.clearPendingFlipState();
            return;
        }

        if (this.potentialFlipProduct != null) {
            this.potentialFlipProduct.destroy();
        }

        this.cachedHelperDisplay = null;
        var product = this.bazaarData.resolveIndexedProduct(info.product());
        if (product.isEmpty()) {
            this.clearPendingFlipState();
            log.warn("Could not resolve flip product '{}'", info.uiProductName());
            return;
        }

        this.potentialFlipProduct = new TrackedProduct(this.bazaarData, product.get());
        log.debug("Set `potentialFlipProduct` for product: {}", product.get());
    }

    private void registerSlotHooks() {
        SlotHookRegistry.register(new OrderFlipHook());
        SlotHookRegistry.register(new OrderProductObserverHook());
    }

    private ItemStack createHelperDisplayStack(double price) {
        var formatted = Utils.formatDecimal(Math.max(price, .1), 1, true);

        var customHelperItem = new ItemStack(Items.NETHER_STAR);
        customHelperItem.set(
            DataComponents.CUSTOM_NAME,
            Component
                .literal("Flip for ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatted).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" coins each").withStyle(ChatFormatting.GRAY))
                .withStyle(style -> style.withItalic(false))
        );
        return customHelperItem;
    }

    private ItemStack getCachedHelperDisplayStack() {
        if (this.potentialFlipProduct == null) {
            this.cachedHelperDisplay = null;
            return null;
        }

        var cachedPrice = this.potentialFlipProduct.getSellOfferPrice()
            .map(price -> Math.max(price - 0.1, .1));
        if (cachedPrice.isEmpty()) {
            this.cachedHelperDisplay = null;
            return null;
        }

        var productName = this.potentialFlipProduct.getProductName();
        var displayPrice = cachedPrice.get();

        if (this.cachedHelperDisplay != null
            && this.cachedHelperDisplay.productName().equals(productName)
            && Double.compare(this.cachedHelperDisplay.displayPrice(), displayPrice) == 0) {
            return this.cachedHelperDisplay.display().copy();
        }

        var display = this.createHelperDisplayStack(displayPrice);
        this.cachedHelperDisplay = new CachedHelperDisplay(productName, displayPrice, display.copy());
        return display;
    }

    private void registerFlipPriceScreenHandler() {
        ScreenInfoHelper.registerOnSwitch(curr -> {
            if (!ConfigManager.get().flipHelper.enabled) {
                return;
            }
            var prev = ScreenInfoHelper.get().getPrevInfo();
            if (prev == null || !prev.inMenu(BazaarMenuType.OrderOptions)) {
                pendingFlip = false;
                return;
            }

            if (!this.pendingFlip) {
                log.debug(
                    "Screen transition from OrderOption without a pendingFlip -> resetting flip state");
                this.clearPendingFlipState();
                return;
            }

            if (!(curr.getScreen() instanceof SignEditScreen signEditScreen)) {
                log.warn("""
                        Expected screen transition from OrderOptions to a SignEditScreen while pendingFlip is set,
                        but switched to a non-SignEditScreen; resetting flip state
                    """);
                this.clearPendingFlipState();
                return;
            }

            if (this.potentialFlipProduct == null) {
                log.warn(
                    "Expected `potentialFlipProduct` to be non-null to proceed with entering the flipPrice");
                this.clearPendingFlipState();
                return;
            }

            var flipPrice = this.potentialFlipProduct
                .getSellOfferPrice()
                .map(price -> Math.max(price - .1, 0.1));

            if (flipPrice.isEmpty()) {
                log.warn(
                    "Could not resolve price for product {}",
                    this.potentialFlipProduct.getProduct()
                );
                this.clearPendingFlipState();
                return;
            }

            var formatted = Utils.formatDecimal(flipPrice.get(), 1, false);
            GameUtils.submitSignValue(signEditScreen, formatted);

            this.pendingFlips.add(new FlipEntry(
                potentialFlipProduct.getProduct(),
                flipPrice.get()
            ));

            this.clearPendingFlipState();
        });
    }

    public void handleFlipped(BazaarMessage.OrderFlipped flipped) {
        var flippedProduct = this.bazaarData.resolveIndexedProduct(
            this.bazaarData.resolveProductName(flipped.productName())
        );
        var match = this.pendingFlips.removeFirstMatch(entry -> entry
            .product()
            .productId()
            .equals(flippedProduct.map(IndexedProduct::productId).orElse(""))
            || entry.product().strippedName().equalsIgnoreCase(flipped.productName()));

        // this may be unnecessary as after entering the price in the sign, it opens the orders
        // menu, might as well leave it atm
        if (match.isEmpty()) {
            log.warn(
                "No matching pending flip for flipped order {}x {}. Orders may be out of sync",
                flipped.volume(),
                flipped.productName()
            );
            Notifier.notifyChatCommand(
                "No matching pending flip found for flipped order. Click to resync tracked orders",
                "managebazaarorders"
            );
            return;
        }

        var entry = match.get();
        double pricePerUnit = entry.pricePerUnit();

        var orderInfo = new OrderInfo.UnfilledOrderInfo(
            ProductIdentity.fromIndex(entry.product()),
            flipped.productName(),
            OrderType.Sell,
            flipped.volume(),
            pricePerUnit,
            0,
            0,
            -1
        );
        BtrBz.orderManager().addTrackedOrder(new TrackedOrder(orderInfo, ProductIdentity.fromIndex(entry.product())));

        log.debug(
            "Added tracked Sell order from flipped chat: {}x {} at {} per unit",
            flipped.volume(),
            entry.product(),
            Utils.formatDecimal(pricePerUnit, 1, true)
        );
    }

    private void clearPendingFlipState() {
        if (this.potentialFlipProduct != null) {
            log.debug(
                "Destroying `potentialFlipProduct` {}",
                this.potentialFlipProduct.getProduct()
            );
            this.potentialFlipProduct.destroy();
        }
        this.cachedHelperDisplay = null;
        this.potentialFlipProduct = null;
        this.pendingFlip = false;
    }

    public final class OrderFlipHook implements SlotHook {

        private OrderFlipHook() { }

        @Override
        public boolean matches(SlotView view) {
            return ConfigManager.get().flipHelper.enabled
                && !view.playerInventorySlot()
                && view.slotIdx() == CUSTOM_HELPER_ITEM_SLOT_IDX
                && view.getCurrInfo().inMenu(BazaarMenuType.OrderOptions)
                && FlipHelper.this.potentialFlipProduct != null;
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

            if (FlipHelper.this.potentialFlipProduct == null || FlipHelper.this.potentialFlipProduct
                .getSellOfferPrice()
                .isEmpty()) {

                log.debug(
                    "Ignoring flip execution click because it's price could not be resolved: '{}'",
                    FlipHelper.this.potentialFlipProduct == null ? "no product selected" : "price not available"
                );
                return SlotClickResult.Pass;
            }

            interactionManager.handleContainerInput(
                handler.containerId,
                FLIP_ORDER_ITEM_SLOT_IDX,
                ctx.button(),
                ContainerInput.PICKUP,
                player
            );
            FlipHelper.this.pendingFlip = true;
            return SlotClickResult.Consume;
        }
    }

    public final class OrderProductObserverHook implements SlotHook {

        private OrderProductObserverHook() { }

        @Override
        public boolean matches(SlotView view) {
            return ConfigManager.get().flipHelper.enabled
                && view.getCurrInfo().inMenu(BazaarMenuType.Orders)
                && !view.playerInventorySlot();
        }

        @Override
        public SlotClickResult onClick(SlotClickContext ctx) {
            var orderInfo = OrderInfoParser.parseOrderInfo(
                ctx.view().getRawStack(),
                ctx.view().slotIdx(),
                FlipHelper.this.bazaarData
            );
            if (orderInfo.isSuccess()) {
                FlipHelper.this.onOrderClick(orderInfo.get());
            }

            return SlotClickResult.Pass;
        }
    }

    private record CachedHelperDisplay(String productName, double displayPrice, ItemStack display) { }

    private record FlipEntry(IndexedProduct product, double pricePerUnit) { }

    public static class FlipHelperConfig {

        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Flip Helper"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Component.literal(
                    "Add a quick-flip action to filled buy orders and suggest a sell-offer price 0.1 coins below the current lowest offer.")))
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
                        "If the best sell offer is 1,000 coins, the suggested price is 999.9 coins.")
                ),
                    ConfigScreen.ConfigImage.FLIP_HELPER
                ))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }
}
