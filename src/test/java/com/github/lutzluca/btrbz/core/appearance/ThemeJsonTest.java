package com.github.lutzluca.btrbz.core.appearance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ThemeJsonTest {
    @Test
    void customThemeRoundTripsEveryRoleAndOpacity() {
        var palette = ThemePreset.Nord.theme().palette().withColor(UiColor.Matched, 0xFF58A6FF)
            .withColor(UiColor.Success, 0xFF44AA88).withOpacity(0.73);
        var theme = new UiTheme("My Nord", palette, false, 61, 73);
        var json = ThemeJson.exportTheme(theme);
        Assertions.assertEquals(theme, ThemeJson.importTheme(json).get());
        var document = JsonParser.parseString(json).getAsJsonObject();
        Assertions.assertEquals(20, document.getAsJsonObject("colors").size());
        Assertions.assertEquals("#58A6FF", document.getAsJsonObject("colors").get("matched").getAsString());
        Assertions.assertFalse(document.get("formattedProductNames").getAsBoolean());
        Assertions.assertEquals(61, document.get("productColorStrength").getAsInt());
        Assertions.assertEquals(73, document.get("orderHighlightOpacity").getAsInt());
        Assertions.assertFalse(document.has("widgets"));
        var zero = theme.withProductNames(false, 0).withOrderHighlightOpacity(0).withPalette(palette.withOpacity(0));
        Assertions.assertEquals(zero, ThemeJson.importTheme(ThemeJson.exportTheme(zero)).get());
    }

    @Test
    void rejectsIncompleteInvalidAndUnsupportedDocuments() {
        var original = JsonParser.parseString(ThemeJson.exportTheme(ThemePreset.Mocha.theme())).getAsJsonObject();
        var missing = original.deepCopy();
        missing.getAsJsonObject("colors").remove("matched");
        var alpha = original.deepCopy();
        alpha.getAsJsonObject("colors").addProperty("matched", "#8858A6FF");
        var numeric = original.deepCopy();
        numeric.getAsJsonObject("colors").addProperty("matched", 0x58A6FF);
        var future = original.deepCopy();
        future.addProperty("schemaVersion", 3);
        var opacity = original.deepCopy();
        opacity.addProperty("panelOpacity", 1.5);
        var foreign = original.deepCopy();
        foreign.addProperty("type", "editor-theme");
        var old = original.deepCopy();
        old.addProperty("schemaVersion", 1);
        var missingPreference = original.deepCopy();
        missingPreference.remove("formattedProductNames");
        var invalidStrength = original.deepCopy();
        invalidStrength.addProperty("productColorStrength", 101);
        var fractional = original.deepCopy();
        fractional.addProperty("orderHighlightOpacity", 75.5);
        var fractionalVersion = original.deepCopy();
        fractionalVersion.addProperty("schemaVersion", 2.5);
        var textVersion = original.deepCopy();
        textVersion.addProperty("schemaVersion", "2");
        var textPreference = original.deepCopy();
        textPreference.addProperty("formattedProductNames", "false");
        var textPercentage = original.deepCopy();
        textPercentage.addProperty("productColorStrength", "0");
        var missingPercentage = original.deepCopy();
        missingPercentage.remove("orderHighlightOpacity");
        for (JsonObject invalid : List.of(missing, alpha, numeric, future, opacity, foreign,
            old, missingPreference, invalidStrength, fractional, fractionalVersion, textVersion,
            textPreference, textPercentage, missingPercentage)) {
            Assertions.assertTrue(ThemeJson.importTheme(invalid.toString()).isFailure(), invalid.toString());
        }
        Assertions.assertTrue(ThemeJson.importTheme("not JSON").isFailure());
        Assertions.assertTrue(ThemeJson.importTheme("{}").isFailure());
    }
}
