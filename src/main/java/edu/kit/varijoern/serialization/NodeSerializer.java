package edu.kit.varijoern.serialization;

import org.prop4j.*;

import java.util.regex.Pattern;

/**
 * Serializes a {@link Node} object into a string representation. The format of the output string is described in
 * {@link NodeDeserializer}.
 * <p>
 * Only {@link Literal}, {@link And}, {@link Or}, and {@link Not} nodes are supported.
 */
public class NodeSerializer {
    protected static final Pattern UNQUOTED_LITERAL_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9-_]+$");

    public String serialize(Node node) {
        StringBuilder sb = new StringBuilder();
        this.serialize(node, sb);
        return sb.toString();
    }

    private void serialize(Node node, StringBuilder sb) {
        if (node.getClass() == Literal.class) {
            this.serializeLiteral((Literal) node, sb);
        } else {
            this.serializeOperator(node, sb);
        }
    }

    private void serializeLiteral(Literal literal, StringBuilder sb) {
        if (!literal.positive) {
            this.serializeOperator(new Not(new Literal(literal.var)), sb);
            return;
        }

        String name = literal.var.toString();
        if (UNQUOTED_LITERAL_NAME_PATTERN.matcher(name).matches()) {
            sb.append(name);
        } else {
            sb.append(this.quote(name));
        }
    }

    private String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void serializeOperator(Node node, StringBuilder sb) {
        String operator;
        if (node.getClass().equals(And.class)) {
            operator = "&";
        } else if (node.getClass().equals(Or.class)) {
            operator = "|";
        } else if (node.getClass().equals(Not.class)) {
            operator = "!";
        } else {
            throw new IllegalArgumentException("Unsupported operator: " + node.getClass());
        }
        sb.append(operator);
        sb.append("(");
        Node[] children = node.getChildren();
        for (int i = 0; i < children.length; i++) {
            Node child = children[i];
            this.serialize(child, sb);
            if (i < children.length - 1) {
                sb.append(" ");
            }
        }
        sb.append(")");
    }
}
