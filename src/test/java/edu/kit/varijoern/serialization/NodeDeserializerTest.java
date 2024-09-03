package edu.kit.varijoern.serialization;

import org.junit.jupiter.api.Test;
import org.prop4j.*;

import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.*;

class NodeDeserializerTest {
    @Test
    void deserializeEmptyInputThrowsException() {
        NodeDeserializer deserializer = new NodeDeserializer("");
        assertThrows(ParseException.class, deserializer::deserialize);
    }

    @Test
    void deserializeWhitespaceThrowsException() {
        NodeDeserializer deserializer = new NodeDeserializer(" ");
        assertThrows(ParseException.class, deserializer::deserialize);
    }

    @Test
    void deserializeWithLeadingAndTrailingWhitespace() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer(" literal ");
        Node node = deserializer.deserialize();
        assertInstanceOf(Literal.class, node);
        assertEquals("literal", ((Literal) node).var.toString());
    }

    @Test
    void deserializeSingleLiteral() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("literal");
        Node node = deserializer.deserialize();
        assertInstanceOf(Literal.class, node);
        assertEquals("literal", ((Literal) node).var.toString());
    }

    @Test
    void deserializeNegativeLiteral() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("!(literal)");
        Node node = deserializer.deserialize();
        assertInstanceOf(Literal.class, node);
        assertEquals("literal", ((Literal) node).var.toString());
        assertFalse(((Literal) node).positive);
    }

    @Test
    void deserializeLiteralWithOperatorInName() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("\"literal&\"");
        Node node = deserializer.deserialize();
        assertInstanceOf(Literal.class, node);
        assertEquals("literal&", ((Literal) node).var.toString());
    }

    @Test
    void deserializeLiteralWithQuoteInName() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("\"literal\\\"\"");
        Node node = deserializer.deserialize();
        assertInstanceOf(Literal.class, node);
        assertEquals("literal\"", ((Literal) node).var.toString());
    }

    @Test
    void deserializeLiteralWithUnescapedQuoteInNameThrowsException() {
        NodeDeserializer deserializer = new NodeDeserializer("\"lite\"ral\"");
        assertThrows(ParseException.class, deserializer::deserialize);
    }

    @Test
    void deserializeLiteralWithBackslashInName() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("\"literal\\\\\"");
        Node node = deserializer.deserialize();
        assertInstanceOf(Literal.class, node);
        assertEquals("literal\\", ((Literal) node).var.toString());
    }

    @Test
    void deserializeAndOperator() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("&(literal1 literal2)");
        Node node = deserializer.deserialize();
        assertInstanceOf(And.class, node);
        assertEquals(2, node.getChildren().length);
    }

    @Test
    void deserializeEmptyAnd() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("&()");
        Node node = deserializer.deserialize();
        assertInstanceOf(And.class, node);
        assertEquals(0, node.getChildren().length);
    }

    @Test
    void deserializeOrOperator() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("|(literal1 literal2)");
        Node node = deserializer.deserialize();
        assertInstanceOf(Or.class, node);
        assertEquals(2, node.getChildren().length);
    }

    @Test
    void deserializeEmptyOr() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("|()");
        Node node = deserializer.deserialize();
        assertInstanceOf(Or.class, node);
        assertEquals(0, node.getChildren().length);
    }

    @Test
    void deserializeNotOperator() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("!(&(literal1 literal2))");
        Node node = deserializer.deserialize();
        assertInstanceOf(Not.class, node);
        Node child = node.getChildren()[0];
        assertInstanceOf(And.class, child);
        assertEquals(2, child.getChildren().length);
    }

    @Test
    void deserializeNotOperatorWithMultipleChildrenThrowsException() {
        NodeDeserializer deserializer = new NodeDeserializer("!(literal1 literal2)");
        assertThrows(ParseException.class, deserializer::deserialize);
    }

    @Test
    void deserializeNotOperatorWithoutChildThrowsException() {
        NodeDeserializer deserializer = new NodeDeserializer("!()");
        assertThrows(ParseException.class, deserializer::deserialize);
    }

    @Test
    void deserializeNestedOperators() throws ParseException {
        NodeDeserializer deserializer = new NodeDeserializer("&(literal1 |(literal1 literal2) !(literal3))");
        Node node = deserializer.deserialize();
        assertInstanceOf(And.class, node);
        Node[] children = node.getChildren();
        assertEquals(3, children.length);
        assertInstanceOf(Literal.class, children[0]);
        assertInstanceOf(Or.class, children[1]);
        assertInstanceOf(Literal.class, children[2]);
        assertFalse(((Literal) children[2]).positive);
    }

    @Test
    void deserializeUnsupportedOperatorThrowsException() {
        NodeDeserializer deserializer = new NodeDeserializer("$(literal)");
        assertThrows(ParseException.class, deserializer::deserialize);
    }

    @Test
    void deserializeUnclosedQuoteThrowsException() {
        NodeDeserializer deserializer = new NodeDeserializer("\"unclosed");
        assertThrows(ParseException.class, deserializer::deserialize);
    }

    @Test
    void deserializeUnclosedOperatorThrowsException() {
        NodeDeserializer deserializer = new NodeDeserializer("&(literal1");
        assertThrows(ParseException.class, deserializer::deserialize);
    }

    @Test
    void deserializeUnclosedEmptyOperatorThrowsException() {
        NodeDeserializer deserializer = new NodeDeserializer("&(");
        assertThrows(ParseException.class, deserializer::deserialize);
    }
}
