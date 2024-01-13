package edu.kit.varijoern.composers.kbuild;

import net.sf.javabdd.BDD;
import org.prop4j.*;
import xtc.lang.cpp.*;
import xtc.tree.Location;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps lines of a single file to presence conditions.
 */
public class LineFeatureMapper {
    private static final Pattern DEFINED_PATTERN = Pattern.compile("\\(defined (.+)\\)|([A-Za-z0-9_]+)");

    private final Map<Integer, Node> linePresenceConditions = new HashMap<>();
    private final int addedLines;

    /**
     * Creates a new {@link LineFeatureMapper} for the specified file.
     *
     * @param inclusionInformation the information about how the file is compiled
     * @param sourcePath           the path to the root of the source directory
     * @param addedLines           the number of lines (#include and #define directives) added to the file by the
     *                             composer
     */
    public LineFeatureMapper(InclusionInformation inclusionInformation, Path sourcePath, int addedLines,
                             Set<String> knownFeatures)
            throws FileNotFoundException {
        this.addedLines = addedLines;
        this.determinePresenceConditions(inclusionInformation, sourcePath, knownFeatures);
    }

    private void determinePresenceConditions(InclusionInformation inclusionInformation, Path sourcePath,
                                             Set<String> knownFeatures)
            throws FileNotFoundException {
        System.out.printf("Determining line presence conditions for %s%n", inclusionInformation.filePath());
        Path filePath = sourcePath.resolve(inclusionInformation.filePath());

        // Create preprocessor
        TokenCreator tokenCreator = new CTokenCreator();
        MacroTable macroTable = new MacroTable(tokenCreator);
        PresenceConditionManager presenceConditionManager = new PresenceConditionManager();
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(ExpressionParser.fromRats(),
                presenceConditionManager, macroTable);
        this.preparePreprocessor(inclusionInformation, macroTable, presenceConditionManager, conditionEvaluator,
                tokenCreator, sourcePath);
        HeaderFileManager headerFileManager = new HeaderFileManager(
                new FileReader(filePath.toFile()),
                filePath.toFile(),
                List.of(),
                inclusionInformation.includePaths().stream()
                        .map(p -> Path.of(p).isAbsolute() ? p : sourcePath.resolve(p).toString())
                        .toList(),
                Arrays.asList(Builtins.sysdirs),
                tokenCreator,
                new StopWatch()
        );
        Preprocessor preprocessor = new Preprocessor(headerFileManager, macroTable, presenceConditionManager,
                conditionEvaluator, tokenCreator);
        //preprocessor.showErrors(true);

        // Compute presence conditions
        Map<Integer, PresenceConditionManager.PresenceCondition> rawPresenceConditions = new HashMap<>();
        Map<Integer, Integer> numSeenPresenceConditions = new HashMap<>();
        Syntax next;
        do {
            try {
                next = preprocessor.next();
            }
            catch (RuntimeException e) {
                System.err.printf("Oh, shit. (At %s):%n%s%n", headerFileManager.include.getLocation(), e);
                break;
            }
            if (preprocessor.isExpanding()) continue;
            Location location = headerFileManager.include.getLocation();
            if (location == null || !Path.of(location.file).equals(filePath)) {
                continue;
            }

            int line = location.line;
            numSeenPresenceConditions.putIfAbsent(line, 0);
            PresenceConditionManager.PresenceCondition presenceCondition = presenceConditionManager.reference();

            if (rawPresenceConditions.get(line) == null ||
                    !presenceCondition.getBDD().equals(rawPresenceConditions.get(line).getBDD())) {
                numSeenPresenceConditions.put(line, numSeenPresenceConditions.get(line) + 1);
                if (numSeenPresenceConditions.get(line) > 1) {
                    System.err.printf("Conflict in line %d on token %s between %s and %s%n", line, next,
                            Optional.ofNullable(rawPresenceConditions.get(line))
                                    .map(PresenceConditionManager.PresenceCondition::toString)
                                    .orElse("<not parsed>"),
                            presenceCondition
                    );
                    continue;
                }
            }

            PresenceConditionManager.PresenceCondition oldCondition = rawPresenceConditions.put(line, presenceCondition);
            if (oldCondition != null) {
                oldCondition.delRef();
            }
        } while (next.kind() != Syntax.Kind.EOF);

        // Remove conflicted presence conditions
        rawPresenceConditions.entrySet().removeIf(e -> numSeenPresenceConditions.get(e.getKey()) > 1);

        // Convert presence conditions to nodes
        for (Map.Entry<Integer, PresenceConditionManager.PresenceCondition> entry : rawPresenceConditions.entrySet()) {
            Optional<Node> nodeOptional = this.convertBDD(entry.getValue().getBDD(), presenceConditionManager);
            if (nodeOptional.isEmpty()) {
                System.err.printf("Could not convert presence condition to node at %s:%s:%n%s%n", filePath,
                        entry.getKey(), entry.getValue());
                continue;
            }

            Node node = nodeOptional.get();
            List<String> unknownFeatures = node.getUniqueLiterals().stream()
                    .filter(literal -> !(literal instanceof True || literal instanceof False))
                    .map(literal -> String.valueOf(literal.var))
                    .filter(var -> !knownFeatures.contains(var))
                    .toList();

            if (!unknownFeatures.isEmpty()) {
                System.err.printf("Unknown features %s in presence condition at %s:%s%n", unknownFeatures, filePath,
                        entry.getKey());
                node = Node.replaceLiterals(node, unknownFeatures, true);
            }

            this.linePresenceConditions.put(entry.getKey(), node);
        }

        for (PresenceConditionManager.PresenceCondition presenceCondition : rawPresenceConditions.values()) {
            presenceCondition.delRef();
        }

        if (this.linePresenceConditions.isEmpty()) {
            System.err.printf("No presence conditions found for %s%n", inclusionInformation.filePath());
        }
    }

