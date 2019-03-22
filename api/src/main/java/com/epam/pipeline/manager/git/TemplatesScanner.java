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

package com.epam.pipeline.manager.git;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.exception.git.TemplateManagerException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * This class responsible for reading pipeline templates folders
 */
public class TemplatesScanner {

    private static final String DESCRIPTION_FILE = "description.txt";
    /**
     * Represents a folder with templates without restricted access (available to all users)
     */
    private static final String COMMON_TEMPLATES_FOLDER = "__COMMON";

    private static final Logger LOGGER = LoggerFactory.getLogger(TemplatesScanner.class);

    private String templatesDirectoryPath;

    public TemplatesScanner(String templatesDirectoryPath) {
        this.templatesDirectoryPath = templatesDirectoryPath;
    }

    /**
     * Lists templates directory (templates.directory property)
     * Expected templates folder structure is:
     *  __COMMON/
     *      template1/
     *      template2/
     *  ROLE1
     *      template3/
     *  GROUP1
     *      template4
     * Where first level folders specify permissions for stored templates
     * In case of duplicate templates only one random value will be returned.
     * @return Map [templateId -> absolutePathToTemplateDirectory]
     */
    public Map<String, Template> listTemplates() {
        File templatesDir = new File(templatesDirectoryPath);
        if (!templatesDir.exists() || !templatesDir.isDirectory() || templatesDir.listFiles() == null) {
            LOGGER.debug("Templates directory '{}' is empty.", templatesDirectoryPath);
            return Collections.emptyMap();
        }

        return getAllowedFolders(templatesDir)
                .stream()
                .map(this::getFolders)
                .flatMap(Collection::stream)
                .map(templateDir -> {
                    String description = readDescriptionFile(templateDir);
                    return new Template(templateDir.getName(), description, templateDir.getAbsolutePath());
                })
                .collect(Collectors.toMap(Template::getId, Function.identity(),
                    (template1, template2) -> template1));
    }

    private List<File> getFolders(File parent) {
        File[] files = parent.listFiles(File::isDirectory);
        return convertArrayToList(files);
    }

    private List<File> getAllowedFolders(File templatesDir) {
        Predicate<String> folderFiler = getTemplatesFilter();
        File[] filteredFolders = templatesDir
                .listFiles(file -> file.isDirectory() && folderFiler.evaluate(file.getName()));
        return convertArrayToList(filteredFolders);
    }

    private List<File> convertArrayToList(File[] files) {
        return files == null ? Collections.emptyList() : Arrays.stream(files).collect(Collectors.toList());
    }

    private String readDescriptionFile(File templateDir) {
        File descriptionFile = Paths.get(templateDir.getAbsolutePath(), DESCRIPTION_FILE).toFile();
        if (!descriptionFile.exists() || !descriptionFile.isFile()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(descriptionFile))) {
            return reader.readLine();
        } catch (IOException e) {
            throw new TemplateManagerException(e);
        }
    }

    private Predicate<String> getTemplatesFilter() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || CollectionUtils.isEmpty(authentication.getAuthorities())) {
            return folderName -> folderName.equalsIgnoreCase(COMMON_TEMPLATES_FOLDER);
        }
        if (authentication.getAuthorities().stream().anyMatch(auth ->
                auth.getAuthority().equalsIgnoreCase(DefaultRoles.ROLE_ADMIN.getName()))) {
            return folderName -> true;
        }
        Set<String> allowedFolders =
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
        allowedFolders.add(COMMON_TEMPLATES_FOLDER);
        return allowedFolders::contains;
    }
}
