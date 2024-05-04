package edu.kit.varijoern.composers.kbuild;

import net.sf.javabdd.BDD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.prop4j.*;
import superc.Builtins;
import superc.core.*;
import superc.cparser.CLexerCreator;
import superc.cparser.CTokenCreator;
import xtc.tree.Location;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps lines of a single file to presence conditions. More specifically, it maps each line to the presence condition
 * at the beginning of the line.
 * <h2>Issues</h2>
 * <ul>
 *     <li>Currently, only BusyBox and similar systems are supported.</li>
 *     <li>Conditions including non-boolean operations (e.g. integer comparisons) are ignored.</li>
 *     <li>Unknown preprocessor symbols are assumed to be undefined. This may be false.</li>
 *     <li>Occurrences of variables are always treated as if they were used in a defined() condition. This may result in
 *     wrong presence conditions if the variable can be defined as 0 and is now {@code ENABLE_<feature>} variable as
 *     they are used in BusyBox.</li>
 * </ul>
 */
public class LineFeatureMapper {
    private static final Pattern DEFINED_PATTERN = Pattern.compile("\\(defined (.+)\\)|([A-Za-z0-9_]+)");
    // In busybox, enabled (non-module) tristate features are defined as CONFIG_<feature> as well as ENABLE_<feature>
    private static final Pattern BUSYBOX_FEATURE_MACRO_PATTERN =
            Pattern.compile("(?:CONFIG_|ENABLE_)([A-Za-z0-9_]+)");
    private static final Logger logger = LogManager.getLogger();

    private final Map<Integer, Node> linePresenceConditions = new HashMap<>();
    private final int addedLines;
    private final int totalLines;

    /**
     * Creates a new {@link LineFeatureMapper} for the specified file.
     * <p>
     * The defines declared in {@code inclusionInformation} are considered to be enabled in every configuration because
     * there is currently no way to determine their conditions.
     *
     * @param inclusionInformation the information about how the file is compiled
     * @param sourcePath           the path to the root of the source directory
     * @param addedLines           the number of lines (#include and #define directives) added to the file by the
     *                             composer
     * @param knownFeatures        the features recorded in the feature model
     * @param system               the system the file belongs to. Currently, only {@code busybox} is supported.
     */
    public LineFeatureMapper(InclusionInformation inclusionInformation, Path sourcePath, int addedLines,
                             Set<String> knownFeatures, String system)
            throws IOException {
        if (!isSupportedSystem(system)) throw new UnsupportedOperationException("Only busybox is supported");

        this.addedLines = addedLines;
        this.totalLines = Files.readAllLines(sourcePath.resolve(inclusionInformation.filePath())).size();
        this.determinePresenceConditions(inclusionInformation, sourcePath, knownFeatures, system);
    }

