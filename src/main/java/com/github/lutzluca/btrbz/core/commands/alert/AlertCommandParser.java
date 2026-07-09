package com.github.lutzluca.btrbz.core.commands.alert;

import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.AlertType;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Binary;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.BinaryOperator;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Literal;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Reference;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.ReferenceType;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AlertCommandParser {

    public AlertCommand parse(String args) throws ParseException {
        var tokens = args == null ? new String[0] : args.trim().split("\\s+", 3);
        if (tokens.length < 1 || tokens[0].isBlank()) {
            throw new ParseException("Missing product id");
        }
        if (tokens.length < 2 || tokens[1].isBlank()) {
            throw new ParseException("Missing order type");
        }
        if (tokens.length < 3 || tokens[2].isBlank()) {
            throw new ParseException("Missing price expression");
        }

        return this.parse(tokens[0], tokens[1], tokens[2]);
    }

    public AlertCommand parse(String productId, String type, String priceExpression) throws ParseException {
        var now = System.currentTimeMillis();
        if (productId == null || productId.isBlank()) {
            throw new ParseException("Missing product id");
        }
        if (type == null || type.isBlank()) {
            throw new ParseException("Missing order type");
        }
        if (priceExpression == null || priceExpression.isBlank()) {
            throw new ParseException("Missing price expression");
        }

        var alertType = AlertType
            .fromIdentifier(type.toLowerCase(Locale.US))
            .getOrElseThrow(err -> new ParseException(err.getMessage()));

        return new AlertCommand(
            now,
            productId.toUpperCase(Locale.US),
            alertType,
            this.parsePriceExpression(priceExpression.toLowerCase(Locale.US))
        );
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
        long timestamp, String productId, AlertType type, PriceExpression expr
    ) {

        public Try<ResolvedAlertArgs> resolve(BazaarData data) {
            return Try
                .of(() -> data
                    .resolveProductId(this.productId)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or unrecognized product id " + '"' + this.productId + '"'
                    )))
                .flatMap(product -> this.expr
                    .resolve(product, this.type, data)
                    .map(price -> new ResolvedAlertArgs(
                        this.timestamp,
                        product,
                        this.type,
                        price
                    )));
        }
    }

    public record ResolvedAlertArgs(
        long timestamp, IndexedProduct product, AlertType type, double price
    ) {

        public String productName() {
            return this.product.strippedName();
        }

        public String visualName() {
            return this.product.formattedName();
        }

        public String productId() {
            return this.product.productId();
        }

        public Try<ResolvedAlertArgs> validate() {
            if (this.price <= 0.0) {
                return Try.failure(new IllegalArgumentException(
                    "Price Expression evaluates to an invalid price of " + '"' + Utils.formatDecimal(
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
