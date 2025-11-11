package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.modules.OrderPresetsModule.OrderPresetsConfig;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.mixin.AbstractSignEditScreenAccessor;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
import com.github.lutzluca.btrbz.widgets.SimpleTextWidget;
import com.github.lutzluca.btrbz.widgets.StaticListWidget;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import io.vavr.control.Try;
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
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class OrderPresetsModule extends Module<OrderPresetsConfig> {

    private static final int GLOBAL_MAX_ORDER_VOLUME = 71680;

    private StaticListWidget<OrderPresetEntry> list;
    private int currMaxVolume = GLOBAL_MAX_ORDER_VOLUME;
    private double currPricePerUnit = 0.0;
    private int pendingVolume = -1;
    private boolean pendingPreset = false;
    private boolean inPresetTransaction = false;

    private static List<String> fetchScoreboardLines() {
        var client = MinecraftClient.getInstance();
        var world = client.world;
        if (world == null) { return List.of(); }

        Scoreboard scoreboard = world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) { return List.of(); }

        var entries = scoreboard.getScoreboardEntries(objective);

        List<String> lines = new ArrayList<>();
        for (ScoreboardEntry entry : entries) {
            String owner = entry.owner();
            Team team = scoreboard.getScoreHolderTeam(owner);

            String text;
            if (team != null) {
                text = team.getPrefix().getString() + owner + team.getSuffix().getString();
            } else {
                text = owner;
            }

            text = text.replaceAll("§.", "").trim();

            if (!text.isBlank()) { lines.add(text); }
        }

        return lines;
    }


    @Override
    public void onLoad() {
        ScreenInfoHelper.registerOnSwitch(info -> {
            boolean inBuyOrderFlow = info.inMenu(
                BazaarMenuType.Item,
                BazaarMenuType.BuyOrderSetupAmount,
                BazaarMenuType.BuyOrderSetupPrice,
                BazaarMenuType.BuyOrderConfirmation
            );

            if ((!inBuyOrderFlow && !this.inPresetTransaction) || info.inMenu(
                BazaarMenuType.BuyOrderSetupAmount,
                BazaarMenuType.BuyOrderSetupPrice
            )) {
                this.currMaxVolume = GLOBAL_MAX_ORDER_VOLUME;
                this.currPricePerUnit = 0.0;
                this.pendingVolume = -1;
                this.pendingPreset = false;
                return;
            }

            if (info.inMenu(BazaarMenuType.BuyOrderSetupAmount)) {
                var prev = ScreenInfoHelper.get().getPrevInfo();

                this.currMaxVolume = info.getGenericContainerScreen().flatMap(gcs -> {
                    var handler = gcs.getScreenHandler();
                    var inventory = handler.getInventory();
                    var item = inventory.getStack(16);
                    return item == ItemStack.EMPTY ? Optional.empty() : Optional.of(item);
                }).flatMap(this::getMaxVolume).orElse(GLOBAL_MAX_ORDER_VOLUME);

                if (prev.inMenu(BazaarMenuType.Item)) {
                    this.currPricePerUnit = prev.getGenericContainerScreen().flatMap(gcs -> {
                        var handler = gcs.getScreenHandler();
                        var inventory = handler.getInventory();
                        var item = inventory.getStack(13);
                        return item == ItemStack.EMPTY ? Optional.empty()
                            : this.getBestBuyOrderPrice(item);
                    }).orElse(0.0);
                }

                this.rebuildList();

                if (!this.inPresetTransaction) {
                    this.pendingVolume = -1;
                    this.pendingPreset = false;
                }
            }
        });

        ScreenInfoHelper.registerOnSwitch(info -> {
            var prev = ScreenInfoHelper.get().getPrevInfo();
            if (!prev.inMenu(BazaarMenuType.BuyOrderSetupAmount) || !(info.getScreen() instanceof SignEditScreen signEditScreen)) {
                return;
            }
            if (!this.pendingPreset || !(this.pendingVolume > 0)) {
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
            info -> info.inMenu(BazaarMenuType.BuyOrderSetupAmount), (info, inventory) -> {
                inventory.getItem(16).flatMap(this::getMaxVolume).ifPresent(maxVolume -> {
                    if (this.currMaxVolume != maxVolume) {
                        this.currMaxVolume = maxVolume;
                        this.rebuildList();
                    }
                });
            }
        );
    }

    public void rebuildList() {
        if (this.list == null) {
            return;
        }

        double purse = getPurse().orElse(0.0);
        boolean priceAvailable = this.currPricePerUnit > 0.0;

        List<OrderPreset> presets = this.configState.presets
            .stream()
            .filter(presetVolume -> presetVolume <= this.currMaxVolume)
            .sorted()
            .map(OrderPreset.Volume::new)
            .collect(Collectors.toList());

        presets.add(new OrderPreset.Max());

        List<OrderPresetEntry> entries = new ArrayList<>();

        for (var preset : presets) {
            OrderPresetEntry entry = new OrderPresetEntry(preset);

            switch (preset) {
                case OrderPreset.Volume volume -> {
                    boolean canAfford = !priceAvailable || (volume.amount * this.currPricePerUnit <= purse);
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

        this.list.rebuildEntries(entries);
    }

    private Optional<Integer> getMaxVolume(@NotNull ItemStack item) {
        return OrderInfoParser
            .getLore(item)
            .stream()
            .filter(line -> line.startsWith("Buy up to"))
            .findFirst()
            .map(line -> line.replaceFirst("Buy up to", "").replaceAll("x*", ""))
            .flatMap(volume -> Util
                .parseUsFormattedNumber(volume)
                .toJavaOptional()
                .map(Number::intValue));
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(BazaarMenuType.BuyOrderSetupAmount);
    }

    @Override
    public List<ClickableWidget> createWidgets(ScreenInfo info) {
        if (this.list != null) {
            return List.of(this.list);
        }

        this.list = new StaticListWidget<>(
            10,
            10,
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

        this.rebuildList();

        return List.of(this.list);
    }

    private void handlePresetClick(OrderPreset preset) {
        log.debug("Handle preset click: {}", preset);

        var volume = switch (preset) {
            case OrderPreset.Volume(int amount) -> amount;
            case OrderPreset.Max() -> {
                if (this.currPricePerUnit <= 0.0) {
                    log.debug("Cannot calculate MAX: price unavailable");
                    yield 0;
                }

                yield getPurse()
                    .map(purse -> Math.min(
                        (int) (purse / this.currPricePerUnit),
                        this.currMaxVolume
                    ))
                    .orElse(0);
            }
        };

        if (volume == 0) {
            log.debug("Clicked preset resolved to a volume of 0 which is invalid");
            return;
        }

        this.inPresetTransaction = true;
        this.pendingPreset = true;
        this.pendingVolume = volume;

        var client = MinecraftClient.getInstance();
        var player = client.player;
        var interactionManager = client.interactionManager;
        if (player == null || interactionManager == null) { return; }

        //noinspection OptionalGetWithoutIsPresent
        interactionManager.clickSlot(
            ScreenInfoHelper
                .get()
                .getCurrInfo()
                .getGenericContainerScreen()
                .get()
                .getScreenHandler().syncId, 16, 1, SlotActionType.PICKUP, player
        );
    }

    public Optional<Double> getBestBuyOrderPrice(ItemStack product) {
        var data = BtrBz.bazaarData();
        return Try
            .of(() -> product.getName().getString())
            .toJavaOptional()
            .flatMap(data::nameToId)
            .flatMap(id -> data.highestBuyPrice(id).map(price -> price + .1));
    }

    private Optional<Double> getPurse() {
        return fetchScoreboardLines()
            .stream()
            .filter(line -> line.startsWith("Purse") || line.startsWith("Piggy"))
            .findFirst()
            .flatMap(line -> {
                var remainder = line.replaceFirst("Purse:|Piggy:", "").trim();
                var spaceIdx = remainder.indexOf(' ');

                return Util
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
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var enabledOption = this.createEnableOption();

            return OptionGroup
                .createBuilder()
                .name(Text.of("Order Presets"))
                .description(OptionDescription.of(Text.literal(
                    "Lets you have predefined order volume for quick access")))
                .option(enabledOption.build())
                .collapsed(false)
                .build();
        }
    }
}