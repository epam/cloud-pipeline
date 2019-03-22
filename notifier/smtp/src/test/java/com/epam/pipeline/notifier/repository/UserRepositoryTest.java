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

package com.epam.pipeline.notifier.repository;

import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.notifier.AbstractSpringTest;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

public class UserRepositoryTest extends AbstractSpringTest {

    public static final String USER_NAME = "James Alan Hetfield";
    @Autowired
    private UserRepository userRepository;

    @Test
    public void loadUserTest() {
        PipelineUser user = new PipelineUser();
        user.setUserName(USER_NAME);
        user.setAdmin(true);
        userRepository.save(user);


        List<PipelineUser> users = userRepository.findByIdIn(Collections.singletonList(user.getId()));
        Assert.assertTrue(users.size() == 1);
        Assert.assertEquals(USER_NAME, users.get(0).getUserName());
    }

}