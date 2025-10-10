package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.TrackedProduct;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.data.OrderModels.ChatFlippedOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.TimedStore;
import com.github.lutzluca.btrbz.mixin.AbstractSignEditScreenAccessor;
import com.github.lutzluca.btrbz.utils.ItemOverrideManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenActionManager;
import com.github.lutzluca.btrbz.utils.ScreenActionManager.ScreenClickRule;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
import io.vavr.control.Try;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;


@Slf4j
public class FlipHelper {

    private static final int flipOrderItemSlotIdx = 15;
    private static final int customHelperItemSlotIdx = 16;

    private final TimedStore<FlipEntry> pendingFlips = new TimedStore<>(15_000L);
    private final BazaarData bazaarData;

    private TrackedProduct potentialFlipProduct = null;
    private boolean pendingFlip = false;

    public FlipHelper(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        this.registerPotentialFlipOrderSelectionListener();
        this.registerFlipHelperItemOverride();
        this.registerFlipExecutionTrigger();
        this.registerFlipPriceScreenHandler();
    }

    private void registerPotentialFlipOrderSelectionListener() {
        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                var player = MinecraftClient.getInstance().player;
                if (player != null && slot.inventory == player.getInventory()) {
                    return false;
                }

                return button == 1 && info.inMenu(ScreenInfoHelper.BazaarMenuType.Orders);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var itemStack = slot.getStack();

                var orderTitleInfo = parseOrderTitle(itemStack);
                if (orderTitleInfo.isEmpty() || orderTitleInfo.get().type != OrderType.Buy) {
                    clearPendingFlipState();
                    return false;
                }

                if (!isFilled(itemStack)) {
                    clearPendingFlipState();
                    return false;
                }

                if (potentialFlipProduct != null) {
                    potentialFlipProduct.destroy();
                }

                var name = orderTitleInfo.get().productName();
                potentialFlipProduct = new TrackedProduct(bazaarData, name);
                log.debug("Set `potentialFlipProduct` for product: '{}'", name);
                return false;
            }
        });
    }

    private void registerFlipHelperItemOverride() {
        ItemOverrideManager.register((info, slot, original) -> {
            if (!info.inMenu(ScreenInfoHelper.BazaarMenuType.OrderOptions)) {
                return Optional.empty();
            }

            if (slot == null || slot.getIndex() != customHelperItemSlotIdx || this.potentialFlipProduct == null) {
                return Optional.empty();
            }
            return this.potentialFlipProduct.getSellOfferPrice().map(price -> {
                var formatted = Util.formatDecimal(Math.max(price - 0.1, .1), 1, true);

                var customHelperItem = new ItemStack(Items.NETHER_STAR);
                customHelperItem.set(
                    DataComponentTypes.CUSTOM_NAME,
                    Text.literal(formatted).formatted(Formatting.DARK_PURPLE)
                );
                return customHelperItem;
            });
        });
    }

    private void registerFlipExecutionTrigger() {
        ScreenActionManager.register(new ScreenClickRule() {
            @Override
            public boolean applies(ScreenInfo info, Slot slot, int button) {
                return slot != null && slot.getIndex() == customHelperItemSlotIdx && info.inMenu(
                    BazaarMenuType.OrderOptions);
            }

            @Override
            public boolean onClick(ScreenInfo info, Slot slot, int button) {
                var client = MinecraftClient.getInstance();
                if (client == null) {
                    return false;
                }

                var gcsOpt = info.getGenericContainerScreen();
                if (gcsOpt.isEmpty()) {
                    return false;
                }

                var handler = gcsOpt.get().getScreenHandler();
                var player = client.player;
                var interactionManager = client.interactionManager;

                if (player == null || interactionManager == null) {
                    return false;
                }

                if (potentialFlipProduct == null || potentialFlipProduct
                    .getSellOfferPrice()
                    .isEmpty()) {

                    log.debug(
                        "Ignoring flip execution click because it's price could not be resolved: '{}'",
                        potentialFlipProduct == null ? "no product selected" : "price not available"
                    );
                    return false;
                }

                Try
                    .run(() -> interactionManager.clickSlot(
                        handler.syncId,
                        flipOrderItemSlotIdx,
                        button,
                        SlotActionType.PICKUP,
                        player
                    ))
                    .onFailure(err -> log.warn("Failed to 'click' flip order", err))
                    .onSuccess(v -> pendingFlip = true);

                return false;
            }
        });
    }

    private void registerFlipPriceScreenHandler() {
        ScreenInfoHelper.registerOnSwitch(curr -> {
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
                    "Could not resolve price for product '{}'",
                    this.potentialFlipProduct.getProductName()
                );
                this.clearPendingFlipState();
                return;
            }

            var formatted = Util.formatDecimal(flipPrice.get(), 1, false);
            var accessor = (AbstractSignEditScreenAccessor) signEditScreen;
            accessor.setCurrentRow(0);
            accessor.invokeSetCurrentRowMessage(formatted);

            Try
                .run(() -> {
                    signEditScreen.close();
                    this.pendingFlips.add(new FlipEntry(
                        potentialFlipProduct.getProductName(),
                        flipPrice.get()
                    ));
                })
                .onFailure(err -> log.warn("Failed to finalize sign edit", err))
                .onSuccess(v -> log.debug("Successfully edited price sign to flip item"));

            this.clearPendingFlipState();
        });
    }

    public void handleFlipped(ChatFlippedOrderInfo flipped) {
        var match = this.pendingFlips.removeFirstMatch(entry -> entry
            .productName()
            .equalsIgnoreCase(flipped.productName()));

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

        var orderInfo = new OrderInfo(
            flipped.productName(),
            OrderType.Sell,
            flipped.volume(),
            pricePerUnit,
            false,
            -1
        );

        BtrBz.orderManager().addTrackedOrder(new TrackedOrder(orderInfo, -1));

        log.debug(
            "Added tracked Sell order from flipped chat: {}x {} at {} per unit",
            flipped.volume(),
            flipped.productName(),
            Util.formatDecimal(pricePerUnit, 1, true)
        );
    }


    // TODO: move this into the `OrderInfoParser` sometime
    private Optional<TitleOrderInfo> parseOrderTitle(ItemStack stack) {
        if (stack == null || stack.isEmpty() || Util.orderScreenNonOrderItem.contains(stack.getItem())) {
            return Optional.empty();
        }

        var title = stack.getName().getString();
        var parts = title.split(" ", 2);
        if (parts.length != 2) {
            log.warn("Item title does not follow '<type> <productName>': '{}'", title);
            return Optional.empty();
        }

        return OrderType
            .tryFrom(parts[0].trim())
            .onFailure(err -> log.warn("Failed to parse Order type from '{}'", parts[0], err))
            .toJavaOptional()
            .map(type -> new TitleOrderInfo(type, parts[1].trim()));
    }

    private boolean isFilled(ItemStack stack) {
        return OrderInfoParser
            .getLore(stack)
            .stream()
            .filter(line -> line.trim().startsWith("Filled"))
            .findFirst()
            .map(line -> line.contains("100%"))
            .orElse(false);
    }


    private void clearPendingFlipState() {
        if (this.potentialFlipProduct != null) {
            log.debug(
                "Destroying `potentialFlipProduct` '{}'",
                this.potentialFlipProduct.getProductName()
            );
            this.potentialFlipProduct.destroy();
        }
        this.potentialFlipProduct = null;
        this.pendingFlip = false;
    }

    private record FlipEntry(String productName, double pricePerUnit) { }

    private record TitleOrderInfo(OrderType type, String productName) { }
}
