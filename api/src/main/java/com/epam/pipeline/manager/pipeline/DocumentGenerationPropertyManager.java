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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.dao.pipeline.DocumentGenerationPropertyDao;
import com.epam.pipeline.entity.pipeline.DocumentGenerationProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

@Service
public class DocumentGenerationPropertyManager {

    public static final String INTRODUCTION_PROPERTY = "INTRODUCTION";
    public static final String DATA_HIERARCHY_PHOTO_PROPERTY = "DATA HIERARCHY PHOTO";

    @Autowired
    private DocumentGenerationPropertyDao documentGenerationPropertyDao;

    @Autowired
    private MessageHelper messageHelper;

    public DocumentGenerationProperty loadProperty(String name, Long id) {
        DocumentGenerationProperty property = documentGenerationPropertyDao.loadProperty(name, id);
        Assert.notNull(property, messageHelper.getMessage(MessageConstants.ERROR_PIPELINE_DOCUMENT_PROPERTY_NOT_FOUND,
                name, id));
        return property;
    }

    public List<DocumentGenerationProperty> loadAllProperties() {
        return documentGenerationPropertyDao.loadAllProperties();
    }

    public List<DocumentGenerationProperty> loadAllPropertiesByPipelineId(Long id) {
        return documentGenerationPropertyDao.loadAllPipelineProperties(id);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DocumentGenerationProperty saveProperty(DocumentGenerationProperty property) {
        if (documentGenerationPropertyDao.loadProperty(property.getPropertyName(), property.getPipelineId()) == null) {
            documentGenerationPropertyDao.createProperty(property);
        } else {
            documentGenerationPropertyDao.updateProperty(property);
        }
        return property;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public DocumentGenerationProperty deleteProperty(String name, Long id) {
        DocumentGenerationProperty property = this.loadProperty(name, id);
        documentGenerationPropertyDao.deleteProperty(property);
        return property;
    }

}