    private void determinePresenceConditions(InclusionInformation inclusionInformation, Path sourcePath,
                                             Set<String> knownFeatures, String system)
            throws FileNotFoundException {
        logger.debug("Determining line presence conditions for {}", inclusionInformation.filePath());
        Path filePath = sourcePath.resolve(inclusionInformation.filePath());

        // Create preprocessor
        TokenCreator tokenCreator = new CTokenCreator();
        LexerCreator lexerCreator = new CLexerCreator();
        MacroTable macroTable = new MacroTable(tokenCreator);
        PresenceConditionManager presenceConditionManager = new PresenceConditionManager();
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(ExpressionParser.fromRats(),
                presenceConditionManager, macroTable);
        this.preparePreprocessor(inclusionInformation, macroTable, presenceConditionManager, conditionEvaluator,
                lexerCreator, tokenCreator, sourcePath);
        try (ConditionCapturingHeaderFileManager headerFileManager = new ConditionCapturingHeaderFileManager(
                new FileReader(filePath.toFile()),
                filePath.toFile(),
                List.of(),
                inclusionInformation.includePaths().stream()
                        .map(p -> Path.of(p).isAbsolute() ? p : sourcePath.resolve(p).toString())
                        .toList(),
                Arrays.asList(Builtins.sysdirs),
                lexerCreator,
                tokenCreator,
                new StopWatch(),
                presenceConditionManager
        )) {
            headerFileManager.showErrors(false);
            Preprocessor preprocessor = new Preprocessor(headerFileManager, macroTable, presenceConditionManager,
                    conditionEvaluator, tokenCreator);

            // Preprocess the file and collect presence conditions
            Syntax next;
            do {
                try {
                    next = preprocessor.next();
                } catch (IllegalStateException | Error e) {
                    // The preprocessor can `throw new Error()` when it encounters an internal error. If a subclass of
                    // `Error` is caught, it was not thrown by the preprocessor and something is seriously wrong.
                    if (e instanceof Error && e.getClass() != Error.class)
                        throw (Error) e;
                    logger.atWarn().withThrowable(e).log("Preprocessor encountered an internal error at {}",
                            headerFileManager.include.getLocation());
                    break;
                }
            } while (next.kind() != Syntax.Kind.EOF);

            // Convert presence conditions to nodes
            for (int line = 1; line <= headerFileManager.getLastLine(); line++) {
                Optional<PresenceConditionManager.PresenceCondition> conditionOptional
                        = headerFileManager.getCondition(line);
                if (conditionOptional.isEmpty()) {
                    logger.debug("No presence condition found for line {} in {}", line, filePath);
                    continue;
                }

                PresenceConditionManager.PresenceCondition condition = conditionOptional.get();

                Optional<Node> nodeOptional = this.convertBDD(condition.getBDD(), presenceConditionManager);
                if (nodeOptional.isEmpty()) {
                    logger.warn("Could not convert presence condition to node at {}:{}: {}", filePath,
                            line, condition);
                    continue;
                }

                condition.delRef();

                Node node = nodeOptional.get();
                this.convertMacrosToFeatures(node, system);
                List<String> unknownFeatures = node.getUniqueLiterals().stream()
                        .filter(literal -> !(literal instanceof True || literal instanceof False))
                        .map(literal -> String.valueOf(literal.var))
                        .filter(var -> !knownFeatures.contains(var))
                        .toList();

                if (!unknownFeatures.isEmpty()) {
                    logger.warn("Unknown features {} in presence condition at {}:{}", unknownFeatures, filePath,
                            line);
                    node = Node.replaceLiterals(node, unknownFeatures, true);
                }

                this.linePresenceConditions.put(line, node);
            }
        }

        if (this.linePresenceConditions.isEmpty()) {
            logger.debug("No presence conditions found for {}", inclusionInformation.filePath());
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
                                     ConditionEvaluator conditionEvaluator, LexerCreator lexerCreator,
                                     TokenCreator tokenCreator, Path sourceRoot) {
        injectSource(Builtins.builtin, macroTable, presenceConditionManager, conditionEvaluator, lexerCreator, tokenCreator, inclusionInformation,
                sourceRoot);

        StringBuilder commandLineDirectives = new StringBuilder();

        for (Map.Entry<String, String> define : inclusionInformation.defines().entrySet()) {
            commandLineDirectives.append("#define ").append(define.getKey()).append(" ").append(define.getValue())
                    .append("\n");
        }

        for (String includedFile : inclusionInformation.includedFiles()) {
            commandLineDirectives.append("#include \"").append(includedFile).append("\"\n");
        }

        injectSource(commandLineDirectives.toString(), macroTable, presenceConditionManager, conditionEvaluator, lexerCreator, tokenCreator, inclusionInformation,
                sourceRoot);
    }

    private static void injectSource(String source, MacroTable macroTable, PresenceConditionManager presenceConditionManager, ConditionEvaluator conditionEvaluator, LexerCreator lexerCreator, TokenCreator tokenCreator, InclusionInformation inclusionInformation,
                                     Path sourceRoot) {
        HeaderFileManager headerFileManager = new HeaderFileManager(
                new StringReader(source),
                new File("<injected-source>"),
                List.of(),
                inclusionInformation.includePaths().stream()
                        .map(p -> Path.of(p).isAbsolute() ? p : sourceRoot.resolve(p).toString())
                        .toList(),
                Arrays.asList(Builtins.sysdirs),
                lexerCreator,
                tokenCreator,
                new StopWatch()
        );
        headerFileManager.showErrors(false);
        Preprocessor preprocessor = new Preprocessor(headerFileManager, macroTable, presenceConditionManager,
                conditionEvaluator, tokenCreator);
        Syntax next;
        do {
            next = preprocessor.next();
        } while (next.kind() != Syntax.Kind.EOF);
    }

    /**
     * Tries to convert the specified BDD to a {@link Node}. This fails if the BDD does not consist entirely of
     * {@code (defined <variable>)} conditions.
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
        logger.debug("Could not parse condition: {}", rawCondition);
        return Optional.empty();
    }

    private void convertMacrosToFeatures(Node node, String system) {
        if (system.equals("busybox")) {
            node.modifyFeatureNames(macro -> {
                Matcher matcher = BUSYBOX_FEATURE_MACRO_PATTERN.matcher(macro);
                if (matcher.matches()) {
                    return matcher.group(1);
                }
                return macro;
            });
        }
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

    /**
     * Returns whether the specified system is supported. Currently, only {@code busybox} is supported.
     *
     * @param system the system
     * @return whether the specified system is supported
     */
    public static boolean isSupportedSystem(String system) {
        return system.equals("busybox");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LineFeatureMapper{");
        int indent = 4 + (int) (Math.floor(Math.log10(this.totalLines))) + 1;
        for (int line = 0; line < this.totalLines; line++) {
            sb.append("\n").append(" ".repeat(indent)).append(line).append(": ");
            Node presenceCondition = this.linePresenceConditions.get(line);
            if (presenceCondition == null) {
                sb.append("<not present>");
            } else {
                sb.append(presenceCondition);
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    private static class ConditionCapturingHeaderFileManager extends HeaderFileManager implements AutoCloseable {
        private final Include mainInclude;
        private final Map<Integer, PresenceConditionManager.PresenceCondition> conditions = new HashMap<>();
        private final PresenceConditionManager presenceConditionManager;
        private Location lastMainLocation;

        public ConditionCapturingHeaderFileManager(Reader in, File file, List<String> iquote,
                                                   List<String> I, List<String> sysdirs,
                                                   LexerCreator lexerCreator, TokenCreator tokenCreator,
                                                   StopWatch lexerTimer, String encoding,
                                                   PresenceConditionManager presenceConditionManager) {
            super(in, file, iquote, I, sysdirs, lexerCreator, tokenCreator, lexerTimer, encoding);
            this.mainInclude = this.include;
            this.presenceConditionManager = presenceConditionManager;
        }

        public ConditionCapturingHeaderFileManager(Reader in, File file, List<String> iquote,
                                                   List<String> I, List<String> sysdirs,
                                                   LexerCreator lexerCreator, TokenCreator tokenCreator,
                                                   StopWatch lexerTimer,
                                                   PresenceConditionManager presenceConditionManager) {
            super(in, file, iquote, I, sysdirs, lexerCreator, tokenCreator, lexerTimer);
            this.mainInclude = this.include;
            this.presenceConditionManager = presenceConditionManager;
        }

        @Override
        public Syntax next() {
            // Location lastLocation = this.include.getLocation();
            Syntax nextToken = super.next();
            Location newLocation = this.include.getLocation();

            if (this.include == this.mainInclude && newLocation != null) {
                // Add presence conditions for lines between the last token and this token.
                // Line this.lastMainLocation.line has already been considered before. The following lines are blank and
                // do not change the presence condition. Line newLocation.line is the first line of the next token and
                // has the same presence condition as the preceding lines.
                int startLine = this.lastMainLocation == null ? 1 : this.lastMainLocation.line + 1;
                for (int i = startLine; i <= newLocation.line; i++) {
                    this.conditions.put(i, this.presenceConditionManager.reference());
                }

                // If this token spans multiple lines, it is also the first token of each line after its first line.
                // Therefore, we need to add the presence condition to each line spanned by the token except for the
                // first.
                int numLinesOfNextToken = (int) nextToken.getTokenText().lines().count();
                for (int i = 1; i < numLinesOfNextToken; i++) {
                    // We need a new reference for each line
                    this.conditions.put(newLocation.line + i, this.presenceConditionManager.reference());
                }

                this.lastMainLocation = new Location(newLocation.file, newLocation.line + numLinesOfNextToken - 1,
                        newLocation.column);
            }

            return nextToken;
        }

        public int getLastLine() {
            return this.lastMainLocation.line;
        }

        public Optional<PresenceConditionManager.PresenceCondition> getCondition(int line) {
            return Optional.ofNullable(this.conditions.get(line))
                    .map(PresenceConditionManager.PresenceCondition::addRef);
        }

        @Override
        public void close() {
            for (PresenceConditionManager.PresenceCondition condition : this.conditions.values()) {
                condition.delRef();
            }
        }
    }
}
