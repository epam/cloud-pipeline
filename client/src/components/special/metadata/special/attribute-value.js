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
import {Link} from 'react-router';
import {getStorageFileAccessInfo} from '../../../../utils/object-storage';

class AttributeValue extends React.PureComponent {
  state = {
    dataStorageInfo: undefined
  };

  get displayValue () {
    const {value, showFileNameOnly} = this.props;
    const {dataStorageInfo} = this.state;
    if (dataStorageInfo && showFileNameOnly && typeof value === 'string') {
      return value.split(/[\\/]/).filter(o => o.length).pop();
    }
    return value;
  }

  componentDidMount () {
    this.updateLinkInfo();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.value !== this.props.value) {
      this.updateLinkInfo();
    }
  }

  updateLinkInfo = () => {
    const {value} = this.props;
    if (typeof value === 'string') {
      getStorageFileAccessInfo(value)
        .then(info => {
          if (!info) {
            return Promise.resolve(undefined);
          }
          const {objectStorage, path} = info;
          const delimiter = objectStorage.delimiter || '/';
          const isFile = /\.[^.\\/]+/.test(path);
          const relativePath = isFile
            ? (path || '').split(delimiter).slice(0, -1).join(delimiter)
            : (path || '');
          return Promise.resolve({
            storageId: objectStorage.id,
            path: relativePath
          });
        })
        .then(info => this.setState({dataStorageInfo: info}));
    } else {
      this.setState({
        dataStorageInfo: undefined
      });
    }
  }

  render () {
    const {
      className,
      style,
      children
    } = this.props;
    const {dataStorageInfo} = this.state;
    const componentStyle = {
      wordBreak: 'break-word',
      ...(style || {})
    };
    if (dataStorageInfo) {
      const {
        storageId,
        path
      } = dataStorageInfo;
      const url = path
        ? `/storage/${storageId}?path=${path}`
        : `/storage/${storageId}`;
      return (
        <Link
          to={url}
          onClick={e => e.stopPropagation()}
          className={className}
          style={componentStyle}
        >
          {children}
          {this.displayValue}
        </Link>
      );
    }
    return (
      <span
        className={className}
        style={componentStyle}
      >
        {children}
        {this.displayValue}
      </span>
    );
  }
}

AttributeValue.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.any,
  showFileNameOnly: PropTypes.bool,
  children: PropTypes.node
};

export default AttributeValue;
