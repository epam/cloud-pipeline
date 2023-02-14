/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize(using = SupportButtonDeserialization.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupportButton {

    private List<Icon> icons;

    public List<Icon> getIcons() {
        return icons;
    }

    public void setIcons(final List<Icon> icons) {
        this.icons = icons;
    }

    public static class Icon {
        private String name;
        private String icon;
        private String content;

        public String getName() {
            return name;
        }

        public String getIcon() {
            return icon;
        }

        public String getContent() {
            return content;
        }

        public void setName(final String name) {
            this.name = name;
        }

        public void setIcon(final String icon) {
            this.icon = icon;
        }

        public void setContent(final String content) {
            this.content = content;
        }
    }
}
