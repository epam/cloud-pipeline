/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests.utils.template;

import com.epam.pipeline.autotests.utils.C;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.*;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class FreeMarkerTemplate {

    public static File processTemplate(final String templateName,
                                       final Map<String, Object> templateData,
                                       final String outputFileName) {
        try {
            final Configuration cfg = new Configuration();
            cfg.setClassForTemplateLoading(FreeMarkerTemplate.class, "/templates");
            cfg.setDefaultEncoding("UTF-8");
            final Template template = cfg.getTemplate(templateName);

            final File outputFile = Paths.get(C.DOWNLOAD_FOLDER).resolve(outputFileName).toFile();
            final Writer fileWriter = new FileWriter(outputFile);
            try {
                template.process(templateData, fileWriter);
            } finally {
                fileWriter.close();
            }
            return outputFile;
        } catch (IOException | TemplateException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("user_name1", "admin.login");
        templateData.put("billing_center1", "eeeve");
        templateData.put("group1", "eveev");
        templateData.put("billing1", "eveverr");
        templateData.put("start_data1", "jmyt");
        templateData.put("end_data", "evv,;;;");
        templateData.put("storage1", "ejgigi444;mlfmvkf_");
        processTemplate("billing-test.ftl", templateData, "output.txt");
    }
}
