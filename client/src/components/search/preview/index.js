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

import React from 'react';
import {SearchItemTypes} from '../../../models/search';
import S3FilePreview from './S3FilePreview';
import S3BucketPreview from './S3BucketPreview';
import PipelineRunPreview from './PipelineRunPreview';
import DefaultPreview from './DefaultPreview';
import PipelinePreview from './PipelinePreview';
import PipelineDocumentPreview from './PipelineDocumentPreview';
import ToolPreview from './ToolPreview';
import FolderPreview from './FolderPreview';
import ConfigurationPreview from './ConfigurationPreview';
import DockerRegistryPreview from './DockerRegistryPreview';
import ToolGroupPreview from './ToolGroupPreview';
import MetadataEntityPreview from './MetadataEntityPreview';
import IssuePreview from './IssuePreview';

export default function preview (props) {
  let Content = DefaultPreview;
  switch (props.item.type) {
    case SearchItemTypes.azFile:
    case SearchItemTypes.s3File:
    case SearchItemTypes.NFSFile:
    case SearchItemTypes.gsFile: Content = S3FilePreview; break;
    case SearchItemTypes.azStorage:
    case SearchItemTypes.NFSBucket:
    case SearchItemTypes.s3Bucket:
    case SearchItemTypes.gsStorage: Content = S3BucketPreview; break;
    case SearchItemTypes.run: Content = PipelineRunPreview; break;
    case SearchItemTypes.pipeline: Content = PipelinePreview; break;
    case SearchItemTypes.pipelineCode: Content = PipelineDocumentPreview; break;
    case SearchItemTypes.tool: Content = ToolPreview; break;
    case SearchItemTypes.folder: Content = FolderPreview; break;
    case SearchItemTypes.configuration: Content = ConfigurationPreview; break;
    case SearchItemTypes.dockerRegistry: Content = DockerRegistryPreview; break;
    case SearchItemTypes.toolGroup: Content = ToolGroupPreview; break;
    case SearchItemTypes.metadataEntity: Content = MetadataEntityPreview; break;
    case SearchItemTypes.issue: Content = IssuePreview; break;
  }
  if (!Content) {
    return null;
  }
  return (
    <Content item={props.item} lightMode={props.lightMode} />
  );
}
