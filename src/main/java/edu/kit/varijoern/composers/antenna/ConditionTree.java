package edu.kit.varijoern.composers.antenna;

import antenna.preprocessor.v3.PPLine;
import antenna.preprocessor.v3.parser.APPLexer;
import antenna.preprocessor.v3.parser.APPParser;
import antenna.preprocessor.v3.parser.CommonAST;
import antenna.preprocessor.v3.parser.PPLineAST;
import org.antlr.runtime.ANTLRReaderStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.TokenRewriteStream;
import org.antlr.runtime.tree.Tree;
import org.jetbrains.annotations.NotNull;
import org.prop4j.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Contains a tree representing the structure of preprocessor statements in a file.
 */
public class ConditionTree {
    private final int firstLine;
    private final int length;
    private final Node condition;
    private final List<ConditionTree> children;

    /**
     * Creates a new {@link ConditionTree} for the specified file content.
     *
     * @param lines the lines of the file
     * @throws ConditionTreeException if the tree could not be generated
     */
    public ConditionTree(List<String> lines) throws ConditionTreeException {
        this.firstLine = 1;
        this.length = lines.size();
        ParsingResult parsingResult = parse(lines, 0, false);
        this.children = parsingResult.children;
        this.condition = Objects.requireNonNullElseGet(parsingResult.condition, True::new);
    }

    /**
     * Creates a subtree of the condition tree of the file starting from the specified line. Stores the specified
     * condition for this subtree. Parsing will stop when a line ending an if block is reached, e.g. {@code //#endif},
     * {@code //#else}, {@code //#endifdef}
     *
     * @param lines     a list containing all lines of the file
     * @param offset    the offset the subtree should start from
     * @param condition the condition for all lines of the subtree
     * @throws ConditionTreeException if the subtree could not be created
     */
    private ConditionTree(List<String> lines,
                          int offset,
                          Node condition) throws ConditionTreeException {
        this.firstLine = offset + 1;
        ParsingResult parsingResult = parse(lines, offset, true);
        if (parsingResult.condition != null) {
            this.condition = new And(parsingResult.condition, condition);
        } else {
            this.condition = condition;
        }
        this.length = parsingResult.length;
        this.children = parsingResult.children;
    }

    /**
     * Parses the specified file starting from the specified offset and returns the subtrees and a condition for this
     * tree if one was specified using the {@code //#condition} directive.
     *
     * @param lines           all lines of the file
     * @param offset          the offset at which to start parsing
     * @param waitingForEndif {@code true} if this tree represents an if block. Parsing will stop once the end of the
     *                        block is reached.
     * @return the subtrees of this tree and its specified condition
     * @throws ConditionTreeException if the tree could not be created
     */
    private static ParsingResult parse(
            List<String> lines,
            int offset,
            boolean waitingForEndif
    ) throws ConditionTreeException {
        List<ConditionTree> children = new ArrayList<>();
        Node ownCondition = null;
        int lineIndex;
        for (lineIndex = offset; lineIndex < lines.size(); lineIndex++) {
            PPLine parsedLine = new PPLine(lines.get(lineIndex));
            if (!parsedLine.getType().equals(PPLine.TYPE_COMMAND)) continue;
            PPLineAST ast = getAST(parsedLine);
            switch (ast.getType()) {
                case APPLexer.IFDEF, APPLexer.IFNDEF, APPLexer.IF -> {
                    lineIndex = handleIf(ast, lines, lineIndex, children);
                }
                case APPLexer.ELSE, APPLexer.ELIF, APPLexer.ELIFDEF, APPLexer.ELIFNDEF, APPLexer.ENDIF -> {
                    if (waitingForEndif)
                        return new ParsingResult(lineIndex - offset, children, ownCondition);
                    throw new ConditionTreeException("Unexpected preprocessor command in line " + (lineIndex + 1));
                }
                case APPLexer.CONDITION -> {
                    if (lineIndex != 0)
                        throw new ConditionTreeException("//#condition is only allowed in line 1");
                    ownCondition = createIfCondition(
                            lines
                                    .get(lineIndex)
                                    .substring(getFirstCharPositionInLine(getNextSibling(ast)))
                    );
                }
                default -> throw new ConditionTreeException("Unsupported preprocessor command in line %d"
                        .formatted(lineIndex + 1));
            }
        }
        if (waitingForEndif)
            throw new ConditionTreeException("Unexpected EOF");
        return new ParsingResult(lineIndex - offset, children, ownCondition);
    }

