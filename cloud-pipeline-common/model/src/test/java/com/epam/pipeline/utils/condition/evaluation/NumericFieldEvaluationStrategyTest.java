package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NumericFieldEvaluationStrategyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String HUNDRED = "100";
    private static final String FIFTY = "50";
    private static final String TWO_HUNDRED = "200";

    private final NumericFieldEvaluationStrategy<String> strategy =
            new NumericFieldEvaluationStrategy<>(field());

    // Equals / not-equals

    @Test
    public void shouldMatchWhenValuesAreEqual() {
        assertTrue(strategy.evaluate(leaf("=", HUNDRED), HUNDRED, NOW));
    }

    @Test
    public void shouldNotMatchWhenValuesAreNotEqual() {
        assertFalse(strategy.evaluate(leaf("=", HUNDRED), TWO_HUNDRED, NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenValuesDiffer() {
        assertTrue(strategy.evaluate(leaf("!=", HUNDRED), TWO_HUNDRED, NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenValuesAreEqual() {
        assertFalse(strategy.evaluate(leaf("!=", HUNDRED), HUNDRED, NOW));
    }

    // Greater

    @Test
    public void shouldReturnTrueWhenSubjectIsGreater() {
        assertTrue(strategy.evaluate(leaf(">", HUNDRED), TWO_HUNDRED, NOW));
    }

    @Test
    public void shouldReturnFalseWhenSubjectIsEqualForGreater() {
        assertFalse(strategy.evaluate(leaf(">", HUNDRED), HUNDRED, NOW));
    }

    @Test
    public void shouldReturnFalseWhenSubjectIsLessForGreater() {
        assertFalse(strategy.evaluate(leaf(">", HUNDRED), FIFTY, NOW));
    }

    // Greater-or-equals

    @Test
    public void shouldReturnTrueForGreaterOrEqualsWhenEqual() {
        assertTrue(strategy.evaluate(leaf(">=", HUNDRED), HUNDRED, NOW));
    }

    @Test
    public void shouldReturnTrueForGreaterOrEqualsWhenGreater() {
        assertTrue(strategy.evaluate(leaf(">=", HUNDRED), TWO_HUNDRED, NOW));
    }

    @Test
    public void shouldReturnFalseForGreaterOrEqualsWhenLess() {
        assertFalse(strategy.evaluate(leaf(">=", HUNDRED), FIFTY, NOW));
    }

    // Less

    @Test
    public void shouldReturnTrueWhenSubjectIsLess() {
        assertTrue(strategy.evaluate(leaf("<", HUNDRED), FIFTY, NOW));
    }

    @Test
    public void shouldReturnFalseWhenSubjectIsEqualForLess() {
        assertFalse(strategy.evaluate(leaf("<", HUNDRED), HUNDRED, NOW));
    }

    // Less-or-equals

    @Test
    public void shouldReturnTrueForLessOrEqualsWhenEqual() {
        assertTrue(strategy.evaluate(leaf("<=", HUNDRED), HUNDRED, NOW));
    }

    @Test
    public void shouldReturnTrueForLessOrEqualsWhenLess() {
        assertTrue(strategy.evaluate(leaf("<=", HUNDRED), FIFTY, NOW));
    }

    @Test
    public void shouldReturnFalseForLessOrEqualsWhenGreater() {
        assertFalse(strategy.evaluate(leaf("<=", HUNDRED), TWO_HUNDRED, NOW));
    }

    // Floating-point

    @Test
    public void shouldHandleFloatingPointValues() {
        assertTrue(strategy.evaluate(leaf(">", "1.5"), "2.0", NOW));
        assertFalse(strategy.evaluate(leaf(">", "2.0"), "1.5", NOW));
    }

    // Null / invalid subject

    @Test
    public void shouldReturnFalseWhenSubjectValueIsNull() {
        assertFalse(strategy.evaluate(leaf("=", HUNDRED), null, NOW));
    }

    @Test
    public void shouldThrowWhenSubjectValueIsNotNumeric() {
        assertThrows(NumberFormatException.class,
                () -> strategy.evaluate(leaf("=", HUNDRED), "not-a-number", NOW));
    }

    @Test
    public void shouldThrowForUnknownOperatorSymbol() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.evaluate(leaf("<>", HUNDRED), HUNDRED, NOW));
    }

    private static ConditionExpression leaf(final String operand, final String value) {
        final ConditionExpression e = new ConditionExpression();
        e.setType(ConditionType.LOGICAL);
        e.setField("test.field");
        e.setOperand(operand);
        e.setValue(value);
        return e;
    }

    private static SubjectEntityField<String> field() {
        return new SubjectEntityField<String>() {
            @Override
            public FieldType getType() {
                return FieldType.NUMERIC;
            }
            @Override
            public boolean isSupportsDuration() {
                return false;
            }
            @Override
            public String extract(final String s) {
                return s;
            }
            @Override
            public List<String> getDisplayNames() {
                return Collections.emptyList();
            }
        };
    }
}
