package edu.kit.varijoern.output;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.prop4j.Node;

import java.io.IOException;

/**
 * Serializes {@link Node} objects to strings using {@link Node#toString()}.
 */
public class NodeSerializer extends StdSerializer<Node> {
    public NodeSerializer() {
        this(null);
    }

    public NodeSerializer(Class<Node> t) {
        super(t);
    }

    @Override
    public void serialize(Node node, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeString(node.toString());
    }
}
