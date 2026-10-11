package com.github.lutzluca.btrbz.core.ui;

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
        var theme = new UiTheme("My Nord", palette);
        var json = ThemeJson.exportTheme(theme);
        Assertions.assertEquals(theme, ThemeJson.importTheme(json).get());
        var document = JsonParser.parseString(json).getAsJsonObject();
        Assertions.assertEquals(20, document.getAsJsonObject("colors").size());
        Assertions.assertEquals("#58A6FF", document.getAsJsonObject("colors").get("matched").getAsString());
        Assertions.assertFalse(document.has("formattedProductNames"));
        Assertions.assertFalse(document.has("widgets"));
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
        future.addProperty("schemaVersion", 2);
        var opacity = original.deepCopy();
        opacity.addProperty("panelOpacity", 1.5);
        var foreign = original.deepCopy();
        foreign.addProperty("type", "editor-theme");
        for (JsonObject invalid : List.of(missing, alpha, numeric, future, opacity, foreign)) {
            Assertions.assertTrue(ThemeJson.importTheme(invalid.toString()).isFailure(), invalid.toString());
        }
        Assertions.assertTrue(ThemeJson.importTheme("not JSON").isFailure());
        Assertions.assertTrue(ThemeJson.importTheme("{}").isFailure());
    }
}
