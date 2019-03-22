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
import {Icon, Row} from 'antd';
import styles from './preview.css';

export function metadataLoad (params, entityClass, {metadataCache, dataStorageCache}) {
  if (!params.item) {
    return null;
  }
  if (entityClass === 'DATA_STORAGE_ITEM' && dataStorageCache) {
    return dataStorageCache.getTags(params.item.parentId, params.item.id);
  } else if (entityClass !== 'DATA_STORAGE_ITEM' && metadataCache) {
    return metadataCache.getMetadata(params.item.id, entityClass);
  } else {
    return null;
  }
}

export function renderAttributes (metadataRequest, isTags = false) {
  if (metadataRequest) {
    if (metadataRequest.pending) {
      return <Row className={styles.attributes} type="flex" justify="center"><Icon type="loading" /></Row>;
    }
    if (metadataRequest.error) {
      return (
        <div className={styles.attributes}>
          <span style={{color: '#ff556b'}}>{metadataRequest.error}</span>
        </div>
      );
    }
    const attributes = [];
    if (isTags) {
      if (metadataRequest.value) {
        for (let key in metadataRequest.value) {
          if (metadataRequest.value.hasOwnProperty(key)) {
            attributes.push({
              key,
              value: metadataRequest.value[key]
            });
          }
        }
      }
    } else {
      if (metadataRequest.value && metadataRequest.value.length && metadataRequest.value[0].data) {
        for (let key in metadataRequest.value[0].data) {
          if (metadataRequest.value[0].data.hasOwnProperty(key)) {
            attributes.push({
              key,
              value: metadataRequest.value[0].data[key].value
            });
          }
        }
      }
    }
    if (attributes.length > 0) {
      return (
        <Row type="flex" className={styles.attributes}>
          {
            attributes.map((attr, index) => {
              return (
                <div key={index} className={styles.attribute}>
                  <div className={styles.attributeName}>{attr.key}</div>
                  <div className={styles.attributeValue}>{attr.value}</div>
                </div>
              );
            })
          }
        </Row>
      );
    }
  }
  return null;
}
