/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.dts.transfer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;
import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Builder
@Wither
@AllArgsConstructor
@NoArgsConstructor
public class TransferTask {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
    @Enumerated(EnumType.ORDINAL)
    private TaskStatus status;
    private LocalDateTime created;
    private LocalDateTime started;
    private LocalDateTime finished;
    @Column(length = Integer.MAX_VALUE)
    private String reason;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="type", column=@Column(name = "source_type")),
        @AttributeOverride(name="path", column=@Column(name = "source_path", length = Integer.MAX_VALUE))
        })
    @NotNull
    private StorageItem source;
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="type", column=@Column(name = "destination_type")),
            @AttributeOverride(name="path", column=@Column(name = "destination_path", length = Integer.MAX_VALUE))
        })
    @NotNull
    private StorageItem destination;

    @ElementCollection
    private List<String> included;
}
