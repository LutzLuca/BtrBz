package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.alert.PriceExpression.Binary;
import com.github.lutzluca.btrbz.core.alert.PriceExpression.BinaryOperator;
import com.github.lutzluca.btrbz.core.alert.PriceExpression.Literal;
import com.github.lutzluca.btrbz.core.alert.PriceExpression.Reference;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PriceExpressionParser {

    private static final int MAX_INPUT_LENGTH = 256;
    private static final int MAX_NESTING_DEPTH = 32;
    private static final int MAX_TOKENS = 128;

    private PriceExpressionParser() {}

    public static Try<PriceExpression> parse(String input) {
        return Try.of(() -> parseChecked(input));
    }

    private static PriceExpression parseChecked(String input) throws ParseException {
        var tokenizer = new Tokenizer(requireInput(input));
        var expression = parseAdditive(tokenizer, 0);
        if (tokenizer.hasNext()) {
            throw new ParseException("Unexpected token after expression: " + tokenizer.peek());
        }
        return expression;
    }

    private static String requireInput(String input) throws ParseException {
        if (input == null || input.isBlank()) {
            throw new ParseException("Enter a price threshold");
        }
        var normalized = input.trim().toLowerCase(Locale.US);
        if (normalized.length() > MAX_INPUT_LENGTH) {
            throw new ParseException("Price expression is too long");
        }
        return normalized;
    }

    private static PriceExpression parseAdditive(Tokenizer tokenizer, int depth) throws ParseException {
        var left = parseMultiplicative(tokenizer, depth);
        while (tokenizer.hasNext() && (tokenizer.peek().equals("+") || tokenizer.peek().equals("-"))) {
            var token = tokenizer.next();
            var operator = token.equals("+") ? BinaryOperator.Add : BinaryOperator.Subtract;
            left = new Binary(left, operator, parseMultiplicative(tokenizer, depth));
        }
        return left;
    }

    private static PriceExpression parseMultiplicative(Tokenizer tokenizer, int depth) throws ParseException {
        var left = parsePrimary(tokenizer, depth);
        while (tokenizer.hasNext() && (tokenizer.peek().equals("*") || tokenizer.peek().equals("/"))) {
            var token = tokenizer.next();
            var operator = token.equals("*") ? BinaryOperator.Multiply : BinaryOperator.Divide;
            left = new Binary(left, operator, parsePrimary(tokenizer, depth));
        }
        return left;
    }

    private static PriceExpression parsePrimary(Tokenizer tokenizer, int depth) throws ParseException {
        if (!tokenizer.hasNext()) {
            throw new ParseException("Expected a value at the end of the expression");
        }
        var token = tokenizer.next();
        if (token.equals("(")) {
            if (depth >= MAX_NESTING_DEPTH) {
                throw new ParseException("Price expression is nested too deeply");
            }
            var expression = parseAdditive(tokenizer, depth + 1);
            if (!tokenizer.hasNext() || !tokenizer.next().equals(")")) {
                throw new ParseException("Unmatched opening parenthesis");
            }
            return expression;
        }
        if (token.equals(")")) {
            throw new ParseException("Unexpected closing parenthesis");
        }
        if (token.equals(PriceSource.Buy.reference())) {
            return new Reference(PriceSource.Buy);
        }
        if (token.equals(PriceSource.Sell.reference())) {
            return new Reference(PriceSource.Sell);
        }
        return new Literal(parseNumber(token));
    }

    private static double parseNumber(String token) throws ParseException {
        try {
            if (token == null || !token.matches("(?:[0-9][0-9,_]*(?:\\.[0-9]*)?|\\.[0-9]+)[kmb]?")) {
                throw new NumberFormatException();
            }
            var cleaned = token.replace(",", "").replace("_", "");
            var multiplier = 1.0;
            var suffix = cleaned.charAt(cleaned.length() - 1);
            if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
                multiplier = switch (suffix) {
                    case 'k' -> 1_000.0;
                    case 'm' -> 1_000_000.0;
                    case 'b' -> 1_000_000_000.0;
                    default -> throw new IllegalStateException();
                };
            }
            var value = Double.parseDouble(cleaned) * multiplier;
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException _) {
            throw new ParseException("Malformed number or price reference: " + token);
        }
    }

    public static final class ParseException extends Exception {

        public ParseException(String message) {
            super(message);
        }
    }

    private static final class Tokenizer {

        private final List<String> tokens = new ArrayList<>();
        private int position;

        private Tokenizer(String input) throws ParseException {
            var current = new StringBuilder();
            for (var ch : input.toCharArray()) {
                if (Character.isWhitespace(ch)) {
                    this.flush(current);
                } else if (ch == '(' || ch == ')' || ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                    this.flush(current);
                    this.tokens.add(String.valueOf(ch));
                } else {
                    current.append(ch);
                }
                if (this.tokens.size() > MAX_TOKENS) {
                    throw new ParseException("Price expression has too many parts");
                }
            }
            this.flush(current);
            if (this.tokens.size() > MAX_TOKENS) {
                throw new ParseException("Price expression has too many parts");
            }
        }

        private void flush(StringBuilder current) {
            if (!current.isEmpty()) {
                this.tokens.add(current.toString());
                current.setLength(0);
            }
        }

        private boolean hasNext() {
            return this.position < this.tokens.size();
        }

        private String peek() {
            return this.hasNext() ? this.tokens.get(this.position) : "";
        }

        private String next() {
            return this.tokens.get(this.position++);
        }
    }
}
