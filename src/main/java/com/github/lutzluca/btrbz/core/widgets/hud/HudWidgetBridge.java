package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHostOptions;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

public final class HudWidgetBridge {
    private HudWidgetBridge() {}

    public static void register(Identifier elementId, WidgetHost host, Consumer<WidgetId> renderedWidget) {
        HudElementRegistry.addLast(elementId, (context, tickCounter) -> {
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

        //? if <26.2 {
        boolean hideGui = client.options.hideGui;
        //?} else {
        /*boolean hideGui = client.gui.hud.isHidden();
         *///?}

        if (shouldSuppressHud(
            hideGui,
            client.options.keyPlayerList.isDown(),
            client.getDebugOverlay().showDebugScreen(),
            client.level == null)) {
            return;
        }

        var screen = GameUtils.screen();

        boolean generalContainer = screen instanceof AbstractContainerScreen<?>
            && !ScreenInfoHelper.inBazaar();

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
        boolean hideGui,
        boolean playerListVisible,
        boolean debugScreenVisible,
        boolean levelMissing
    ) {
        return hideGui || playerListVisible || debugScreenVisible || levelMissing;
    }
}