    /**
     * Parses an if-elif-else construct and adds the resulting subtrees to the specified list of children. This method
     * also supports {@code ifdef} and {@code ifndef} blocks.
     *
     * @param ifLine       the line containing the initial if condition
     * @param lines        all lines of the file
     * @param ifLineOffset the offset of the line containing the if condition
     * @param children     the list of children to add the new subtrees to
     * @return the offset of the line containing the {@code //#endif} directive
     * @throws ConditionTreeException if the if-elif-else construct could not be parsed or a subtree could not be
     *                                created
     */
    private static int handleIf(PPLineAST ifLine, List<String> lines, int ifLineOffset, List<ConditionTree> children)
            throws ConditionTreeException {
        List<Node> previousConditions = new ArrayList<>();
        ConditionTree lastChild = createNewIfChild(ifLine, lines, ifLineOffset, previousConditions);
        children.add(lastChild);

        int currentOffset = ifLineOffset + lastChild.length + 1;

        while (true) {
            PPLineAST ast = getAST(new PPLine(lines.get(currentOffset)));
            switch (ast.getType()) {
                case APPLexer.ELSE, APPLexer.ELIF, APPLexer.ELIFDEF, APPLexer.ELIFNDEF -> {
                    lastChild = createNewIfChild(ast, lines, currentOffset, previousConditions);
                    children.add(lastChild);
                    currentOffset += lastChild.length + 1;
                }
                case APPLexer.ENDIF -> {
                    return currentOffset;
                }
                default -> throw new ConditionTreeException(
                        "Unexpected or unsupported preprocessor command in line " + currentOffset
                );
            }
        }
    }

    /**
     * Creates a new subtree for a code block of an if, elif or else directive.
     *
     * @param ifLine             the line containing the directive starting the code block
     * @param lines              all lines of the file
     * @param ifLineOffset       the offset of the line containing the directive
     * @param previousConditions the conditions of the preceding {@code if}, {@code elif}, {@code elifdef} and
     *                           {@code elifndef} lines if {@code ifLine} contains an else / elif / elifdef / elifndef
     *                           directive. If this block specifies a new condition, it is added to this list.
     * @return the subtree representing the code block
     * @throws ConditionTreeException if the subtree could not be created
     */
    @NotNull
    private static ConditionTree createNewIfChild(PPLineAST ifLine, List<String> lines, int ifLineOffset,
                                                  @NotNull List<Node> previousConditions)
            throws ConditionTreeException {
        Stream<Not> negatedPreviousConditions = previousConditions.stream().map(Not::new);
        Node fullCondition;
        if (ifLine.getType() == APPLexer.ELSE) {
            fullCondition = new And(negatedPreviousConditions.toArray());
        } else {
            PPLineAST conditionAST = getNextSibling(ifLine);
            final String conditionText = lines.get(ifLineOffset).substring(getFirstCharPositionInLine(conditionAST));
            Node ifCondition = switch (ifLine.getType()) {
                case APPLexer.IF, APPLexer.ELIF -> createIfCondition(conditionText);
                case APPLexer.IFDEF, APPLexer.ELIFDEF -> definedCondition(conditionText, false);
                case APPLexer.IFNDEF, APPLexer.ELIFNDEF -> definedCondition(conditionText, true);
                default -> throw new RuntimeException();
            };
            fullCondition = new And(
                    Stream.concat(negatedPreviousConditions, Stream.of(ifCondition)).toArray()
            );
            previousConditions.add(ifCondition);
        }
        return new ConditionTree(lines, ifLineOffset + 1, fullCondition);
    }

    /**
     * Iteratively selects the first child of the current tree until a leaf is found and returns the leaf's character
     * position in its line.
     *
     * @param tokenTree the root to start iterating from
     * @return the leaf's character position in its line
     */
    private static int getFirstCharPositionInLine(Tree tokenTree) {
        Tree parent = tokenTree;
        while (parent.getChildCount() > 0) {
            parent = parent.getChild(0);
        }
        return parent.getCharPositionInLine();
    }

