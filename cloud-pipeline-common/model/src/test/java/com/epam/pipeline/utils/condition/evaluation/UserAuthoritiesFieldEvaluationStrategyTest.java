package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UserAuthoritiesFieldEvaluationStrategyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

    private Map<String, Set<String>> authoritiesStore;
    private UserAuthoritiesFieldEvaluationStrategy<String> strategy;

    @Before
    public void setUp() {
        authoritiesStore = new HashMap<>();
        strategy = new UserAuthoritiesFieldEvaluationStrategy<>(
            ownerField(),
            owner -> authoritiesStore.getOrDefault(owner, Collections.emptySet()));
    }

    @Test
    public void shouldMatchWhenOwnerIsMemberOfGroup() {
        authoritiesStore.put("alice", Collections.singleton("team-a"));
        assertTrue(strategy.evaluate(leaf("=", "team-a"), "alice", NOW));
    }

    @Test
    public void shouldNotMatchWhenOwnerIsNotMemberOfGroup() {
        authoritiesStore.put("alice", Collections.singleton("team-b"));
        assertFalse(strategy.evaluate(leaf("=", "team-a"), "alice", NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenOwnerIsMember() {
        authoritiesStore.put("alice", Collections.singleton("team-a"));
        assertFalse(strategy.evaluate(leaf("!=", "team-a"), "alice", NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenOwnerIsNotMember() {
        authoritiesStore.put("alice", Collections.singleton("team-b"));
        assertTrue(strategy.evaluate(leaf("!=", "team-a"), "alice", NOW));
    }

    @Test
    public void shouldMatchGroupCaseInsensitively() {
        authoritiesStore.put("alice", Collections.singleton("Team-A"));
        assertTrue(strategy.evaluate(leaf("=", "team-a"), "alice", NOW));
    }

    @Test
    public void shouldMatchOwnerInAnyOfMultipleGroups() {
        authoritiesStore.put("alice", new HashSet<>(Arrays.asList("team-a", "team-b")));
        assertTrue(strategy.evaluate(leaf("=", "team-b"), "alice", NOW));
    }

    @Test
    public void shouldReturnFalseWhenOwnerHasNoAuthorities() {
        assertFalse(strategy.evaluate(leaf("=", "team-a"), "alice", NOW));
    }

    @Test
    public void shouldReturnFalseWhenOwnerIsNull() {
        assertFalse(strategy.evaluate(leaf("=", "team-a"), null, NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenOwnerIsNull() {
        assertTrue(strategy.evaluate(leaf("!=", "team-a"), null, NOW));
    }

    @Test
    public void shouldReturnFalseWhenAuthoritiesResolverReturnsNull() {
        final UserAuthoritiesFieldEvaluationStrategy<String> strategyWithNullResolver =
                new UserAuthoritiesFieldEvaluationStrategy<>(ownerField(), owner -> null);
        assertFalse(strategyWithNullResolver.evaluate(leaf("=", "team-a"), "alice", NOW));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForUnsupportedOperator() {
        strategy.evaluate(leaf(">", "team-a"), "alice", NOW);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowForUnknownOperatorSymbol() {
        strategy.evaluate(leaf("<>", "team-a"), "alice", NOW);
    }

    private static ConditionExpression leaf(final String operand, final String value) {
        final ConditionExpression e = new ConditionExpression();
        e.setType(ConditionType.LOGICAL);
        e.setField("run.owner.authorities");
        e.setOperand(operand);
        e.setValue(value);
        return e;
    }

    private static SubjectEntityField<String> ownerField() {
        return new SubjectEntityField<String>() {
            @Override
            public FieldType getType() {
                return FieldType.USER_AUTHORITIES;
            }
            @Override
            public boolean isSupportsDuration() {
                return false;
            }
            @Override
            public String extract(final String owner) {
                return owner;
            }
            @Override
            public List<String> getDisplayNames() {
                return Collections.emptyList();
            }
        };
    }
}
