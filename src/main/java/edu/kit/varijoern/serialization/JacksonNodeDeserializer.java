package edu.kit.varijoern.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.prop4j.Node;

import java.text.ParseException;

public class JacksonNodeDeserializer extends StdDeserializer<Node> {
    public JacksonNodeDeserializer() {
        this(null);
    }

    public JacksonNodeDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Node deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
            throws java.io.IOException {
        try {
            return new NodeDeserializer(jsonParser.getValueAsString()).deserialize();
        } catch (ParseException e) {
            throw new JsonMappingException(jsonParser, "Failed to parse node", e);
        }
    }
}
