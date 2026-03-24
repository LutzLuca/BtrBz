package com.github.lutzluca.btrbz.core.modules.orderpreset;

import com.github.lutzluca.btrbz.core.modules.Module;
import com.github.lutzluca.btrbz.data.OrderInfoParser;

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
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.world.inventory.ClickType;
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
                    "Starting buy order transaction for product '{}' with maxVolume '{}'",
                    this.getCurrentProductId(),
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

    private @Nullable String getCurrentProductId() {
        var info = this.context().productInfoProvider().getOpenedProductNameInfo();
        return info != null ? info.productId() : null;
    }

    private boolean isOrderFlowSignScreen(ScreenInfo curr, ScreenInfo prev) {
        return curr.getScreen() instanceof SignEditScreen && prev.inMenu(
            BazaarMenuType.BuyOrderSetupVolume,
            BazaarMenuType.BuyOrderSetupPrice
        );
    }

    public void rebuildList() {
        if (list == null) {
            return;
        }

        var purse = GameUtils.getPurse();
        var pricePerUnit = Optional
            .ofNullable(this.getCurrentProductId())
            .flatMap(this.context().bazaarData()::highestBuyPrice)
            .map(price -> price + .1);
        var priceAvailable = pricePerUnit.isPresent();

        log.debug(
            "Rebuilding Order Preset list: maxVolume={}, pricePerUnit={}, purse={}",
            this.currMaxVolume,
            pricePerUnit,
            purse
        );

        List<OrderPreset> presets = configState.presets
            .stream()
            .filter(presetVolume -> presetVolume <= this.currMaxVolume)
            .sorted()
            .map(OrderPreset.Volume::new)
            .collect(Collectors.toList());

        presets.addFirst(new OrderPreset.Max());

        var clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clipboard != null && !clipboard.isBlank()) {
            Utils.parseUsFormattedNumber(clipboard).map(Number::intValue).onSuccess(clipboardVolume -> {
                if (clipboardVolume > 0 && clipboardVolume <= this.currMaxVolume) {
                    presets.add(1, new OrderPreset.Clipboard(clipboardVolume));
                }
            });
        }

        List<Renderable> entries = new ArrayList<>();

        for (var preset : presets) {
            var entry = new OrderPreset.RenderableEntry(preset);

            switch (preset) {
                case OrderPreset.Max ignored -> this.configureMaxEntry(entry, priceAvailable, pricePerUnit, purse);
                case OrderPreset.Clipboard clipboardPreset -> {
                    var amount = clipboardPreset.amount();

                    boolean canAfford = !priceAvailable || purse.map(coins -> amount * pricePerUnit.get() <= coins).orElse(false);
                    if (!canAfford) {
                        entry.setDisabled(true);
                        entry.setTooltipLines(List.of(Component.literal("Insufficient coins")));
                    } else {
                        entry.setTooltipLines(List.of(Component.literal("From Clipboard")));
                    }
                }
                case OrderPreset.Volume(int amount) -> {
                    boolean canAfford = !priceAvailable || purse.map(coins -> amount * pricePerUnit.get() <= coins).orElse(false);
                    if (!canAfford) {
                        entry.setDisabled(true);
                        entry.setTooltipLines(List.of(Component.literal("Insufficient coins")));
                    }
                }
            }

            entries.add(entry);
        }

        list.setItems(entries);
    }

    private Optional<Integer> getMaxVolume(@NotNull ItemStack item) {
        return OrderInfoParser
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
    public List<DraggableWidget> createWidgets(ScreenInfo info) {
        var screen = this.getPresetScreen(info);
        if (screen.isEmpty()) {
            log.warn(
                "OrderPresetsModule: createWidgets was called but no valid preset screen was found. " +
                    "Current Title: '{}', Current Screen: {}, Previous Title: '{}', In Transaction: {}",
                info.containerName().orElse("N/A"),
                info.getScreen() != null ? info.getScreen().getClass().getSimpleName() : "N/A",
                ScreenInfoHelper.get().getPrevInfo().containerName().orElse("N/A"),
                this.inTransaction
            );

            return List.of();
        }

        var screenType = screen.get();
        var position = this.getConfigPosition(screenType).orElseGet(() -> 
            switch (screenType) {
                case PresetScreen.VolumeSetupContainer -> new Position(570, 180);
                case PresetScreen.EnterVolumeSign -> new Position(20, 20);
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
            return List.of(this.list);
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
                    this.handlePresetClick(preset.getPreset());
                }
            }).onDragEnd((self, pos) -> this.savePosition(pos, this.getPresetScreen(ScreenInfoHelper.get().getCurrInfo()).orElse(PresetScreen.VolumeSetupContainer)));

        this.rebuildList();

        return List.of(this.list);
    }

    private void savePosition(Position pos, PresetScreen screen) {
        log.debug("Saving new position for OrderPresetsModule ({}): {}", screen, pos);
        this.updateConfig(cfg -> {
            switch (screen) {
                case PresetScreen.VolumeSetupContainer -> {
                    cfg.containerX = pos.x();
                    cfg.containerY = pos.y();
                }
                case PresetScreen.EnterVolumeSign -> {
                    cfg.signX = pos.x();
                    cfg.signY = pos.y();
                }
            }
        });
    }

    private Optional<Position> getConfigPosition(PresetScreen screen) {
        return switch (screen) {
            case PresetScreen.VolumeSetupContainer -> Utils.zipNullables(this.configState.containerX, this.configState.containerY)
                .map(pair -> new Position(pair.getLeft(), pair.getRight()));
            case PresetScreen.EnterVolumeSign -> Utils.zipNullables(this.configState.signX, this.configState.signY)
                .map(pair -> new Position(pair.getLeft(), pair.getRight()));
        };
    }

    private void handlePresetClick(OrderPreset preset) {
        log.debug("Handle preset click: {}", preset);

        int volume = switch (preset) {
            case OrderPreset.Max ignored -> {
                var productId = this.getCurrentProductId();
                if (productId == null) {
                    log.debug("Cannot calculate MAX: product ID unavailable");
                    yield 0;
                }

                var price = this.context()
                    .bazaarData()
                    .highestBuyPrice(productId)
                    .map(currPrice -> currPrice + 0.1);

                if (price.isEmpty()) {
                    log.debug("Cannot calculate MAX: price unavailable");
                    yield 0;
                }

                yield GameUtils
                    .getPurse()
                    .map(purse -> this.calculateMaxVolume(purse, price.get()))
                    .orElse(0);
            }
            case OrderPreset.Clipboard clipboardPreset -> clipboardPreset.amount();
            case OrderPreset.Volume volumePreset -> volumePreset.amount();
        };

        if (volume == 0) {
            log.debug("Clicked preset resolved to a volume of 0 which is invalid");
            return;
        }

        var client = Minecraft.getInstance();
        var player = client.player;
        var interactionManager = client.gameMode;
        if (player == null || interactionManager == null) {
            return;
        }

        this.pendingPreset = true;
        this.pendingVolume = volume;

        log.debug("Preset click processed: volume={}", volume);

        var currInfo = ScreenInfoHelper.get().getCurrInfo();
        if (currInfo.getScreen() instanceof SignEditScreen signEditScreen) {
            GameUtils.submitSignValue(signEditScreen, String.valueOf(volume));

            this.pendingVolume = -1;
            this.pendingPreset = false;
            return;
        }

        // noinspection OptionalGetWithoutIsPresent
        interactionManager.handleInventoryMouseClick(
            currInfo.getGenericContainerScreen().get().getMenu().containerId, 16, 1, ClickType.PICKUP, player
        );
    }

    private int calculateMaxVolume(double purse, double pricePerUnit) {
        return Math.min((int) (purse / pricePerUnit), this.currMaxVolume);
    }

    private void configureMaxEntry(
        OrderPreset.RenderableEntry entry,
        boolean priceAvailable,
        Optional<Double> pricePerUnit,
        Optional<Double> purse
    ) {
        if (!priceAvailable) {
            entry.setDisabled(true);
            entry.setTooltipLines(List.of(Component.literal("Unable to determine price information")));
            return;
        }

        if (purse.isEmpty()) {
            entry.setDisabled(true);
            entry.setTooltipLines(List.of(Component.literal("Unable to determine purse amount")));
            return;
        }

        int maxVolume = this.calculateMaxVolume(purse.get(), pricePerUnit.get());

        if (maxVolume == 0) {
            entry.setDisabled(true);
            double missing = pricePerUnit.get() - purse.get();
            String formattedMissing = Utils.formatCompact(missing, 1);

            entry.setTooltipLines(List.of(
                Component.literal("Missing " + formattedMissing + " coins"),
                Component.literal("to buy one item")
            ));
            return;
        }

        entry.setTooltipLines(List.of(
            Component.literal(Utils.formatDecimal(maxVolume, 0, true) + " items")
        ));
    }
}