    /**
     * Prepares the macro table and presence condition manager for preprocessing by adding the specified defines and
     * included files.
     *
     * @param inclusionInformation     the information about how the file is compiled (i.e. the defines and included files)
     * @param macroTable               the macro table
     * @param presenceConditionManager the presence condition manager
     * @param conditionEvaluator       the condition evaluator
     * @param tokenCreator             the token creator
     */
    private void preparePreprocessor(InclusionInformation inclusionInformation, MacroTable macroTable,
                                     PresenceConditionManager presenceConditionManager,
                                     ConditionEvaluator conditionEvaluator, TokenCreator tokenCreator,
                                     Path sourceRoot) {
        StringBuilder commandLineDirectives = new StringBuilder();

        for (Map.Entry<String, String> define : inclusionInformation.defines().entrySet()) {
            commandLineDirectives.append("#define ").append(define.getKey()).append(" ").append(define.getValue())
                    .append("\n");
        }

        for (String includedFile : inclusionInformation.includedFiles()) {
            commandLineDirectives.append("#include \"").append(includedFile).append("\"\n");
        }

        HeaderFileManager headerFileManager = new HeaderFileManager(
                new StringReader(commandLineDirectives.toString()),
                new File("<command-line>"),
                List.of(),
                inclusionInformation.includePaths().stream()
                        .map(p -> Path.of(p).isAbsolute() ? p : sourceRoot.resolve(p).toString())
                        .toList(),
                Arrays.asList(Builtins.sysdirs),
                tokenCreator,
                new StopWatch()
        );
        Preprocessor preprocessor = new Preprocessor(headerFileManager, macroTable, presenceConditionManager,
                conditionEvaluator, tokenCreator);
        preprocessor.showErrors(true);
        Syntax next;
        do {
            next = preprocessor.next();
        } while (next.kind() != Syntax.Kind.EOF);
    }

    /**
     * Tries to convert the specified BDD to a {@link Node}. This fails if the BDD does not consist entirely of
     * `(defined <variable>)` conditions.
     *
     * @param bdd                      the BDD to convert
     * @param presenceConditionManager the presence condition manager. Used to get the names of the variables.
     * @return a {@link Node} representing the specified BDD
     */
    private Optional<Node> convertBDD(BDD bdd, PresenceConditionManager presenceConditionManager) {
        if (bdd.isOne()) return Optional.of(new True());
        if (bdd.isZero()) return Optional.of(new False());

        String rawCondition = presenceConditionManager.getVariableManager().getName(bdd.var());

        Optional<Node> condition = this.parseCondition(rawCondition);
        if (condition.isEmpty()) return Optional.empty();
        Optional<Node> high = this.convertBDD(bdd.high(), presenceConditionManager);
        if (high.isEmpty()) return Optional.empty();
        Optional<Node> low = this.convertBDD(bdd.low(), presenceConditionManager);
        return low.map(lowNode -> new Or(
                new And(condition.get(), high.get()),
                new And(new Not(condition.get()), lowNode)
        ));
    }

    /**
     * Tries to parse a variable name used by {@link PresenceConditionManager} into a {@link Node}.
     *
     * @param rawCondition the variable name
     * @return a {@link Node} representing the specified variable name
     */
    private Optional<Node> parseCondition(String rawCondition) {
        Matcher definedMatcher = DEFINED_PATTERN.matcher(rawCondition);
        if (definedMatcher.matches()) {
            String variableName = definedMatcher.group(1) == null ? definedMatcher.group(2) : definedMatcher.group(1);
            return Optional.of(new Literal(variableName));
        }
        System.err.printf("Could not parse condition: %s%n", rawCondition);
        return Optional.empty();
    }

    /**
     * Returns the presence condition for the specified line if it could be determined. The line is the line number in
     * the file generated by the composer.
     *
     * @param line the line number
     * @return the presence condition for the specified line
     */
    public Optional<Node> getPresenceCondition(int line) {
        return Optional.ofNullable(this.linePresenceConditions.get(line - this.addedLines));
    }
}
