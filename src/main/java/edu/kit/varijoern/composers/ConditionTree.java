package edu.kit.varijoern.composers;

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

    public ConditionTree(List<String> lines) throws ConditionTreeException {
        this.firstLine = 1;
        this.length = lines.size();
        ParsingResult parsingResult = parse(lines, 0, false);
        this.children = parsingResult.children;
        this.condition = Objects.requireNonNullElseGet(parsingResult.condition, True::new);
    }

    private ConditionTree(List<String> lines,
                          int offset,
                          Node condition,
                          boolean waitingForEndif) throws ConditionTreeException {
        this.firstLine = offset + 1;
        ParsingResult parsingResult = parse(lines, offset, waitingForEndif);
        if (parsingResult.condition != null) {
            this.condition = new And(parsingResult.condition, condition);
        } else {
            this.condition = condition;
        }
        this.length = parsingResult.length;
        this.children = parsingResult.children;
    }

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
                case APPLexer.IFDEF, APPLexer.IFNDEF, APPLexer.IF ->
                    lineIndex = handleIf(ast, lines, lineIndex, children);
                case APPLexer.ELSE, APPLexer.ELIF, APPLexer.ELIFDEF, APPLexer.ELIFNDEF, APPLexer.ENDIF -> {
                    if (waitingForEndif)
                        return new ParsingResult(lineIndex - offset, children, ownCondition);
                    throw new ConditionTreeException("Unexpected preprocessor command in line " + (lineIndex + 1));
                }
                case APPLexer.CONDITION -> {
                    if (lineIndex != 0)
                        throw new ConditionTreeException("//#condition is only allowed in line 1");
                    ownCondition = createIfCondition(getNextSibling(ast).getText());
                }
            }
        }
        if (waitingForEndif)
            throw new ConditionTreeException("Unexpected EOF");
        return new ParsingResult(lineIndex - offset, children, ownCondition);
    }

    private static int handleIf(PPLineAST ifLine, List<String> lines, int ifLineOffset, List<ConditionTree> children)
        throws ConditionTreeException {
        List<Node> previousConditions = new ArrayList<>();
        ConditionTree lastChild = createNewIfChild(ifLine, lines, ifLineOffset, previousConditions);
        children.add(lastChild);
        previousConditions.add(lastChild.condition);

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
        }
        return new ConditionTree(lines, ifLineOffset + 1, fullCondition, true);
    }

    /**
     * Iteratively selects the first child of the current tree until a leaf is found and returns the leaf's character
     * position in its line.
     *
     * @param tokenTree the root to start iterating from
     * @return the leaf's character position in its line
     */
    private static int getFirstCharPositionInLine(Tree tokenTree) {
        while (tokenTree.getChildCount() > 0) {
            tokenTree = tokenTree.getChild(0);
        }
        return tokenTree.getCharPositionInLine();
    }

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

    public int getFirstLine() {
        return firstLine;
    }

    public int getLength() {
        return length;
    }

    public List<ConditionTree> getChildren() {
        return children;
    }

    public Node getCondition() {
        return condition;
    }

    public Node getConditionOfLine(int lineNumber) {
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
        for (ConditionTree child :
            this.children) {
            sb.append(child);
            sb.append(System.lineSeparator());
        }
        sb.append("%d: endif".formatted(this.firstLine + this.length));
        return sb.toString();
    }

    private record ParsingResult(int length, List<ConditionTree> children, Node condition) {
    }
}
