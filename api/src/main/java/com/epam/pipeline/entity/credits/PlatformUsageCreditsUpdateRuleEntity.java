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

package com.epam.pipeline.entity.credits;

import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateAction;
import com.epam.pipeline.utils.condition.ConditionExpression;
import com.epam.pipeline.dto.credits.PlatformUsageCreditsUpdateRuleType;
import com.epam.pipeline.entity.utils.TimestampConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@Table(name = "usage_credits_update_rule", schema = "pipeline")
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUsageCreditsUpdateRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private PlatformUsageCreditsUpdateRuleType ruleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "statement", nullable = false)
    private ConditionExpression statement;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exclude")
    private ConditionExpression exclude;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private PlatformUsageCreditsUpdateAction.ActionType actionType;

    @Column(name = "action_value", nullable = false)
    private int actionValue;

    @Column(name = "action_message")
    private String actionMessage;

    @Column(name = "time_window")
    private Integer timeWindow;

    @Convert(converter = TimestampConverter.class)
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Convert(converter = TimestampConverter.class)
    @Column(name = "modified_date", nullable = false)
    private LocalDateTime modifiedDate;
}
