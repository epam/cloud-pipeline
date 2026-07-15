package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TagFieldEvaluationStrategyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final int DURATION_48H = 48;
    private static final int DURATION_50H = 50;
    private static final int DURATION_24H = 24;

    private final TagFieldEvaluationStrategy<Map<String, String>> strategy =
            new TagFieldEvaluationStrategy<>(field(), map -> map);

    // Basic key-presence checks

    @Test
    public void shouldMatchWhenTagKeyIsPresent() {
        assertTrue(strategy.evaluate(leaf("=", "IDLE"), tags("IDLE", "true"), NOW));
    }

    @Test
    public void shouldNotMatchWhenTagKeyIsAbsent() {
        assertFalse(strategy.evaluate(leaf("=", "IDLE"), tags("OTHER", "true"), NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenTagIsAbsent() {
        assertTrue(strategy.evaluate(leaf("!=", "IDLE"), tags("OTHER", "true"), NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenTagIsPresent() {
        assertFalse(strategy.evaluate(leaf("!=", "IDLE"), tags("IDLE", "true"), NOW));
    }

    @Test
    public void shouldMatchTagKeyCaseInsensitively() {
        assertTrue(strategy.evaluate(leaf("=", "IDLE"), tags("idle", "true"), NOW));
        assertTrue(strategy.evaluate(leaf("=", "idle"), tags("IDLE", "true"), NOW));
    }

    @Test
    public void shouldMatchFirstTagOfMultiple() {
        final Map<String, String> t = new HashMap<>();
        t.put("LONG-RUNNING", "true");
        t.put("IDLE", "true");
        assertTrue(strategy.evaluate(leaf("=", "IDLE"), t, NOW));
    }

    @Test
    public void shouldReturnFalseForNullTagsMap() {
        assertFalse(strategy.evaluate(leaf("=", "IDLE"), null, NOW));
    }

    @Test
    public void shouldReturnFalseForEmptyTagsMap() {
        assertFalse(strategy.evaluate(leaf("=", "IDLE"), Collections.emptyMap(), NOW));
    }

    // Duration gate: null duration → skip gate, return boolean result

    @Test
    public void shouldReturnBooleanResultWhenDurationIsNull() {
        assertTrue(strategy.evaluate(leaf("=", "IDLE"), tags("IDLE", "true"), NOW));
        assertFalse(strategy.evaluate(leaf("=", "IDLE"), tags("OTHER", "true"), NOW));
    }

    // Duration gate: boolean check passes, then duration gate applies

    @Test
    public void shouldMatchWhenTagPresentAndDurationExceeded() {
        final Map<String, String> t = tagsWithDate("IDLE", NOW.minusHours(DURATION_50H));
        assertTrue(strategy.evaluate(leafWithDuration("=", "IDLE", DURATION_48H), t, NOW));
    }

    @Test
    public void shouldNotMatchWhenTagPresentButDurationNotYetMet() {
        final Map<String, String> t = tagsWithDate("IDLE", NOW.minusHours(DURATION_24H));
        assertFalse(strategy.evaluate(leafWithDuration("=", "IDLE", DURATION_48H), t, NOW));
    }

    @Test
    public void shouldMatchWhenTagPresentAndDurationExactlyMet() {
        final Map<String, String> t = tagsWithDate("IDLE", NOW.minusHours(DURATION_48H));
        assertTrue(strategy.evaluate(leafWithDuration("=", "IDLE", DURATION_48H), t, NOW));
    }

    @Test
    public void shouldMatchWithZeroDurationWhenTagJustApplied() {
        final Map<String, String> t = tagsWithDate("IDLE", NOW);
        assertTrue(strategy.evaluate(leafWithDuration("=", "IDLE", 0), t, NOW));
    }

    @Test
    public void shouldNotMatchWhenTagAbsentAndDurationSet() {
        final Map<String, String> t = tags("OTHER", "true");
        assertFalse(strategy.evaluate(leafWithDuration("=", "IDLE", DURATION_48H), t, NOW));
    }

    @Test
    public void shouldNotMatchWhenTagPresentButDateTagAbsent() {
        final Map<String, String> t = tags("IDLE", "true");
        assertFalse(strategy.evaluate(leafWithDuration("=", "IDLE", DURATION_48H), t, NOW));
    }

    @Test
    public void shouldNotMatchWhenDateTagValueIsUnparseable() {
        final Map<String, String> t = new HashMap<>();
        t.put("IDLE", "true");
        t.put("IDLE" + TagFieldEvaluationStrategy.DATE_SUFFIX, "not-a-date");
        assertFalse(strategy.evaluate(leafWithDuration("=", "IDLE", DURATION_48H), t, NOW));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForUnsupportedOperator() {
        strategy.evaluate(leaf(">", "IDLE"), tags("IDLE", "true"), NOW);
    }

    private static Map<String, String> tags(final String key, final String value) {
        final Map<String, String> t = new HashMap<>();
        t.put(key, value);
        return t;
    }

    private static Map<String, String> tagsWithDate(final String tagName,
                                                     final LocalDateTime appliedAt) {
        final Map<String, String> t = new HashMap<>();
        t.put(tagName, "true");
        t.put(tagName + TagFieldEvaluationStrategy.DATE_SUFFIX, DATE_FMT.format(appliedAt));
        return t;
    }

    private static ConditionExpression leaf(final String operand, final String value) {
        final ConditionExpression e = new ConditionExpression();
        e.setType(ConditionType.LOGICAL);
        e.setField("run.tag");
        e.setOperand(operand);
        e.setValue(value);
        return e;
    }

    private static ConditionExpression leafWithDuration(final String operand,
                                                         final String value,
                                                         final int durationHours) {
        final ConditionExpression e = leaf(operand, value);
        e.setDuration(durationHours);
        return e;
    }

    private static SubjectEntityField<Map<String, String>> field() {
        return new SubjectEntityField<Map<String, String>>() {
            @Override
            public FieldType getType() {
                return FieldType.TAGS;
            }
            @Override
            public boolean isSupportsDuration() {
                return true;
            }
            @Override
            public String extract(final Map<String, String> m) {
                if (m == null || m.isEmpty()) {
                    return "";
                }
                return String.join(",", m.keySet());
            }
            @Override
            public List<String> getDisplayNames() {
                return Collections.emptyList();
            }
        };
    }
}
