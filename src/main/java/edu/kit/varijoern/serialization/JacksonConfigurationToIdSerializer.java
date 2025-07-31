package edu.kit.varijoern.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import edu.kit.varijoern.samplers.Configuration;
import edu.kit.varijoern.samplers.SampleTracker;

import java.io.IOException;

/**
 * Serializes a {@link Configuration} object to its index, as provided by a {@link SampleTracker}.
 */
public class JacksonConfigurationToIdSerializer extends StdSerializer<Configuration> {
    public JacksonConfigurationToIdSerializer() {
        this(null);
    }

    public JacksonConfigurationToIdSerializer(Class<Configuration> t) {
        super(t);
    }

    @Override
    public void serialize(Configuration value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeNumber(value.index());
    }
}
