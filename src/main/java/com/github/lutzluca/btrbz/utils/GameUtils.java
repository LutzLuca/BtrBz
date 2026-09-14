package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.jetbrains.annotations.Nullable;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager.OrderManagerConfig.QueueDisplayMode;
import com.github.lutzluca.btrbz.mixin.AbstractSignEditScreenAccessor;

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
        if (!BtrBz.isActive() || ScreenTracker.get().getCurrInfo().getScreen() != signEditScreen) {
            return;
        }
        var accessor = (AbstractSignEditScreenAccessor) signEditScreen;
        accessor.setLine(0);
        accessor.invokeSetMessage(value);
        setScreen(null);
    }

    public static final int GLOBAL_MAX_ORDER_VOLUME = 71680;

    private GameUtils() {}

    public static void setScreen(@Nullable Screen screen) {
        //? if <26.2 {
        Minecraft.getInstance().setScreen(screen);
        //?} else {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?}
    }

    public static @Nullable Screen screen() {
        //? if <26.2 {
        return Minecraft.getInstance().screen;
        //?} else {
        /*return Minecraft.getInstance().gui.screen();
        *///?}
    }

    public static List<String> getLore(ItemStack item) {
        return getLoreComponents(item)
            .stream()
            .map(Component::getString)
            .toList();
    }

    public static List<Component> getLoreComponents(ItemStack item) {
        return Optional
            .ofNullable(item.get(DataComponents.LORE))
            .map(ItemLore::lines)
            .orElseGet(ArrayList::new);
    }

    public static boolean orderScreenNonOrderItemsFilter(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }

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
        if (world == null) {
            return List.of();
        }

        Scoreboard scoreboard = world.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return List.of();
        }

        var entries = scoreboard.listPlayerScores(objective);

        List<String> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : entries) {
            String owner = entry.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(owner);

            String text;
            if (team != null) {
                var prefix = team.getPlayerPrefix().getString();
                var suffix = team.getPlayerSuffix().getString();
                text = prefix + owner + suffix;
            } else {
                text = owner;
            }

            text = Utils.stripScoreboardFormattingCodes(text);

            if (!text.isBlank()) {
                lines.add(text);
            }
        }

        return lines;
    }

    public static void runCommand(String command) {
        if (!BtrBz.isActive()) {
            return;
        }
        var client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.connection.sendCommand(command);
        }
    }

    public static <T> void copyToClipboard(T value) {
        Minecraft client = Minecraft.getInstance();
        client.keyboardHandler.setClipboard(String.valueOf(value));
    }

    public static boolean isPlayerInventorySlot(@Nullable Slot slot) {
        if (slot == null) {
            return false;
        }

        var player = Minecraft.getInstance().player;

        if (player == null) {
            return false;
        }

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
                var amountToken = spaceIdx == -1 ? remainder : remainder.substring(0, spaceIdx);

                return Utils
                    .parseUsFormattedNumber(amountToken)
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

    public static Optional<String> customDataId(ItemStack stack) {
        return Optional
            .ofNullable(stack.get(DataComponents.CUSTOM_DATA))
            .flatMap(data -> data.copyTag().getString("id"))
            .map(String::trim)
            .filter(id -> !id.isEmpty());
    }

    public static Optional<String> matchingCustomNameLegacy(ItemStack stack, String expectedName) {
        return Optional
            .ofNullable(stack.get(DataComponents.CUSTOM_NAME))
            .filter(name -> Utils
                .normalizeDisplayName(name.getString())
                .equals(Utils.normalizeDisplayName(expectedName)))
            .map(GameUtils::legacyFormattedText);
    }

    public static Optional<String> matchingLegacySuffix(Component component, String expectedSuffix) {
        if (component == null || expectedSuffix == null || expectedSuffix.isBlank()) {
            return Optional.empty();
        }

        var plainText = component.getString();
        if (!plainText.endsWith(expectedSuffix)) {
            return Optional.empty();
        }

        return legacyFormattedRange(component, plainText.length() - expectedSuffix.length(), plainText.length())
            .filter(legacy -> Utils
                .normalizeDisplayName(legacy)
                .equals(Utils.normalizeDisplayName(expectedSuffix)));
    }

    public static String legacyFormattedText(Component component) {
        return legacyFormattedRange(component, 0, component.getString().length()).orElse("");
    }

    public static Component legacyFormattedComponent(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return Component.empty();
        }

        var segments = new ArrayList<MutableComponent>();
        var style = Style.EMPTY;
        int segmentStart = 0;
        for (int index = 0; index + 1 < value.length(); index++) {
            if (value.charAt(index) != ChatFormatting.PREFIX_CODE) {
                continue;
            }

            var formatting = ChatFormatting.getByCode(value.charAt(index + 1));
            if (formatting == null) {
                continue;
            }

            if (segmentStart < index) {
                segments.add(Component.literal(value.substring(segmentStart, index)).setStyle(style));
            }
            style = style.applyLegacyFormat(formatting);
            index++;
            segmentStart = index + 1;
        }
        if (segmentStart < value.length()) {
            segments.add(Component.literal(value.substring(segmentStart)).setStyle(style));
        }
        if (segments.isEmpty()) {
            return Component.empty().setStyle(style);
        }

        var result = segments.getFirst();
        for (int index = 1; index < segments.size(); index++) {
            result.append(segments.get(index));
        }
        return result;
    }

    private static Optional<String> legacyFormattedRange(Component component, int startInclusive, int endExclusive) {
        if (component == null || startInclusive < 0 || endExclusive < startInclusive) {
            return Optional.empty();
        }

        var segments = new ArrayList<StyledTextSegment>();
        component.visit((Style style, String content) -> {
            segments.add(new StyledTextSegment(content, style));
            return Optional.empty();
        }, Style.EMPTY);

        var out = new StringBuilder();
        var cursor = 0;
        for (var segment : segments) {
            var content = segment.content();
            if (content.isEmpty()) {
                continue;
            }

            var segmentStart = cursor;
            var segmentEnd = cursor + content.length();
            cursor = segmentEnd;

            var copyStart = Math.max(startInclusive, segmentStart);
            var copyEnd = Math.min(endExclusive, segmentEnd);
            if (copyStart >= copyEnd) {
                continue;
            }

            appendLegacyStyle(out, segment.style());
            out.append(content, copyStart - segmentStart, copyEnd - segmentStart);
        }

        return out.isEmpty() ? Optional.empty() : Optional.of(out.toString());
    }

    private static void appendLegacyStyle(StringBuilder out, Style style) {
        TextColor color = style.getColor();
        if (color != null) {
            //? if <26.2 {
            var formatting = ChatFormatting.getByName(color.serialize());
            if (formatting != null && formatting.isColor()) {
                out.append(formatting);
            }
            //?} else {
            /*String serialized = color.serialize();
            if (!serialized.startsWith("#")) {
                try {
                    out.append(ChatFormatting.valueOf(serialized.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException _) {
                    //ignore unknown color names
                }
            }
            *///?}
        }

        if (style.isObfuscated()) {
            out.append(ChatFormatting.OBFUSCATED);
        }
        if (style.isBold()) {
            out.append(ChatFormatting.BOLD);
        }
        if (style.isStrikethrough()) {
            out.append(ChatFormatting.STRIKETHROUGH);
        }
        if (style.isUnderlined()) {
            out.append(ChatFormatting.UNDERLINE);
        }
        if (style.isItalic()) {
            out.append(ChatFormatting.ITALIC);
        }
    }

    private record StyledTextSegment(String content, Style style) {}
}
