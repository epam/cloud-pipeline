import React from 'react';
import PropTypes from 'prop-types';
import {
  CP_CAP_FS_PARAMETERS_HINTS,
  getShareFsDeploymentTypeOptions, getShareFsIOPSOptions, getShareFsThroughputOptions,
  normalizeFsConfig,
  ShareFsType, ShareFsTypeName, validateFsDeploymentType,
  validateFsIops,
  validateFsThroughput,
  validateFsVolume
} from './utilities';
import styles from './configure-file-system.css';
import classNames from 'classnames';
import {Icon, Input, Select, Tooltip} from 'antd';
import {
  CP_CAP_SHARE_FS_DEPLOYMENT_TYPE, CP_CAP_SHARE_FS_IOPS,
  CP_CAP_SHARE_FS_SIZE,
  CP_CAP_SHARE_FS_THROUGHPUT,
  CP_CAP_SHARE_FS_TYPE
} from "../parameters";

function renderParameterTooltip (parameter, style) {
  if (!CP_CAP_FS_PARAMETERS_HINTS[parameter]) {
    return null;
  }
  return (
    <Tooltip title={CP_CAP_FS_PARAMETERS_HINTS[parameter]}>
      <Icon
        type="question-circle"
        style={{...(style || {}), marginLeft: 5, flexShrink: 0}} />
    </Tooltip>
  );
}

function getOptionValue(option) {
  if (option === undefined) {
    return 'not-set';
  }
  return option.toString();
}

function getOptionDescription(option) {
  if (option === undefined) {
    return 'Not set';
  }
  return option.toString();
}

function parseOptionValue(value) {
  if (value === 'not-set' || Number.isNaN(Number(value))) {
    return undefined;
  }
  return Number(value);
}

