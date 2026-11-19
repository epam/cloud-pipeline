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

public class EnumFieldEvaluationStrategyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_STOPPED = "STOPPED";

    private final EnumFieldEvaluationStrategy<String> strategy =
            new EnumFieldEvaluationStrategy<>(field());

    @Test
    public void shouldMatchExactEnumName() {
        assertTrue(strategy.evaluate(leaf("=", STATUS_RUNNING), STATUS_RUNNING, NOW));
    }

    @Test
    public void shouldMatchCaseInsensitively() {
        assertTrue(strategy.evaluate(leaf("=", "running"), STATUS_RUNNING, NOW));
        assertTrue(strategy.evaluate(leaf("=", STATUS_RUNNING), "running", NOW));
        assertTrue(strategy.evaluate(leaf("=", "Running"), STATUS_RUNNING, NOW));
    }

    @Test
    public void shouldNotMatchDifferentEnumValue() {
        assertFalse(strategy.evaluate(leaf("=", STATUS_STOPPED), STATUS_RUNNING, NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenValuesDiffer() {
        assertTrue(strategy.evaluate(leaf("!=", STATUS_STOPPED), STATUS_RUNNING, NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenValuesMatch() {
        assertFalse(strategy.evaluate(leaf("!=", STATUS_RUNNING), STATUS_RUNNING, NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWithCaseInsensitiveMatch() {
        assertFalse(strategy.evaluate(leaf("!=", "running"), STATUS_RUNNING, NOW));
    }

    @Test
    public void shouldReturnFalseWhenSubjectValueIsNull() {
        assertFalse(strategy.evaluate(leaf("=", STATUS_RUNNING), null, NOW));
    }

    @Test
    public void shouldThrowForUnsupportedOperator() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.evaluate(leaf(">", STATUS_RUNNING), STATUS_RUNNING, NOW));
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
                return FieldType.ENUM;
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
