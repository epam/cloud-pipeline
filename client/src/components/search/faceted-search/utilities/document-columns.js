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
import classNames from 'classnames';
import {Icon} from 'antd';
import {isObservableArray} from 'mobx';
import {PreviewIcons} from '../../preview/previewIcons';
import SearchItemTypes from '../../../../models/search/search-item-types';
import getDocumentName from '../document-presentation/utilities/get-document-name';
import UserName from '../../../special/UserName';
import displaySize from '../../../../utils/displaySize';
import displayDate from '../../../../utils/displayDate';
import styles from '../search-results.css';
import OpenInToolAction from '../../../special/file-actions/open-in-tool';

function parseExtraColumns (preferences) {
  const configuration = preferences.searchExtraFieldsConfiguration;
  if (configuration) {
    if (Array.isArray(configuration) || isObservableArray(configuration)) {
      return configuration.map(field => ({
        key: field,
        name: field
      }));
    } else {
      const result = [];
      const types = Object.keys(configuration);
      for (let t = 0; t < types.length; t++) {
        const type = types[t];
        const subConfiguration = configuration[type];
        if (Array.isArray(subConfiguration) || isObservableArray(subConfiguration)) {
          for (let f = 0; f < subConfiguration.length; f++) {
            const field = subConfiguration[f];
            let item = result.find(r => r.key === field);
            if (!item) {
              item = {
                key: field,
                name: field,
                types: new Set()
              };
              result.push(item);
            }
            item.types.add(type);
          }
        }
      }
      return result;
    }
  } else {
    return [];
  }
}

function fetchAndParseExtraColumns (preferences) {
  return new Promise((resolve) => {
    preferences
      .fetchIfNeededOrWait()
      .then(() => resolve(parseExtraColumns(preferences)))
      .catch(() => resolve([]));
  });
}

const renderIcon = (resultItem) => {
  if (PreviewIcons[resultItem.type]) {
    return (
      <Icon
        className={classNames('cp-icon-larger', styles.icon, 'cp-search-result-item-main')}
        type={PreviewIcons[resultItem.type]} />
    );
  }
  return null;
};

const DocumentColumns = [
  {
    key: 'name',
    name: 'Name',
    renderFn: (value, document, onClick) => (
      <div
        style={{
          display: 'flex',
          width: '100%',
          height: '100%',
          alignItems: 'center',
          flexWrap: 'nowrap'
        }}
      >
        <div className="cp-search-result-item-actions">
          <Icon
            type="info-circle-o"
            className={
              classNames(
                'cp-search-result-item-action',
                'cp-icon-larger',
                'cp-search-result-item-main'
              )
            }
            onClick={(e) => {
              e.stopPropagation();
              e.preventDefault();
              onClick && onClick(document);
            }}
          />
          <OpenInToolAction
            file={document.path}
            storageId={document.parentId}
            className={
              classNames(
                'cp-search-result-item-action',
                'cp-icon-larger'
              )
            }
            titleStyle={{height: '1em'}}
          />
        </div>
        {renderIcon(document)}
        <span
          className={
            classNames(
              'cp-ellipsis-text',
              'cp-search-result-item-main'
            )
          }
        >
          <b>
            {getDocumentName(document)}
          </b>
        </span>
      </div>
    ),
    width: '25%'
  },
  {
    key: 'owner',
    name: 'Owner',
    width: '15%',
    renderFn: (value) => (
      <UserName
        userName={value}
        style={{
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis'
        }}
      />
    )
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
    renderFn: value => (
      <span className="cp-ellipsis-text">
        {displayDate(value, 'MMM D, YYYY, HH:mm')}
      </span>
    ),
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
    width: '25%',
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
  },
  {
    key: 'size',
    name: 'Size',
    width: '15%',
    renderFn: value => (
      <span className="cp-ellipsis-text">
        {displaySize(value, false)}
      </span>
    ),
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
    renderFn: value => (
      <span className="cp-ellipsis-text">
        {displayDate(value, 'MMM D, YYYY, HH:mm')}
      </span>
    ),
    types: new Set([SearchItemTypes.run])
  },
  {
    key: 'endDate',
    name: 'Finished',
    width: '15%',
    renderFn: value => (
      <span className="cp-ellipsis-text">
        {displayDate(value, 'MMM D, YYYY, HH:mm')}
      </span>
    ),
    types: new Set([SearchItemTypes.run])
  }
];

export {DocumentColumns, fetchAndParseExtraColumns, parseExtraColumns};
