/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.manager.cluster.capacityreservation;

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationPolicy;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationPolicyAction;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.user.UserManager;
import com.epam.pipeline.utils.condition.FieldType;
import com.epam.pipeline.utils.condition.evaluation.BooleanFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.ConditionExpressionEvaluator;
import com.epam.pipeline.utils.condition.evaluation.EntityConditionEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.EnumFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.NumericFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.StringFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.evaluation.UserAuthoritiesFieldEvaluationStrategy;
import com.epam.pipeline.utils.condition.field.CapacityReservationField;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decides whether a capacity reservation request is approved outright or has to wait for an administrator.
 */
@Service
@Slf4j
public class CapacityReservationApprovalService {

    private final ConditionExpressionEvaluator<CapacityReservation> evaluator;
    private final PreferenceManager preferenceManager;
    private final AuthManager authManager;

    /**
     * Lazy: this service is constructed as part of the reservation service's own graph, so an eager edge back to
     * it would be a cycle.
     */
    @Autowired
    @Lazy
    private CapacityReservationService reservationService;

    @Autowired
    public CapacityReservationApprovalService(final PreferenceManager preferenceManager,
                                             final AuthManager authManager,
                                             final UserManager userManager) {
        this.preferenceManager = preferenceManager;
        this.authManager = authManager;
        this.evaluator = new ConditionExpressionEvaluator<>(buildRegistry(username -> {
            if (username == null) {
                return Collections.emptySet();
            }
            final PipelineUser user = userManager.loadUserByName(username);
            if (user == null) {
                return Collections.emptySet();
            }
            return Stream.concat(
                    Optional.ofNullable(user.getGroups()).map(Collection::stream).orElseGet(Stream::empty),
                    ListUtils.emptyIfNull(user.getRoles()).stream().map(Role::getName)
            ).collect(Collectors.toList());
        }));
    }

    /**
     * @return {@link CapacityReservationStatus#APPROVED} when the request may proceed without a human, and
     *         {@link CapacityReservationStatus#REQUIRED_APPROVE} when it must wait for one.
     */
    public CapacityReservationStatus evaluate(final CapacityReservation reservation) {
        if (inheritsApproval(reservation)) {
            log.debug("Capacity reservation for node pool {} inherits the approval of reservation {}",
                    reservation.getNodePoolId(), reservation.getOriginId());
            return CapacityReservationStatus.APPROVED;
        }
        if (authManager.isAdmin()) {
            log.debug("Capacity reservation for node pool {} auto-approved: requester is an administrator",
                    reservation.getNodePoolId());
            return CapacityReservationStatus.APPROVED;
        }

        final List<CapacityReservationPolicy> policies = ListUtils.emptyIfNull(
                preferenceManager.getPreference(SystemPreferences.CLUSTER_CAPACITY_RESERVATION_POLICIES));

        // Deny rules are evaluated first, so a deny always wins over an auto-approve it overlaps with.
        if (matchesAny(policies, CapacityReservationPolicyAction.DENY, reservation)) {
            return CapacityReservationStatus.REQUIRED_APPROVE;
        }
        if (matchesAny(policies, CapacityReservationPolicyAction.AUTO_APPROVE, reservation)) {
            return CapacityReservationStatus.APPROVED;
        }
        return CapacityReservationStatus.REQUIRED_APPROVE;
    }

    /**
     * Whether this request may reuse the approval already given to the one it retries.
     *
     * <p>Only from a {@code FAILED} original, and only when nothing material changed. {@code FAILED} is the one
     * terminal state that means the request was approved, submitted, and then refused by the provider - so a
     * human has already agreed to this spend and the refusal was not the requester's doing. Every other state
     * fails the check for a reason:
     *
     * <ul>
     *   <li>{@code PURCHASE_FAILED} - our own call errored, so the provider may never have seen it and nobody
     *       has agreed to anything beyond the first attempt;</li>
     *   <li>{@code CANCELLED} - somebody deliberately gave this capacity up, and inheriting would quietly
     *       undo that;</li>
     *   <li>{@code FINISHED} - a reservation that ran its course is not evidence that another one is wanted;</li>
     *   <li>anything non-terminal - still in flight, so there is nothing settled to inherit.</li>
     * </ul>
     *
     * <p>A missing original also fails the check rather than passing: its pool may have been deleted, and an
     * unresolvable reference must not become a free pass.
     */
    private boolean inheritsApproval(final CapacityReservation reservation) {
        return Optional.ofNullable(reservation.getOriginId())
                .flatMap(reservationService::find)
                .filter(origin -> CapacityReservationStatus.FAILED == origin.getStatus())
                .filter(origin -> sameEssentials(origin, reservation))
                .isPresent();
    }

