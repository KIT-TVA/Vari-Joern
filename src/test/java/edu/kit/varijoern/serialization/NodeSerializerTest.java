package edu.kit.varijoern.serialization;

import org.junit.jupiter.api.Test;
import org.prop4j.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeSerializerTest {
    @Test
    void serializeSingleLiteral() {
        NodeSerializer serializer = new NodeSerializer();
        Literal literal = new Literal("literal");
        String result = serializer.serialize(literal);
        assertEquals("literal", result);
    }

    @Test
    void serializeNegativeLiteral() {
        NodeSerializer serializer = new NodeSerializer();
        Literal literal = new Literal("literal", false);
        String result = serializer.serialize(literal);
        assertEquals("!(literal)", result);
    }

    @Test
    void serializeLiteralWithReservedCharacters() {
        NodeSerializer serializer = new NodeSerializer();
        Literal literal = new Literal("literal&");
        String result = serializer.serialize(literal);
        assertEquals("\"literal&\"", result);
    }

    @Test
    void serializeLiteralWithNonAlphaNumericCharacters() {
        NodeSerializer serializer = new NodeSerializer();
        Literal literal = new Literal("literal$123");
        String result = serializer.serialize(literal);
        assertEquals("\"literal$123\"", result);
    }

    @Test
    void serializeLiteralWithQuote() {
        NodeSerializer serializer = new NodeSerializer();
        Literal literal = new Literal("literal\"");
        String result = serializer.serialize(literal);
        assertEquals("\"literal\\\"\"", result);
    }

    @Test
    void serializeLiteralWithBackslash() {
        NodeSerializer serializer = new NodeSerializer();
        Literal literal = new Literal("literal\\");
        String result = serializer.serialize(literal);
        assertEquals("\"literal\\\\\"", result);
    }

    @Test
    void serializeAndOperator() {
        NodeSerializer serializer = new NodeSerializer();
        And andNode = new And(new Literal("literal1"), new Literal("literal2"));
        String result = serializer.serialize(andNode);
        assertEquals("&(literal1 literal2)", result);
    }

    @Test
    void serializeOrOperator() {
        NodeSerializer serializer = new NodeSerializer();
        Or orNode = new Or(new Literal("literal1"), new Literal("literal2"));
        String result = serializer.serialize(orNode);
        assertEquals("|(literal1 literal2)", result);
    }

    @Test
    void serializeNotOperator() {
        NodeSerializer serializer = new NodeSerializer();
        Not notNode = new Not(new Literal("literal"));
        String result = serializer.serialize(notNode);
        assertEquals("!(literal)", result);
    }

    @Test
    void serializeEmptyAndOperator() {
        NodeSerializer serializer = new NodeSerializer();
        And andNode = new And();
        String result = serializer.serialize(andNode);
        assertEquals("&()", result);
    }

    @Test
    void serializeEmptyOrOperator() {
        NodeSerializer serializer = new NodeSerializer();
        Or orNode = new Or();
        String result = serializer.serialize(orNode);
        assertEquals("|()", result);
    }

    @Test
    void serializeNestedOperators() {
        NodeSerializer serializer = new NodeSerializer();
        And andNode = new And(new Literal("literal1"),
                new Or(new Literal("literal1"), new Literal("literal2")), new Not(new Literal("literal3")));
        String result = serializer.serialize(andNode);
        assertEquals("&(literal1 |(literal1 literal2) !(literal3))", result);
    }

    @Test
    void serializeUnsupportedOperatorThrowsException() {
        NodeSerializer serializer = new NodeSerializer();
        Node unsupportedNode = new Choose(2, new Literal("literal1"), new Literal("literal2"));
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(unsupportedNode));
    }
}
