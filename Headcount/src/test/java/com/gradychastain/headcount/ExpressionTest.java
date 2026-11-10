package com.gradychastain.headcount;

import com.gradychastain.headcount.scoring.Expression;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionTest {
    private static double eval(String s, Map<String, Double> vars) {
        return Expression.parse(s).evaluate(vars::get);
    }

    private static double eval(String s) {
        return eval(s, Map.of());
    }

    @Test
    void defaultFormula() {
        assertEquals(0, eval("unique - ceil(active / 4)", Map.of("unique", 3.0, "active", 12.0)));
        assertEquals(7, eval("unique - ceil(active / 4)", Map.of("unique", 10.0, "active", 12.0)));
        assertEquals(3, eval("unique - ceil(active / 4)", Map.of("unique", 5.0, "active", 5.0)));
    }

    @Test
    void precedenceAndAssociativity() {
        assertEquals(14, eval("2 + 3 * 4"));
        assertEquals(20, eval("(2 + 3) * 4"));
        assertEquals(-4, eval("-2 ^ 2"));
        assertEquals(512, eval("2 ^ 3 ^ 2"));
        assertEquals(1, eval("10 % 3"));
        assertEquals(2.5, eval("5 / 2"));
        assertEquals(1, eval("8 - 4 - 3"));
    }

    @Test
    void comparisonsLogicAndTernary() {
        assertEquals(1, eval("3 >= 3"));
        assertEquals(0, eval("3 > 3"));
        assertEquals(1, eval("2 < 3 && 4 != 5"));
        assertEquals(1, eval("0 || 2"));
        assertEquals(0, eval("!5"));
        assertEquals(10, eval("rank <= 3 ? 10 : 0", Map.of("rank", 2.0)));
        assertEquals(-3, eval("rank <= 3 ? 10 - rank * 2 : (percentile < 25 ? -3 : 0)", Map.of("rank", 9.0, "percentile", 10.0)));
    }

    @Test
    void functions() {
        assertEquals(2, eval("min(5, 2, 9)"));
        assertEquals(9, eval("max(5, 2, 9)"));
        assertEquals(4, eval("avg(2, 6)"));
        assertEquals(3, eval("floor(3.9)"));
        assertEquals(4, eval("ceil(3.1)"));
        assertEquals(3.14, eval("round(pi, 2)"));
        assertEquals(4, eval("round(3.5)"));
        assertEquals(5, eval("abs(-5)"));
        assertEquals(8, eval("pow(2, 3)"));
        assertEquals(3, eval("sqrt(9)"));
        assertEquals(2, eval("log(100, 10)"));
        assertEquals(10, eval("clamp(15, 0, 10)"));
        assertEquals(0, eval("clamp(-4, 0, 10)"));
        assertEquals(7, eval("if(1 > 0, 7, 8)"));
        assertEquals(-1, eval("sign(-3)"));
        assertEquals(3, eval("trunc(3.99)"));
    }

    @Test
    void variablesAndPlaceholders() {
        Expression e = Expression.parse("unique + %statistic_kills% * 2 + missing");
        assertTrue(e.variables().contains("unique"));
        assertTrue(e.placeholders().contains("%statistic_kills%"));
        Map<String, Double> vars = Map.of("unique", 5.0, "%statistic_kills%", 3.0);
        assertEquals(11, e.evaluate(vars::get)); // "missing" is unknown and counts as 0
        // the remainder operator still works next to a placeholder-looking percent sign
        assertEquals(1, eval("10 % 3"));
    }

    @Test
    void errors() {
        assertThrows(IllegalArgumentException.class, () -> Expression.parse("2 +"));
        assertThrows(IllegalArgumentException.class, () -> Expression.parse("foo(1)"));
        assertThrows(IllegalArgumentException.class, () -> Expression.parse("pow(1)"));
        assertThrows(IllegalArgumentException.class, () -> Expression.parse("(1 + 2"));
        assertThrows(IllegalArgumentException.class, () -> Expression.parse("1 2"));
    }
}
