import React from 'react';
import PropTypes from 'prop-types';
import {Checkbox, Modal, Input} from 'antd';
import electron from 'electron';
import './configuration.css';
import writeWebDavConfiguration from '../../../write-webdav-configuration';

class Configuration extends React.Component {
  state = {
    server: undefined,
    password: undefined,
    username: undefined,
    ignoreCertificateErrors: false,
    modified: false,
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
    this.setState({
      server: webdavClientConfig.server,
      password: webdavClientConfig.password,
      username: webdavClientConfig.username,
      ignoreCertificateErrors: webdavClientConfig.ignoreCertificateErrors,
      modified: false,
    })
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

  render () {
    const {
      visible,
      onClose,
    } = this.props;
    const {
      server,
      username,
      password,
    } = this.state;
    return (
      <Modal
        visible={visible}
        title="Configuration"
        footer={null}
        onCancel={this.onClose}
        onClose={this.onClose}
      >
        <div
          className="configuration"
        >
          <div
            className="row"
          >
            <span className="label">
              Server:
            </span>
            <Input
              className="input"
              value={server}
              onChange={this.onSettingChanged('server')}
            />
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
        </div>
      </Modal>
    );
  }
}

Configuration.propTypes = {
  visible: PropTypes.bool,
  onClose: PropTypes.func
};

export default Configuration;
