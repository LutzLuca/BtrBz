package com.github.lutzluca.btrbz.data.conversions;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.network.chat.Component;

/**
 * Reads the indexed list syntax used by legacy NEU item tags, for example
 * {@code [0:"first",1:"second"]}. Adapted from Skyblocker's LGPL-3.0
 * LegacyStringNbtReader.
 */
final class LegacyNbtParser {
    private static final SimpleCommandExceptionType TRAILING = new SimpleCommandExceptionType(
        Component.translatable("argument.nbt.trailing")
    );
    private static final SimpleCommandExceptionType EXPECTED_KEY = new SimpleCommandExceptionType(
        Component.translatable("argument.nbt.expected.key")
    );
    private static final SimpleCommandExceptionType EXPECTED_VALUE = new SimpleCommandExceptionType(
        Component.translatable("argument.nbt.expected.value")
    );
    private static final Dynamic2CommandExceptionType LIST_MIXED = new Dynamic2CommandExceptionType(
        (received, expected) -> Component.translatableEscape("argument.nbt.list.mixed", received, expected)
    );
    private static final Dynamic2CommandExceptionType ARRAY_MIXED = new Dynamic2CommandExceptionType(
        (received, expected) -> Component.translatableEscape("argument.nbt.array.mixed", received, expected)
    );
    private static final DynamicCommandExceptionType ARRAY_INVALID = new DynamicCommandExceptionType(
        type -> Component.translatableEscape("argument.nbt.array.invalid", type)
    );
    private static final Pattern DOUBLE_IMPLICIT = Pattern.compile(
        "[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DOUBLE = Pattern.compile(
        "[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FLOAT = Pattern.compile(
        "[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BYTE = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern INT = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)");

    private final StringReader reader;

    private LegacyNbtParser(String value) {
        this.reader = new StringReader(value);
    }

    static CompoundTag parse(String value) throws CommandSyntaxException {
        return new LegacyNbtParser(value).readCompound();
    }

    private CompoundTag readCompound() throws CommandSyntaxException {
        var result = this.parseCompound();
        this.reader.skipWhitespace();
        if (this.reader.canRead()) {
            throw TRAILING.createWithContext(this.reader);
        }
        return result;
    }

    private CompoundTag parseCompound() throws CommandSyntaxException {
        this.expect('{');
        var result = new CompoundTag();
        this.reader.skipWhitespace();
        while (this.reader.canRead() && this.reader.peek() != '}') {
            int cursor = this.reader.getCursor();
            var key = this.readString();
            if (key.isEmpty()) {
                this.reader.setCursor(cursor);
                throw EXPECTED_KEY.createWithContext(this.reader);
            }
            this.expect(':');
            result.put(key, this.parseElement());
            if (!this.readComma()) {
                break;
            }
            if (!this.reader.canRead()) {
                throw EXPECTED_KEY.createWithContext(this.reader);
            }
        }
        this.expect('}');
        return result;
    }

