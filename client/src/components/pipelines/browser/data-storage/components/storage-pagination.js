/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {observer} from 'mobx-react';
import {Button, Icon, Row} from 'antd';
import classNames from 'classnames';
import styles from './storage-pagination.css';

function StoragePagination ({className, style, storage}) {
  if (!storage || !storage.infoLoaded) {
    return null;
  }
  return (
    <Row
      className={
        classNames(
          className,
          styles.storagePagination
        )
      }
      type="flex"
      justify="end"
      align="middle"
      style={style}
    >
      <Button
        id="first-page-button"
        onClick={storage.navigateToFirstPage}
        disabled={!storage.currentPagination.first}
        size="small"
      >
        <Icon style={{marginRight: '-3px'}} type="caret-left" />
        <Icon type="caret-left" />
      </Button>
      <Button
        id="prev-page-button"
        onClick={storage.navigateToPreviousPage}
        disabled={!storage.currentPagination.previous}
        style={{margin: 3}}
        size="small"
      >
        <Icon type="caret-left" />
      </Button>
      <span
        className={classNames(
          styles.currentPageCounter,
          'ant-pagination-item',
          'ant-pagination-item-active'
        )}
      >
        {storage.currentPagination.page + 1}
      </span>
      <Button
        id="next-page-button"
        onClick={storage.navigateToNextPage}
        disabled={!storage.currentPagination.next}
        style={{margin: 3}}
        size="small"
      >
        <Icon type="caret-right" />
      </Button>
    </Row>
  );
}

export default observer(StoragePagination);
