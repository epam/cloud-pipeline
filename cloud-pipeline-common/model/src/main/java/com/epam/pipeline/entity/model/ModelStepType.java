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

package com.epam.pipeline.entity.model;

/**
 * Model step type.
 *
 * It defines the way the step inputs, outputs and execution will be handled.
 */
public enum ModelStepType {

    /**
     * Original R script model step.
     */
    MODEL,

    /**
     * Rosetta protocol model step.
     */
    PROTOCOL;

    /**
     * Model type names.
     *
     * Its fields can be used as an annotation arguments.
     */
    public final class Name {
        public static final String MODEL = "MODEL";
        public static final String PROTOCOL = "PROTOCOL";

        private Name() {}
    }
}
