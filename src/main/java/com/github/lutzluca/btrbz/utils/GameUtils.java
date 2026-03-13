package com.github.lutzluca.btrbz.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.jetbrains.annotations.Nullable;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.OrderManagerConfig.QueueDisplayMode;

@Slf4j
public final class GameUtils {

    /**
     * Submits a value to a {@link SignEditScreen} by setting line 0 to the given value
     * and closing the screen.
     *
     * <p>Note: {@code signEditScreen.onClose()} is intentionally avoided because it gets
     * broken by Skyblocker; {@code setScreen(null)} is used instead.</p>
     */
    public static void submitSignValue(SignEditScreen signEditScreen, String value) {
        var accessor = (com.github.lutzluca.btrbz.mixin.AbstractSignEditScreenAccessor) signEditScreen;
        accessor.setLine(0);
        accessor.invokeSetMessage(value);
        Minecraft.getInstance().setScreen(null);
    }

    public static final int GLOBAL_MAX_ORDER_VOLUME = 71680;

    private GameUtils() { }

    public static boolean orderScreenNonOrderItemsFilter(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) { return true; }

        return switch (stack.getItem()) {
            case Item item when item == Items.ARROW ->
                !stack.getHoverName().getString().equals("Go Back");
            case Item item when item == Items.HOPPER ->
                !stack.getHoverName().getString().equals("Claim All Coins");
            default -> true;
        };
    }

    public static List<String> getScoreboardLines() {
        var client = Minecraft.getInstance();
        var world = client.level;
        if (world == null) { return List.of(); }

        Scoreboard scoreboard = world.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) { return List.of(); }

        var entries = scoreboard.listPlayerScores(objective);

        List<String> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : entries) {
            String owner = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(owner);

            String text;
            if (team != null) {
                text = team.getPlayerPrefix().getString() + owner + team.getPlayerSuffix().getString();
            } else {
                text = owner;
            }

            text = text.replaceAll("§.", "").trim();

            if (!text.isBlank()) { lines.add(text); }
        }

        return lines;
    }

    public static void runCommand(String command) {
        var client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.connection.sendCommand(command);
        }
    }

    public static <T> void copyToClipboard(T value) {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.keyboardHandler.setClipboard(String.valueOf(value));
        }
    }

    public static Component join(List<Component> lines, String sequence) {
        var res = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            res.append(lines.get(i));
            if (i < lines.size() - 1) {
                res.append(sequence);
            }
        }
        return res;
    }

    public static boolean isPlayerInventorySlot(@Nullable Slot slot) {
        if (slot == null) { return false; }
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null) { return false; }

        return slot.container == player.getInventory();
    }

    public static Optional<Double> getPurse() {
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

    public static MutableComponent buildQueueComponent(int orders, int items, QueueDisplayMode mode) {
        String itemsLabel = items == 1 ? " item" : " items";

        if (mode == QueueDisplayMode.ItemsOnly) {
            return Component.literal(Utils.formatDecimal(items, 0, true))
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(itemsLabel).withStyle(ChatFormatting.GRAY));
        }

        String ordersLabel = orders == 1 ? " order" : " orders";

        return Component.literal(String.valueOf(orders))
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(ordersLabel + " / ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(Utils.formatDecimal(items, 0, true)).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(itemsLabel).withStyle(ChatFormatting.GRAY));
    }
}
