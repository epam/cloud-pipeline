/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
 *  limitations under the License.
 */

package com.epam.pipeline.manager.keypair;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SshKeyPairManagerTest {

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new JSchSshKeyPairManager()},
        });
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testKeysAreNotBlank(final SshKeyPairManager manager) {
        final SshKeyPair pair = manager.generate();

        assertTrue(StringUtils.isNotBlank(pair.privateKey()));
        assertTrue(StringUtils.isNotBlank(pair.publicKey()));
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testKeysAreUnique(final SshKeyPairManager manager) {
        assertThat(manager.generate(), is(not(manager.generate())));
    }
}
