/*
 * Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.elasticsearchagent.rest.index;

import com.epam.pipeline.elasticsearchagent.rest.AbstractRestController;
import com.epam.pipeline.elasticsearchagent.service.index.StorageDocumentService;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@ConditionalOnProperty(value = "git.hook.endpoint.disable", matchIfMissing = true, havingValue = "false")
@Slf4j
@RestController
public class StorageDocumentController extends AbstractRestController {

    private final StorageDocumentService storageDocumentService;

    @PostMapping(value = "/index/document")
    public ResponseEntity processDocs(@RequestBody final List<DataStorageFile> files,
                                      @RequestParam final Long storageId) {
        storageDocumentService.indexFile(storageId, files);
        return new ResponseEntity(HttpStatus.OK);
    }
}
