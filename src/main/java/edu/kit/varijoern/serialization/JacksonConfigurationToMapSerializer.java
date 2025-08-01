package edu.kit.varijoern.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import edu.kit.varijoern.samplers.Configuration;

import java.io.IOException;

/**
 * Serializes a {@link Configuration} object to a map of enabled features.
 * This serializer outputs the enabled features as a JSON object where keys are feature names
 * and values are booleans indicating whether the feature is enabled (true) or disabled (false).
 */
public class JacksonConfigurationToMapSerializer extends StdSerializer<Configuration> {
    public JacksonConfigurationToMapSerializer() {
        this(null);
    }

    public JacksonConfigurationToMapSerializer(Class<Configuration> t) {
        super(t);
    }

    @Override
    public void serialize(Configuration value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeObject(value.enabledFeatures());
    }
}
