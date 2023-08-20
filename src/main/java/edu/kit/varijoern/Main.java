package edu.kit.varijoern;

import edu.kit.varijoern.config.Config;
import edu.kit.varijoern.config.InvalidConfigException;

import java.io.IOException;
import java.nio.file.Path;

public class Main {
    private static final String USAGE = "Usage: vari-joern [config]";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println(USAGE);
            System.exit(1);
        }

        Config config;
        try {
            config = new Config(Path.of(args[0]));
        } catch (IOException e) {
            System.err.printf("The configuration file could not be read: %s%n", e.getMessage());
            System.exit(1);
        } catch (InvalidConfigException e) {
            System.err.printf("The configuration file could not be parsed:%n%s%n", e.getMessage());
            System.exit(1);
        }
    }
}