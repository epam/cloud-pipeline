package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BooleanFieldEvaluationStrategyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    private final BooleanFieldEvaluationStrategy<String> strategy =
            new BooleanFieldEvaluationStrategy<>(field());

    @Test
    public void shouldMatchTrueSubjectAgainstTrueExpression() {
        assertTrue(strategy.evaluate(leaf("=", "true"), "true", NOW));
    }

    @Test
    public void shouldMatchFalseSubjectAgainstFalseExpression() {
        assertTrue(strategy.evaluate(leaf("=", "false"), "false", NOW));
    }

    @Test
    public void shouldNotMatchTrueSubjectAgainstFalseExpression() {
        assertFalse(strategy.evaluate(leaf("=", "false"), "true", NOW));
    }

    @Test
    public void shouldNotMatchFalseSubjectAgainstTrueExpression() {
        assertFalse(strategy.evaluate(leaf("=", "true"), "false", NOW));
    }

    @Test
    public void shouldMatchCaseInsensitivelyForTrueValues() {
        assertTrue(strategy.evaluate(leaf("=", "true"), "TRUE", NOW));
        assertTrue(strategy.evaluate(leaf("=", "TRUE"), "true", NOW));
        assertTrue(strategy.evaluate(leaf("=", "True"), "true", NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenValuesDiffer() {
        assertTrue(strategy.evaluate(leaf("!=", "false"), "true", NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenValuesMatch() {
        assertFalse(strategy.evaluate(leaf("!=", "true"), "true", NOW));
    }

    @Test
    public void shouldReturnFalseWhenSubjectValueIsNull() {
        assertFalse(strategy.evaluate(leaf("=", "true"), null, NOW));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForUnsupportedOperator() {
        strategy.evaluate(leaf(">", "true"), "true", NOW);
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
                return FieldType.BOOLEAN;
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
