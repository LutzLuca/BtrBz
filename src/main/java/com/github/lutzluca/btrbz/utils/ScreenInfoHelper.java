package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.mixin.HandledScreenAccessor;
import com.github.lutzluca.btrbz.utils.InventoryLoadWatcher.SlotSnapshot;
import io.vavr.control.Try;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
    private final List<Consumer<ScreenInfo>> switchListeners = new CopyOnWriteArrayList<>();
    private final List<ScreenLoadListenerEntry> screenLoadListenerEntries = new CopyOnWriteArrayList<>();

    @Getter
    private volatile ScreenInfo currInfo = new ScreenInfo(null);
    @Getter
    private volatile ScreenInfo prevInfo = new ScreenInfo(null);

    private ScreenInfoHelper() { }

    public static ScreenInfoHelper get() {
        return INSTANCE;
    }

    public static boolean inMenu(BazaarMenuType... menu) {
        return INSTANCE.currInfo.inMenu(menu);
    }

    public static Runnable registerOnSwitch(Consumer<ScreenInfo> listener) {
        INSTANCE.switchListeners.add(listener);
        return () -> INSTANCE.switchListeners.remove(listener);
    }

    public static Runnable registerOnLoaded(
        Predicate<ScreenInfo> matcher,
        BiConsumer<ScreenInfo, List<SlotSnapshot>> listener
    ) {
        var info = new ScreenLoadListenerEntry(matcher, listener);
        INSTANCE.screenLoadListenerEntries.add(info);
        return () -> INSTANCE.screenLoadListenerEntries.remove(info);
    }

    public void setScreen(Screen screen) {
        var info = new ScreenInfo(screen);
        if (this.currInfo.equals(info)) {
            return;
        }
        this.prevInfo = this.currInfo;
        this.currInfo = info;

        // @formatter:off
        info.getGenericContainerScreen().ifPresent(gcs -> {
            var matchingLoadListenerEntries = this.screenLoadListenerEntries
                .stream()
                .filter(listener -> listener.matcher.test(info))
                .toList();

            if (matchingLoadListenerEntries.isEmpty()) {
                return;
            }

            new InventoryLoadWatcher(
                gcs,
                slots -> matchingLoadListenerEntries.forEach(onLoadedInfo ->
                    onLoadedInfo.listener.accept(info, slots)
                )
            );
        });
        // @formatter:on
    }

    public void fireScreenSwitchCallbacks() {
        this.switchListeners.forEach(listener -> listener.accept(this.currInfo));
    }

    private enum BazaarCategory {
        Farming, // Farming
        Mining, // Mining
        Combat, // Combat
        WoodsAndFishes, // Woods & Fishes
        Oddities; // Oddities

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
        Main, // Bazaar ➜ <category>
        Orders, // Your Bazaar Orders
        InstaBuy, // <product name> ➜ Instant Buy
        BuyOrderSetup, // How much do you want to pay?
        BuyOrderConfirmation, // Confirm Buy Order
        SellOfferSetup, // At what price are you selling?
        SellOfferConfirmation, // Confirm Sell Offer
        Item, // <group> ➜ <product name> | use chest idx 34 -> "View Graphs" (paper)
        ItemGroup, // 'Optional: (page / max page)' <category / subcategory> ➜ <group>
        InstaSellIgnoreList, // Instasell Ignore List
        InventorySellConfirmation, // Are you sure?
        OrderOptions, // Order options
        Graphs, // <product name> ➜ Graphs
        Settings; // Bazaar ➜ Settings


        // Note: Checks for Item and ItemGroup rely on slot checks, which are only valid
        // after the UI has been populated. Calling Item/ItemGroup.matches(info)
        // before the UI is populated, for example after `setScreen` has been called on the
        // MinecraftClient (-> ScreenInfoHelper.onSwitch), they will return false even if
        // you're technically on the correct screen.
        public boolean matches(@NotNull ScreenInfo info) {
            if (info.getScreen() == null || info.containerName().isEmpty()) {
                return false;
            }

            var title = info.containerName().get();

            return switch (this) {
                case Main -> {
                    if (!title.startsWith("Bazaar ➜ ")) {
                        yield false;
                    }
                    var category = title.substring("Bazaar ➜ ".length());
                    yield BazaarCategory.tryFrom(category.trim()).isSuccess();
                }
                case Orders -> title.equals("Your Bazaar Orders");
                case InstaBuy -> title.endsWith("➜ Instant Buy");
                case BuyOrderSetup -> title.equals("How much do you want to pay?");
                case BuyOrderConfirmation -> title.equals("Confirm Buy Order");
                case SellOfferSetup -> title.equals("At what price are you selling?");
                case SellOfferConfirmation -> title.equals("Confirm Sell Offer");
                case Item -> info.getGenericContainerScreen().map((gcs) -> {
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
                case ItemGroup -> {
                    if (!title.contains("➜")) {
                        yield false;
                    }

                    yield info.getGenericContainerScreen().map(gcs -> {
                        var handler = gcs.getScreenHandler();
                        var inventory = handler.getInventory();
                        var slot = inventory.size() - 3;

                        return Try
                            .of(() -> inventory.getStack(slot))
                            .map((itemStack) -> itemStack
                                .getItem()
                                .equals(Items.CAULDRON) || itemStack
                                .getItem()
                                .equals(Items.BLACK_STAINED_GLASS_PANE))
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

        @Getter
        private final @Nullable Screen screen;

        private final @Nullable GenericContainerScreen containerScreen;

        public ScreenInfo(@Nullable Screen screen) {
            this.screen = screen;
            this.containerScreen = (screen instanceof GenericContainerScreen gcs) ? gcs : null;
        }

        public Optional<GenericContainerScreen> getGenericContainerScreen() {
            return Optional.ofNullable(this.containerScreen);
        }

        public Optional<String> containerName() {
            return Optional.ofNullable(this.screen).map(Screen::getTitle).map(Text::getString);
        }

        public boolean inMenu(BazaarMenuType... menu) {
            return Arrays.stream(menu).anyMatch((menuType) -> menuType.matches(this));
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


        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof ScreenInfo other)) {
                return false;
            }

            return Objects.equals(this.screen, other.screen);
        }
    }

    private record ScreenLoadListenerEntry(
        Predicate<ScreenInfo> matcher, BiConsumer<ScreenInfo, List<SlotSnapshot>> listener
    ) { }

    public record HandledScreenBounds(int x, int y, int width, int height) { }
}
