/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.entity.user;

import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentialsEntity;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@EqualsAndHashCode
@AllArgsConstructor
@Entity
@Table(name = "role", schema = "pipeline")
public class Role implements StorageContainer, Serializable {

    public static final String ROLE_PREFIX = "ROLE_";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "role_generator")
    @SequenceGenerator(name="role_generator", sequenceName = "s_role", schema = "pipeline")
    private Long id;

    private String name;

    private boolean predefined;

    private boolean userDefault;

    private Long defaultStorageId;

    private Long defaultProfileId;

    @Transient
    private Boolean blocked;

    @ManyToMany
    @JoinTable(
            name = "cloud_profile_credentials_role",
            schema = "pipeline",
            inverseJoinColumns = { @JoinColumn(name = "cloud_profile_credentials_id") },
            joinColumns = { @JoinColumn(name = "role_id") }
    )
    private List<CloudProfileCredentialsEntity> cloudProfiles;

    public Role() {
        this.predefined = false;
        this.userDefault = false;
    }

    public Role(String name) {
        this();
        this.name = name;
    }

    public Role(Long id, String name) {
        this(name);
        this.id = id;
    }

    @Override
    public Long getDefaultStorageId() {
        return defaultStorageId;
    }

    @Override
    public String toString() {
        return name;
    }
}
