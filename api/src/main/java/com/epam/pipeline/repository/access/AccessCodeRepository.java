/*
 * Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.repository.access;

import com.epam.pipeline.entity.access.AccessCodeEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;


import java.util.Optional;

public interface AccessCodeRepository extends CrudRepository<AccessCodeEntity, Long> {

    Optional<AccessCodeEntity> findByCodeChallenge(String codeChallenge);
    Optional<AccessCodeEntity> findByCode(String code);

    /**
     * Deletes all rows older than a specified interval (in minutes)
     * @param minutes threshold in minutes
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM pipeline.access_code WHERE created < NOW() - (?1 * interval '1 minute')",
            nativeQuery = true)
    void deleteExpired(int minutes);
}
