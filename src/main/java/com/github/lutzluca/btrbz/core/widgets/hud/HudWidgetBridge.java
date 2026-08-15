package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHost;
import com.github.lutzluca.btrbz.core.widgets.runtime.WidgetHostOptions;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

public final class HudWidgetBridge {
    private HudWidgetBridge() {}

    public static void register(Identifier elementId, WidgetHost host) {
        HudElementRegistry.addLast(elementId, (context, tickCounter) -> {
            render(host, context, tickCounter.getGameTimeDeltaPartialTick(false));
        });
    }

    private static void render(WidgetHost host, GuiGraphicsExtractor graphics, float partialTicks) {
        var client = Minecraft.getInstance();
        if (shouldSuppressHud(
            client.options.hideGui,
            client.options.keyPlayerList.isDown(),
            client.getDebugOverlay().showDebugScreen(),
            client.level == null)) {
            return;
        }

        boolean generalContainer = client.screen instanceof AbstractContainerScreen<?>
            && !ScreenInfoHelper.inBazaar();

        if (client.screen != null && !(client.screen instanceof ChatScreen) && !generalContainer) {
            return;
        }

        var window = client.getWindow();
        host.render(
            graphics,
            -1,
            -1,
            partialTicks,
            new WidgetCanvas(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight()),
            WidgetHostOptions.runtime(false),
            null);
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
