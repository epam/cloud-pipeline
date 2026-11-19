package com.epam.pipeline.utils.condition.evaluation;

import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.utils.condition.ConditionType;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UserAuthoritiesFieldEvaluationStrategyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    private static final String ALICE = "alice";
    private static final String TEAM_A = "team-a";
    private static final String TEAM_B = "team-b";

    private Map<String, Set<String>> authoritiesStore;
    private UserAuthoritiesFieldEvaluationStrategy<String> strategy;

    @BeforeEach
    public void setUp() {
        authoritiesStore = new HashMap<>();
        strategy = new UserAuthoritiesFieldEvaluationStrategy<>(
            ownerField(),
            owner -> authoritiesStore.getOrDefault(owner, Collections.emptySet()));
    }

    @Test
    public void shouldMatchWhenOwnerIsMemberOfGroup() {
        authoritiesStore.put(ALICE, Collections.singleton(TEAM_A));
        assertTrue(strategy.evaluate(leaf("=", TEAM_A), ALICE, NOW));
    }

    @Test
    public void shouldNotMatchWhenOwnerIsNotMemberOfGroup() {
        authoritiesStore.put(ALICE, Collections.singleton(TEAM_B));
        assertFalse(strategy.evaluate(leaf("=", TEAM_A), ALICE, NOW));
    }

    @Test
    public void shouldReturnFalseForNotEqualsWhenOwnerIsMember() {
        authoritiesStore.put(ALICE, Collections.singleton(TEAM_A));
        assertFalse(strategy.evaluate(leaf("!=", TEAM_A), ALICE, NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenOwnerIsNotMember() {
        authoritiesStore.put(ALICE, Collections.singleton(TEAM_B));
        assertTrue(strategy.evaluate(leaf("!=", TEAM_A), ALICE, NOW));
    }

    @Test
    public void shouldMatchGroupCaseInsensitively() {
        authoritiesStore.put(ALICE, Collections.singleton("Team-A"));
        assertTrue(strategy.evaluate(leaf("=", TEAM_A), ALICE, NOW));
    }

    @Test
    public void shouldMatchOwnerInAnyOfMultipleGroups() {
        authoritiesStore.put(ALICE, new HashSet<>(Arrays.asList(TEAM_A, TEAM_B)));
        assertTrue(strategy.evaluate(leaf("=", TEAM_B), ALICE, NOW));
    }

    @Test
    public void shouldReturnFalseWhenOwnerHasNoAuthorities() {
        assertFalse(strategy.evaluate(leaf("=", TEAM_A), ALICE, NOW));
    }

    @Test
    public void shouldReturnFalseWhenOwnerIsNull() {
        assertFalse(strategy.evaluate(leaf("=", TEAM_A), null, NOW));
    }

    @Test
    public void shouldReturnTrueForNotEqualsWhenOwnerIsNull() {
        assertTrue(strategy.evaluate(leaf("!=", TEAM_A), null, NOW));
    }

    @Test
    public void shouldReturnFalseWhenAuthoritiesResolverReturnsNull() {
        final UserAuthoritiesFieldEvaluationStrategy<String> strategyWithNullResolver =
                new UserAuthoritiesFieldEvaluationStrategy<>(ownerField(), owner -> null);
        assertFalse(strategyWithNullResolver.evaluate(leaf("=", TEAM_A), ALICE, NOW));
    }

    @Test
    public void shouldThrowForUnsupportedOperator() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.evaluate(leaf(">", TEAM_A), ALICE, NOW));
    }

    @Test
    public void shouldThrowForUnknownOperatorSymbol() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.evaluate(leaf("<>", TEAM_A), ALICE, NOW));
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
