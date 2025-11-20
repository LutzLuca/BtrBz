package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.core.modules.OrderPresetsModule.OrderPresetsConfig;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.mixin.AbstractSignEditScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.SimpleTextWidget;
import com.github.lutzluca.btrbz.widgets.StaticListWidget;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class OrderPresetsModule extends Module<OrderPresetsConfig> {


    private StaticListWidget<OrderPresetEntry> list;
    private int currMaxVolume = GameUtils.GLOBAL_MAX_ORDER_VOLUME;

    // TODO: combine `openedProductId` with ProductInfoProvider's state for `openedProductId`
    // expose the `openedProductId` of the ProductInfoProver and keep state across order
    // transaction flow (the menus associated with it)
    private @Nullable String currProductId = null;
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
                this.currProductId = prev
                    .getItemStack(13)
                    .map(ItemStack::getName)
                    .map(Text::getString)
                    .flatMap(BtrBz.bazaarData()::nameToId)
                    .orElse(null);

                this.currMaxVolume = curr
                    .getItemStack(16)
                    .flatMap(this::getMaxVolume)
                    .orElse(GameUtils.GLOBAL_MAX_ORDER_VOLUME);

                this.inTransaction = true;
                log.debug(
                    "Starting transaction for resolved product '{}' with maxVolume '{}'",
                    this.currProductId,
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
                    "Canceling transaction for resolved product '{}': prev={}, curr={}",
                    this.currProductId,
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

            var accessor = (AbstractSignEditScreenAccessor) signEditScreen;
            accessor.setCurrentRow(0);
            accessor.invokeSetCurrentRowMessage(String.valueOf(this.pendingVolume));

            signEditScreen.close();

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
        log.debug("Ending transaction for product '{}'", this.currProductId);
        this.inTransaction = false;

        this.pendingVolume = -1;
        this.pendingPreset = false;

        this.currMaxVolume = GameUtils.GLOBAL_MAX_ORDER_VOLUME;
        this.currProductId = null;
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

        var purse = this.getPurse();

        var pricePerUnit = Optional
            .ofNullable(this.currProductId)
            .flatMap(BtrBz.bazaarData()::highestBuyPrice)
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
            .filter(presetVolume -> presetVolume <= currMaxVolume)
            .sorted()
            .map(OrderPreset.Volume::new)
            .collect(Collectors.toList());

        presets.add(new OrderPreset.Max());

        List<OrderPresetEntry> entries = new ArrayList<>();

        for (var preset : presets) {
            OrderPresetEntry entry = new OrderPresetEntry(preset);

            switch (preset) {
                case OrderPreset.Volume volume -> {
                    boolean canAfford = !priceAvailable || (purse.isPresent() && (volume.amount * pricePerUnit.get() <= purse.get()));
                    if (!canAfford) {
                        entry.setDisabled(true);
                        entry.setTooltipLines(List.of(Text.literal("Insufficient coins")));
                    }
                }
                case OrderPreset.Max ignored -> {
                    if (!priceAvailable) {
                        entry.setDisabled(true);
                        entry.setTooltipLines(List.of(Text.literal(
                            "Unable to determine price information")));
                    }
                }
            }

            entries.add(entry);
        }

        list.rebuildEntries(entries);
    }

    private Optional<Integer> getMaxVolume(@NotNull ItemStack item) {
        return OrderInfoParser
            .getLore(item)
            .stream()
            .filter(line -> line.startsWith("Buy up to"))
            .findFirst()
            .map(line -> line.replaceFirst("Buy up to", "").replaceAll("x*", ""))
            .flatMap(volume -> Utils
                .parseUsFormattedNumber(volume)
                .toJavaOptional()
                .map(Number::intValue));
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(BazaarMenuType.BuyOrderSetupVolume);
    }

    @Override
    public List<ClickableWidget> createWidgets(ScreenInfo info) {
        if (this.list != null) {
            return List.of(this.list);
        }
        // TODO get a better default position
        var position = this.getConfigPosition().orElse(new Position(10, 10));
        this.list = new StaticListWidget<>(
            position.x(),
            position.y(),
            60,
            100,
            Text.literal("Presets"),
            info.getScreen()
        );

        this.list.setMaxVisibleChildren(5);

        this.list.onChildClick((entry, index) -> {
            if (!entry.isDisabled()) {
                this.handlePresetClick(entry.getPreset());
            }
        });

        this.list.onDragEnd((self, pos) -> this.savePosition(pos));

        this.rebuildList();

        return List.of(this.list);
    }

    private void savePosition(Position pos) {
        log.debug("Saving new position for BookmarkedItemsModule: {}", pos);
        this.updateConfig(cfg -> {
            cfg.x = pos.x();
            cfg.y = pos.y();
        });
    }

    private Optional<Position> getConfigPosition() {
        return Utils
            .zipNullables(this.configState.x, this.configState.y)
            .map(pair -> new Position(pair.getLeft(), pair.getRight()));
    }

    private void handlePresetClick(OrderPreset preset) {
        log.debug("Handle preset click: {}", preset);

        int volume = switch (preset) {
            case OrderPreset.Volume(int amount) -> amount;
            case OrderPreset.Max() -> {
                if (this.currProductId == null) {
                    log.debug("Cannot calculate MAX: product ID unavailable");
                    yield 0;
                }

                var price = BtrBz
                    .bazaarData()
                    .highestBuyPrice(this.currProductId)
                    .map(currPrice -> currPrice + 0.1);

                if (price.isEmpty()) {
                    log.debug("Cannot calculate MAX: price unavailable");
                    yield 0;
                }

                yield this
                    .getPurse()
                    .map(purse -> Math.min((int) (purse / price.get()), this.currMaxVolume))
                    .orElse(0);
            }
        };

        if (volume == 0) {
            log.debug("Clicked preset resolved to a volume of 0 which is invalid");
            return;
        }

        var client = MinecraftClient.getInstance();
        var player = client.player;
        var interactionManager = client.interactionManager;
        if (player == null || interactionManager == null) {
            return;
        }

        this.pendingPreset = true;
        this.pendingVolume = volume;

        log.debug("Preset click processed: volume={}", volume);

        // noinspection OptionalGetWithoutIsPresent
        interactionManager.clickSlot(
            ScreenInfoHelper
                .get()
                .getCurrInfo()
                .getGenericContainerScreen()
                .get()
                .getScreenHandler().syncId, 16, 1, SlotActionType.PICKUP, player
        );
    }

    private Optional<Double> getPurse() {
        return GameUtils
            .getScoreboardLines()
            .stream()
            .filter(line -> line.startsWith("Purse") || line.startsWith("Piggy"))
            .findFirst()
            .flatMap(line -> {
                var remainder = line.replaceFirst("Purse:|Piggy:", "").trim();
                var spaceIdx = remainder.indexOf(' ');

                return Utils
                    .parseUsFormattedNumber(
                        spaceIdx == -1 ? remainder : remainder.substring(0, spaceIdx))
                    .map(Number::doubleValue)
                    .toJavaOptional();
            });
    }

    private sealed interface OrderPreset permits OrderPreset.Volume,
        OrderPreset.Max {

        record Volume(int amount) implements OrderPreset {

            @Override
            public @NotNull String toString() {
                return String.valueOf(amount);
            }
        }

        record Max() implements OrderPreset {

            @Override
            public @NotNull String toString() {
                return "MAX";
            }
        }
    }

    @Getter
    private static class OrderPresetEntry extends SimpleTextWidget {

        private final OrderPreset preset;

        public OrderPresetEntry(OrderPreset preset) {
            super(0, 0, 60, 14, Text.literal(preset.toString()));
            this.preset = preset;

            if (preset instanceof OrderPreset.Max) {
                this.setBackgroundColor(0x80404020);
            }
        }
    }

    public static class OrderPresetsConfig {

        public Integer x, y;
        public boolean enabled = true;
        public List<Integer> presets = List.of();

        public Builder<Boolean> createEnableOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.of("Order Presets"))
                .description(OptionDescription.of(Text.literal(
                    "Enable or disable the Order Presets module for quick access to predefined order volumes")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnableOption());

            return OptionGroup
                .createBuilder()
                .name(Text.of("Order Presets"))
                .description(OptionDescription.of(Text.literal(
                    "Lets you have predefined order volume for quick access")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}
