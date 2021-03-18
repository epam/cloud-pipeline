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

import {SearchItemTypes} from '../../models/search';
import displayCount from '../../utils/displayCount';

const pluralString = (plural) => plural === undefined ? 's' : plural;

const countString = (key, localizationFn, count = 0, plural) =>
  !count
    ? `${localizationFn(key)}${pluralString(plural)}`
    : (
      count > 1
        ? `${displayCount(count, true)} ${localizationFn(key.toLowerCase())}${pluralString(plural)}`
        : `${count} ${localizationFn(key.toLowerCase())}`
    );

const titleFn = (key, pluralStr) =>
  (localizationFn) =>
    (count = 0) =>
      countString(key, localizationFn, count, pluralStr);

export const SearchGroupTypes = {
  folder: {
    types: [SearchItemTypes.folder, SearchItemTypes.metadataEntity],
    icon: 'folder',
    title: titleFn('Folder'),
    test (o) {
      return this.types.filter(t => ((o || []).indexOf(t) >= 0)).length > 0;
    }
  },
  pipeline: {
    types: [SearchItemTypes.pipeline, SearchItemTypes.configuration, SearchItemTypes.pipelineCode],
    icon: 'fork',
    title: titleFn('Pipeline'),
    test (o) {
      return this.types.filter(t => ((o || []).indexOf(t) >= 0)).length > 0;
    }
  },
  run: {
    types: [SearchItemTypes.run],
    icon: 'play-circle',
    title: titleFn('Run'),
    test (o) {
      return this.types.filter(t => ((o || []).indexOf(t) >= 0)).length > 0;
    }
  },
  tool: {
    types: [SearchItemTypes.tool, SearchItemTypes.dockerRegistry, SearchItemTypes.toolGroup],
    icon: 'tool',
    title: titleFn('Tool'),
    test (o) {
      return this.types.filter(t => ((o || []).indexOf(t) >= 0)).length > 0;
    }
  },
  storage: {
    types: [
      SearchItemTypes.azFile,
      SearchItemTypes.azStorage,
      SearchItemTypes.s3File,
      SearchItemTypes.s3Bucket,
      SearchItemTypes.NFSFile,
      SearchItemTypes.NFSBucket,
      SearchItemTypes.gsFile,
      SearchItemTypes.gsStorage
    ],
    icon: 'file',
    title: titleFn('Data', ''),
    test (o) {
      return this.types.filter(t => ((o || []).indexOf(t) >= 0)).length > 0;
    }
  },
  issue: {
    types: [SearchItemTypes.issue],
    icon: 'message',
    title: titleFn('Issue'),
    test (o) {
      return this.types.filter(t => ((o || []).indexOf(t) >= 0)).length > 0;
    }
  }
};
