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

package com.epam.pipeline.manager.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PythonExecutableUtilsTest {

    @Test
    public void resolvesToCpPythonPathWhenSet() {
        assertEquals("/usr/local/python3.12/bin/python3.12",
                PythonExecutableUtils.resolvePythonExecutable("/usr/local/python3.12/bin/python3.12"));
    }

    @Test
<<<<<<< ours
    public void fallsBackToPython3WhenCpPythonPathIsNull() {
        Assert.assertEquals("python3", PythonExecutableUtils.resolvePythonExecutable(null));
||||||| base
    public void fallsBackToPythonWhenCpPythonPathIsNull() {
        Assert.assertEquals("python", PythonExecutableUtils.resolvePythonExecutable(null));
=======
    public void fallsBackToPythonWhenCpPythonPathIsNull() {
        assertEquals("python", PythonExecutableUtils.resolvePythonExecutable(null));
>>>>>>> theirs
    }

    @Test
<<<<<<< ours
    public void fallsBackToPython3WhenCpPythonPathIsBlank() {
        Assert.assertEquals("python3", PythonExecutableUtils.resolvePythonExecutable("   "));
||||||| base
    public void fallsBackToPythonWhenCpPythonPathIsBlank() {
        Assert.assertEquals("python", PythonExecutableUtils.resolvePythonExecutable("   "));
=======
    public void fallsBackToPythonWhenCpPythonPathIsBlank() {
        assertEquals("python", PythonExecutableUtils.resolvePythonExecutable("   "));
>>>>>>> theirs
    }
}
