package com.github.lutzluca.btrbz.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

public final class GameUtils {

    public static final Set<Item> ORDER_SCREEN_NON_ORDER_ITEMS = Set.of(
        Items.BLACK_STAINED_GLASS_PANE,
        Items.ARROW,
        Items.HOPPER
    );

    private GameUtils() { }

    public static List<String> getScoreboardLines() {
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

    public static void runCommand(String command) {
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.networkHandler.sendChatCommand(command);
        }
    }

    public static <T> void copyIntToClipboard(T value) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.keyboard.setClipboard(String.valueOf(value));
        }
    }

    public static Text join(List<Text> lines, String sequence) {
        var res = Text.empty();
        for (int i = 0; i < lines.size(); i++) {
            res.append(lines.get(i));
            if (i < lines.size() - 1) {
                res.append(sequence);
            }
        }
        return res;
    }
}