    /**
     * Parses an if condition inside an {@code //#if} or {@code //#elif} directive.
     *
     * @param condition the condition excluding the initial {@code //#if} and {@code //#elif} part
     * @return the parsed condition
     */
    private static Node createIfCondition(String condition) {
        NodeReader nodeReader = new NodeReader();
        String preparedCondition = condition.trim()
                .replace("&&", "&")
                .replace("||", "|")
                .replace("!", "-")
                .replace("==", "=")
                .replace("&", " and ")
                .replace("|", " or ")
                .replace("-", " not ")
                .replace("=", " iff ");
        return nodeReader.stringToNode(
                preparedCondition
        );
    }

    @NotNull
    private static PPLineAST getNextSibling(PPLineAST ast) {
        return (PPLineAST) ast.getParent().getChild(ast.getIndex() + 1);
    }

    /**
     * Returns the condition for {@code //#ifdef}-like directives.
     *
     * @param symbol the symbol specified by the directive
     * @param negate {@code true} if the symbol should be negated, for example when an {@code //#ifndef} directive is
     *               used
     * @return the condition
     */
    private static Node definedCondition(String symbol, boolean negate) {
        return new Literal(symbol, !negate);
    }

    private static PPLineAST getAST(PPLine line) throws ConditionTreeException {
        APPParser parser = getParser(line);
        PPLineAST ast;
        try {
            ast = (PPLineAST) parser.line().getTree();
        } catch (RecognitionException e) {
            throw new ConditionTreeException("Failed to parse preprocessor line", e);
        }
        CommonAST.fillParentInfo(ast);
        if (ast.isNil()) {
            ast = (PPLineAST) ast.getChild(1);
        }
        return ast;
    }

    @NotNull
    private static APPParser getParser(PPLine line) throws ConditionTreeException {
        String raw = line.getText();
        if (raw.startsWith("expand") || raw.startsWith("include"))
            throw new ConditionTreeException("\"expand\" and \"include\" are not supported");
        APPLexer lexer;
        try {
            lexer = new APPLexer(new ANTLRReaderStream(new StringReader(line.getSource())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        APPParser parser = new APPParser(new TokenRewriteStream(lexer));
        parser.setTreeAdaptor(PPLineAST.adaptor);
        return parser;
    }

    /**
     * Returns the line number of the first line of the code block represented by this tree.
     *
     * @return the line number of the first line of the code block, 1-based
     */
    public int getFirstLine() {
        return firstLine;
    }

    /**
     * Returns the number of lines the code block spans
     *
     * @return the number of lines of the code block
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns a list of all subtrees of this tree.
     *
     * @return all subtrees
     */
    public List<ConditionTree> getChildren() {
        return children;
    }

    /**
     * Returns the condition of this tree. This condition does not include the conditions of the trees this tree is a
     * subtree of.
     *
     * @return the condition of this tree
     */
    public Node getCondition() {
        return condition;
    }

    /**
     * Returns the condition of the specified line by combining the conditions of all subtrees containing this line.
     *
     * @param lineNumber the line number. Line 1 is the first line.
     * @return the condition of the line
     */
    public Node getConditionOfLine(int lineNumber) {
        if (lineNumber < 1 || lineNumber >= this.firstLine + this.length)
            throw new IndexOutOfBoundsException(
                    "Cannot get condition for line %d because it is out of range.".formatted(lineNumber)
            );
        Optional<ConditionTree> candidate = this.children.stream()
                .filter(child -> child.firstLine <= lineNumber && lineNumber < child.firstLine + child.length)
                .findAny();
        return candidate
                .map(child -> (Node) new And(this.condition, child.getConditionOfLine(lineNumber)))
                .orElse(this.condition);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("%d: if %s%n".formatted(this.firstLine - 1, this.condition));
        for (ConditionTree child : this.children) {
            sb.append(child);
            sb.append(System.lineSeparator());
        }
        sb.append("%d: endif".formatted(this.firstLine + this.length));
        return sb.toString();
    }

    /**
     * Contains the result of parsing a tree.
     *
     * @param length    the number of lines of the code block the tree represents
     * @param children  the children of the tree
     * @param condition the condition of the tree as specified by the {@code //#condition} directive
     */
    private record ParsingResult(int length, List<ConditionTree> children, Node condition) {
    }
}
