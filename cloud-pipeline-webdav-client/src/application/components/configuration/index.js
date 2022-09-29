import React from 'react';
import PropTypes from 'prop-types';
import {Button, Checkbox, Modal, Input, Tooltip} from 'antd';
import {CheckCircleFilled, CloseCircleFilled} from '@ant-design/icons';
import electron from 'electron';
import UpdateButton from './update-button';
import checker from '../../../auto-update';
import writeWebDavConfiguration from '../../../write-webdav-configuration';
import copyPingConfiguration from '../../models/file-systems/copy-ping-configuration';
import './configuration.css';

function TestState({tested = false, error}) {
  if (!tested) {
    return null;
  }
  if (error) {
    return (
      <Tooltip title={error}>
        <CloseCircleFilled style={{color: 'red'}} />
      </Tooltip>
    );
  }
  return (
    <CheckCircleFilled style={{color: 'green'}}/>
  );
}

class Configuration extends React.Component {
  state = {
    api: undefined,
    server: undefined,
    password: undefined,
    username: undefined,
    ignoreCertificateErrors: false,
    modified: false,
    version: undefined,
    pingAfterCopy: false,
    maxWaitSeconds: undefined,
    pingTimeoutSeconds: undefined,
    pending: false,
    webDavDiagnoseState: undefined,
    webDavDiagnoseFile: undefined,
    webDavError: undefined,
    webDavTested: false,
    apiDiagnoseState: undefined,
    apiDiagnoseFile: undefined,
    apiError: undefined,
    apiTested: false
  };
  componentDidMount() {
    this.updateSettings();
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.updateSettings();
    }
  }

  updateSettings = () => {
    const cfg = electron.remote.getGlobal('webdavClient');
    const {config: webdavClientConfig = {}} = cfg || {};
    let {
      maxWaitSeconds = copyPingConfiguration.maxWaitSeconds,
      pingTimeoutSeconds = copyPingConfiguration.pingTimeoutSeconds,
      name
    } = webdavClientConfig;
    if (
      Number.isNaN(Number(maxWaitSeconds)) ||
      Number.isNaN(Number(pingTimeoutSeconds)) ||
      Number(maxWaitSeconds) <= 0
    ) {
      maxWaitSeconds = 0;
      pingTimeoutSeconds = 0;
    }
    if (Number(maxWaitSeconds) > 0 && Number(pingTimeoutSeconds) > Number(maxWaitSeconds)) {
      pingTimeoutSeconds = maxWaitSeconds;
    }
    const pingAfterCopy = !Number.isNaN(Number(maxWaitSeconds)) &&
      !Number.isNaN(Number(pingTimeoutSeconds)) &&
      Number(maxWaitSeconds) > 0;
    this.setState({
      api: webdavClientConfig.api,
      server: webdavClientConfig.server,
      password: webdavClientConfig.password,
      username: webdavClientConfig.username,
      ignoreCertificateErrors: webdavClientConfig.ignoreCertificateErrors,
      modified: false,
      version: webdavClientConfig.version,
      name,
      pingAfterCopy,
      maxWaitSeconds,
      pingTimeoutSeconds,
      pending: false,
      webDavDiagnoseState: undefined,
      webDavDiagnoseFile: undefined,
      webDavError: undefined,
      webDavTested: false,
      apiDiagnoseState: undefined,
      apiDiagnoseFile: undefined,
      apiError: undefined,
      apiTested: false
    });
  };

  onSettingChanged = (settingName) => (e) => {
    this.setState({
      [settingName]: e.target.value,
      modified: true,
    });
    const cfg = electron.remote.getGlobal('webdavClient');
    cfg.config[settingName] = e.target.value;
    writeWebDavConfiguration(cfg.config);
  };

  onPingAfterCopyChanged = (e) => {
    let {
      maxWaitSeconds,
      pingTimeoutSeconds
    } = copyPingConfiguration;
    const pingAfterCopy = e.target.checked;
    if (!pingAfterCopy) {
      maxWaitSeconds = 0;
      pingTimeoutSeconds = 0;
    }
    const cfg = electron.remote.getGlobal('webdavClient');
    cfg.config.maxWaitSeconds = maxWaitSeconds;
    cfg.config.pingTimeoutSeconds = pingTimeoutSeconds;
    writeWebDavConfiguration(cfg.config);
    this.setState({
      pingAfterCopy,
      maxWaitSeconds,
      pingTimeoutSeconds,
      modified: true
    });
  };

  onIgnoreCertificateErrorsSettingChanged = (e) => {
    this.setState({
      ignoreCertificateErrors: e.target.checked,
      modified: true,
    });
    const cfg = electron.remote.getGlobal('webdavClient');
    cfg.config.ignoreCertificateErrors = e.target.checked;
    writeWebDavConfiguration(cfg.config);
  };

  onClose = () => {
    const {onClose} = this.props;
    if (onClose) {
      onClose(this.state.modified);
    }
  };

  diagnose = (options = {}) => {
    const {
      logStatePropertyName,
      fileStatePropertyName,
      resultStatePropertyName,
      resultErrorStatePropertyName,
      testOptions = {}
    } = options || {};
    const {fileSystem} = this.props;
    const {
      api,
      server,
      password,
      username,
      ignoreCertificateErrors
    } = this.state;
    if (fileSystem) {
      this.setState({
        pending: true,
        [fileStatePropertyName]: undefined,
        [resultStatePropertyName]: false,
        [resultErrorStatePropertyName]: undefined
      }, () => {
        const cb = (o) => this.setState({[logStatePropertyName]: o});
        fileSystem
          .diagnose(
            {
              api,
              server,
              password,
              username,
              ignoreCertificateErrors,
              ...testOptions
            },
            cb
          )
          .then((result) => {
            const {
              filePath
            } = result || {};
            this.setState({
              [logStatePropertyName]: undefined,
              pending: false,
              [fileStatePropertyName]: filePath,
              [resultStatePropertyName]: true,
              [resultErrorStatePropertyName]: result.error
            })
          });
      });
    }
  };

  diagnoseWebDav = () => {
    this.diagnose({
      logStatePropertyName: 'webDavDiagnoseState',
      fileStatePropertyName: 'webDavDiagnoseFile',
      resultStatePropertyName: 'webDavTested',
      resultErrorStatePropertyName: 'webDavError',
      testOptions: {
        testWebdav: true
      }
    });
  };

  diagnoseAPI = () => {
    this.diagnose({
      logStatePropertyName: 'apiDiagnoseState',
      fileStatePropertyName: 'apiDiagnoseFile',
      resultStatePropertyName: 'apiTested',
      resultErrorStatePropertyName: 'apiError',
      testOptions: {
        testApi: true
      }
    });
  };

  renderDiagnoseInfo = (title, log, filePath) => {
    if ((log || filePath)) {
      return (
        <div
          className="row network-logs"
        >
          {
            filePath && (
              <span
                className="label"
                style={{marginRight: 5}}
              >
                {title} Network Log file:
              </span>
            )
          }
          <div style={{flex: 1}}>
            {
              filePath && (
                <Input
                  value={filePath}
                  readOnly
                  style={{width: '100%'}}
                />
              )
            }
            {log}
          </div>
        </div>
      );
    }
    return null;
  };

  render () {
    const {
      visible,
      fileSystem
    } = this.props;
    const {
      api,
      server,
      username,
      password,
      version,
      name = 'Cloud Data',
      maxWaitSeconds,
      pingTimeoutSeconds,
      pingAfterCopy,
      pending,
      webDavDiagnoseFile,
      webDavDiagnoseState,
      webDavError,
      webDavTested,
      apiDiagnoseFile,
      apiDiagnoseState,
      apiError,
      apiTested
    } = this.state;
    return (
      <Modal
        visible={visible}
        title="Configuration"
        footer={null}
        onCancel={this.onClose}
        onClose={this.onClose}
        width="60%"
      >
        <div
          className="configuration"
        >
          <div
            className="row"
          >
            <span className="label">
              Data Server:
            </span>
            <Input
              className="input"
              value={server}
              onChange={this.onSettingChanged('server')}
              suffix={(
                <TestState tested={webDavTested} error={webDavError} />
              )}
            />
            {
              fileSystem && (
                <Button
                  disabled={pending}
                  onClick={this.diagnoseWebDav}
                  style={{marginLeft: 5}}
                >
                  TEST
                </Button>
              )
            }
          </div>
          <div
            className="row"
          >
            <span className="label">
              API Server:
            </span>
            <Input
              className="input"
              value={api}
              onChange={this.onSettingChanged('api')}
              suffix={(
                <TestState tested={apiTested} error={apiError} />
              )}
            />
            {
              fileSystem && (
                <Button
                  disabled={pending}
                  onClick={this.diagnoseAPI}
                  style={{marginLeft: 5}}
                >
                  TEST
                </Button>
              )
            }
          </div>
          <div
            className="row"
          >
            <span className="label">
              User name:
            </span>
            <Input
              className="input"
              value={username}
              onChange={this.onSettingChanged('username')}
            />
          </div>
          <div
            className="row"
          >
            <span className="label">
              Password:
            </span>
            <Input.Password
              className="input"
              value={password}
              onChange={this.onSettingChanged('password')}
            />
          </div>
          <div
            className="row"
          >
            <Checkbox
              checked={this.state.ignoreCertificateErrors}
              onChange={this.onIgnoreCertificateErrorsSettingChanged}
            >
              Ignore certificate errors (re-launch is required)
            </Checkbox>
          </div>
          <div
            className="row"
          >
            <Checkbox
              checked={pingAfterCopy}
              onChange={this.onPingAfterCopyChanged}
            >
              Check destination file existence after copy operation
            </Checkbox>
          </div>
          {
            pingAfterCopy && (
              <div
                className="row"
              >
                <span className="label-small">
                  Ping duration (seconds):
                </span>
                <Input
                  className="input"
                  value={maxWaitSeconds}
                  onChange={this.onSettingChanged('maxWaitSeconds')}
                  size="small"
                />
              </div>
            )
          }
          {
            pingAfterCopy && (
              <div
                className="row"
              >
                <span className="label-small">
                  Ping every (seconds):
                </span>
                <Input
                  className="input"
                  value={pingTimeoutSeconds}
                  onChange={this.onSettingChanged('pingTimeoutSeconds')}
                  size="small"
                />
              </div>
            )
          }
          {
            version && (
              <div className="app-version">
                {name} App Version: <b>{version}</b>
              </div>
            )
          }
          {
            checker.componentVersion && (
              <div className="app-version">
                {name} App Component Version: {checker.componentVersion}
              </div>
            )
          }
          <UpdateButton />
          {this.renderDiagnoseInfo('WebDav', webDavDiagnoseState, webDavDiagnoseFile)}
          {this.renderDiagnoseInfo('API', apiDiagnoseState, apiDiagnoseFile)}
        </div>
      </Modal>
    );
  }
}

Configuration.propTypes = {
  visible: PropTypes.bool,
  onClose: PropTypes.func,
  fileSystem: PropTypes.object
};

export default Configuration;
