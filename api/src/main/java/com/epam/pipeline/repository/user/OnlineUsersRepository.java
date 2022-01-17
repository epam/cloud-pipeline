/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.repository.user;

import com.epam.pipeline.entity.user.OnlineUsersEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface OnlineUsersRepository extends CrudRepository<OnlineUsersEntity, Long> {

    void deleteByLogDateLessThan(LocalDateTime date);

    List<OnlineUsersEntity> findByLogDateGreaterThanAndLogDateLessThan(LocalDateTime start, LocalDateTime end);

    List<OnlineUsersEntity> findByLogDateGreaterThanAndLogDateLessThanAndUserIdsIn(LocalDateTime start,
                                                                                   LocalDateTime end,
                                                                                   Set<Long> userIds);
}
