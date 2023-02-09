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
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {message, Tooltip, Icon} from 'antd';
import {computed, observable} from 'mobx';
import {inject, observer} from 'mobx-react';
import {STORAGE_CLASSES} from '../../pipelines/browser/data-storage';
import DataStoragePathUsage from '../../../models/dataStorage/DataStoragePathUsage';
import DataStoragePathUsageUpdate from '../../../models/dataStorage/DataStoragePathUsageUpdate';
import displaySize from '../../../utils/displaySize';
import styles from './storage-size.css';

const REFRESH_REQUESTED_MESSAGE =
  'Storage size refresh has been requested. Please wait a couple of minutes.';

function InfoTooltip ({size}) {
  const {realSize, effectiveSize} = size;

  if (!effectiveSize || effectiveSize === realSize) {
    return null;
  }

  const tooltip = (
    <div>
      <div>Effective size: {displaySize(effectiveSize, effectiveSize > 1024)}</div>
      <div>Real size: {displaySize(realSize, realSize > 1024)}</div>
    </div>
  );

  return (
    <Tooltip
      title={tooltip}
      placement="top"
    >
      <Icon
        type="info-circle"
        className="cp-text"
        style={{marginRight: 5, marginLeft: 5}}
      />
    </Tooltip>
  );
}
@inject('preferences')
@observer
class StorageSize extends React.PureComponent {
  state = {
    showDetailedInfo: false
  };

  @observable info;

