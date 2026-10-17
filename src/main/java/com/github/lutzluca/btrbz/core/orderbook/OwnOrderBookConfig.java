package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.ConfigUi;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.gui.image.ImageRenderer;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import net.minecraft.network.chat.Component;

public final class OwnOrderBookConfig {
    @SerialEntry
    public Mode mode = Mode.Detailed;
    @SerialEntry
    public boolean showRemainingInNativeLore;

    public OptionGroup createGroup(Runnable modeChanged) {
        var modeText = ConfigUi.paragraphs(
            ConfigUi.text("Mark price levels containing your tracked orders in both widgets and native Bazaar lore. "
                + "Off hides personal cues everywhere and keeps all public levels and quantities."),
            ConfigUi.text("Highlight marks matching widget prices. Detailed also shows your count in parentheses. "
                + "9 (2) means 9 orders in total, 2 of them yours."));
        var off = ConfigImages.OwnOrdersEmbeddedOff.description(modeText);
        var highlight = ConfigImages.OwnOrdersEmbeddedHighlight.description(modeText);
        var detailed = ConfigImages.OwnOrdersEmbeddedDetailed.description(modeText);
        var selectedModeDescription = switch (this.mode) {
            case Off -> off;
            case Highlight -> highlight;
            case Detailed -> detailed;
        };
        var modePreview = new PreviewDescription(selectedModeDescription);
        var modeOption = Option.<Mode>createBuilder()
            .name(Component.literal("Own orders"))
            .description(modePreview)
            .addListener((option, event) -> modePreview.selected = switch (option.pendingValue()) {
                case Off -> off;
                case Highlight -> highlight;
                case Detailed -> detailed;
            })
            .binding(Mode.Detailed, () -> this.mode, value -> {
                this.mode = value;
                modeChanged.run();
            })
            .controller(option -> EnumControllerBuilder.create(option).enumClass(Mode.class))
            .build();
        var nativeText = ConfigUi.paragraphs(
            ConfigUi.text("Add a line with your order count and remaining items below matching native Bazaar levels. "
                + "When off, append only the count in parentheses. Own orders must be enabled."),
            ConfigUi.note("Uses your last observed Orders-menu state. Remaining items are an estimate, not live."));
        var compact = ConfigImages.OwnOrdersNativeCompact.description(nativeText);
        var remaining = ConfigImages.OwnOrdersNativeRemaining.description(nativeText);
        var nativePreview = new PreviewDescription(this.showRemainingInNativeLore ? remaining : compact);
        var nativeOption = Option.<Boolean>createBuilder()
            .name(Component.literal("Show remaining items in native lore"))
            .description(nativePreview)
            .addListener((option, event) -> nativePreview.selected = option.pendingValue() ? remaining : compact)
            .binding(false, () -> this.showRemainingInNativeLore, value -> this.showRemainingInNativeLore = value)
            .controller(ConfigUi::createBooleanController)
            .build();
        return OptionGroup.createBuilder().name(Component.literal("Own orders in order books"))
            .options(List.of(modeOption, nativeOption)).collapsed(true).build();
    }

    /** YACL retains the focused description. Read its selected image on every draw, including keyboard edits. */
    private static final class PreviewDescription implements OptionDescription {
        private OptionDescription selected;

        private PreviewDescription(OptionDescription selected) {
            this.selected = selected;
        }

        @Override
        public Component text() {
            return this.selected.text();
        }

        @Override
        public CompletableFuture<Optional<ImageRenderer>> image() {
            return this.selected.image();
        }
    }

    public enum Mode {
        Off,
        Highlight,
        Detailed
    }
}
