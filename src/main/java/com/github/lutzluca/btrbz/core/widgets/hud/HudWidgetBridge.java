package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHostOptions;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

public final class HudWidgetBridge {
    private HudWidgetBridge() {}

    public static void register(Identifier elementId, WidgetHost host, Consumer<WidgetId> renderedWidget) {
        HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, elementId, (context, tickCounter) -> {
            render(host, context, tickCounter.getGameTimeDeltaPartialTick(false), renderedWidget);
        });
    }

    private static void render(
        WidgetHost host,
        GuiGraphicsExtractor graphics,
        float partialTicks,
        Consumer<WidgetId> renderedWidget
    ) {
        var client = Minecraft.getInstance();

        if (shouldSuppressHud(
            client.options.keyPlayerList.isDown(),
            client.debugEntries.isOverlayVisible(),
            client.level == null)) {
            return;
        }

        var screen = GameUtils.screen();

        boolean generalContainer = screen instanceof AbstractContainerScreen<?>
            && !ScreenTracker.inBazaar();

        if (screen != null && !(screen instanceof ChatScreen) && !generalContainer) {
            return;
        }

        var window = client.getWindow();
        var results = host.render(
            graphics,
            -1,
            -1,
            partialTicks,
            new WidgetCanvas(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight()),
            WidgetHostOptions.runtime(false),
            null);

        results.forEach(result -> renderedWidget.accept(result.definition().getId()));
    }

    static boolean shouldSuppressHud(
        boolean playerListVisible,
        boolean debugOverlayVisible,
        boolean levelMissing
    ) {
        return playerListVisible || debugOverlayVisible || levelMissing;
    }
}
