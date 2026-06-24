package com.github.lutzluca.btrbz.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameUtilsScoreboardTest {

    @Nested
    @DisplayName("stripScoreboardFormattingCodes")
    class StripScoreboardFormattingCodes {

        @Test
        void removesNonstandardOwnerFormattingToken() {
            var line = "Purse: \u00a761,395,2\u00a7j\u00a7639,458";
            var stripped = GameUtils.stripScoreboardFormattingCodes(line);
            var parsed = Utils.parseUsFormattedNumber(stripped.replace("Purse:", "").trim());

            assertEquals("Purse: 1,395,239,458", stripped);
            assertTrue(parsed.isSuccess());
            assertEquals(1_395_239_458L, parsed.get().longValue());
        }
    }
}
