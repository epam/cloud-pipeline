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

import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationMonitorSettings;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationPolicy;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationPolicyAction;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.utils.condition.field.CapacityReservationField;
import com.epam.pipeline.utils.condition.field.SubjectEntityField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class CapacityReservationPolicyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int INSTANCE_COUNT = 4;
    private static final long COMMITMENT_DURATION = 48 * 3600L;

    private static final String POLICIES_JSON = "["
            + "{"
            + "  \"action\": \"DENY\","
            + "  \"statement\": {"
            + "    \"type\": \"AND\","
            + "    \"expressions\": ["
            + "      {\"type\": \"LOGICAL\", \"field\": \"cloud.provider\", \"operand\": \"=\", "
            + "       \"value\": \"AWS\"},"
            + "      {\"type\": \"LOGICAL\", \"field\": \"commitment.duration\", \"operand\": \">\", "
            + "       \"value\": \"1209600\"}"
            + "    ]"
            + "  }"
            + "},"
            + "{"
            + "  \"action\": \"AUTO_APPROVE\","
            + "  \"statement\": {"
            + "    \"type\": \"AND\","
            + "    \"expressions\": ["
            + "      {\"type\": \"LOGICAL\", \"field\": \"instance.type\", \"operand\": \"=\", "
            + "       \"value\": \"p5.*\"},"
            + "      {\"type\": \"LOGICAL\", \"field\": \"owner.authorities\", \"operand\": \"=\", "
            + "       \"value\": \"capacity_block_requester\"}"
            + "    ]"
            + "  }"
            + "}"
            + "]";

    @Test
    public void shouldDeserializePolicyList() {
        final List<CapacityReservationPolicy> policies = JsonMapper.parseData(
                POLICIES_JSON, new TypeReference<List<CapacityReservationPolicy>>() {}, MAPPER);

        assertThat(policies).hasSize(2);
        assertThat(policies.get(0).getAction()).isEqualTo(CapacityReservationPolicyAction.DENY);
        assertThat(policies.get(0).getStatement()).isNotNull();
        assertThat(policies.get(0).getExclude()).isNull();
        assertThat(policies.get(1).getAction()).isEqualTo(CapacityReservationPolicyAction.AUTO_APPROVE);
        assertThat(policies.get(1).getStatement()).isNotNull();
    }

    @Test
    public void shouldDeserializeMonitorSettings() {
        final CapacityReservationMonitorSettings settings = JsonMapper.parseData(
                "{\"finalizingLeadHours\": 6}",
                new TypeReference<CapacityReservationMonitorSettings>() {}, MAPPER);

        assertThat(settings.getFinalizingLeadHours()).isEqualTo(6);
    }

    @Test
    public void shouldResolveEveryFieldNameUsedInPolicies() {
        assertThat(CapacityReservationField.findByDisplayName("cloud.provider").isPresent()).isTrue();
        assertThat(CapacityReservationField.findByDisplayName("commitment.duration").isPresent()).isTrue();
        assertThat(CapacityReservationField.findByDisplayName("instance.type").isPresent()).isTrue();
        assertThat(CapacityReservationField.findByDisplayName("instance.count").isPresent()).isTrue();
        assertThat(CapacityReservationField.findByDisplayName("reservation.type").isPresent()).isTrue();
        assertThat(CapacityReservationField.findByDisplayName("region.id").isPresent()).isTrue();
        assertThat(CapacityReservationField.findByDisplayName("owner").isPresent()).isTrue();
        assertThat(CapacityReservationField.findByDisplayName("owner.authorities").isPresent()).isTrue();
        assertThat(CapacityReservationField.findByDisplayName("no.such.field").isPresent()).isFalse();
    }

    @Test
    public void shouldRegisterFieldsForCapacityReservationSubjectType() {
        final List<SubjectEntityField<CapacityReservation>> fields =
                SubjectEntityField.forSubjectType(CapacityReservation.class);

        assertThat(fields).hasSize(CapacityReservationField.values().length);
        assertThat(SubjectEntityField.forSubjectType(PipelineRun.class)).isNotEmpty();
    }

    @Test
    public void shouldExtractFieldValuesFromReservation() {
        final CapacityReservation reservation = new CapacityReservation();
        reservation.setInstanceType("p5.48xlarge");
        reservation.setInstanceCount(INSTANCE_COUNT);
        reservation.setCommitmentDuration(COMMITMENT_DURATION);

        assertThat(extract(CapacityReservationField.INSTANCE_TYPE, reservation)).isEqualTo("p5.48xlarge");
        assertThat(extract(CapacityReservationField.INSTANCE_COUNT, reservation))
                .isEqualTo(String.valueOf(INSTANCE_COUNT));
        assertThat(extract(CapacityReservationField.COMMITMENT_DURATION, reservation))
                .isEqualTo(String.valueOf(COMMITMENT_DURATION));
    }

    @Test
    public void shouldExtractNullsFromEmptyReservation() {
        final CapacityReservation empty = new CapacityReservation();

        assertThat(extract(CapacityReservationField.COMMITMENT_DURATION, empty)).isNull();
        assertThat(extract(CapacityReservationField.CLOUD_PROVIDER, empty)).isNull();
        assertThat(extract(CapacityReservationField.RESERVATION_TYPE, empty)).isNull();
        assertThat(extract(CapacityReservationField.OWNER, empty)).isNull();
    }

    @Test
    public void shouldNotTreatAnyFieldAsDurationBased() {
        for (final CapacityReservationField field : CapacityReservationField.values()) {
            assertThat(field.isSupportsDuration()).isFalse();
        }
    }

    private static String extract(final CapacityReservationField field,
                                  final CapacityReservation reservation) {
        return Optional.ofNullable(field.extract(reservation)).orElse(null);
    }
}
