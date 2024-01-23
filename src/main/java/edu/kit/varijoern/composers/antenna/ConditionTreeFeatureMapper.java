package edu.kit.varijoern.composers.antenna;

import edu.kit.varijoern.composers.FeatureMapper;
import org.prop4j.Node;

import java.nio.file.Path;
import java.util.*;

/**
 * A feature mapper for the Antenna composer. Builds a tree of {@code //#if} conditions for each file.
 */
public class ConditionTreeFeatureMapper implements FeatureMapper {
    private final Map<Path, ConditionTree> trees = new HashMap<>();

    /**
     * Builds a condition tree for the specified file contents and stores them for use by
     * {@link ConditionTreeFeatureMapper#getPresenceCondition(Path, int)}. The specified path is used to identify the tree.
     *
     * @param path  the path to the file whose content is specified
     * @param lines the lines of the file
     * @return {@code true} if the condition tree was successfully created, {@code false} otherwise
     */
    public boolean tryAddFile(Path path, List<String> lines) {
        ConditionTree tree;
        try {
            tree = new ConditionTree(lines);
            System.out.println(path);
            System.out.println(tree);
        } catch (ConditionTreeException e) {
            System.err.printf("Failed to create condition tree for file %s: %s%n", path, e.getMessage());
            return false;
        }
        this.trees.put(path, tree);
        return true;
    }

    @Override
    public Optional<Node> getPresenceCondition(Path file, int lineNumber) {
        return Optional.ofNullable(this.trees.get(file)).map(tree -> tree.getConditionOfLine(lineNumber));
    }
}
