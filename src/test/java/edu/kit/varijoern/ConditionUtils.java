package edu.kit.varijoern;

import org.prop4j.And;
import org.prop4j.Node;
import org.prop4j.Not;
import org.prop4j.Or;

public class ConditionUtils {
    /**
     * Checks whether two conditions are equivalent by brute force.
     *
     * @param condition1 The first condition.
     * @param condition2 The second condition.
     * @return whether the two conditions are equivalent.
     */
    public static boolean areEquivalent(Node condition1, Node condition2) {
        return new Or(
                new And(condition1, condition2),
                new And(new Not(condition1), new Not(condition2))
        ).getContradictingAssignments().isEmpty();
    }
}
