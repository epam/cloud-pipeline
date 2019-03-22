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
package com.epam.pipeline.autotests.utils;

public enum PrivilegeValue {
    ALLOW {
        @Override
        public void shouldBeCorrect(Checkbox allowCheckbox, Checkbox denyCheckbox) {
            allowCheckbox.shouldBeChecked();
            denyCheckbox.shouldNotBeChecked();
        }

        @Override
        public void setTo(Checkbox allowCheckbox, Checkbox denyCheckbox) {
            allowCheckbox.switchOn();
        }
    },
    DENY {
        @Override
        public void shouldBeCorrect(Checkbox allowCheckbox, Checkbox denyCheckbox) {
            allowCheckbox.shouldNotBeChecked();
            denyCheckbox.shouldBeChecked();
        }

        @Override
        public void setTo(Checkbox allowCheckbox, Checkbox denyCheckbox) {
            denyCheckbox.switchOn();
        }
    },
    INHERIT {
        @Override
        public void shouldBeCorrect(Checkbox allowCheckbox, Checkbox denyCheckbox) {
            allowCheckbox.shouldNotBeChecked();
            denyCheckbox.shouldNotBeChecked();
        }

        @Override
        public void setTo(Checkbox allowCheckbox, Checkbox denyCheckbox) {
            allowCheckbox.switchOff();
            denyCheckbox.switchOff();
        }
    };

    public abstract void shouldBeCorrect(Checkbox allowCheckbox, Checkbox denyCheckbox);
    public abstract void setTo(Checkbox allowCheckbox, Checkbox denyCheckbox);
}
