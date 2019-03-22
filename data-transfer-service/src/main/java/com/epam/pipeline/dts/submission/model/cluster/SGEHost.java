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

package com.epam.pipeline.dts.submission.model.cluster;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SGEHost {

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "hostvalue")
    private List<HostValue> hostAttributes;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "queue")
    private List<Queue> hostQueues;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HostValue {
        @JacksonXmlProperty(isAttribute = true)
        private String name;
        @JacksonXmlText
        private String value;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Queue {
        @JacksonXmlProperty(isAttribute = true)
        private String name;
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "queuevalue")
        private List<QueueValue> values;

        @JsonIgnore
        public int getSlotsNumber() {
            return getIntegerAttribute("slots");
        }

        @JsonIgnore
        public int getSlotsInUseNumber() {
            return getIntegerAttribute("slots_resv") + getIntegerAttribute("slots_used");
        }

        private int getIntegerAttribute(String slots) {
            return ListUtils.emptyIfNull(values)
                    .stream()
                    .filter(value -> slots.equals(value.getAttribute()))
                    .filter(value -> NumberUtils.isDigits(value.getValue()))
                    .findFirst()
                    .map(value -> Integer.parseInt(value.getValue()))
                    .orElse(0);
        }
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueueValue {
        @JacksonXmlProperty(isAttribute = true, localName = "qname")
        private String queueName;
        @JacksonXmlProperty(isAttribute = true, localName = "name")
        private String attribute;
        @JacksonXmlText
        private String value;
    }
}
