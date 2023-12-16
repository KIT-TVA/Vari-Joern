package edu.kit.varijoern.composers.kbuild;

import org.prop4j.*;
import org.smtlib.*;
import org.smtlib.command.C_assert;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts SMT-LIB strings to {@link Node}s by reading all `assert` commands and chaining the specified conditions.
 * Only or, not and let statements are supported.
 */
public class SMTLibConverter {
    /**
     * Converts an SMT-LIB string to a {@link Node}.
     *
     * @param smtLib the SMT-LIB string to convert
     * @return the converted {@link Node}
     * @throws ParseException if the SMT-LIB string could not be parsed
     */
    public Node convert(String smtLib) throws ParseException {
        SMT smt = new SMT();
        ISource source = smt.smtConfig.smtFactory.createSource(new CharSequenceReader(new StringReader(smtLib)), null);
        IParser parser = smt.smtConfig.smtFactory.createParser(smt.smtConfig, source);
        List<Node> assertions = new ArrayList<>();
        while (true) {
            try {
                if (parser.isEOD()) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (IParser.ParserException e) {
                throw new ParseException("Could not parse SMT-LIB string", e.pos().charStart());
            }

            ICommand command;
            try {
                command = parser.parseCommand();
            } catch (IParser.ParserException e) {
                throw new ParseException("Could not parse SMT-LIB command", e.pos().charStart());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (command == null)
                throw new ParseException("Could not parse SMT-LIB command. Error: %s"
                    .formatted(parser.lastError() == null ? "(unknown)" : parser.lastError().toString()),
                    parser.lastError() == null ? 0 : parser.lastError().pos().charStart());

            if (command instanceof C_assert) {
                Node assertion;
                try {
                    assertion = ((C_assert) command).expr().accept(new SMTLibConverterVisitor());
                } catch (IVisitor.VisitorException e) {
                    throw new RuntimeException(e);
                }
                if (assertion == null)
                    throw new ParseException("Could not parse assertion", ((C_assert) command).pos().charStart());
                assertions.add(assertion);
            }
        }
        return new And(assertions.toArray(new Node[0]));
    }

    private static class SMTLibConverterVisitor extends IVisitor.NullVisitor<Node> {
        private final Map<String, Node> letBindings = new HashMap<>();

        @Override
        public Node visit(IExpr.IFcnExpr e) throws VisitorException {
            if (e.head().toString().equals("or")) {
                List<Node> arguments = new ArrayList<>();
                for (IExpr argument : e.args()) {
                    Node convertedArgument = argument.accept(this);
                    if (convertedArgument == null) return null;
                    arguments.add(convertedArgument);
                }
                return new Or(arguments.toArray(new Node[0]));
            } else if (e.head().toString().equals("not")) {
                if (e.args().size() != 1) return null;
                Node convertedArgument = e.args().get(0).accept(this);
                if (convertedArgument == null) return null;
                return new Not(convertedArgument);
            }
            return null;
        }

        @Override
        public Node visit(IExpr.ISymbol e) {
            if (this.letBindings.containsKey(e.toString())) {
                return this.letBindings.get(e.toString()).clone();
            }
            return new Literal(e.toString());
        }

        @Override
        public Node visit(IExpr.ILet e) throws VisitorException {
            Map<String, Node> shadowedBindings = new HashMap<>();
            for (IExpr.IBinding binding : e.bindings()) {
                Node convertedBinding = binding.expr().accept(this);
                if (convertedBinding == null) return null;
                shadowedBindings.put(binding.parameter().value(), convertedBinding);
            }
            shadowedBindings.replaceAll(this.letBindings::put);
            Node convertedBody = e.expr().accept(this);
            if (convertedBody == null) return null;
            for (IExpr.IBinding binding : e.bindings()) {
                this.letBindings.remove(binding.parameter().value());
                if (shadowedBindings.containsKey(binding.parameter().value())) {
                    this.letBindings.put(binding.parameter().value(), shadowedBindings.get(binding.parameter().value()));
                }
            }
            return convertedBody;
        }
    }
}
