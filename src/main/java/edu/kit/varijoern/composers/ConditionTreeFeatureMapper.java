package edu.kit.varijoern.composers;

import org.prop4j.Node;

import java.nio.file.Path;
import java.util.*;

public class ConditionTreeFeatureMapper implements FeatureMapper {
    private final Map<Path, ConditionTree> trees = new HashMap<>();

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
    public Optional<Node> getCondition(Path file, int lineNumber) {
        return Optional.ofNullable(this.trees.get(file)).map(tree -> tree.getConditionOfLine(lineNumber));
    }
}
