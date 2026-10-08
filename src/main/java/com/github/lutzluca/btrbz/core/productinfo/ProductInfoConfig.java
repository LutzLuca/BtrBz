package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.core.config.ConfigUi;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import net.minecraft.network.chat.Component;

public final class ProductInfoConfig {
    public boolean enabled = true;
    public boolean itemClickEnabled = true;
    public boolean ctrlShiftEnabled = true;
    public boolean ctrlShiftOnBazaarItems = true;
    public boolean showOutsideBazaar = false;
    public boolean priceTooltipEnabled = true;
    public boolean requireMatchingName = true;
    public Site site = Site.SkyblockBz;

    public Builder<Boolean> createEnabledOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Enable Product Information"))
            .description(OptionDescription.of(Component.literal(
                "Enable external product-page shortcuts and Bazaar price details in item tooltips.")))
            .binding(true, () -> this.enabled, value -> this.enabled = value)
            .controller(ConfigUi::createBooleanController);
    }

    public Builder<Boolean> createItemClickOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Show Product Info Paper on Product Page"))
            .description(ConfigUi.createDescription(
                "Open the selected product on your preferred information site when clicking the Product Info "
                    + "paper in its Bazaar menu.",
                ConfigImages.ProductInfoPaper))
            .binding(true, () -> this.itemClickEnabled, value -> this.itemClickEnabled = value)
            .controller(ConfigUi::createBooleanController);
    }

    public Builder<Boolean> createCtrlShiftOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Enable Product Lookup Click"))
            .description(OptionDescription.of(Component.literal(
                "Hold Ctrl+Shift and click a Bazaar product to open it on your preferred information site.")))
            .binding(true, () -> this.ctrlShiftEnabled, value -> this.ctrlShiftEnabled = value)
            .controller(ConfigUi::createBooleanController);
    }

    public Builder<Boolean> createCtrlShiftOnBazaarItemsOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Lookup Bazaar Menu Items"))
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "Allow Product Lookup Click on items inside Bazaar menus. A normal click keeps its usual "
                        + "Bazaar or bookmark action."),
                ConfigUi.requires("Enable Product Lookup Click"))))
            .binding(
                true,
                () -> this.ctrlShiftOnBazaarItems,
                value -> this.ctrlShiftOnBazaarItems = value)
            .controller(ConfigUi::createBooleanController);
    }

    public Builder<Boolean> createShowOutsideBazaarOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Lookup Items Outside the Bazaar"))
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text("Allow Product Lookup Click in inventories and chests outside the Bazaar."),
                ConfigUi.requires("Enable Product Lookup Click"))))
            .binding(false, () -> this.showOutsideBazaar, value -> this.showOutsideBazaar = value)
            .controller(ConfigUi::createBooleanController);
    }

    public Builder<Boolean> createPriceTooltipOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Show Price Tooltips"))
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "Add the best current buy-order and sell-offer prices to Bazaar product tooltips."),
                ConfigUi
                    .note("Hold Shift to show the total for a stack or the quantity represented by a menu item."))))
            .binding(
                true,
                () -> this.priceTooltipEnabled,
                value -> this.priceTooltipEnabled = value)
            .controller(ConfigUi::createBooleanController);
    }

    public Builder<Site> createSiteOption() {
        return Option
            .<Site>createBuilder()
            .name(Component.literal("Preferred Information Site"))
            .description(OptionDescription.of(Component.literal(
                "Choose the external website used by the Product Info paper and Product Lookup Click.")))
            .binding(
                Site.SkyblockBz,
                () -> this.site != null ? this.site : Site.SkyblockBz,
                site -> this.site = site)
            .controller(Site::controller);
    }

    public Builder<Boolean> createRequireMatchingNameOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Require Matching Displayed Names"))
            .description(ConfigUi.createDescription(
                "Check item names before showing prices or allowing product lookup. "
                    + "Colors and formatting are ignored."))
            .binding(true, () -> this.requireMatchingName, value -> this.requireMatchingName = value)
            .controller(ConfigUi::createBooleanController);
    }

    public OptionGroup createGroup() {
        var enabledBuilder = this.createEnabledOption();
        var ctrlShiftGroup = new OptionGrouping(this.createCtrlShiftOption()).addOptions(
            this.createCtrlShiftOnBazaarItemsOption(),
            this.createShowOutsideBazaarOption());
        var rootGroup = new OptionGrouping(enabledBuilder)
            .addOptions(this.createItemClickOption())
            .addSubgroups(ctrlShiftGroup)
            .addOptions(this.createPriceTooltipOption(), this.createRequireMatchingNameOption(),
                this.createSiteOption());

        return OptionGroup
            .createBuilder()
            .name(Component.literal("Product Information"))
            .description(ConfigUi.createDescription(
                "View current prices in item tooltips or open a Bazaar product on an external information site.",
                ConfigImages.ProductInfo))
            .options(rootGroup.build())
            .collapsed(true)
            .build();
    }

    public enum Site {
        Coflnet("https://sky.coflnet.com/item/%s?range=day"),
        SkyblockBz("https://skyblock.bz/product/%s"),
        SkyblockFinance("https://skyblock.finance/items/%s");

        private final String urlFormat;

        Site(String urlFormat) {
            this.urlFormat = urlFormat;
        }

        public static EnumControllerBuilder<Site> controller(Option<Site> option) {
            return EnumControllerBuilder
                .create(option)
                .enumClass(Site.class)
                .formatValue(site -> Component
                    .literal("Use site: ")
                    .append(Component
                        .literal(site.displayName())
                        .withStyle(UiStyles.action())));
        }

        public String format(String productId) {
            return String.format(this.urlFormat, productId);
        }

        public String displayName() {
            return switch (this) {
                case SkyblockBz -> "Skyblock.bz";
                case SkyblockFinance -> "Skyblock.Finance";
                case Coflnet -> "Coflnet";
            };
        }
    }
}
