package com.github.lutzluca.btrbz.utils;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

@Slf4j
public final class GameUtils {

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

    public static <T> void copyIntToClipboard(T value) {
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
}
