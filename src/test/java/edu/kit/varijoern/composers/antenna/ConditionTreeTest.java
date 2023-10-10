package edu.kit.varijoern.composers.antenna;

import org.junit.jupiter.api.Test;
import org.prop4j.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConditionTreeTest {
    @Test
    void noCondition() throws ConditionTreeException {
        List<String> lines = """
            abc
            def""".lines().toList();
        ConditionTree conditionTree = new ConditionTree(lines);
        assertLineConditionsEqual(conditionTree, List.of(
            new True(),
            new True()
        ));
    }

    @Test
    void simpleIf() throws ConditionTreeException {
        List<String> lines = """
            abc
            //#if foo
            def
            //#endif
            ghi""".lines().toList();
        ConditionTree conditionTree = new ConditionTree(lines);
        assertLineConditionsEqual(conditionTree, List.of(
            new True(),
            new True(),
            new And(new True(), new And(new Literal("foo"))),
            new True(),
            new True()
        ));
    }

    @Test
    void compositeIf() throws ConditionTreeException {
        List<String> lines = """
            abc
            //#if foo && bar || baz
            def
            //#endif""".lines().toList();
        ConditionTree conditionTree = new ConditionTree(lines);
        assertLineConditionsEqual(conditionTree, List.of(
            new True(),
            new True(),
            new And(new True(), new And(
                new Or(new And(new Literal("foo"), new Literal("bar")), new Literal("baz")))
            ),
            new True()
        ));
    }

    @Test
    void ifElifElse() throws ConditionTreeException {
        List<String> lines = """
            //#if foo
            abc
            //#elif bar
            def
            //#else
            ghi
            //#endif
            jkl""".lines().toList();
        ConditionTree conditionTree = new ConditionTree(lines);
        assertLineConditionsEqual(conditionTree, List.of(
            new True(),
            new And(new True(), new And(new Literal("foo"))),
            new True(),
            new And(new True(), new And(new Not(new Literal("foo")), new Literal("bar"))),
            new True(),
            new And(new True(), new And(new Not(new Literal("foo")), new Not(new Literal("bar")))),
            new True(),
            new True()
        ));
    }

    @Test
    void ifElse() throws ConditionTreeException {
        List<String> lines = """
            //#if foo
            abc
            //#else
            def
            //#endif""".lines().toList();
        ConditionTree conditionTree = new ConditionTree(lines);
        assertLineConditionsEqual(conditionTree, List.of(
            new True(),
            new And(new True(), new And(new Literal("foo"))),
            new True(),
            new And(new True(), new And(new Not(new Literal("foo")))),
            new True()
        ));
    }

    @Test
    void ifdef() throws ConditionTreeException {
        List<String> lines = """
            //#ifdef foo
            abc
            //#elifdef bar
            def
            //#endif
            //#ifndef foo
            ghi
            //#elifndef baz
            jkl
            //#else
            mno
            //#endif""".lines().toList();
        ConditionTree conditionTree = new ConditionTree(lines);
        assertLineConditionsEqual(conditionTree, List.of(
            new True(),
            new And(new True(), new And(new Literal("foo"))),
            new True(),
            new And(new True(), new And(new Not(new Literal("foo")), new Literal("bar"))),
            new True(),
            new True(),
            new And(new True(), new And(new Literal("foo", false))),
            new True(),
            new And(new True(), new And(new Not(new Literal("foo", false)), new Literal("baz", false))),
            new True(),
            new And(new True(),
                new And(new Not(new Literal("foo", false)), new Not(new Literal("baz", false)))
            ),
            new True()
        ));
    }

    @Test
    void nestedIf() throws ConditionTreeException {
        List<String> lines = """
            //#if foo
            abc
            //#if bar
            def
            //#endif
            ghi
            //#endif
            jkl""".lines().toList();
        ConditionTree conditionTree = new ConditionTree(lines);
        assertLineConditionsEqual(conditionTree, List.of(
            new True(),
            new And(new True(), new And(new Literal("foo"))),
            new And(new True(), new And(new Literal("foo"))),
            new And(new True(), new And(new And(new Literal("foo")), new And(new Literal("bar")))),
            new And(new True(), new And(new Literal("foo"))),
            new And(new True(), new And(new Literal("foo"))),
            new True(),
            new True()
        ));
    }

    @Test
    void condition() throws ConditionTreeException {
        List<String> lines = """
            //#condition foo & bar
            abc
            //#if baz
            def
            //#endif""".lines().toList();
        ConditionTree conditionTree = new ConditionTree(lines);
        And fileCondition = new And(new Literal("foo"), new Literal("bar"));
        assertLineConditionsEqual(conditionTree, List.of(
            fileCondition,
            fileCondition,
            fileCondition,
            new And(fileCondition, new And(new Literal("baz"))),
            fileCondition
        ));
    }

    private void assertLineConditionsEqual(ConditionTree conditionTree, List<Node> lineConditions) {
        for (int i = 0; i < lineConditions.size(); i++) {
            assertEquals(lineConditions.get(i), conditionTree.getConditionOfLine(i + 1),
                "Unequal conditions for line %d".formatted(i + 1));
        }
        assertThrows(IndexOutOfBoundsException.class,
            () -> conditionTree.getConditionOfLine(lineConditions.size() + 1)
        );
    }
}