    private Tag parseElement() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw EXPECTED_VALUE.createWithContext(this.reader);
        }
        return switch (this.reader.peek()) {
            case '{' -> this.parseCompound();
            case '[' -> this.parseArray();
            default -> this.parsePrimitiveElement();
        };
    }

    private Tag parseArray() throws CommandSyntaxException {
        return this.reader.canRead(3)
            && !StringReader.isQuotedStringStart(this.reader.peek(1))
            && this.reader.peek(2) == ';'
            ? this.parsePrimitiveArray()
            : this.parseList();
    }

    private ListTag parseList() throws CommandSyntaxException {
        this.expect('[');
        var result = new ListTag();
        TagType<?> elementType = null;
        this.reader.skipWhitespace();
        while (this.reader.canRead() && this.reader.peek() != ']') {
            int originalCursor = this.reader.getCursor();
            while (this.reader.canRead() && Character.isDigit(this.reader.peek())) {
                this.reader.skip();
            }
            if (this.reader.canRead() && this.reader.peek() == ':') {
                this.reader.skip();
                this.reader.skipWhitespace();
            } else {
                this.reader.setCursor(originalCursor);
            }

            int elementCursor = this.reader.getCursor();
            var element = this.parseElement();
            if (elementType == null) {
                elementType = element.getType();
            } else if (element.getType() != elementType) {
                this.reader.setCursor(elementCursor);
                throw LIST_MIXED.createWithContext(
                    this.reader,
                    element.getType().getPrettyName(),
                    elementType.getPrettyName()
                );
            }
            result.add(element);
            if (!this.readComma()) {
                break;
            }
        }
        this.expect(']');
        return result;
    }

    private Tag parsePrimitiveArray() throws CommandSyntaxException {
        this.expect('[');
        int typeCursor = this.reader.getCursor();
        char type = this.reader.read();
        this.reader.read();
        this.reader.skipWhitespace();
        return switch (type) {
            case 'B' -> new ByteArrayTag(this.toByteArray(this.readArray(ByteArrayTag.TYPE, ByteTag.TYPE)));
            case 'I' -> new IntArrayTag(this.readArray(IntArrayTag.TYPE, IntTag.TYPE)
                .stream().mapToInt(Number::intValue).toArray());
            case 'L' -> new LongArrayTag(this.readArray(LongArrayTag.TYPE, LongTag.TYPE)
                .stream().mapToLong(Number::longValue).toArray());
            default -> {
                this.reader.setCursor(typeCursor);
                throw ARRAY_INVALID.createWithContext(this.reader, String.valueOf(type));
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T extends Number> List<T> readArray(TagType<?> arrayType, TagType<?> elementType)
        throws CommandSyntaxException {
        var values = new ArrayList<T>();
        while (this.reader.canRead() && this.reader.peek() != ']') {
            int cursor = this.reader.getCursor();
            var element = this.parseElement();
            if (element.getType() != elementType) {
                this.reader.setCursor(cursor);
                throw ARRAY_MIXED.createWithContext(
                    this.reader,
                    element.getType().getPrettyName(),
                    arrayType.getPrettyName()
                );
            }
            var numeric = (NumericTag) element;
            if (elementType == ByteTag.TYPE) {
                values.add((T) Byte.valueOf(numeric.byteValue()));
            } else if (elementType == LongTag.TYPE) {
                values.add((T) Long.valueOf(numeric.longValue()));
            } else {
                values.add((T) Integer.valueOf(numeric.intValue()));
            }
            if (!this.readComma()) {
                break;
            }
        }
        this.expect(']');
        return values;
    }

    private Tag parsePrimitiveElement() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw EXPECTED_VALUE.createWithContext(this.reader);
        }
        if (StringReader.isQuotedStringStart(this.reader.peek())) {
            return StringTag.valueOf(this.reader.readQuotedString());
        }
        int cursor = this.reader.getCursor();
        var value = this.reader.readUnquotedString();
        if (value.isEmpty()) {
            this.reader.setCursor(cursor);
            throw EXPECTED_VALUE.createWithContext(this.reader);
        }
        return this.parsePrimitive(value);
    }

    private Tag parsePrimitive(String value) {
        try {
            if (FLOAT.matcher(value).matches()) {
                return FloatTag.valueOf(Float.parseFloat(value.substring(0, value.length() - 1)));
            }
            if (BYTE.matcher(value).matches()) {
                return ByteTag.valueOf(Byte.parseByte(value.substring(0, value.length() - 1)));
            }
            if (LONG.matcher(value).matches()) {
                return LongTag.valueOf(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (SHORT.matcher(value).matches()) {
                return ShortTag.valueOf(Short.parseShort(value.substring(0, value.length() - 1)));
            }
            if (INT.matcher(value).matches()) {
                return IntTag.valueOf(Integer.parseInt(value));
            }
            if (DOUBLE.matcher(value).matches()) {
                return DoubleTag.valueOf(Double.parseDouble(value.substring(0, value.length() - 1)));
            }
            if (DOUBLE_IMPLICIT.matcher(value).matches()) {
                return DoubleTag.valueOf(Double.parseDouble(value));
            }
            if ("true".equalsIgnoreCase(value)) {
                return ByteTag.ONE;
            }
            if ("false".equalsIgnoreCase(value)) {
                return ByteTag.ZERO;
            }
        } catch (NumberFormatException _) {
            // Preserve malformed numeric-looking values as strings, matching vanilla's parser.
        }
        return StringTag.valueOf(value);
    }

    private String readString() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw EXPECTED_KEY.createWithContext(this.reader);
        }
        return this.reader.readString();
    }

    private boolean readComma() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == ',') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        }
        return false;
    }

    private void expect(char expected) throws CommandSyntaxException {
        this.reader.skipWhitespace();
        this.reader.expect(expected);
    }

    private byte[] toByteArray(List<Number> numbers) {
        var values = new byte[numbers.size()];
        for (int index = 0; index < numbers.size(); index++) {
            values[index] = numbers.get(index).byteValue();
        }
        return values;
    }
}
