package edu.kit.varijoern.composers.kbuild;

import org.junit.jupiter.api.Test;
import org.prop4j.*;

import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SMTLibConverterTest {
    @Test
    void simpleAssertion() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (assert CONFIG_UBIATTACH)
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(new And(new Literal("CONFIG_UBIATTACH")), result);
    }

    @Test
    void invalidSyntax() {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (assert CONFIG_UBIATTACH
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        assertThrows(ParseException.class, () -> converter.convert(smtLib));
    }

    @Test
    void multipleAssertions() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (declare-fun CONFIG_UBIDETACH () Bool)
            (assert CONFIG_UBIATTACH)
            (assert CONFIG_UBIDETACH)
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(new And(new Literal("CONFIG_UBIATTACH"), new Literal("CONFIG_UBIDETACH")), result);
    }

    @Test
    void assertionWithOr() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIUPDATEVOL () Bool)
            (declare-fun CONFIG_UBIRSVOL () Bool)
            (declare-fun CONFIG_UBIRMVOL () Bool)
            (declare-fun CONFIG_UBIMKVOL () Bool)
            (declare-fun CONFIG_UBIDETACH () Bool)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (assert
             (or CONFIG_UBIATTACH CONFIG_UBIDETACH CONFIG_UBIMKVOL CONFIG_UBIRMVOL CONFIG_UBIRSVOL CONFIG_UBIUPDATEVOL))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(
            new And(
                new Or(
                    new Literal("CONFIG_UBIATTACH"),
                    new Literal("CONFIG_UBIDETACH"),
                    new Literal("CONFIG_UBIMKVOL"),
                    new Literal("CONFIG_UBIRMVOL"),
                    new Literal("CONFIG_UBIRSVOL"),
                    new Literal("CONFIG_UBIUPDATEVOL")
                )
            ),
            result
        );
    }

    @Test
    void assertionWithNot() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIUPDATEVOL () Bool)
            (assert
             (not CONFIG_UBIUPDATEVOL))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(new And(new Not(new Literal("CONFIG_UBIUPDATEVOL"))), result);
    }

    @Test
    void assertionWithNotWithoutArgs() {
        String smtLib = """
            (set-info :status unknown)
            (assert
             (not))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        assertThrows(ParseException.class, () -> converter.convert(smtLib));
    }

    @Test
    void assertionWithNotWithMultipleArgs() {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIUPDATEVOL () Bool)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (assert
             (not CONFIG_UBIUPDATEVOL CONFIG_UBIATTACH))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        assertThrows(ParseException.class, () -> converter.convert(smtLib));
    }

    @Test
    void simpleLet() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (assert
             (let (($x1 CONFIG_UBIATTACH))
              $x1))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(new And(new Literal("CONFIG_UBIATTACH")), result);
    }

    @Test
    void nestedLet() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (assert
             (let (($x1 CONFIG_UBIATTACH))
              (let (($x2 CONFIG_UBIDETACH))
               (or $x1 $x2))))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(new And(new Or(new Literal("CONFIG_UBIATTACH"), new Literal("CONFIG_UBIDETACH"))), result);
    }

    @Test
    void parallelLet() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (declare-fun CONFIG_UBIDETACH () Bool)
            (assert
             (let (($x1 CONFIG_UBIATTACH) ($x2 CONFIG_UBIDETACH))
               (or $x1 $x2)))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(new And(new Or(new Literal("CONFIG_UBIATTACH"), new Literal("CONFIG_UBIDETACH"))), result);
    }

    @Test
    void letShadowing() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (declare-fun CONFIG_UBIDETACH () Bool)
            (assert
             (let (($x1 CONFIG_UBIATTACH))
              (or (let (($x1 CONFIG_UBIDETACH))
               $x1) $x1)))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(new And(new Or(new Literal("CONFIG_UBIDETACH"), new Literal("CONFIG_UBIATTACH"))), result);
    }

    @Test
    void parallelLetWithShadowing() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (declare-fun CONFIG_UBIDETACH () Bool)
            (assert
             (let (($x1 CONFIG_UBIATTACH))
              (or (let (($x1 CONFIG_UBIDETACH) ($x2 $x1))
               (or $x1 $x2)) $x1)))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(new And(new Or(new Or(new Literal("CONFIG_UBIDETACH"), new Literal("CONFIG_UBIATTACH")),
            new Literal("CONFIG_UBIATTACH"))), result);
    }

    @Test
    void letRedefining() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (assert
             (let (($x1 CONFIG_UBIATTACH))
              (let (($x1 $x1))
               $x1)))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(new And(new Literal("CONFIG_UBIATTACH")), result);
    }

    @Test
    void letComplexBinding() throws ParseException {
        String smtLib = """
            (set-info :status unknown)
            (declare-fun CONFIG_UBIATTACH () Bool)
            (declare-fun CONFIG_UBIDETACH () Bool)
            (assert
             (let (($x1 (or CONFIG_UBIATTACH CONFIG_UBIDETACH)))
              $x1))
            (check-sat)""";
        SMTLibConverter converter = new SMTLibConverter();
        Node result = converter.convert(smtLib);
        assertEquals(
            new And(
                new Or(
                    new Literal("CONFIG_UBIATTACH"),
                    new Literal("CONFIG_UBIDETACH")
                )
            ),
            result
        );
    }
}