package edu.kit.varijoern.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import edu.kit.varijoern.samplers.Configuration;
import edu.kit.varijoern.serialization.JacksonConfigurationToIdSerializer;
import edu.kit.varijoern.serialization.JacksonNodeSerializer;
import org.prop4j.Node;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Formats the results of the analysis into a JSON format.
 */
public class JSONOutputFormatter implements OutputFormatter {
    @Override
    public void printResults(OutputData results, PrintStream outStream) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        SimpleModule nodeModule = new SimpleModule("NodeSerializer");
        nodeModule.addSerializer(Node.class, new JacksonNodeSerializer());
        nodeModule.addSerializer(Path.class, new PathSerializer());
        nodeModule.addSerializer(Configuration.class, new JacksonConfigurationToIdSerializer());
        objectMapper.registerModule(nodeModule);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outStream, results);
    }
}
