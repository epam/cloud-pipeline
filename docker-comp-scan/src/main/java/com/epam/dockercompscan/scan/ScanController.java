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

package com.epam.dockercompscan.scan;

import com.epam.dockercompscan.scan.domain.ImageScanResult;
import com.epam.dockercompscan.scan.domain.LayerScanResult;
import com.epam.dockercompscan.scan.domain.ScanRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScanController {

    @Autowired
    private ScanService scanService;

    @GetMapping("/scan/{id:.+}")
    public ImageScanResult getImageScanResult(@PathVariable String id) {
        return scanService.loadImageScan(id);
    }

    @PostMapping("/scan")
    public LayerScanResult scan(@RequestBody ScanRequest request) {
        return scanService.scan(request);
    }

}