class ConfigureFileSystem extends React.Component {
  state = {
    fsType: ShareFsType.lfs,
    deploymentType: undefined,
    volume: undefined,
    throughput: undefined,
    iops: undefined,
    deploymentTypeError: undefined,
    volumeError: undefined,
    throughputError: undefined,
    iopsError: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.fsConfig !== this.props.fsConfig) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      fsConfig
    } = this.props;
    if (fsConfig) {
      this.setState(normalizeFsConfig(fsConfig), () => this.validate());
    } else {
      this.setState({
        fsType: ShareFsType.lfs,
        deploymentType: undefined,
        volume: undefined,
        throughput: undefined,
        iops: undefined
      }, () => this.validate());
    }
  };

  validate = (cb) => {
    const {error: deploymentTypeError} = validateFsDeploymentType(this.state);
    const {error: volumeError} = validateFsVolume(this.state);
    const {error: throughputError} = validateFsThroughput(this.state);
    const {error: iopsError} = validateFsIops(this.state);
    const valid = !volumeError && !throughputError && !iopsError && !deploymentTypeError;
    this.setState({
      deploymentTypeError,
      volumeError,
      throughputError,
      iopsError
    }, () => {
      if (cb && typeof cb === 'function') {
        cb(valid);
      }
    });
    return valid;
  }

  validateAndReport = () => {
    this.validate((valid) => {
      if (valid) {
        this.reportChange();
      }
    });
  };

  reportChange = () => {
    const {
      fsType,
      deploymentType,
      volume,
      throughput,
      iops
    } = normalizeFsConfig(this.state);
    const {
      onChange
    } = this.props;
    if (!onChange) {
      return;
    }
    if (fsType === ShareFsType.lfs) {
      onChange({
        fsType,
        deploymentType: undefined,
        volume: undefined,
        throughput: undefined,
        iops: undefined
      });
    } else {
      onChange({
        fsType,
        deploymentType,
        volume,
        throughput,
        iops
      });
    }
  };

  onChangeFsType = (fsType) => {
    this.setState(normalizeFsConfig({...this.state, fsType}), this.validateAndReport);
  };

  onChangeDeploymentType = (deploymentType) => {
    this.setState(normalizeFsConfig({
      ...this.state,
      deploymentType,
    }), this.validateAndReport);
  };

  onChangeFsVolume = (e) => {
    this.setState({...this.state, volume: e.target.value}, this.validateAndReport);
  };

  onChangeFsThroughput = (throughput) => {
    this.setState(normalizeFsConfig({
      ...this.state,
      throughput: parseOptionValue(throughput)
    }), this.validateAndReport);
  };

  onChangeFsIops = (iops) => {
    this.setState(normalizeFsConfig({
      ...this.state,
      iops: parseOptionValue(iops)
    }), this.validateAndReport);
  };

  render () {
    const {
      notSupported,
      className,
      style,
      cloudRegionProvider
    } = this.props;
    if (notSupported || (cloudRegionProvider && !/^aws$/i.test(cloudRegionProvider))) {
      return null;
    }
    const {
      fsType,
      deploymentType,
      deploymentTypeError,
      volume,
      volumeError,
      throughput,
      throughputError,
      iops,
      iopsError
    } = this.state;
    const deploymentTypes = getShareFsDeploymentTypeOptions(fsType);
    const throughputOptions = getShareFsThroughputOptions(deploymentType);
    const iopsOptions = getShareFsIOPSOptions(deploymentType);
    return (
      <div
        className={classNames(
          className,
          styles.configureFileSystemContainer,
          {[styles.large]: fsType === ShareFsType.lustre && throughputOptions.length > 0}
        )}
        style={style}
      >
        <div style={{fontWeight: 'bold'}}>
          Configure File System
        </div>
        <div className={styles.configureFsRowWithErrorContainer}>
          <div className={styles.configureFsRow}>
            <span className={styles.configureFsTitle}>
              Type:
            </span>
            <Select
              className={styles.configureFsControl}
              value={fsType}
              onChange={this.onChangeFsType}
            >
              {
                [ShareFsType.lfs, ShareFsType.lustre].map((ft) => (
                  <Select.Option key={ft} value={ft}>
                    {ShareFsTypeName[ft]}
                  </Select.Option>
                ))
              }
            </Select>
            {renderParameterTooltip(CP_CAP_SHARE_FS_TYPE)}
          </div>
        </div>
        {
          fsType === ShareFsType.lustre && (
            <div className={styles.configureFsRowWithErrorContainer}>
              <div className={styles.configureFsRow}>
                <span className={styles.configureFsTitle}>
                  Deployment type:
                </span>
                <Select
                  className={classNames(
                    styles.configureFsControl,
                    {'cp-error': deploymentTypeError}
                  )}
                  value={deploymentType}
                  onChange={this.onChangeDeploymentType}
                >
                  {
                    deploymentTypes.map((dt) => (
                      <Select.Option key={dt} value={dt}>
                        {dt}
                      </Select.Option>
                    ))
                  }
                </Select>
                {renderParameterTooltip(CP_CAP_SHARE_FS_DEPLOYMENT_TYPE)}
              </div>
              {
                deploymentTypeError && (
                  <div className={classNames(styles.configureFsError, 'cp-error')}>
                    {deploymentTypeError}
                  </div>
                )
              }
            </div>
          )
        }
        {
          fsType === ShareFsType.lustre && (
            <div className={styles.configureFsRowWithErrorContainer}>
              <div className={styles.configureFsRow}>
                <span className={styles.configureFsTitle}>
                  <span>Volume</span>
                  <span className="cp-text-not-important" style={{marginLeft: 5}}>(GB)</span>
                  <span>:</span>
                </span>
                <Input
                  className={classNames(styles.configureFsControl, {'cp-error': volumeError})}
                  value={volume === undefined ? '' : volume.toString()}
                  onChange={this.onChangeFsVolume}
                />
                {renderParameterTooltip(CP_CAP_SHARE_FS_SIZE)}
              </div>
              {
                volumeError && (
                  <div className={classNames(styles.configureFsError, 'cp-error')}>
                    {volumeError}
                  </div>
                )
              }
            </div>
          )
        }
        {
          fsType === ShareFsType.lustre && throughputOptions.length > 0 && (
            <div className={styles.configureFsRowWithErrorContainer}>
              <div className={styles.configureFsRow}>
                <span className={styles.configureFsTitle}>
                  <span>Throughput</span>
                  <span className="cp-text-not-important" style={{marginLeft: 5}}>(MB/s/TiB)</span>
                  <span>:</span>
                </span>
                <Select
                  className={classNames(styles.configureFsControl, {'cp-error': throughputError})}
                  value={getOptionValue(throughput)}
                  onChange={this.onChangeFsThroughput}
                >
                  {
                    throughputOptions.map((to) => (
                      <Select.Option
                        key={getOptionValue(to)}
                        value={getOptionValue(to)}
                      >
                        <span>{getOptionDescription(to)}</span>
                      </Select.Option>
                    ))
                  }
                </Select>
                {renderParameterTooltip(CP_CAP_SHARE_FS_THROUGHPUT)}
              </div>
              {
                throughputError && (
                  <div className={classNames(styles.configureFsError, 'cp-error')}>
                    {throughputError}
                  </div>
                )
              }
            </div>
          )
        }
        {
          fsType === ShareFsType.lustre && iopsOptions.length > 0 && (
            <div className={styles.configureFsRowWithErrorContainer}>
              <div className={styles.configureFsRow}>
                <span className={styles.configureFsTitle}>
                  <span>IOPS</span>
                  <span className="cp-text-not-important" style={{marginLeft: 5}}>(IOPS/TiB)</span>
                  <span>:</span>
                </span>
                <Select
                  className={classNames(styles.configureFsControl, {'cp-error': iopsError})}
                  value={getOptionValue(iops)}
                  onChange={this.onChangeFsIops}
                >
                  {
                    iopsOptions.map((io) => (
                      <Select.Option
                        key={getOptionValue(io)}
                        value={getOptionValue(io)}
                      >
                        <span>{getOptionDescription(io)}</span>
                      </Select.Option>
                    ))
                  }
                </Select>
                {renderParameterTooltip(CP_CAP_SHARE_FS_IOPS)}
              </div>
              {
                iopsError && (
                  <div className={classNames(styles.configureFsError, 'cp-error')}>
                    {iopsError}
                  </div>
                )
              }
            </div>
          )
        }
      </div>
    );
  }
}

ConfigureFileSystem.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  fsConfig: PropTypes.shape({
    fsType: PropTypes.string,
    deploymentType: PropTypes.string,
    volume: PropTypes.number,
    throughput: PropTypes.number,
    iops: PropTypes.number
  }),
  onChange: PropTypes.func,
  notSupported: PropTypes.bool,
  cloudRegionProvider: PropTypes.string
};

ConfigureFileSystem.defaultProps = {
  notSupported: false
};

export default ConfigureFileSystem;
