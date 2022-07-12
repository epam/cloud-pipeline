/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.dts;

import com.epam.pipeline.controller.vo.dts.StorageItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "dts_transfer_task", schema = "pipeline")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    private Long registryId;

    @ElementCollection
    @CollectionTable(name = "dts_included", joinColumns = @JoinColumn(name = "task_id"))
    private List<String> included;

    @Column(name = "user_name")
    private String user;

    private boolean deleteSource;
}
