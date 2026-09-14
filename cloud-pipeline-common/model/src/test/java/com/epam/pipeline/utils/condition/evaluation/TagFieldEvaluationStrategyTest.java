package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.field.PipelineRunField;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
    private static final String TAG_IDLE = "IDLE";
    private static final String TAG_OTHER = "OTHER";
    private static final String TAG_VALUE_TRUE = "true";
    private static final String TAG_VALUE_FALSE = "false";

    private final TagFieldEvaluationStrategy<PipelineRun> strategy =
            new TagFieldEvaluationStrategy<>(field());

    // Basic key-presence checks

    @Test
    public void shouldMatchWhenTagKeyIsPresent() {
        assertTrue(strategy.evaluate(leaf("=", TAG_IDLE), tags(TAG_IDLE, TAG_VALUE_TRUE), NOW));
    }

    @Test
    public void shouldNotMatchWhenTagKeyIsAbsent() {
        assertFalse(strategy.evaluate(leaf("=", TAG_IDLE), tags(TAG_OTHER, TAG_VALUE_TRUE), NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenTagIsAbsent() {
        assertTrue(strategy.evaluate(leaf("!=", TAG_IDLE), tags(TAG_OTHER, TAG_VALUE_TRUE), NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenTagIsPresent() {
        assertFalse(strategy.evaluate(leaf("!=", TAG_IDLE), tags(TAG_IDLE, TAG_VALUE_TRUE), NOW));
    }

    @Test
    public void shouldMatchTagKeyCaseInsensitively() {
        assertTrue(strategy.evaluate(leaf("=", TAG_IDLE), tags("idle", TAG_VALUE_TRUE), NOW));
        assertTrue(strategy.evaluate(leaf("=", "idle"), tags(TAG_IDLE, TAG_VALUE_TRUE), NOW));
    }

    @Test
    public void shouldMatchFirstTagOfMultiple() {
        PipelineRun pipelineRun = new PipelineRun();
        final Map<String, String> t = new HashMap<>();
        t.put("LONG-RUNNING", TAG_VALUE_TRUE);
        t.put(TAG_IDLE, TAG_VALUE_TRUE);
        pipelineRun.setTags(t);
        assertTrue(strategy.evaluate(leaf("=", TAG_IDLE), pipelineRun, NOW));
    }

    @Test
    public void shouldReturnFalseForNullTagsMap() {
        assertFalse(strategy.evaluate(leaf("=", TAG_IDLE), new PipelineRun(), NOW));
    }

    @Test
    public void shouldReturnFalseForEmptyTagsMap() {
        assertFalse(strategy.evaluate(leaf("=", TAG_IDLE), new PipelineRun(), NOW));
    }

    // Duration gate: null duration → skip gate, return boolean result

    @Test
    public void shouldReturnBooleanResultWhenDurationIsNull() {
        assertTrue(strategy.evaluate(leaf("=", TAG_IDLE), tags(TAG_IDLE, TAG_VALUE_TRUE), NOW));
        assertFalse(strategy.evaluate(leaf("=", TAG_IDLE), tags(TAG_OTHER, TAG_VALUE_TRUE), NOW));
    }

    // Duration gate: boolean check passes, then duration gate applies

    @Test
    public void shouldMatchWhenTagPresentAndDurationExceeded() {
        final PipelineRun t = tagsWithDate(TAG_IDLE, NOW.minusHours(DURATION_50H));
        assertTrue(strategy.evaluate(leafWithDuration("=", TAG_IDLE, DURATION_48H), t, NOW));
    }

    @Test
    public void shouldNotMatchWhenTagPresentButDurationNotYetMet() {
        final PipelineRun t = tagsWithDate(TAG_IDLE, NOW.minusHours(DURATION_24H));
        assertFalse(strategy.evaluate(leafWithDuration("=", TAG_IDLE, DURATION_48H), t, NOW));
    }

    @Test
    public void shouldMatchWhenTagPresentAndDurationExactlyMet() {
        final PipelineRun t = tagsWithDate(TAG_IDLE, NOW.minusHours(DURATION_48H));
        assertTrue(strategy.evaluate(leafWithDuration("=", TAG_IDLE, DURATION_48H), t, NOW));
    }

    @Test
    public void shouldMatchWithZeroDurationWhenTagJustApplied() {
        final PipelineRun t = tagsWithDate(TAG_IDLE, NOW);
        assertTrue(strategy.evaluate(leafWithDuration("=", TAG_IDLE, 0), t, NOW));
    }

    @Test
    public void shouldNotMatchWhenTagAbsentAndDurationSet() {
        final PipelineRun t = tags(TAG_OTHER, TAG_VALUE_TRUE);
        assertFalse(strategy.evaluate(leafWithDuration("=", TAG_IDLE, DURATION_48H), t, NOW));
    }

    @Test
    public void shouldNotMatchWhenTagPresentButDateTagAbsent() {
        final PipelineRun t = tags(TAG_IDLE, TAG_VALUE_TRUE);
        assertFalse(strategy.evaluate(leafWithDuration("=", TAG_IDLE, DURATION_48H), t, NOW));
    }

    @Test
    public void shouldNotMatchWhenDateTagValueIsUnparseable() {
        final Map<String, String> t = new HashMap<>();
        t.put(TAG_IDLE, TAG_VALUE_TRUE);
        t.put(TAG_IDLE + TagFieldEvaluationStrategy.DATE_SUFFIX, "not-a-date");
        assertFalse(strategy.evaluate(leafWithDuration("=", TAG_IDLE, DURATION_48H), pipelineRunWithTags(t), NOW));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForUnsupportedOperator() {
        strategy.evaluate(leaf(">", TAG_IDLE), tags(TAG_IDLE, TAG_VALUE_TRUE), NOW);
    }

    @Test
    public void shouldMatchWhenKeyAndValueSatisfy() {
        Map<String, String> tags = new HashMap<>();
        tags.put(TAG_IDLE, TAG_VALUE_TRUE);
        tags.put(TAG_OTHER, TAG_VALUE_FALSE);
        assertTrue(strategy.evaluate(leaf("=", TAG_IDLE + "=" + TAG_VALUE_TRUE), pipelineRunWithTags(tags), NOW));
    }

    @Test
    public void shouldNotMatchWhenKeyAndValueSatisfy() {
        Map<String, String> tags = new HashMap<>();
        tags.put(TAG_IDLE, TAG_VALUE_TRUE);
        tags.put(TAG_OTHER, TAG_VALUE_FALSE);
        assertFalse(strategy.evaluate(leaf("=", TAG_IDLE + "=" + TAG_VALUE_FALSE), pipelineRunWithTags(tags), NOW));
    }

    @Test
    public void shouldMatchWhenKeyAndValueSatisfyAndDuration() {
        final PipelineRun t = tagsWithDate(TAG_IDLE, NOW.minusHours(DURATION_48H));
        assertTrue(strategy.evaluate(leafWithDuration("=", TAG_IDLE + "=" + TAG_VALUE_TRUE, DURATION_24H), t, NOW));
    }

    @Test
    public void shouldMatchWhenKeyAndValueSatisfyAndDurationFits() {
        final PipelineRun t = tagsWithDate(TAG_IDLE, NOW.minusHours(DURATION_48H));
        assertTrue(strategy.evaluate(leafWithDuration("=", TAG_IDLE + "=" + TAG_VALUE_TRUE, DURATION_24H), t, NOW));
    }

    @Test
    public void shouldMatchWhenKeyAndValueNotSatisfyAndDurationFits() {
        final PipelineRun t = tagsWithDate(TAG_IDLE, NOW.minusHours(DURATION_48H));
        assertFalse(strategy.evaluate(leafWithDuration("=", TAG_OTHER + "=" + TAG_VALUE_TRUE, DURATION_24H), t, NOW));
    }

    @Test
    public void shouldNotMatchWhenKeyAndValueSatisfyAndDurationNotFits() {
        final PipelineRun t = tagsWithDate(TAG_IDLE, NOW.minusHours(DURATION_48H));
        assertFalse(strategy.evaluate(leafWithDuration("=", TAG_IDLE + "=" + TAG_VALUE_TRUE, DURATION_50H), t, NOW));
    }

    @Test
    public void shouldMatchWhenKeySatisfyAndValueNotForNotEquals() {
        Map<String, String> tags = new HashMap<>();
        tags.put(TAG_IDLE, TAG_VALUE_TRUE);
        tags.put(TAG_OTHER, TAG_VALUE_FALSE);
        assertTrue(strategy.evaluate(leaf("!=", TAG_IDLE + "=" + TAG_VALUE_FALSE), pipelineRunWithTags(tags), NOW));
    }

    @Test
    public void shouldNotMatchWhenKeyAndValueSatisfyForNotEquals() {
        Map<String, String> tags = new HashMap<>();
        tags.put(TAG_IDLE, TAG_VALUE_TRUE);
        tags.put(TAG_OTHER, TAG_VALUE_FALSE);
        assertFalse(strategy.evaluate(leaf("!=", TAG_IDLE + "=" + TAG_VALUE_TRUE), pipelineRunWithTags(tags), NOW));
    }

    private static PipelineRun tags(final String key, final String value) {
        final Map<String, String> t = new HashMap<>();
        t.put(key, value);
        return pipelineRunWithTags(t);
    }

    private static PipelineRun tagsWithDate(final String tagName,
                                                     final LocalDateTime appliedAt) {
        final Map<String, String> t = new HashMap<>();
        t.put(tagName, TAG_VALUE_TRUE);
        t.put(tagName + TagFieldEvaluationStrategy.DATE_SUFFIX, DATE_FMT.format(appliedAt));
        return pipelineRunWithTags(t);
    }

    private static PipelineRun pipelineRunWithTags(Map<String, String> tags) {
        final PipelineRun pipelineRun = new PipelineRun();
        pipelineRun.setTags(tags);
        return pipelineRun;
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

    private static SubjectEntityField<PipelineRun> field() {
        return PipelineRunField.TAG;
    }
}
