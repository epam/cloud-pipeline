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

package com.epam.pipeline.manager.metadata.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.io.ByteSource;
import org.apache.commons.lang3.text.StrSubstitutor;

public final class MetadataFileBuilder {

    public static final String HEADER =
            "Sample:ID${d}name${d}type${d}patient:Participant:ID${d}membership:pairs:Pair:ID\n";
    public static final String LINE1 = "s1${d}Sample1${d}DNA${d}p1${d}set1\n";
    public static final String LINE2 = "s1${d}Sample1${d}${d}p1${d}set2\n";
    public static final String LINE3 = "s2${d}Sample2${d}RNA${d}p2${d}set1\n";


    public static final String MULTIVALUE_HEADER =
            "Sample:ID${d}name${d}type${d}patient:Participant:ID${d}membership:pairs:Pair:ID${d}" +
                    "membership:sets:Set:ID\n";
    public static final String MULTIVALUE_LINE1 = "s1${d}Sample1${d}DNA${d}p1${d}set1${d}sset1\n";
    public static final String MULTIVALUE_LINE2 = "s1${d}Sample1${d}${d}p1${d}set2${d}sset2\n";
    public static final String MULTIVALUE_LINE3 = "s1${d}Sample1${d}RNA${d}p2${d}set2${d}sset3\n";


    public static final String SAMPLE_CLASS_NAME = "Sample";
    public static final String PARTICIPANT_CLASS_NAME = "Participant";
    public static final String PAIR_CLASS_NAME = "Pair";
    public static final String SET_CLASS_NAME = "Set";

    public static final String SAMPLE1_ID = "s1";
    public static final String SAMPLE2_ID = "s2";

    public static final String PARTICIPANT1_ID = "p1";
    public static final String PARTICIPANT2_ID = "p2";

    public static final String PAIR1_ID = "set1";
    public static final String PAIR2_ID = "set2";

    public static final String SET1_ID = "sset1";
    public static final String SET2_ID = "sset2";
    public static final String SET3_ID = "sset3";

    private MetadataFileBuilder() {
        // no operations
    }

    public static InputStream prepareInputData(String delimiter) throws
            IOException {
        return prepareInputData(delimiter, Arrays.asList(HEADER, LINE1, LINE2, LINE3));
    }


    public static InputStream prepareInputData(String delimiter, List<String> lines) throws
            IOException {
        Map<String, String> placeholders = Collections.singletonMap("d", delimiter);
        StringBuilder result = new StringBuilder();
        lines.forEach(line -> result.append(formatLine(line, placeholders)));
        return ByteSource.wrap(result.toString().getBytes()).openStream();
    }

    public static String formatLine(String line, Map<String, String> placeholders) {
        return StrSubstitutor.replace(line, placeholders);
    }
}
