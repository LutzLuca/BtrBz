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
import com.github.lutzluca.btrbz.widgets.ListWidget;
import com.github.lutzluca.btrbz.widgets.Renderable;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
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
                    .map(ItemStack::getHoverName)
                    .map(Component::getString)
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
            accessor.setLine(0);
            accessor.invokeSetMessage(String.valueOf(this.pendingVolume));

            //NOTE: signEditScreen.onClose() gets broken by Skyblocker so setScreen is used instead.
            Minecraft.getInstance().setScreen(null);

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
            .filter(presetVolume -> presetVolume <= this.currMaxVolume)
            .sorted()
            .map(OrderPreset.Volume::new)
            .collect(Collectors.toList());

        presets.addFirst(new OrderPreset.Max());
        List<Renderable> entries = new ArrayList<>();

        for (var preset : presets) {
            var entry = new OrderPresetRenderable(preset);

            switch (preset) {
                case OrderPreset.Volume volume -> {
                    boolean canAfford = !priceAvailable || purse.map(coins -> volume.amount * pricePerUnit.get() <= coins).orElse(false);
                    if (!canAfford) {
                        entry.setDisabled(true);
                        entry.setTooltipLines(List.of(Component.literal("Insufficient coins")));
                    }
                }
                case OrderPreset.Max ignored -> {
                    if (!priceAvailable) {
                        entry.setDisabled(true);
                        entry.setTooltipLines(List.of(Component.literal(
                            "Unable to determine price information")));
                        break;
                    }

                    if (purse.isEmpty()) {
                        entry.setDisabled(true);
                        entry.setTooltipLines(List.of(Component.literal(
                            "Unable to determine purse amount")));
                        break;
                    }

                    int maxVolume = this.calculateMaxVolume(purse.get(), pricePerUnit.get());
                    String formattedVolume = Utils.formatDecimal(maxVolume, 0, true);

                    if (maxVolume == 0) {
                        entry.setDisabled(true);
                        double needed = pricePerUnit.get() * this.currMaxVolume;
                        double currentPurse = purse.get();
                        double missing = needed - currentPurse;
                        String formattedMissing = Utils.formatDecimal(missing, 0, true);

                        entry.setTooltipLines(List.of(
                            Component.literal("Missing " + formattedMissing + " coins"),
                            Component.literal("to buy one item")
                        ));
                        break;
                    }

                    entry.setTooltipLines(List.of(
                        Component.literal(formattedVolume + " items")
                    ));
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
    public List<DraggableWidget> createWidgets(ScreenInfo info) {
        if (this.list != null) {
            return List.of(this.list);
        }

        var position = this.getConfigPosition().orElse(new Position(570, 180));
        this.list = new ListWidget(
            position.x(),
            position.y(),
            60,
            100,
            "Presets"
        );

        this.list.setItemHeight(16)
            .setItemSpacing(1)
            .setReorderable(false)
            .setRemovable(false)
            .onItemClick((self, item, idx) -> {
                var preset = (OrderPresetRenderable) item;
                if (!preset.isDisabled()) {
                    this.handlePresetClick(preset.getPreset());
                }
            }).onDragEnd((self, pos) -> this.savePosition(pos));

        this.rebuildList();

        return List.of(this.list);
    }

    private void savePosition(Position pos) {
        log.debug("Saving new position for OrderPresetsModule: {}", pos);
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

        var client = Minecraft.getInstance();
        var player = client.player;
        var interactionManager = client.gameMode;
        if (player == null || interactionManager == null) {
            return;
        }

        this.pendingPreset = true;
        this.pendingVolume = volume;

        log.debug("Preset click processed: volume={}", volume);

        // noinspection OptionalGetWithoutIsPresent
        interactionManager.handleInventoryMouseClick(
            ScreenInfoHelper
                .get()
                .getCurrInfo()
                .getGenericContainerScreen()
                .get()
                .getMenu().containerId, 16, 1, ClickType.PICKUP, player
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

    private int calculateMaxVolume(double purse, double pricePerUnit) {
        return Math.min((int) (purse / pricePerUnit), this.currMaxVolume);
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

    public static class OrderPresetRenderable implements Renderable {
        @Getter
        private final OrderPreset preset;
        @Getter
        @Setter
        private boolean disabled = false;
        private List<Component> tooltipLines = null;
        private final Component displayText;
        private final int backgroundColor;

        public OrderPresetRenderable(OrderPreset preset) {
            this.preset = preset;
            this.displayText = Component.literal(preset.toString());
            this.backgroundColor = (preset instanceof OrderPreset.Max)
                ? 0x80404020
                : 0x80000000;
        }

        public void setTooltipLines(List<Component> lines) {
            this.tooltipLines = lines;
        }

        @Override
        public void render(
            net.minecraft.client.gui.GuiGraphics graphics,
            int x, int y, int width, int height,
            int mouseX, int mouseY, float delta,
            boolean hovered
        ) {
            var font = Minecraft.getInstance().font;

            int bgColor = hovered && !disabled ? 0x60FFFFFF : this.backgroundColor;
            graphics.fill(x, y, x + width, y + height, bgColor);

            int textColor = disabled ? 0xFF888888 : 0xFFFFFFFF;
            int textY = y + (height - font.lineHeight) / 2;
            graphics.drawString(font, this.displayText, x + 4, textY, textColor);
        }

        @Override
        public List<Component> getTooltip() {
            return this.tooltipLines;
        }

    }



    public static class OrderPresetsConfig {

        public Integer x, y;
        public boolean enabled = true;
        public List<Integer> presets = List.of();

        public Builder<Boolean> createEnableOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.nullToEmpty("Order Presets"))
                .description(OptionDescription.of(Component.literal(
                    "Enable or disable the Order Presets module for quick access to predefined order volumes")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnableOption());

            return OptionGroup
                .createBuilder()
                .name(Component.nullToEmpty("Order Presets"))
                .description(OptionDescription.of(Component.literal(
                    "Lets you have predefined order volume for quick access")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}
