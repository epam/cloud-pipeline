package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KeyValueFieldEvaluationStrategyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String KEY_DATASET = "dataset";
    private static final String VALUE_PROD = "prod";
    private static final String KEY_KV = "key";

    private final KeyValueFieldEvaluationStrategy<Map<String, String>> strategy =
            new KeyValueFieldEvaluationStrategy<>(mapField());

    // Key-only expressions

    @Test
    public void shouldMatchWhenKeyIsPresentAndNoValueRequired() {
        assertTrue(strategy.evaluate(leaf("=", KEY_DATASET), map(KEY_DATASET, VALUE_PROD), NOW));
    }

    @Test
    public void shouldNotMatchWhenKeyIsAbsentAndEqualOperator() {
        assertFalse(strategy.evaluate(leaf("=", "missing"), map(KEY_DATASET, VALUE_PROD), NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenKeyIsAbsent() {
        assertTrue(strategy.evaluate(leaf("!=", "missing"), map(KEY_DATASET, VALUE_PROD), NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenKeyIsPresent() {
        assertFalse(strategy.evaluate(leaf("!=", KEY_DATASET), map(KEY_DATASET, VALUE_PROD), NOW));
    }

    @Test
    public void shouldMatchKeyNameCaseInsensitively() {
        assertTrue(strategy.evaluate(leaf("=", "DATASET"), map(KEY_DATASET, VALUE_PROD), NOW));
        assertTrue(strategy.evaluate(leaf("=", KEY_DATASET), map("DATASET", VALUE_PROD), NOW));
    }

    // Key + value expressions

    @Test
    public void shouldMatchWhenKeyAndValueBothMatch() {
        assertTrue(strategy.evaluate(leaf("=", "dataset=prod"), map(KEY_DATASET, VALUE_PROD), NOW));
    }

    @Test
    public void shouldNotMatchWhenKeyPresentButValueDiffers() {
        assertFalse(strategy.evaluate(leaf("=", "dataset=staging"), map(KEY_DATASET, VALUE_PROD), NOW));
    }

    @Test
    public void shouldNotMatchWhenKeyAbsentInKeyValueExpression() {
        assertFalse(strategy.evaluate(leaf("=", "missing=prod"), map(KEY_DATASET, VALUE_PROD), NOW));
    }

    @Test
    public void shouldMatchKeyValueWithWildcardInValue() {
        assertTrue(strategy.evaluate(leaf("=", "dataset=prod-*"), map(KEY_DATASET, "prod-dataset"), NOW));
    }

    @Test
    public void shouldNotMatchKeyValueWhenWildcardPatternFails() {
        assertFalse(strategy.evaluate(leaf("=", "dataset=staging-*"), map(KEY_DATASET, "prod-dataset"), NOW));
    }

    @Test
    public void shouldMatchKeyValueWildcardCaseInsensitively() {
        assertTrue(strategy.evaluate(leaf("=", "dataset=PROD*"), map(KEY_DATASET, "prod-dataset"), NOW));
    }

    @Test
    public void shouldMatchEmptyValueWhenSubjectValueIsEmpty() {
        assertTrue(strategy.evaluate(leaf("=", "dataset="), map(KEY_DATASET, ""), NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenKeyPresentButValueDiffers() {
        assertTrue(strategy.evaluate(leaf("!=", "dataset=staging"), map(KEY_DATASET, VALUE_PROD), NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenKeyAndValueBothMatch() {
        assertFalse(strategy.evaluate(leaf("!=", "dataset=prod"), map(KEY_DATASET, VALUE_PROD), NOW));
    }

    // Null / empty map

    @Test
    public void shouldReturnFalseForNullMap() {
        assertFalse(strategy.evaluate(leaf("=", KEY_KV), null, NOW));
    }

    @Test
    public void shouldReturnFalseForEmptyMap() {
        assertFalse(strategy.evaluate(leaf("=", KEY_KV), Collections.emptyMap(), NOW));
    }

    // Operator validation

    @Test
    public void shouldThrowForUnsupportedOperator() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.evaluate(leaf(">", KEY_KV), map(KEY_KV, "val"), NOW));
    }

    @Test
    public void shouldThrowForUnknownOperatorSymbol() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.evaluate(leaf("<>", KEY_KV), map(KEY_KV, "val"), NOW));
    }

    private static Map<String, String> map(final String key, final String value) {
        final Map<String, String> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    private static ConditionExpression leaf(final String operand, final String value) {
        final ConditionExpression e = new ConditionExpression();
        e.setType(ConditionType.LOGICAL);
        e.setField("run.parameter");
        e.setOperand(operand);
        e.setValue(value);
        return e;
    }

    private static SubjectEntityField<Map<String, String>> mapField() {
        return new SubjectEntityField<Map<String, String>>() {
            @Override
            public FieldType getType() {
                return FieldType.KEY_VALUE;
            }
            @Override
            public boolean isSupportsDuration() {
                return false;
            }
            @Override
            public String extract(final Map<String, String> m) {
                return null;
            }
            @Override
            public Map<String, String> extractMap(final Map<String, String> m) {
                return m != null ? m : Collections.emptyMap();
            }
            @Override
            public List<String> getDisplayNames() {
                return Collections.emptyList();
            }
        };
    }
}
