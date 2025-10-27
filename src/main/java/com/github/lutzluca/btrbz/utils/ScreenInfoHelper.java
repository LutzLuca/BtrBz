package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.mixin.HandledScreenAccessor;
import com.github.lutzluca.btrbz.utils.ScreenInventoryTracker.Inventory;
import io.vavr.control.Try;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class ScreenInfoHelper {

    private static final ScreenInfoHelper INSTANCE = new ScreenInfoHelper();
    @Getter
    final ScreenInventoryTracker inventoryWatcher = new ScreenInventoryTracker();
    private final List<Consumer<ScreenInfo>> switchListeners = new CopyOnWriteArrayList<>();
    private final List<ScreenLoadListenerEntry> screenLoadListenerEntries = new CopyOnWriteArrayList<>();
    private final List<ScreenCloseListenerEntry> screenCloseListenerEntries = new CopyOnWriteArrayList<>();
    @Getter
    private volatile ScreenInfo currInfo = new ScreenInfo(null);
    @Getter
    private volatile ScreenInfo prevInfo = new ScreenInfo(null);

    private ScreenInfoHelper() {
        this.setupInventoryWatcher();
    }

    public static ScreenInfoHelper get() {
        return INSTANCE;
    }

    public static boolean inMenu(BazaarMenuType... menu) {
        return INSTANCE.currInfo.inMenu(menu);
    }

    public static boolean inBazaar() {
        return INSTANCE.currInfo.inBazaar();
    }

    public static void registerOnSwitch(Consumer<ScreenInfo> listener) {
        INSTANCE.switchListeners.add(listener);
    }

    public static void registerOnLoaded(
        Predicate<ScreenInfo> matcher,
        BiConsumer<ScreenInfo, Inventory> listener
    ) {
        var info = new ScreenLoadListenerEntry(matcher, listener);
        INSTANCE.screenLoadListenerEntries.add(info);
    }

    public static void registerOnClose(
        Predicate<ScreenInfo> matcher,
        Consumer<ScreenInfo> listener
    ) {
        var info = new ScreenCloseListenerEntry(matcher, listener);
        INSTANCE.screenCloseListenerEntries.add(info);
    }

    private void setupInventoryWatcher() {
        this.inventoryWatcher.setOnLoaded(inventory -> {
            this.currInfo.markInventoryLoaded();

            this.screenLoadListenerEntries
                .stream()
                .filter(entry -> entry.matcher.test(this.currInfo))
                .forEach(entry -> entry.listener.accept(this.currInfo, inventory));
        });

        this.inventoryWatcher.setOnClose(title -> {
            log.trace("Inventory closed: '{}'", title);

            this.screenCloseListenerEntries
                .stream()
                .filter(entry -> entry.matcher.test(this.prevInfo))
                .forEach(entry -> entry.listener.accept(this.prevInfo));
        });
    }

    public void setScreen(Screen screen) {
        var next = this.prevInfo;
        next.setScreen(screen);

        this.prevInfo = this.currInfo;
        this.currInfo = next;
    }

    public void fireScreenSwitchCallbacks() {
        this.switchListeners.forEach(listener -> listener.accept(this.currInfo));
    }

    private enum BazaarCategory {
        Farming, Mining, Combat, WoodsAndFishes, Oddities;

        private static Try<BazaarCategory> tryFrom(String value) {
            return switch (value) {
                case "Farming" -> Try.success(BazaarCategory.Farming);
                case "Mining" -> Try.success(BazaarCategory.Mining);
                case "Combat" -> Try.success(BazaarCategory.Combat);
                case "Woods & Fishes" -> Try.success(BazaarCategory.WoodsAndFishes);
                case "Oddities" -> Try.success(BazaarCategory.Oddities);
                default -> Try.failure(new IllegalArgumentException("Unknown category: " + value));
            };
        }
    }

    public enum BazaarMenuType {
        Main, // Bazaar ➜ <category> / "<search>"
        Orders, // Your Bazaar Orders
        InstaBuy, // <product name> ➜ Instant Buy
        BuyOrderSetup, // How much do you want to pay?
        BuyOrderConfirmation, // Confirm Buy Order
        SellOfferSetup, // At what price are you selling?
        SellOfferConfirmation, // Confirm Sell Offer
        Item, // <group> ➜ <product name> | product name from title & fallback to inventory idx 34
        // -> "View Graphs" (paper)
        ItemGroup, // 'Optional: (page / max page)' <category / subcategory> ➜ <group>
        InstaSellIgnoreList, // Instasell Ignore List
        InventorySellConfirmation, // Are you sure?
        OrderOptions, // Order options
        Graphs, // <product name> ➜ Graphs
        Settings; // Bazaar ➜ Settings

        public static final BazaarMenuType[] VALUES = BazaarMenuType.values();


        // Note: Checks for Item and ItemGroup rely on slot checks, which are only valid
        // after the UI has been populated. Calling Item/ItemGroup.matches(info)
        // before the UI is populated, for example after `setScreen` has been called on the
        // MinecraftClient (-> ScreenInfoHelper.onSwitch), they will return false even if
        // you're technically on the correct screen.
        public boolean matches(@NotNull ScreenInfo info) {
            var titleOpt = info.containerName();
            if (titleOpt.isEmpty()) {
                return false;
            }

            var title = titleOpt.get();
            return switch (this) {
                case Main -> {
                    if (!title.startsWith("Bazaar ➜ ")) {
                        yield false;
                    }
                    var str = title.substring("Bazaar ➜ ".length()).trim();
                    yield BazaarCategory
                        .tryFrom(str.trim())
                        .isSuccess() || (str.startsWith("\"") && str.endsWith("\""));
                }
                case Orders -> title.equals("Your Bazaar Orders");
                case InstaBuy -> title.endsWith("➜ Instant Buy");
                case BuyOrderSetup -> title.equals("How much do you want to pay?");
                case BuyOrderConfirmation -> title.equals("Confirm Buy Order");
                case SellOfferSetup -> title.equals("At what price are you selling?");
                case SellOfferConfirmation -> title.equals("Confirm Sell Offer");
                case Item -> {
                    var parts = title.split("➜", 2);
                    if (parts.length != 2) {
                        yield false;
                    }

                    yield info.getGenericContainerScreen().map((gcs) -> {
                        final int GRAPH_PAPER_IDX = 33;
                        var handler = gcs.getScreenHandler();
                        var inventory = handler.getInventory();

                        if (inventory.size() < GRAPH_PAPER_IDX) {
                            return false;
                        }

                        var slot = inventory.getStack(GRAPH_PAPER_IDX);
                        return slot.getItem().equals(Items.PAPER) && slot
                            .getName()
                            .getString()
                            .equals("View Graphs");
                    }).orElse(false);
                }
                case ItemGroup -> {
                    if (!title.contains("➜") || title.endsWith("Graphs") || title.endsWith(
                        "Settings")) {
                        yield false;
                    }

                    yield info.getGenericContainerScreen().map(gcs -> {
                        var handler = gcs.getScreenHandler();
                        var inventory = handler.getInventory();
                        var slot = inventory.size() - 3;

                        return Try
                            .of(() -> inventory.getStack(slot).getItem())
                            .map((item) -> item.equals(Items.CAULDRON) || item.equals(Items.BLACK_STAINED_GLASS_PANE))
                            .getOrElse(false);
                    }).orElse(false);
                }
                case InstaSellIgnoreList -> title.equals("Instasell Ignore List");
                case InventorySellConfirmation -> title.equals("Are you sure?");
                case OrderOptions -> title.equals("Order options");
                case Graphs -> title.endsWith("➜ Graphs");
                case Settings -> title.equals("Bazaar ➜ Settings");
            };
        }
    }

    public static class ScreenInfo {

        private final MenuState state = new MenuState();
        @Getter
        private @Nullable Screen screen;
        private @Nullable GenericContainerScreen containerScreen;

        public ScreenInfo(@Nullable Screen screen) {
            this.setScreen(screen);
        }

        public void setScreen(Screen screen) {
            if (this.screen == screen) {
                return;
            }

            this.resetMenuMatchState();
            this.screen = screen;
            this.containerScreen = (screen instanceof GenericContainerScreen gcs) ? gcs : null;
        }

        public boolean inBazaar() {
            return this.inMenu(BazaarMenuType.VALUES);
        }

        public boolean inMenu(BazaarMenuType... menu) {
            return Arrays.stream(menu).anyMatch((menuType) -> this.state.matches(this, menuType));
        }

        public Optional<GenericContainerScreen> getGenericContainerScreen() {
            return Optional.ofNullable(this.containerScreen);
        }

        public Optional<String> containerName() {
            return Optional.ofNullable(this.screen).map(Screen::getTitle).map(Text::getString);
        }

        public Optional<HandledScreenBounds> getHandledScreenBounds() {
            if (!(this.screen instanceof HandledScreenAccessor accessor)) {
                return Optional.empty();
            }

            return Optional.of(new HandledScreenBounds(
                accessor.getX(),
                accessor.getY(),
                accessor.getBackgroundWidth(),
                accessor.getBackgroundHeight()
            ));
        }

        private void markInventoryLoaded() {
            this.state.inventoryLoaded = true;
        }

        private void resetMenuMatchState() {
            this.state.reset();
        }
    }

    private record ScreenLoadListenerEntry(
        Predicate<ScreenInfo> matcher,
        BiConsumer<ScreenInfo, ScreenInventoryTracker.Inventory> listener
    ) { }

    private record ScreenCloseListenerEntry(
        Predicate<ScreenInfo> matcher, Consumer<ScreenInfo> listener
    ) { }

    public record HandledScreenBounds(int x, int y, int width, int height) { }

    private static final class MenuState {

        private int verifiedMenu = 0;
        private int verifiedNotMenu = 0;
        private boolean inventoryLoaded = false;

        public void reset() {
            this.verifiedMenu = 0;
            this.verifiedNotMenu = 0;
            this.inventoryLoaded = false;
        }

        public boolean matches(ScreenInfo info, BazaarMenuType type) {
            int typeBit = 1 << type.ordinal();
            if ((this.verifiedMenu & typeBit) != 0) {
                return true;
            }

            if (this.verifiedMenu != 0) {
                return false;
            }

            if ((this.verifiedNotMenu & typeBit) != 0) {
                return false;
            }

            if ((type == BazaarMenuType.Item || type == BazaarMenuType.ItemGroup) && !this.inventoryLoaded) {
                return false;
            }

            boolean matches = type.matches(info);
            if (matches) {
                this.verifiedMenu |= typeBit;
                log.debug("Matched menu: {}", type);
            } else {
                this.verifiedNotMenu |= typeBit;
            }
            return matches;
        }
    }
}
