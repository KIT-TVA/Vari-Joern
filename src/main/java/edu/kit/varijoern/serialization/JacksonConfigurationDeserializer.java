package edu.kit.varijoern.serialization;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import edu.kit.varijoern.samplers.Configuration;
import edu.kit.varijoern.samplers.SampleTracker;

import java.io.IOException;
import java.util.Map;

/**
 * Deserializes a JSON representation of a configuration into a {@link Configuration} object.
 * This deserializer expects the JSON to be a map where keys are feature names and values are booleans
 * indicating whether the feature is enabled (true) or disabled (false).
 * It uses a {@link SampleTracker} to track the configuration and assign it an index.
 */
public class JacksonConfigurationDeserializer extends StdDeserializer<Configuration> {
    public JacksonConfigurationDeserializer() {
        this(null);
    }

    public JacksonConfigurationDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Configuration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        Map<String, Boolean> rawConfiguration = p.readValueAs(new TypeReference<Map<String, Boolean>>() {
        });
        if (rawConfiguration == null) {
            return null;
        }
        SampleTracker sampleTracker = (SampleTracker) ctxt.getAttribute("sampleTracker");
        return sampleTracker.trackConfiguration(rawConfiguration);
    }
}