  componentDidMount () {
    this.updateStorageSize();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.storage !== this.props.storage ||
      prevProps.storageId !== this.props.storageId
    ) {
      this.updateStorageSize();
    }
  }

  @computed
  get usageInfo () {
    if (!this.info) {
      return null;
    }
    const sizes = this.info.usage && this.info.usage[STORAGE_CLASSES.standard]
      ? this.info.usage[STORAGE_CLASSES.standard]
      : this.info;
    const archivedClasses = Object.values(this.info.usage || {})
      .filter(({storageClass}) => storageClass !== STORAGE_CLASSES.standard);
    const archivedSizes = archivedClasses && archivedClasses.length
      ? archivedClasses.reduce((acc, {
        size = 0,
        effectiveSize = 0,
        oldVersionsSize = 0,
        oldVersionsEffectiveSize = 0
      }) => {
        acc.total += effectiveSize || size;
        acc.previous += oldVersionsEffectiveSize || oldVersionsSize;
        return acc;
      }, {total: 0, previous: 0})
      : null;
    return {
      size: sizes.size || 0,
      effective: sizes.effectiveSize || 0,
      previous: sizes.oldVersionsEffectiveSize || sizes.oldVersionsSize || 0,
      archiveSizeTotal: archivedSizes
        ? archivedSizes.total
        : 0,
      archivePreviousTotal: archivedSizes
        ? archivedSizes.previous
        : 0,
      details: [
        Object.values(this.info.usage || {})
          .find(({storageClass}) => storageClass === STORAGE_CLASSES.standard),
        ...Object.values(this.info.usage || {})
          .filter(({storageClass}) => storageClass !== STORAGE_CLASSES.standard)
      ].filter(Boolean)
        .map(archived => ({
          size: archived.effectiveSize || archived.size || 0,
          previous: archived.oldVersionsEffectiveSize || archived.oldVersionsSize || 0,
          storageClass: archived.storageClass
        }))
    };
  }

  updateStorageSize = async () => {
    const {
      storage,
      storageId
    } = this.props;
    let id = storageId;
    if (id === undefined && typeof storage === 'object') {
      id = storage.id;
    }
    if (id !== undefined) {
      const request = new DataStoragePathUsage(id);
      await request.fetch();
      if (request.error) {
        message.error(request.error, 5);
      }
      if (request.value && request.value.size) {
        this.info = request.value;
      }
    }
  };

  refreshSize = async () => {
    const {
      storage,
      storageId,
      preferences
    } = this.props;
    let id = storageId;
    if (id === undefined && typeof storage === 'object') {
      id = storage.id;
    }
    if (id !== undefined) {
      try {
        const request = new DataStoragePathUsageUpdate(id);
        await request.fetch();
        await preferences.fetchIfNeededOrWait();
        const disclaimer = preferences.storageSizeRequestDisclaimer || REFRESH_REQUESTED_MESSAGE;
        message.info(disclaimer, 7);
      } catch (e) {
        message.error(e.message, 5);
      }
    }
  };

  toggleDetails = (event, showDetailedInfo) => {
    event && event.preventDefault();
    this.setState({showDetailedInfo});
  };

  renderInfo = () => {
    const {
      size,
      effective,
      previous,
      archiveSizeTotal,
      archivePreviousTotal
    } = this.usageInfo;
    const totalSize = displaySize(
      (effective || size) + previous,
      (effective || size) + previous > 1024
    );
    const previousVersionsSize = displaySize(previous, previous > 1024);
    const totalArchiveSize = displaySize(
      archiveSizeTotal + archivePreviousTotal,
      archiveSizeTotal + archivePreviousTotal > 1024
    );
    const archivePreviousVersionsSize = displaySize(
      archivePreviousTotal,
      archivePreviousTotal > 1024
    );
    return (
      <div className={styles.detailsContainer}>
        <div className={styles.storageSize}>
          <span className={styles.detail}>
            Storage size: {`${totalSize} (${previousVersionsSize})`}
          </span>
          <InfoTooltip size={{size, effective}} />
        </div>
        <span className={styles.detail}>
          Archive size: {`${totalArchiveSize} (${archivePreviousVersionsSize})`}
        </span>
      </div>
    );
  };

  renderDetailedInfo = () => {
    const {details} = this.usageInfo;
    const getHeading = (storageClass) => {
      let storageStatus = 'Archive';
      if (storageClass === STORAGE_CLASSES.standard) {
        storageStatus = 'Storage';
      }
      const formattedName = `${storageClass[0].toUpperCase()}${storageClass
        .substring(1)
        .toLowerCase()}`.replace('_', ' ');
      return `${storageStatus} size (${formattedName})`;
    };
    return (
      <div className={styles.detailsContainer}>
        {details.map(({storageClass, size, previous}) => (
          <span
            key={storageClass}
            className={styles.detail}
          >
            {/* eslint-disable-next-line max-len */}
            {`${getHeading(storageClass)}: ${displaySize(size, size > 1024)} (${displaySize(previous, previous > 1024)})`}
          </span>
        ))}
      </div>
    );
  };

  renderControls = () => {
    const {showDetailedInfo} = this.state;
    return (
      <div>
        {this.usageInfo?.details?.length > 0 ? (
          <a
            className={styles.controlsButton}
            onClick={event => this.toggleDetails(event, !showDetailedInfo)}
          >
            {showDetailedInfo ? 'Hide details' : 'Show details'}
          </a>
        ) : null}
        <a
          className={styles.controlsButton}
          onClick={this.refreshSize}
        >
          Re-index
        </a>
      </div>
    );
  };

  render () {
    const {className, style} = this.props;
    const {showDetailedInfo} = this.state;
    if (this.usageInfo) {
      return (
        <div
          className={
            classNames(
              styles.storageSizeContainer,
              'cp-text',
              className
            )
          }
          style={style}
        >
          {showDetailedInfo
            ? this.renderDetailedInfo()
            : this.renderInfo()
          }
          {this.renderControls()}
        </div>
      );
    }
    return (
      <div
        className={
          classNames(
            styles.storageSize,
            className
          )
        }
        style={style}
      >
        <a
          className={styles.refreshButton}
          onClick={this.refreshSize}
        >
          Request storage re-index
        </a>
      </div>
    );
  }
}

StorageSize.propTypes = {
  className: PropTypes.string,
  storage: PropTypes.object,
  storageId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  style: PropTypes.object
};

export default StorageSize;
