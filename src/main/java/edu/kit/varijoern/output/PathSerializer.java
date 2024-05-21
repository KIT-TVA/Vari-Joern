package edu.kit.varijoern.output;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Serializes {@link Path} objects to strings. The reason this class is needed is that, by default, Jackson converts
 * relative paths to absolute paths, which leads to incorrect results when evidence paths are serialized.
 */
public class PathSerializer extends StdSerializer<Path> {
    public PathSerializer() {
        this(null);
    }

    public PathSerializer(Class<Path> t) {
        super(t);
    }

    @Override
    public void serialize(Path value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString());
    }
}
