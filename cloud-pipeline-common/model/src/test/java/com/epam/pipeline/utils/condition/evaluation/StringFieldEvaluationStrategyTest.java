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

public class StringFieldEvaluationStrategyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String M5_XLARGE = "m5.xlarge";
    private static final String M5_WILDCARD = "m5.*";

    private final StringFieldEvaluationStrategy<String> strategy =
            new StringFieldEvaluationStrategy<>(field());

    @Test
    public void shouldMatchOnExactEquality() {
        assertTrue(strategy.evaluate(leaf("=", M5_XLARGE), M5_XLARGE, NOW));
    }

    @Test
    public void shouldNotMatchWhenValuesAreDifferent() {
        assertFalse(strategy.evaluate(leaf("=", M5_XLARGE), "c5.xlarge", NOW));
    }

    @Test
    public void shouldMatchCaseInsensitively() {
        assertTrue(strategy.evaluate(leaf("=", M5_XLARGE), "M5.XLARGE", NOW));
    }

    @Test
    public void shouldMatchWildcardSuffix() {
        assertTrue(strategy.evaluate(leaf("=", M5_WILDCARD), M5_XLARGE, NOW));
    }

    @Test
    public void shouldMatchWildcardPrefix() {
        assertTrue(strategy.evaluate(leaf("=", "*.xlarge"), M5_XLARGE, NOW));
    }

    @Test
    public void shouldMatchWildcardInMiddle() {
        assertTrue(strategy.evaluate(leaf("=", "m5.x*ge"), M5_XLARGE, NOW));
    }

    @Test
    public void shouldMatchStandaloneWildcardAgainstAnything() {
        assertTrue(strategy.evaluate(leaf("=", "*"), "anything-at-all", NOW));
    }

    @Test
    public void shouldNotMatchWildcardPatternForDifferentFamily() {
        assertFalse(strategy.evaluate(leaf("=", M5_WILDCARD), "c5.xlarge", NOW));
    }

    @Test
    public void shouldNotMatchWildcardWhenLiteralPartDiffers() {
        assertFalse(strategy.evaluate(leaf("=", "m5.x*ge"), "m5.large", NOW));
    }

    @Test
    public void shouldTreatDotAsLiteralNotRegexWildcard() {
        assertFalse(strategy.evaluate(leaf("=", M5_XLARGE), "m5Axlarge", NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenValuesAreDifferent() {
        assertTrue(strategy.evaluate(leaf("!=", M5_WILDCARD), "c5.xlarge", NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenPatternMatches() {
        assertFalse(strategy.evaluate(leaf("!=", M5_WILDCARD), M5_XLARGE, NOW));
    }

    @Test
    public void shouldReturnFalseWhenSubjectValueIsNull() {
        assertFalse(strategy.evaluate(leaf("=", M5_XLARGE), null, NOW));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForUnsupportedNumericOperator() {
        strategy.evaluate(leaf(">", M5_XLARGE), M5_XLARGE, NOW);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForUnknownOperatorSymbol() {
        strategy.evaluate(leaf("<>", M5_XLARGE), M5_XLARGE, NOW);
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
                return FieldType.STRING;
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
