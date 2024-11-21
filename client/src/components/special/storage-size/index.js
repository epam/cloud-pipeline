/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  message,
  Tooltip,
  Icon,
  Modal,
  Spin
} from 'antd';
import {computed, observable} from 'mobx';
import {inject, observer} from 'mobx-react';
import {STORAGE_CLASSES} from '../../pipelines/browser/data-storage';
import DataStoragePathUsage from '../../../models/dataStorage/DataStoragePathUsage';
import DataStoragePathUsageUpdate from '../../../models/dataStorage/DataStoragePathUsageUpdate';
import displaySize from '../../../utils/displaySize';
import styles from './storage-size.css';

const STORAGE_DESCRIPTION = {
  STANDARD: 'Standard',
  GLACIER: 'Glacier',
  GLACIER_IR: 'Glacier IR',
  DEEP_ARCHIVE: 'Deep archive'
};

export const MODE = {
  storage: 'storage',
  folder: 'folder'
};

const modeToggler = {
  [MODE.storage]: MODE.folder,
  [MODE.folder]: MODE.storage
};

const REFRESH_REQUESTED_MESSAGE =
  'Storage size refresh has been requested. Please wait a couple of minutes.';

function InfoTooltip ({sizes, isNFS}) {
  const {size, effective} = sizes;
  const showEffectiveSizeDisclaimer = effective && effective !== size;

  if (isNFS && !showEffectiveSizeDisclaimer) {
    return null;
  }

  const tooltip = (
    <div>
      {!isNFS ? (
        <p>
          {/* eslint-disable-next-line max-len */}
          First number shows sum of the Current and Previous versioned objects volume within a storage class. A number in the parenthesis shows Previous versioned objects volume only.
        </p>
      ) : null}
      {showEffectiveSizeDisclaimer ? (
        <div style={{marginTop: 10}}>
          <div>
            Effective size: {displaySize(effective, effective > 1024)}
          </div>
          <div>
            Real size: {displaySize(size, size > 1024)}
          </div>
        </div>
      ) : null}
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
    showDetailedInfo: false,
    mode: MODE.storage,
    pending: false
  };

  @observable info;

  componentDidMount () {
    this.updateMode();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (
      prevProps.path !== this.props.path ||
      prevProps.storage !== this.props.storage ||
      prevProps.storageId !== this.props.storageId
    ) {
      this.updateMode();
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
        acc.total += (effectiveSize || size) + (oldVersionsEffectiveSize || oldVersionsSize);
        acc.previousTotal += oldVersionsEffectiveSize || oldVersionsSize;
        return acc;
      }, {total: 0, previousTotal: 0})
      : null;
    return {
      size: sizes.size || 0,
      effective: sizes.effectiveSize || 0,
      previous: sizes.oldVersionsEffectiveSize || sizes.oldVersionsSize || 0,
      archiveSizeTotal: archivedSizes
        ? archivedSizes.total
        : 0,
      archivePreviousTotal: archivedSizes
        ? archivedSizes.previousTotal
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

  @computed
  get hasArchivedData () {
    const {storage} = this.props;
    if (!storage || !this.usageInfo) {
      return false;
    }
    return !this.isNFS && (
      this.usageInfo.archiveSizeTotal > 0 ||
      this.usageInfo.archivePreviousTotal > 0
    );
  }

  get isNFS () {
    const {storage = {}} = this.props;
    return (storage.type || '').toUpperCase() === 'NFS';
  }

  get isRoot () {
    return !this.props.path;
  }

  updateMode = () => {
    const {path, onModeChange} = this.props;
    this.setState({mode: path ? MODE.folder : MODE.storage}, () => {
      this.updateStorageSize();
      onModeChange && onModeChange(this.state.mode);
    });
  };

  updateStorageSize = () => {
    const {mode} = this.state;
    const {
      storage,
      storageId,
      path: pathProp
    } = this.props;
    let id = storageId;
    if (id === undefined && typeof storage === 'object') {
      id = storage.id;
    }
    if (id !== undefined) {
      this.setState({pending: true}, async () => {
        const path = mode === MODE.folder
          ? pathProp
          : undefined;
        const request = new DataStoragePathUsage(id, path);
        await request.fetch();
        if (request.error) {
          console.warn(request.error);
          this.info = null;
        } else if (request.value) {
          this.info = request.value;
        } else {
          this.info = null;
        }
        this.setState({pending: false});
      });
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

  showDetailedInfo = (event) => {
    event && event.preventDefault();
    this.setState({showDetailedInfo: true});
  };

  closeDetailedInfo = (event) => {
    event && event.preventDefault();
    this.setState({showDetailedInfo: false});
  };

  renderInfo = () => {
    const {mode} = this.state;
    const {path, storage} = this.props;
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
      archiveSizeTotal,
      archiveSizeTotal > 1024
    );
    const archivePreviousVersionsSize = displaySize(
      archivePreviousTotal,
      archivePreviousTotal > 1024
    );
    const previousVersionsInfo = this.isNFS
      ? ''
      : ` (${previousVersionsSize})`;
    let header = storage?.pathMask || storage?.path || 'Storage';
    if (mode === MODE.folder) {
      header = (path || '').split('/').pop();
    }
    return (
      <div className={styles.detailsContainer}>
        <b className={styles.folderSizeTitle}>{header}</b>
        <div className={styles.standardContainer}>
          <div
            className={styles.standardDetailRow}
            style={{marginRight: this.isNFS ? 5 : 0}}
          >
            <span className={styles.detail}>
              {STORAGE_DESCRIPTION.STANDARD} size: {`${totalSize}${previousVersionsInfo}`}
            </span>
            <InfoTooltip isNFS={this.isNFS} sizes={{size, effective}} />
          </div>
          {this.isNFS ? this.renderControls() : null}
        </div>
        {this.hasArchivedData ? (
          <span className={styles.detail}>
            Archive size: {`${totalArchiveSize} (${archivePreviousVersionsSize})`}
          </span>
        ) : null}
        {this.isNFS ? null : this.renderControls()}
      </div>
    );
  };

  renderDetailedInfoModal = () => {
    const {showDetailedInfo} = this.state;
    const {details} = this.usageInfo;
    const convertBytesToGb = (bytes) => {
      const minimalValue = 0.01;
      const Gb = bytes / Math.pow(1024, 3);
      if (Gb > 0 && Gb < minimalValue) {
        return `< ${minimalValue}`;
      }
      return Gb.toFixed(2);
    };
    const heading = (
      <div
        className={classNames(
          styles.detailedInfoGridRow,
          'cp-divider',
          'bottom'
        )}
      >
        <b className={classNames(
          styles.headingCell,
          styles.storageClass
        )}>
          Storage class
        </b>
        <b className={classNames(
          styles.headingCell,
          styles.size
        )}>
          Current ver. (Gb)
        </b>
        <b className={classNames(
          styles.headingCell,
          styles.previous
        )}>
          Previous ver. (Gb)
        </b>
        <b className={classNames(
          styles.headingCell,
          styles.total
        )}>
          Total (Gb)
        </b>
      </div>
    );
    return (
      <Modal
        onCancel={this.closeDetailedInfo}
        visible={showDetailedInfo}
        footer={false}
        title="Usage details"
        width={600}
      >
        <div className={styles.detailsContainer}>
          {heading}
          {details.map(({storageClass, size, previous}) => (
            <div
              key={storageClass}
              className={classNames(
                styles.detailedInfoGridRow,
                'cp-divider',
                'bottom'
              )}
            >
              <div className={classNames(
                styles.storageClass,
                styles.cell
              )}>
                {STORAGE_DESCRIPTION[storageClass] || storageClass}
              </div>
              <div className={classNames(
                styles.size,
                styles.cell
              )}>
                {convertBytesToGb(size)}
              </div>
              <div className={classNames(
                styles.previous,
                styles.cell
              )}>
                {convertBytesToGb(previous)}
              </div>
              <div className={classNames(
                styles.total,
                styles.cell
              )}>
                {convertBytesToGb(size + previous)}
              </div>
            </div>
          ))}
        </div>
      </Modal>
    );
  };

  renderControls = () => {
    const {mode} = this.state;
    const {onModeChange} = this.props;
    const toggleMode = () => this.setState({mode: modeToggler[mode]}, () => {
      onModeChange && onModeChange(this.state.mode);
      this.updateStorageSize();
    });
    return (
      <div>
        {!this.isRoot ? (
          <a
            className={styles.controlsButton}
            onClick={toggleMode}
          >
            Show {modeToggler[mode]} size
          </a>
        ) : null}
        {this.isRoot || mode === MODE.storage ? (
          <a
            className={styles.controlsButton}
            onClick={this.refreshSize}
          >
            Re-index
          </a>
        ) : null}
        {this.hasArchivedData && this.usageInfo?.details?.length > 0 ? (
          <a
            className={styles.controlsButton}
            onClick={this.showDetailedInfo}
          >
            Show details
          </a>
        ) : null}
      </div>
    );
  };

  render () {
    const {pending} = this.state;
    const {className, style} = this.props;
    if (this.usageInfo && (
      this.usageInfo.size !== undefined ||
      this.usageInfo.archiveSizeTotal !== undefined
    )) {
      return (
        <Spin spinning={pending}>
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
            {this.renderInfo()}
            {this.renderDetailedInfoModal()}
          </div>
        </Spin>
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
  style: PropTypes.object,
  path: PropTypes.string
};

export default StorageSize;
