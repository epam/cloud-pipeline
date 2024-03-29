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
import {Icon} from 'antd';
import classNames from 'classnames';

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

/**
 * @typedef {Object} AttributesOptions
 * @property {boolean} [tags=false]
 * @property {boolean} [column=false]
 * @property {boolean} [showLoadingIndicator=true]
 * @property {boolean} [showError=true]
 */

/**
 * Renders attributes
 * @param {Object} metadataRequest
 * @param {AttributesOptions} options
 * @return {JSX.Element|null}
 */
export function renderAttributes (metadataRequest, options = {}) {
  const {
    tags = false,
    column = false,
    showLoadingIndicator = true,
    showError = true
  } = options;
  if (metadataRequest) {
    if (metadataRequest.pending) {
      if (!showLoadingIndicator) {
        return null;
      }
      return (
        <div
          className={styles.attributes}
          style={{
            justifyContent: 'center'
          }}
        >
          <Icon type="loading" />
        </div>
      );
    }
    if (metadataRequest.error) {
      if (!showError) {
        return null;
      }
      return (
        <div className={styles.attributes}>
          <span className={'cp-search-preview-error'}>{metadataRequest.error}</span>
        </div>
      );
    }
    const attributes = [];
    if (tags) {
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
      const attrs = attributes.map((attr, index) => {
        return (
          <div key={index} className={styles.attribute}>
            <div
              className={
                classNames(
                  styles.attributeName,
                  'cp-search-attribute-name'
                )
              }
            >
              {attr.key}
            </div>
            <div
              className={
                classNames(
                  styles.attributeValue,
                  'cp-search-attribute-value'
                )
              }
            >
              {attr.value}
            </div>
          </div>
        );
      });
      return (
        <div
          className={
            classNames(
              styles.attributes,
              {
                [styles.column]: column
              }
            )
          }
        >
          {attrs}
        </div>
      );
    }
  }
  return null;
}
