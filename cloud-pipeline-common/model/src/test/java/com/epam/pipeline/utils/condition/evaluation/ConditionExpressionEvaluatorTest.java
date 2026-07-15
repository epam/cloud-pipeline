package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.RunInstance;
import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.field.PipelineRunField;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConditionExpressionEvaluatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String NODE_TYPE_M5 = "m5.xlarge";
    private static final String NODE_TYPE_C5 = "c5.xlarge";

    private static final PipelineRun RUN = runWithNodeType(NODE_TYPE_M5);

    private ConditionExpressionEvaluator<PipelineRun> evaluator;

    @Before
    public void setUp() {
        final Map<String, EntityConditionEvaluationStrategy<PipelineRun>> registry = new HashMap<>();
        final StringFieldEvaluationStrategy<PipelineRun> strategy =
                new StringFieldEvaluationStrategy<>(PipelineRunField.INSTANCE_TYPE);
        for (final String name : PipelineRunField.INSTANCE_TYPE.getDisplayNames()) {
            registry.put(name, strategy);
        }
        evaluator = new ConditionExpressionEvaluator<>(registry);
    }

    @Test
    public void shouldReturnFalseForNullExpression() {
        assertFalse(evaluator.evaluate(null, RUN, NOW));
    }

    @Test
    public void shouldDelegateLogicalLeafToRegisteredStrategy() {
        assertTrue(evaluator.evaluate(nodeTypeLeaf("=", NODE_TYPE_M5), RUN, NOW));
    }

    @Test
    public void shouldReturnFalseWhenLogicalLeafDoesNotMatch() {
        assertFalse(evaluator.evaluate(nodeTypeLeaf("=", NODE_TYPE_C5), RUN, NOW));
    }

    @Test
    public void shouldReturnTrueForAndNodeWhenAllChildrenMatch() {
        assertTrue(evaluator.evaluate(
                and(nodeTypeLeaf("=", NODE_TYPE_M5), nodeTypeLeaf("=", NODE_TYPE_M5)),
                RUN, NOW));
    }

    @Test
    public void shouldReturnFalseForAndNodeWhenOneChildFails() {
        assertFalse(evaluator.evaluate(
                and(nodeTypeLeaf("=", NODE_TYPE_M5), nodeTypeLeaf("=", NODE_TYPE_C5)),
                RUN, NOW));
    }

    @Test
    public void shouldReturnTrueForAndNodeWithEmptyChildren() {
        final ConditionExpression node = new ConditionExpression();
        node.setType(ConditionType.AND);
        node.setExpressions(Collections.emptyList());
        assertTrue(evaluator.evaluate(node, RUN, NOW));
    }

    @Test
    public void shouldReturnTrueForAndNodeWithNullChildren() {
        final ConditionExpression node = new ConditionExpression();
        node.setType(ConditionType.AND);
        assertTrue(evaluator.evaluate(node, RUN, NOW));
    }

    @Test
    public void shouldReturnTrueForOrNodeWhenOneChildMatches() {
        assertTrue(evaluator.evaluate(
                or(nodeTypeLeaf("=", NODE_TYPE_C5), nodeTypeLeaf("=", NODE_TYPE_M5)),
                RUN, NOW));
    }

    @Test
    public void shouldReturnFalseForOrNodeWhenNoChildMatches() {
        assertFalse(evaluator.evaluate(
                or(nodeTypeLeaf("=", NODE_TYPE_C5), nodeTypeLeaf("=", NODE_TYPE_C5)),
                RUN, NOW));
    }

    @Test
    public void shouldReturnFalseForOrNodeWithEmptyChildren() {
        final ConditionExpression node = new ConditionExpression();
        node.setType(ConditionType.OR);
        node.setExpressions(Collections.emptyList());
        assertFalse(evaluator.evaluate(node, RUN, NOW));
    }

    @Test
    public void shouldReturnFalseForOrNodeWithNullChildren() {
        final ConditionExpression node = new ConditionExpression();
        node.setType(ConditionType.OR);
        assertFalse(evaluator.evaluate(node, RUN, NOW));
    }

    @Test
    public void shouldReturnFalseForUnknownField() {
        assertFalse(evaluator.evaluate(leaf("no.such.field", "=", NODE_TYPE_M5), RUN, NOW));
    }

    @Test
    public void shouldReturnFalseWhenExpressionTypeIsNull() {
        final ConditionExpression node = new ConditionExpression();
        assertFalse(evaluator.evaluate(node, RUN, NOW));
    }

    @Test
    public void shouldReturnFalseWhenStrategyThrowsIllegalArgumentException() {
        assertFalse(evaluator.evaluate(nodeTypeLeaf(">", NODE_TYPE_M5), RUN, NOW));
    }

    @Test
    public void shouldEvaluateNestedAndInsideOrCorrectly() {
        // OR( AND(m5 match, c5 no-match), AND(m5 match, m5 match) ) → OR(false, true) → true
        assertTrue(evaluator.evaluate(
                or(
                    and(nodeTypeLeaf("=", NODE_TYPE_M5), nodeTypeLeaf("=", NODE_TYPE_C5)),
                    and(nodeTypeLeaf("=", NODE_TYPE_M5), nodeTypeLeaf("=", NODE_TYPE_M5))
                ),
                RUN, NOW));
    }

    @Test
    public void shouldEvaluateNestedOrInsideAndCorrectly() {
        // AND( OR(c5 no-match, m5 match), OR(m5 match, c5 no-match) ) → AND(true, true) → true
        assertTrue(evaluator.evaluate(
                and(
                    or(nodeTypeLeaf("=", NODE_TYPE_C5), nodeTypeLeaf("=", NODE_TYPE_M5)),
                    or(nodeTypeLeaf("=", NODE_TYPE_M5), nodeTypeLeaf("=", NODE_TYPE_C5))
                ),
                RUN, NOW));
    }

    @Test
    public void shouldReturnFalseForNestedAndInsideOrWhenBothAndsFail() {
        // OR( AND(m5 match, c5 no-match), AND(c5 no-match, m5 match) ) → OR(false, false) → false
        assertFalse(evaluator.evaluate(
                or(
                    and(nodeTypeLeaf("=", NODE_TYPE_M5), nodeTypeLeaf("=", NODE_TYPE_C5)),
                    and(nodeTypeLeaf("=", NODE_TYPE_C5), nodeTypeLeaf("=", NODE_TYPE_M5))
                ),
                RUN, NOW));
    }

    private static ConditionExpression nodeTypeLeaf(final String operand, final String value) {
        return leaf("node.type", operand, value);
    }

    private static ConditionExpression leaf(final String field, final String operand, final String value) {
        final ConditionExpression e = new ConditionExpression();
        e.setType(ConditionType.LOGICAL);
        e.setField(field);
        e.setOperand(operand);
        e.setValue(value);
        return e;
    }

    private static ConditionExpression and(final ConditionExpression... children) {
        final ConditionExpression e = new ConditionExpression();
        e.setType(ConditionType.AND);
        e.setExpressions(Arrays.asList(children));
        return e;
    }

    private static ConditionExpression or(final ConditionExpression... children) {
        final ConditionExpression e = new ConditionExpression();
        e.setType(ConditionType.OR);
        e.setExpressions(Arrays.asList(children));
        return e;
    }

    private static PipelineRun runWithNodeType(final String nodeType) {
        final RunInstance instance = new RunInstance();
        instance.setNodeType(nodeType);
        final PipelineRun run = new PipelineRun();
        run.setInstance(instance);
        return run;
    }
}
