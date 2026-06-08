import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './launch-form-parameter-input.css';
import {DownloadOutlined, FolderOutlined, SelectOutlined, UploadOutlined} from '@ant-design/icons';
import BucketBrowser from '../../../../dialogs/BucketBrowser';
import MetadataAutoComplete from './metadata-auto-complete';

function getIcon (pathType) {
  switch (pathType) {
    case 'input': return DownloadOutlined;
    case 'output': return UploadOutlined;
    case 'common': return SelectOutlined;
    default: return FolderOutlined;
  }
}

const defaultBucketTypes = ['AZ', 'S3', 'GS', 'DTS', 'NFS'];
const omicsBucketTypes = [...defaultBucketTypes, 'AWS_OMICS_SEQ', 'AWS_OMICS_REF'];

/**
 * @param pathType
 * @returns {string[]}
 */
function getBucketTypes (pathType) {
  if (!pathType || typeof pathType !== 'string') {
    return defaultBucketTypes;
  }
  switch (pathType.toLowerCase()) {
    case 'path':
    case 'input':
      return omicsBucketTypes;
    default:
      return defaultBucketTypes;
  }
}

class LaunchFormPathParameterInput extends React.PureComponent {
  state = {
    bucketBrowserOpened: false
  };

  openBucketBrowser = () => this.setState({bucketBrowserOpened: true});
  closeBucketBrowser = () => this.setState({bucketBrowserOpened: false});

  render () {
    const {
      className,
      style,
      parameter,
      onChange,
      value,
      disabled,
      currentCloudRegionId,
      currentProjectId,
      currentMetadataEntity,
      currentProjectMetadata,
      rootEntityId,
      metadataAutoComplete
    } = this.props;
    let {
      type: pathType = 'path'
    } = parameter;
    if (typeof pathType !== 'string' || !['path', 'common', 'input', 'output'].includes(pathType)) {
      pathType = 'path';
    }
    const IconComponent = getIcon(pathType);
    const onPathChange = (path) => {
      if (typeof onChange === 'function') {
        onChange(path || '');
      }
      this.closeBucketBrowser();
    };
    const onAddonClick = () => {
      if (!disabled) {
        this.openBucketBrowser();
      }
    };
    const {
      bucketBrowserOpened
    } = this.state;
    const isOutputType = pathType.toLowerCase() === 'output';
    const isPathType = pathType.toLowerCase() === 'path';
    return (
      <div
        className={classNames(className, styles.launchParameterInput)}
        style={style}
      >
        <MetadataAutoComplete
          style={{width: '100%'}}
          value={value ? String(value) : ''}
          onChange={onPathChange}
          disabled={disabled}
          addonBefore={(
            <div
              className={styles.launchParameterPathInputAddon}
              onClick={onAddonClick}>
              <IconComponent />
            </div>
          )}
          placeholder="Path"
          currentProjectId={currentProjectId}
          currentMetadataEntity={currentMetadataEntity}
          currentProjectMetadata={currentProjectMetadata}
          rootEntityId={rootEntityId}
          defaultInput={!metadataAutoComplete}
        />
        <BucketBrowser
          multiple={!isOutputType}
          onSelect={onPathChange}
          onCancel={this.closeBucketBrowser}
          visible={bucketBrowserOpened}
          uploadFilesAllowed={!isOutputType}
          path={value}
          showOnlyFolder={isOutputType}
          allowBucketSelection={isPathType}
          checkWritePermissions={isOutputType}
          bucketTypes={getBucketTypes(pathType)}
          cloudRegionId={currentCloudRegionId}
          filterObjectStorages={false}
          filterNonObjectStorages
        />
      </div>
    );
  }
}

LaunchFormPathParameterInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.any,
  parameter: PropTypes.object,
  required: PropTypes.bool,
  onChange: PropTypes.func,
  disabled: PropTypes.bool,
  currentCloudRegionId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  currentProjectMetadata: PropTypes.object,
  currentMetadataEntity: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  rootEntityId: PropTypes.string,
  metadataAutoComplete: PropTypes.bool
};

export default LaunchFormPathParameterInput;
