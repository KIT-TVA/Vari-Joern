package edu.kit.varijoern.composers.antenna;

import edu.kit.varijoern.composers.PresenceConditionMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.prop4j.Node;

import java.nio.file.Path;
import java.util.*;

/**
 * A presence condition mapper for the Antenna composer. Builds a tree of {@code //#if} conditions for each file.
 */
public class ConditionTreePresenceConditionMapper implements PresenceConditionMapper {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Map<Path, ConditionTree> trees = new HashMap<>();

    /**
     * Builds a condition tree for the specified file contents and stores them for use by
     * {@link ConditionTreePresenceConditionMapper#getPresenceCondition(Path, int)}. The specified path is used to
     * identify the tree.
     *
     * @param path  the path to the file whose content is specified
     * @param lines the lines of the file
     */
    public void tryAddFile(Path path, List<String> lines) {
        ConditionTree tree;
        try {
            tree = new ConditionTree(lines);
        } catch (ConditionTreeException e) {
            LOGGER.atWarn().withThrowable(e).log("Failed to create condition tree for file {}", path);
            return;
        }
        this.trees.put(path, tree);
    }

    @Override
    public Optional<Node> getPresenceCondition(Path file, int lineNumber) {
        return Optional.ofNullable(this.trees.get(file)).map(tree -> tree.getConditionOfLine(lineNumber));
    }
}
