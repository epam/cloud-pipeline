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
    expandDetails: false
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
      archiveSize: archivedSizes
        ? archivedSizes.total
        : 0,
      archivePrevious: archivedSizes
        ? archivedSizes.previous
        : 0
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

  toggleDetails = (event, expanded) => {
    event && event.preventDefault();
    this.setState({expandDetails: expanded});
  };

  renderDetails = () => {
    const {expandDetails} = this.state;
    return (
      <div className={classNames(
        styles.detailsContainer,
        expandDetails ? styles.expanded : styles.collapsed
      )}>
        Details
      </div>
    );
  };

  render () {
    const {className, style} = this.props;
    const {expandDetails} = this.state;
    if (this.usageInfo) {
      const {
        size,
        effective,
        previous,
        archiveSize,
        archivePrevious
      } = this.usageInfo;
      const totalSize = displaySize(
        (effective || size) + previous,
        (effective || size) + previous > 1024
      );
      const previousVersionsSize = displaySize(previous, previous > 1024);
      const totalArchiveSize = displaySize(
        archiveSize + archivePrevious,
        archiveSize + archivePrevious > 1024
      );
      const archivePreviousVersionsSize = displaySize(
        archivePrevious,
        archivePrevious > 1024
      );
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
          <div className={styles.storageSize}>
            <span>
              Storage size: {`${totalSize} (${previousVersionsSize})`}
            </span>
            <InfoTooltip size={{size, effective}} />
          </div>
          <span className={styles.storageSize}>
            Archive size: {`${totalArchiveSize} (${archivePreviousVersionsSize})`}
            <a
              className={styles.refreshButton}
              onClick={this.refreshSize}
            >
              Re-index
            </a>
          </span>
          {this.renderDetails()}
          <a onClick={event => this.toggleDetails(event, !expandDetails)}>
            {expandDetails ? 'Hide details' : 'Show details'}
          </a>
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
