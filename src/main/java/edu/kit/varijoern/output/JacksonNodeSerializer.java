package edu.kit.varijoern.output;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import edu.kit.varijoern.serialization.NodeSerializer;
import org.jetbrains.annotations.NotNull;
import org.prop4j.Node;

import java.io.IOException;

/**
 * Serializes {@link Node} objects to strings using {@link NodeSerializer}.
 */
public class JacksonNodeSerializer extends StdSerializer<Node> {
    public JacksonNodeSerializer() {
        this(null);
    }

    public JacksonNodeSerializer(Class<Node> t) {
        super(t);
    }

    @Override
    public void serialize(@NotNull Node node, @NotNull JsonGenerator jsonGenerator,
                          SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeString(new NodeSerializer().serialize(node));
    }
}
