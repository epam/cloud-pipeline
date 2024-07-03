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
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {Link} from 'react-router';
import dataStorages from '../../../models/dataStorage/DataStorages';
import {getStorageLinkInfo} from './utilities';

function DataStorageLink (
  {
    children,
    className,
    isFolder,
    dataStorages,
    path,
    storageId,
    style
  }
) {
  const {
    storageId: parsedStorageId,
    path: parsedPath
  } = getStorageLinkInfo({
    storages: dataStorages.loaded ? (dataStorages.value || []) : [],
    storageId,
    path,
    isFolder
  });
  if (parsedStorageId) {
    const url = parsedPath && parsedPath.length
      ? `/storage/${parsedStorageId}?path=${parsedPath}`
      : `/storage/${parsedStorageId}`;
    return (
      <Link
        to={url}
        className={className}
        style={style}
      >
        {children || path || null}
      </Link>
    );
  }
  return (
    <span
      className={className}
      style={style}
    >
      {children || path || null}
    </span>
  );
}

DataStorageLink.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  path: PropTypes.string,
  isFolder: PropTypes.bool,
  children: PropTypes.node
};

export default inject(() => ({dataStorages}))(observer(DataStorageLink));
