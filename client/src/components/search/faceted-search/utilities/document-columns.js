/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import {Icon} from 'antd';
import {PreviewIcons} from '../../preview/previewIcons';
import SearchItemTypes from '../../../../models/search/search-item-types';
import getDocumentName from '../document-presentation/utilities/get-document-name';
import UserName from '../../../special/UserName';
import displaySize from '../../../../utils/displaySize';
import displayDate from '../../../../utils/displayDate';
import styles from '../search-results.css';

const renderIcon = (resultItem) => {
  if (PreviewIcons[resultItem.type]) {
    return (
      <Icon
        className={styles.icon}
        type={PreviewIcons[resultItem.type]} />
    );
  }
  return null;
};

export default [
  {
    key: 'name',
    name: 'Name',
    renderFn: (value, document) => (
      <span className={styles.cellValue}>
        {renderIcon(document)}
        <b style={{marginLeft: '5px'}}>
          {getDocumentName(document)}
        </b>
      </span>
    ),
    width: '25%'
  },
  {
    key: 'owner',
    name: 'Owner',
    width: '15%',
    renderFn: (value) => (<UserName userName={value} />)
  },
  {
    key: 'description',
    name: 'Description',
    width: '15%',
    types: new Set([
      SearchItemTypes.pipeline,
      SearchItemTypes.tool,
      SearchItemTypes.toolGroup,
      SearchItemTypes.configuration,
      SearchItemTypes.issue,
      SearchItemTypes.NFSBucket,
      SearchItemTypes.azStorage,
      SearchItemTypes.gsStorage,
      SearchItemTypes.s3Bucket
    ])
  },
  {
    key: 'lastModified',
    name: 'Changed',
    width: '15%',
    renderFn: value => displayDate(value, 'MMM d, YYYY, HH:mm'),
    types: new Set([
      SearchItemTypes.s3File,
      SearchItemTypes.gsFile,
      SearchItemTypes.azFile,
      SearchItemTypes.NFSFile
    ])
  },
  {
    key: 'startDate',
    name: 'Started',
    width: '15%',
    renderFn: value => displayDate(value, 'MMM d, YYYY, HH:mm'),
    types: new Set([SearchItemTypes.run])
  },
  {
    key: 'endDate',
    name: 'Finished',
    width: '15%',
    renderFn: value => displayDate(value, 'MMM d, YYYY, HH:mm'),
    types: new Set([SearchItemTypes.run])
  },
  {
    key: 'size',
    name: 'Size',
    width: '15%',
    renderFn: value => displaySize(value, false),
    types: new Set([
      SearchItemTypes.s3File,
      SearchItemTypes.gsFile,
      SearchItemTypes.azFile,
      SearchItemTypes.NFSFile
    ])
  },
  {
    key: 'path',
    name: 'Path',
    width: '15%',
    types: new Set([
      SearchItemTypes.tool,
      SearchItemTypes.s3File,
      SearchItemTypes.gsFile,
      SearchItemTypes.azFile,
      SearchItemTypes.NFSFile,
      SearchItemTypes.NFSBucket,
      SearchItemTypes.azStorage,
      SearchItemTypes.gsStorage,
      SearchItemTypes.s3Bucket
    ])
  }
];
