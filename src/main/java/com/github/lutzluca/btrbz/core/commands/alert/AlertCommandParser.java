package com.github.lutzluca.btrbz.core.commands.alert;

import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.AlertType;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Binary;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.BinaryOperator;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Literal;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Reference;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.ReferenceType;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.Util;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AlertCommandParser {

    public AlertCommand parse(String args) throws ParseException {
        var now = System.currentTimeMillis();
        if (args.isEmpty()) {
            throw new ParseException("Missing command arguments");
        }

        String[] tokens = args.toLowerCase().trim().split("\\s+");

        int orderTypeIndex = -1;
        AlertType orderType = null;

        for (int i = tokens.length - 1; i >= 0; i--) {
            var type = AlertType.fromIdentifier(tokens[i]);
            if (type.isFailure()) {
                continue;
            }

            orderTypeIndex = i;
            orderType = type.get();
            break;
        }

        if (orderType == null) {
            throw new ParseException("Unrecognized or missing order type");
        }

        if (orderTypeIndex == 0) {
            throw new ParseException("Missing product name");
        }

        if (orderTypeIndex >= tokens.length - 1) {
            throw new ParseException("Missing price expression");
        }

        String[] productTokens = Arrays.copyOfRange(tokens, 0, orderTypeIndex);
        String productName = this.normalizeProductName(productTokens);

        String[] priceTokens = Arrays.copyOfRange(tokens, orderTypeIndex + 1, tokens.length);
        String priceExprString = String.join(" ", priceTokens);

        PriceExpression priceExpression = this.parsePriceExpression(priceExprString);

        return new AlertCommand(now, productName, orderType, priceExpression);
    }

    private String normalizeProductName(String[] tokens) throws ParseException {
        var titleCaseTokens = Arrays
            .stream(tokens)
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
            .collect(Collectors.toList());

        var lastToken = titleCaseTokens.getLast();
        var number = Try.of(() -> Integer.parseInt(lastToken));
        if (number.isSuccess()) {
            var roman = number
                .map(Util::intToRoman)
                .getOrElseThrow(err -> new ParseException("Invalid product name number format: " + '"' + lastToken + '"'));

            titleCaseTokens.set(titleCaseTokens.size() - 1, roman);
        }

        return String.join(" ", titleCaseTokens);
    }

    private PriceExpression parsePriceExpression(String expression) throws ParseException {
        Tokenizer tokenizer = new Tokenizer(expression);

        PriceExpression expr = this.parseAdditive(tokenizer);

        if (tokenizer.hasNext()) {
            throw new ParseException("Unexpected token after expression: " + tokenizer.peek());
        }

        return expr;
    }

    private PriceExpression parseAdditive(Tokenizer tokenizer) throws ParseException {
        PriceExpression left = this.parseMultiplicative(tokenizer);

        while (tokenizer.hasNext()) {
            String token = tokenizer.peek();
            if (!token.equals("+") && !token.equals("-")) {
                break;
            }

            BinaryOperator op = token.equals("+") ? BinaryOperator.Add : BinaryOperator.Subtract;
            tokenizer.next();

            if (!tokenizer.hasNext()) {
                throw new ParseException("Expected operand after '" + token + "'.");
            }

            PriceExpression right = this.parseMultiplicative(tokenizer);
            left = new Binary(left, op, right);
        }

        return left;
    }

    private PriceExpression parseMultiplicative(Tokenizer tokenizer) throws ParseException {
        PriceExpression left = this.parsePrimary(tokenizer);

        while (tokenizer.hasNext()) {
            String token = tokenizer.peek();
            if (!token.equals("*") && !token.equals("/")) {
                break;
            }

            BinaryOperator op = token.equals("*") ? BinaryOperator.Multiply : BinaryOperator.Divide;
            tokenizer.next();

            if (!tokenizer.hasNext()) {
                throw new ParseException("Expected operand after '" + token + "'.");
            }

            PriceExpression right = this.parsePrimary(tokenizer);
            left = new Binary(left, op, right);
        }

        return left;
    }

    private PriceExpression parsePrimary(Tokenizer tokenizer) throws ParseException {
        if (!tokenizer.hasNext()) {
            throw new ParseException("Expected operand but reached end of expression.");
        }
        String token = tokenizer.next();

        if (token.equals("(")) {
            PriceExpression expr = this.parseAdditive(tokenizer);
            if (!tokenizer.hasNext() || !tokenizer.next().equals(")")) {
                throw new ParseException("Unmatched opening parenthesis.");
            }

            return expr;
        }

        return ReferenceType
            .fromIdentifier(token)
            .<PriceExpression>map(Reference::new)
            .orElse(() -> this.parseNumber(token).map(Literal::new))
            .getOrElseThrow(err -> new ParseException(err.getMessage()));
    }

    private Try<Double> parseNumber(String token) {
        return Try
            .of(() -> {
                String cleaned = token.replace(",", "").replace("_", "");
                var lastChar = cleaned.charAt(cleaned.length() - 1);

                return switch (lastChar) {
                    case 'k' -> {
                        cleaned = cleaned.substring(0, cleaned.length() - 1);
                        yield Double.parseDouble(cleaned) * 1_000.0;
                    }
                    case 'm' -> {
                        cleaned = cleaned.substring(0, cleaned.length() - 1);
                        yield Double.parseDouble(cleaned) * 1_000_000.0;
                    }
                    case 'b' -> {
                        cleaned = cleaned.substring(0, cleaned.length() - 1);
                        yield Double.parseDouble(cleaned) * 1_000_000_000.0;
                    }
                    default -> Double.parseDouble(cleaned);
                };
            })
            .map(val -> Math.round(val * 10.0) / 10.0)
            .recoverWith(err -> Try.failure(new ParseException("Malformed number format: " + token)));
    }

    public record AlertCommand(
        long timestamp, String productName, AlertType type, PriceExpression expr
    ) {

        public Try<ResolvedAlertArgs> resolve(BazaarData data) {
            return this.expr
                .resolve(this.productName, this.type, data)
                .map(price -> new ResolvedAlertArgs(
                    this.timestamp,
                    this.productName,
                    data.nameToId(this.productName).get(),
                    this.type,
                    price
                ));
        }
    }

    public record ResolvedAlertArgs(
        long timestamp, String productName, String productId, AlertType type, double price
    ) {

        public Try<ResolvedAlertArgs> validate() {
            if (this.price <= 0.0) {
                return Try.failure(new IllegalArgumentException(
                    "Price Expression evaluates to an invalid price of " + '"' + Util.formatDecimal(
                        this.price,
                        1,
                        true
                    ) + '"' + ". Expected a positive price"));
            }

            return Try.success(this);
        }
    }

    public static class ParseException extends Exception {

        public ParseException(String msg) {
            super(msg);
        }
    }

    private static class Tokenizer {

        private final List<String> tokens;
        private int pos;

        public Tokenizer(String expression) {
            this.tokens = new ArrayList<>();
            this.pos = 0;

            var curr = new StringBuilder();
            for (char ch : expression.toCharArray()) {
                if (Character.isWhitespace(ch)) {
                    if (curr.isEmpty()) {
                        continue;
                    }

                    tokens.add(curr.toString());
                    curr.setLength(0);
                    continue;
                }

                if (ch == '(' || ch == ')' || ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                    if (!curr.isEmpty()) {
                        tokens.add(curr.toString());
                        curr.setLength(0);
                    }

                    tokens.add(String.valueOf(ch));
                    continue;
                }

                curr.append(ch);
            }

            if (!curr.isEmpty()) {
                tokens.add(curr.toString());
            }
        }

        public boolean hasNext() {
            return this.pos < this.tokens.size();
        }

        public String peek() {
            return this.hasNext() ? this.tokens.get(this.pos) : null;
        }

        public String next() {
            return this.tokens.get(this.pos++);
        }
    }
}
