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

import {SearchItemTypes} from '../../../models/search';
import {
  AppstoreOutlined,
  FileOutlined,
  FileTextOutlined,
  FolderOutlined,
  ForkOutlined,
  HddOutlined,
  InboxOutlined,
  MessageOutlined,
  PlayCircleOutlined,
  SettingOutlined,
  ToolOutlined
} from '@ant-design/icons';

export const PreviewIcons = {
  [SearchItemTypes.pipeline]: ForkOutlined,
  [SearchItemTypes.pipelineCode]: FileTextOutlined,
  [SearchItemTypes.run]: PlayCircleOutlined,
  [SearchItemTypes.azStorage]: InboxOutlined,
  [SearchItemTypes.azFile]: FileOutlined,
  [SearchItemTypes.s3Bucket]: InboxOutlined,
  [SearchItemTypes.s3File]: FileOutlined,
  [SearchItemTypes.NFSBucket]: HddOutlined,
  [SearchItemTypes.NFSFile]: FileOutlined,
  [SearchItemTypes.gsStorage]: HddOutlined,
  [SearchItemTypes.gsFile]: FileOutlined,
  [SearchItemTypes.tool]: ToolOutlined,
  [SearchItemTypes.toolGroup]: ToolOutlined,
  [SearchItemTypes.dockerRegistry]: ToolOutlined,
  [SearchItemTypes.folder]: FolderOutlined,
  [SearchItemTypes.configuration]: SettingOutlined,
  [SearchItemTypes.metadataEntity]: AppstoreOutlined,
  [SearchItemTypes.issue]: MessageOutlined
};
