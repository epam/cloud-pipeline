import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './launch-form-parameter-input.css';
import {Icon, Input} from 'antd';
import BucketBrowser from '../../../../dialogs/BucketBrowser';

function getIcon (pathType) {
  switch (pathType) {
    case 'input': return 'download';
    case 'output': return 'upload';
    case 'common': return 'select';
    default: return 'folder';
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
      pathType = 'path',
      onChange,
      value,
      readOnly,
      disabled
    } = this.props;
    const icon = getIcon(pathType);
    const onPathChange = (path) => {
      if (typeof onChange === 'function') {
        onChange(path || '');
      }
      this.closeBucketBrowser();
    };
    const onInputChange = (e) => {
      onPathChange(e.target.value);
    };
    const onAddonClick = () => {
      if (!disabled && !readOnly) {
        this.openBucketBrowser();
      }
    };
    const {
      bucketBrowserOpened
    } = this.state;
    const isOutputType = typeof pathType === 'string' && pathType.toLowerCase() === 'output';
    const isPathType = typeof pathType === 'string' && pathType.toLowerCase() === 'path';
    return (
      <div
        className={classNames(className, styles.launchParameterInput)}
        style={style}
      >
        <Input
          style={{width: '100%'}}
          value={value ? String(value) : ''}
          onChange={onInputChange}
          disabled={readOnly || disabled}
          addonBefore={(
            <div
              className={styles.launchParameterPathInputAddon}
              onClick={onAddonClick}>
              <Icon type={icon} />
            </div>
          )}
          placeholder="Path"
          size="large"
        />
        <BucketBrowser
          multiple
          onSelect={onPathChange}
          onCancel={this.closeBucketBrowser}
          visible={bucketBrowserOpened}
          uploadFilesAllowed={!isOutputType}
          path={value}
          showOnlyFolder={isOutputType}
          allowBucketSelection={isPathType}
          checkWritePermissions={isOutputType}
          bucketTypes={getBucketTypes(pathType)}
        />
      </div>
    );
  }
}

LaunchFormPathParameterInput.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.any,
  pathType: PropTypes.string,
  required: PropTypes.bool,
  onChange: PropTypes.func,
  readOnly: PropTypes.bool,
  disabled: PropTypes.bool
};

export default LaunchFormPathParameterInput;
