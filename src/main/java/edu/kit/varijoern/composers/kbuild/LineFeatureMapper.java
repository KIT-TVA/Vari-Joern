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
    private static final Pattern DEFINED_PATTERN = Pattern.compile("\\(defined (.+)\\)");

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
        Map<Integer, Integer> numSeenPresenceConditions = new HashMap<>();
        Syntax next;
        do {
            next = preprocessor.next();
            Location location = headerFileManager.include.getLocation();
            if (location == null || !Path.of(location.file).equals(filePath)) {
                continue;
            }

            int line = location.line;
            numSeenPresenceConditions.putIfAbsent(line, 0);
            PresenceConditionManager.PresenceCondition presenceCondition = presenceConditionManager.reference();
            Optional<Node> presenceConditionNodeOptional = this.convertBDD(presenceCondition.getBDD(),
                    presenceConditionManager);
            presenceCondition.delRef();

            numSeenPresenceConditions.put(line, numSeenPresenceConditions.get(line) + 1);

            if (presenceConditionNodeOptional.isEmpty()) {
                System.err.printf("Could not parse presence condition at location %s: %s%n", location,
                        presenceCondition);
                continue;
            }

            Node presenceConditionNode = presenceConditionNodeOptional.get();

            if (numSeenPresenceConditions.get(line) > 1) {
                System.err.printf("Conflict in line %d between %s and %s%n", line,
                        Optional.ofNullable(this.linePresenceConditions.get(line))
                                .map(Node::toString)
                                .orElse("<not parsed>"),
                        presenceConditionNode
                );
                continue;
            }

            // FIXME: This breaks conflict detection.
            List<String> unknownFeatures = presenceConditionNode.getUniqueLiterals().stream()
                    // True and False are literals
                    .filter(literal -> !(literal instanceof True || literal instanceof False))
                    .map(literal -> String.valueOf(literal.var))
                    .filter(v -> !knownFeatures.contains(v))
                    .toList();
            if (!unknownFeatures.isEmpty()) {
                System.err.printf("Unknown features %s in line %d. Replacing with `false`%n", unknownFeatures, line);
                presenceConditionNode = Node.replaceLiterals(presenceConditionNode, unknownFeatures, true);
            }

            this.linePresenceConditions.put(line, presenceConditionNode);
        } while (next.kind() != Syntax.Kind.EOF);

        // Remove conflicted presence conditions
        this.linePresenceConditions.entrySet().removeIf(e -> numSeenPresenceConditions.get(e.getKey()) > 1);

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
            return Optional.of(new Literal(definedMatcher.group(1)));
        }
        // FIXME: `#if MY_FEATURE` also means `#if defined(MY_FEATURE)`, but this is not handled here.
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
