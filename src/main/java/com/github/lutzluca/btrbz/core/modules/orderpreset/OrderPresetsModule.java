package com.github.lutzluca.btrbz.core.modules.orderpreset;

import com.github.lutzluca.btrbz.core.modules.Module;
import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;

import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.ListWidget;
import com.github.lutzluca.btrbz.widgets.Renderable;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class OrderPresetsModule extends Module<OrderPresetsConfig> {


    private ListWidget list;
    private int currMaxVolume = GameUtils.GLOBAL_MAX_ORDER_VOLUME;

    // Tracks the buy order transaction flow (volume → price → confirmation)
    private int pendingVolume = -1;
    private boolean pendingPreset = false;
    private boolean inTransaction = false;
    private final BazaarData bazaarData;
    private final ProductInfoProvider productInfoProvider;

    public OrderPresetsModule(BazaarData bazaarData, ProductInfoProvider productInfoProvider) {
        this.bazaarData = bazaarData;
        this.productInfoProvider = productInfoProvider;
    }

    @Override
    public void onLoad() {
        ScreenInfoHelper.registerOnSwitch(curr -> {
            if (!this.configState.enabled) {
                return;
            }
            var prev = ScreenInfoHelper.get().getPrevInfo();

            if (curr.inMenu(BazaarMenuType.BuyOrderSetupVolume) && prev.inMenu(BazaarMenuType.Item)) {
                this.currMaxVolume = curr
                    .getItemStack(16)
                    .flatMap(this::getMaxVolume)
                    .orElse(GameUtils.GLOBAL_MAX_ORDER_VOLUME);

                this.inTransaction = true;
                log.debug(
                    "Starting buy order transaction for product {} with maxVolume '{}'",
                    Optional
                        .ofNullable(this.getCurrentProduct())
                        .map(Object::toString)
                        .orElse("<unknown>"),
                    this.currMaxVolume
                );

                this.rebuildList();
                return;
            }

            if (this.inTransaction && prev.getScreen() instanceof SignEditScreen && curr.getScreen() == null) {
                return;
            }

            var isOrderFlowMenu = curr.inMenu(
                BazaarMenuType.BuyOrderSetupVolume,
                BazaarMenuType.BuyOrderSetupPrice,
                BazaarMenuType.BuyOrderConfirmation
            );

            var isOrderFlowSignScreen = this.isOrderFlowSignScreen(curr, prev);

            if (this.inTransaction && (!isOrderFlowMenu && !isOrderFlowSignScreen)) {
                log.debug(
                    "Canceling buy order transaction: prev={}, curr={}",
                    prev.getMenuType(),
                    curr.getMenuType()
                );

                this.cancelTransaction();
            }
        });

        ScreenInfoHelper.registerOnSwitch(info -> {
            if (!this.configState.enabled) {
                return;
            }

            var prev = ScreenInfoHelper.get().getPrevInfo();
            if (!prev.inMenu(BazaarMenuType.BuyOrderSetupVolume) || !(info.getScreen() instanceof SignEditScreen signEditScreen)) {
                return;
            }
            if (!this.pendingPreset || this.pendingVolume <= 0) {
                return;
            }

            GameUtils.submitSignValue(signEditScreen, String.valueOf(this.pendingVolume));

            this.pendingVolume = -1;
            this.pendingPreset = false;
        });

        ScreenInfoHelper.registerOnLoaded(
            info -> info.inMenu(BazaarMenuType.BuyOrderSetupVolume), (info, inventory) -> {
                if (!this.configState.enabled) {
                    return;
                }

                if (ScreenInfoHelper
                    .get()
                    .getPrevInfo()
                    .inMenu(BazaarMenuType.BuyOrderSetupPrice)) {
                    return;
                }

                inventory.getItem(16).flatMap(this::getMaxVolume).ifPresent(maxVolume -> {
                    if (this.currMaxVolume != maxVolume) {
                        this.currMaxVolume = maxVolume;
                        this.rebuildList();
                    }
                });
            }
        );
    }

    public void cancelTransaction() {
        log.debug("Ending buy order transaction");
        this.inTransaction = false;

        this.pendingVolume = -1;
        this.pendingPreset = false;

        this.currMaxVolume = GameUtils.GLOBAL_MAX_ORDER_VOLUME;
    }

    private @Nullable IndexedProduct getCurrentProduct() {
        return this.productInfoProvider.getOpenedProduct();
    }

    private boolean isOrderFlowSignScreen(ScreenInfo curr, ScreenInfo prev) {
        return curr.getScreen() instanceof SignEditScreen && prev.inMenu(
            BazaarMenuType.BuyOrderSetupVolume,
            BazaarMenuType.BuyOrderSetupPrice
        );
    }

    public void rebuildList() {
        if (this.list == null) {
            return;
        }

        List<Renderable> entries = this.currentPresets()
            .stream()
            .map(this::createPresetEntry)
            .map(Renderable.class::cast)
            .toList();

        this.list.setItems(entries);
    }

    public List<PresetDescription> currentPresets() {
        var purse = GameUtils.getPurse();
        var pricePerUnit = Optional
            .ofNullable(this.getCurrentProduct())
            .map(ProductIdentity::fromIndex)
            .flatMap(this.bazaarData::highestBuyOrderPrice)
            .map(price -> price + .1);

        log.debug(
            "Rebuilding Order Preset list: maxVolume={}, pricePerUnit={}, purse={}",
            this.currMaxVolume,
            pricePerUnit,
            purse
        );

        OptionalInt clipboardVolume = OptionalInt.empty();
        var clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (!clipboard.isBlank()) {
            clipboardVolume = Utils
                .parseUsFormattedNumber(clipboard)
                .map(Number::intValue)
                .filter(volume -> volume > 0 && volume <= this.currMaxVolume)
                .map(OptionalInt::of)
                .getOrElse(OptionalInt.empty());
        }

        return describePresets(
            this.configState.presets,
            this.currMaxVolume,
            clipboardVolume,
            pricePerUnit,
            purse,
            this.configState.hideUnaffordablePresets
        );
    }

    public static List<PresetDescription> describePresets(
        List<Integer> configuredVolumes,
        int maxVolume,
        OptionalInt clipboardVolume,
        Optional<Double> pricePerUnit,
        Optional<Double> purse,
        boolean hideUnaffordable
    ) {
        List<OrderPreset> presets = configuredVolumes
            .stream()
            .filter(volume -> volume <= maxVolume)
            .sorted()
            .map(volume -> (OrderPreset) new OrderPreset.Volume(volume))
            .collect(Collectors.toCollection(ArrayList::new));
        
        presets.addFirst(new OrderPreset.Max());
        if (clipboardVolume.isPresent()) {
            presets.add(1, new OrderPreset.Clipboard(clipboardVolume.getAsInt()));
        }

        return presets.stream()
            .map(preset -> 
            	switch (preset) {
            	    case OrderPreset.Max _ -> describeMaxPreset(preset, maxVolume, pricePerUnit, purse);
            	    case OrderPreset.Clipboard(int clipboardAmount) -> describeAmountPreset(preset, clipboardAmount, pricePerUnit, purse);
            	    case OrderPreset.Volume(int presetAmount) -> describeAmountPreset(preset, presetAmount, pricePerUnit, purse);
            	}
            )
            .filter(description -> !hideUnaffordable
                || description.unavailableReason() != PresetUnavailableReason.INSUFFICIENT_COINS)
            .toList();
    }

    private static PresetDescription describeAmountPreset(
        OrderPreset preset,
        int amount,
        Optional<Double> pricePerUnit,
        Optional<Double> purse
    ) {
        if (pricePerUnit.isEmpty()) {
            return PresetDescription.available(preset, amount);
        }
        if (purse.isEmpty()) {
            return PresetDescription.unavailable(preset, PresetUnavailableReason.PURSE_UNAVAILABLE, 0.0);
        }
        if (amount * pricePerUnit.get() > purse.get()) {
            return PresetDescription.unavailable(preset, PresetUnavailableReason.INSUFFICIENT_COINS, 0.0);
        }

        return PresetDescription.available(preset, amount);
    }

    private static PresetDescription describeMaxPreset(
        OrderPreset preset,
        int maxVolume,
        Optional<Double> pricePerUnit,
        Optional<Double> purse
    ) {
        if (pricePerUnit.isEmpty()) {
            return PresetDescription.unavailable(preset, PresetUnavailableReason.PRICE_UNAVAILABLE, 0.0);
        }
        if (purse.isEmpty()) {
            return PresetDescription.unavailable(preset, PresetUnavailableReason.PURSE_UNAVAILABLE, 0.0);
        }

        int maxVol = calculateMaxVolume(purse.get(), pricePerUnit.get(), maxVolume);
        if (maxVol == 0) {
            return PresetDescription.unavailable(
                preset,
                PresetUnavailableReason.INSUFFICIENT_COINS,
                pricePerUnit.get() - purse.get()
            );
        }
        return PresetDescription.available(preset, maxVol);
    }

    private OrderPreset.RenderableEntry createPresetEntry(PresetDescription description) {
        var entry = new OrderPreset.RenderableEntry(description.preset());
        entry.setDisabled(!description.canApply());
        
        switch (description.unavailableReason()) {
            case NONE -> {
                if (description.preset() instanceof OrderPreset.Max) {
                    entry.setTooltipLines(List.of(Component.literal(
                        Utils.formatDecimal(description.resolvedVolume().orElseThrow(), 0, true) + " items"
                    )));
                } else if (description.preset() instanceof OrderPreset.Clipboard) {
                    entry.setTooltipLines(List.of(Component.literal("From Clipboard")));
                }
            }
            case PRICE_UNAVAILABLE -> entry.setTooltipLines(List.of(Component.literal(
                "Unable to determine price information"
            )));
            case PURSE_UNAVAILABLE -> entry.setTooltipLines(List.of(Component.literal(
                "Unable to determine purse amount"
            )));
            case INSUFFICIENT_COINS -> {
                if (description.preset() instanceof OrderPreset.Max) {
                    String formattedMissing = Utils.formatCompact(description.missingCoins(), 1);
                    entry.setTooltipLines(List.of(
                        Component.literal("Missing " + formattedMissing + " coins"),
                        Component.literal("to buy one item")
                    ));
                } else {
                    entry.setTooltipLines(List.of(Component.literal("Insufficient coins")));
                }
            }
        }
        return entry;
    }

    private Optional<Integer> getMaxVolume(@NotNull ItemStack item) {
        return GameUtils
            .getLore(item)
            .stream()
            .filter(line -> line.startsWith("Buy up to"))
            .findFirst()
            .map(line -> line.replaceFirst("Buy up to", "").replaceAll("x+", ""))
            .flatMap(volume -> Utils
                .parseUsFormattedNumber(volume)
                .toJavaOptional()
                .map(Number::intValue));
    }

    public enum PresetScreen {
        VolumeSetupContainer,
        EnterVolumeSign
    }

    private Optional<PresetScreen> getPresetScreen(ScreenInfo info) {
        if (info.inMenu(BazaarMenuType.BuyOrderSetupVolume)) {
            return Optional.of(PresetScreen.VolumeSetupContainer);
        }

        var prev = ScreenInfoHelper.get().getPrevInfo();
        if (this.inTransaction && prev.inMenu(BazaarMenuType.BuyOrderSetupVolume) && info.getScreen() instanceof SignEditScreen) {
            return Optional.of(PresetScreen.EnterVolumeSign);
        }

        return Optional.empty();
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        if (!this.configState.enabled) {
            return false;
        }

        return this.getPresetScreen(info).map(screen -> switch (screen) {
            case VolumeSetupContainer -> this.configState.enableOnContainer;
            case EnterVolumeSign -> this.configState.enableOnSign;
        }).orElse(false);
    }

    @Override
    public Optional<DraggableWidget> createWidget(ScreenInfo info) {
        var screen = this.getPresetScreen(info);
        if (screen.isEmpty()) {
            log.warn(
                "OrderPresetsModule: createWidget was called but no valid preset screen was found. " +
                    "Current Title: '{}', Current Screen: {}, Previous Title: '{}', In Transaction: {}",
                info.containerName().orElse("N/A"),
                info.getScreen() != null ? info.getScreen().getClass().getSimpleName() : "N/A",
                ScreenInfoHelper.get().getPrevInfo().containerName().orElse("N/A"),
                this.inTransaction
            );

            return Optional.empty();
        }

        var screenType = screen.get();
        var position = this.getConfigPosition(screenType).orElseGet(() -> 
            switch (screenType) {
                case PresetScreen.VolumeSetupContainer -> new Position(570, 180);
                case PresetScreen.EnterVolumeSign -> new Position(580, 40);
            }
        );

        int maxVisible = switch (screenType) {
            case PresetScreen.VolumeSetupContainer -> 6;
            case PresetScreen.EnterVolumeSign -> 8;
        };

        if (this.list != null) {
            this.list.setX(position.x());
            this.list.setY(position.y());
            this.list.setMaxVisibleItems(maxVisible);
            this.rebuildList();
            return Optional.of(this.list);
        }

        this.list = new ListWidget(
            position.x(),
            position.y(),
            60,
            100,
            "Presets"
        );

        this.list
            .setMaxVisibleItems(maxVisible)
            .setItemHeight(16)
            .setItemSpacing(1)
            .setReorderable(false)
            .setRemovable(false)
            .onItemClick((self, item, idx) -> {
                var preset = (OrderPreset.RenderableEntry) item;
                if (!preset.isDisabled()) {
                    this.applyPreset(preset.getPreset());
                }
            }).onDragEnd((self, pos) -> this.savePosition(
                pos,
                this.getPresetScreen(ScreenInfoHelper.get().getCurrInfo()).orElse(PresetScreen.VolumeSetupContainer)
            ));

        this.rebuildList();

        return Optional.of(this.list);
    }

    private void savePosition(Position pos, PresetScreen screen) {
        switch (screen) {
            case PresetScreen.VolumeSetupContainer -> this.updateConfig(cfg -> cfg.containerPosition = pos);
            case PresetScreen.EnterVolumeSign -> this.updateConfig(cfg -> cfg.signPosition = pos);
        }
    }

    private Optional<Position> getConfigPosition(PresetScreen screen) {
        return switch (screen) {
            case PresetScreen.VolumeSetupContainer -> Optional.ofNullable(this.configState.containerPosition);
            case PresetScreen.EnterVolumeSign -> Optional.ofNullable(this.configState.signPosition);
        };
    }

    public boolean applyPreset(OrderPreset preset) {
        log.debug("Handle preset click: {}", preset);

        int volume = switch (preset) {
            case OrderPreset.Max _ -> {
                var product = this.getCurrentProduct();
                if (product == null) {
                    yield 0;
                }

                var price = this.bazaarData
                    .highestBuyOrderPrice(ProductIdentity.fromIndex(product))
                    .map(currentPrice -> currentPrice + 0.1);
                if (price.isEmpty()) {
                    yield 0;
                }

                yield GameUtils
                    .getPurse()
                    .map(purse -> calculateMaxVolume(purse, price.get(), this.currMaxVolume))
                    .orElse(0);
            }
            case OrderPreset.Clipboard clipboardPreset -> clipboardPreset.amount();
            case OrderPreset.Volume volumePreset -> volumePreset.amount();
        };

        if (volume == 0) {
            log.debug("Clicked preset resolved to a volume of 0 which is invalid");
            return false;
        }

        var client = Minecraft.getInstance();
        var player = client.player;
        var interactionManager = client.gameMode;
        if (player == null || interactionManager == null) {
            return false;
        }

        this.pendingPreset = true;
        this.pendingVolume = volume;

        log.debug("Preset click processed: volume={}", volume);

        var currInfo = ScreenInfoHelper.get().getCurrInfo();
        if (currInfo.getScreen() instanceof SignEditScreen signEditScreen) {
            GameUtils.submitSignValue(signEditScreen, String.valueOf(volume));

            this.pendingVolume = -1;
            this.pendingPreset = false;
            return true;
        }

        // noinspection OptionalGetWithoutIsPresent
        interactionManager.handleContainerInput(
            currInfo.getGenericContainerScreen().get().getMenu().containerId, 16, 1, ContainerInput.PICKUP, player
        );
        return true;
    }

    public static int calculateMaxVolume(double purse, double pricePerUnit, int maxVolume) {
        return Math.min((int) (purse / pricePerUnit), maxVolume);
    }

    public enum PresetUnavailableReason {
        NONE,
        PRICE_UNAVAILABLE,
        PURSE_UNAVAILABLE,
        INSUFFICIENT_COINS
    }

    public record PresetDescription(
        OrderPreset preset,
        String displayText,
        boolean canApply,
        OptionalInt resolvedVolume,
        PresetUnavailableReason unavailableReason,
        double missingCoins
    ) {
        private static PresetDescription available(OrderPreset preset, int resolvedVolume) {
            return new PresetDescription(
                preset,
                preset.toString(),
                true,
                OptionalInt.of(resolvedVolume),
                PresetUnavailableReason.NONE,
                0.0
            );
        }

        private static PresetDescription unavailable(
            OrderPreset preset,
            PresetUnavailableReason reason,
            double missingCoins
        ) {
            return new PresetDescription(
                preset,
                preset.toString(),
                false,
                OptionalInt.empty(),
                reason,
                missingCoins
            );
        }
    }
}
