package edu.kit.varijoern.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.prop4j.*;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deserializes a string representation of a {@link Node} into a {@link Node} object.
 * <h1>Format</h1>
 * The format of the input string is as follows:
 * <ul>
 *     <li>Leading and trailing whitespace is ignored.</li>
 *     <li>Operators are represented by a single character: {@code &} for {@link And}, {@code |} for {@link Or},
 *     and {@code !} for {@link Not}.</li>
 *     <li>Literal names are sequences of alphanumeric characters, underscores, and hyphens. They may be quoted with
 *     double quotes.</li>
 *     <li>Quoted literals may contain any character. Backslashes and quotes must be escaped with a backslash.
 *     Whitespaces in quoted literals are not ignored.</li>
 *     <li>Operators are followed by a list of children enclosed in parentheses. Children are separated by whitespace.
 *     {@link Not} operators must have exactly one child. {@link And} and {@link Or} operators may have no children.
 *     </li>
 *     <li>Example: {@code |(&("a b" -(c)) d)}</li>
 * </ul>
 * Note that an empty {@link And} node represents a tautology, while an empty {@link Or} node represents
 * a contradiction.
 */
public class NodeDeserializer {
    private static final Set<Character> OPERATORS = Set.of('&', '|', '!');

    private final String input;
    private int index = 0;

    /**
     * Creates a new deserializer for the given input string.
     *
     * @param input the input string
     */
    public NodeDeserializer(String input) {
        this.input = input;
    }

    /**
     * Deserializes the input string into a {@link Node} object.
     *
     * @return the deserialized node
     * @throws ParseException if the input string is invalid
     */
    public @NotNull Node deserialize() throws ParseException {
        this.index = 0;
        Node node = this.deserializeNode();
        if (node == null) {
            throw new ParseException("Empty input", this.index);
        }
        this.skipWhitespace();
        if (this.index < this.input.length()) {
            throw new ParseException("Unexpected input after node", this.index);
        }
        return node;
    }

    private @Nullable Node deserializeNode() throws ParseException {
        this.skipWhitespace();
        if (this.index >= this.input.length()) {
            return null;
        }
        if (OPERATORS.contains(this.input.charAt(this.index))) {
            return this.deserializeOperator();
        } else {
            return this.deserializeLiteral();
        }
    }

    private @NotNull Node deserializeOperator() throws ParseException {
        char operator = this.next();
        this.expect('(');
        int startOfChildren = this.index;
        Node[] children = this.deserializeChildren();
        this.expect(')');
        return switch (operator) {
            case '&' -> new And(children);
            case '|' -> new Or(children);
            case '!' -> {
                if (children.length != 1)
                    throw new ParseException("Not operator must have exactly one child", startOfChildren);
                Node child = children[0];
                if (child instanceof Literal) {
                    ((Literal) child).flip();
                    yield child;
                }
                yield new Not(children[0]);
            }
            default -> throw new ParseException("Unsupported operator: " + operator, startOfChildren - 2);
        };
    }

    private @NotNull Node[] deserializeChildren() throws ParseException {
        List<Node> children = new ArrayList<>();
        this.ensureNotEOI("Unmatched parenthesis");
        while (this.input.charAt(this.index) != ')') {
            Node child = this.deserializeNode();
            if (child == null) {
                throw new ParseException("Unexpected end of input", this.index);
            }
            children.add(child);
            this.skipWhitespace();
            this.ensureNotEOI("Unmatched parenthesis");
        }
        return children.toArray(new Node[0]);
    }

    private Node deserializeLiteral() throws ParseException {
        char nextChar = this.input.charAt(this.index);
        if (nextChar == '"') {
            return new Literal(this.unquote());
        } else if (this.isLegalUnquotedCharacter(nextChar)) {
            return new Literal(this.readName());
        } else {
            throw new ParseException("Unexpected character '" + nextChar, this.index);
        }
    }

    private String unquote() throws ParseException {
        this.expect('"');
        ensureNotEOI("Unclosed quote");
        StringBuilder sb = new StringBuilder();
        while (this.input.charAt(this.index) != '"') {
            if (this.input.charAt(this.index) == '\\') {
                this.index++;
                ensureNotEOI("Unexpected end of input after backslash");
            }
            sb.append(this.input.charAt(this.index));
            this.index++;
            ensureNotEOI("Unclosed quote");
        }
        this.expect('"');
        return sb.toString();
    }

    private String readName() {
        StringBuilder sb = new StringBuilder();
        while (this.index < this.input.length() && !Character.isWhitespace(this.input.charAt(this.index))
                && this.isLegalUnquotedCharacter(this.input.charAt(this.index))) {
            sb.append(this.input.charAt(this.index));
            this.index++;
        }
        return sb.toString();
    }

    private boolean isLegalUnquotedCharacter(char c) {
        return Character.isAlphabetic(c) || Character.isDigit(c) || c == '_' || c == '-';
    }

    private char next() throws ParseException {
        if (this.index >= this.input.length()) {
            throw new ParseException("Unexpected end of input", this.index);
        }
        return this.input.charAt(this.index++);
    }

    private void ensureNotEOI(String msg) throws ParseException {
        if (this.index >= this.input.length()) {
            throw new ParseException(msg, this.index);
        }
    }

    private void expect(char expected) throws ParseException {
        if (this.index >= this.input.length() || this.input.charAt(this.index) != expected) {
            throw new ParseException("Expected '" + expected, this.index);
        }
        this.index++;
    }

    private void skipWhitespace() {
        while (this.index < this.input.length() && Character.isWhitespace(this.input.charAt(this.index))) {
            this.index++;
        }
    }
}