    /**
     * The parameters that determine what is being bought and at what cost. A retry that changes any of them is a
     * different request and needs its own decision; the dates deliberately are not compared, since retrying a
     * window that found nothing is the normal reason to retry at all.
     */
    private boolean sameEssentials(final CapacityReservation origin, final CapacityReservation retry) {
        return Objects.equals(origin.getInstanceType(), retry.getInstanceType())
                && origin.getInstanceCount() == retry.getInstanceCount()
                && Objects.equals(origin.getDurationHours(), retry.getDurationHours())
                && Objects.equals(origin.getReservationType(), retry.getReservationType())
                && Objects.equals(origin.getCloudProvider(), retry.getCloudProvider())
                && Objects.equals(origin.getRegionId(), retry.getRegionId())
                && Objects.equals(origin.getOwner(), retry.getOwner());
    }

    private boolean matchesAny(final List<CapacityReservationPolicy> policies,
                               final CapacityReservationPolicyAction action,
                               final CapacityReservation reservation) {
        return policies.stream()
                .filter(policy -> action == policy.getAction())
                .anyMatch(policy -> matches(policy, reservation));
    }

    private boolean matches(final CapacityReservationPolicy policy, final CapacityReservation reservation) {
        final java.time.LocalDateTime now = DateUtils.nowUTC();
        if (!evaluator.evaluate(policy.getStatement(), reservation, now)) {
            return false;
        }
        if (Objects.nonNull(policy.getExclude())
                && evaluator.evaluate(policy.getExclude(), reservation, now)) {
            log.debug("Capacity reservation for node pool {} matched a {} policy but was excluded from it",
                    reservation.getNodePoolId(), policy.getAction());
            return false;
        }
        log.debug("Capacity reservation for node pool {} matched a {} policy",
                reservation.getNodePoolId(), policy.getAction());
        return true;
    }

    /**
     * One evaluation strategy per field, chosen by the field's own declared type. Mirrors how
     * {@code PlatformUsageCreditsUpdateRuleEvaluator} builds its registry for pipeline runs.
     */
    private static Map<String, EntityConditionEvaluationStrategy<CapacityReservation>> buildRegistry(
            final java.util.function.Function<String, Collection<String>> authoritiesResolver) {
        final Map<String, EntityConditionEvaluationStrategy<CapacityReservation>> registry = new HashMap<>();
        for (final CapacityReservationField field : CapacityReservationField.values()) {
            final EntityConditionEvaluationStrategy<CapacityReservation> strategy =
                    strategyFor(field, authoritiesResolver);
            field.getDisplayNames().forEach(name -> registry.put(name, strategy));
        }
        return registry;
    }

    private static EntityConditionEvaluationStrategy<CapacityReservation> strategyFor(
            final CapacityReservationField field,
            final java.util.function.Function<String, Collection<String>> authoritiesResolver) {
        final FieldType type = field.getType();
        switch (type) {
            case STRING:
                return new StringFieldEvaluationStrategy<>(field);
            case NUMERIC:
                return new NumericFieldEvaluationStrategy<>(field);
            case BOOLEAN:
                return new BooleanFieldEvaluationStrategy<>(field);
            case ENUM:
                return new EnumFieldEvaluationStrategy<>(field);
            case USER_AUTHORITIES:
                return new UserAuthoritiesFieldEvaluationStrategy<>(field, authoritiesResolver);
            default:
                throw new IllegalStateException("Unsupported capacity reservation field type " + type);
        }
    }

    /**
     * Kept for tests, which need a registry they control rather than one built from a live user service.
     */
    CapacityReservationApprovalService(final PreferenceManager preferenceManager,
                                       final AuthManager authManager,
                                       final Map<String, EntityConditionEvaluationStrategy<CapacityReservation>>
                                               registry) {
        this.preferenceManager = preferenceManager;
        this.authManager = authManager;
        this.evaluator = new ConditionExpressionEvaluator<>(registry);
    }

    static Map<String, EntityConditionEvaluationStrategy<CapacityReservation>> defaultRegistry() {
        return buildRegistry(username -> Collections.singletonList(DefaultRoles.ROLE_USER.getName()));
    }
}
