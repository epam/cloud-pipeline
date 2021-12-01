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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {
  Button,
  Modal,
  Radio
} from 'antd';
import {
  ThemesPreferenceModes
} from '../../../../themes';
import styles from './save-dialog.css';

@inject('themes', 'preferences')
@observer
class SaveDialog extends React.Component {
  state = {
    currentMode: ThemesPreferenceModes.payload,
    mode: ThemesPreferenceModes.payload
  };

  @computed
  get deploymentName () {
    const {preferences} = this.props;
    return preferences.deploymentName || 'Cloud Pipeline';
  }

  @computed
  get currentThemesURI () {
    const {themes} = this.props;
    const url = themes.themesURL;
    try {
      return (new URL(url, window.location.href)).href;
    } catch (_) {
      return url;
    }
  }

  componentDidMount () {
    if (this.props.visible) {
      this.onBecomeVisible();
    }
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (this.props.visible && this.props.visible !== prevProps.visible) {
      this.onBecomeVisible();
    }
  }

  onBecomeVisible = () => {
    const {themes} = this.props;
    this.setState({
      currentMode: themes.mode,
      mode: themes.mode
    });
  };

  onSaveClicked = () => {
    const {mode} = this.state;
    const {onSave} = this.props;
    if (onSave) {
      onSave({mode});
    }
  };

  renderDisclaimer = () => {
    const {
      currentMode
    } = this.state;
    const options = (
      <p>
        You can download modified configuration file and save it manually
        or save the configuration using <b>{this.deploymentName}</b> preferences.
      </p>
    );
    if (currentMode === ThemesPreferenceModes.url) {
      return (
        <div className={styles.disclaimer}>
          <h3>
            Current UI themes configuration is stored at the <b>{this.currentThemesURI}</b>.
          </h3>
          {options}
        </div>
      );
    }
    return (
      <div className={styles.disclaimer}>
        <h3>
          Current UI themes configuration is stored using
          the <b>{this.deploymentName}</b> preferences.
        </h3>
        {options}
      </div>
    );
  };

  renderModeSelector = () => {
    const {mode} = this.state;
    const {disabled} = this.props;
    const onChangeMode = (e) => {
      this.setState({
        mode: e.target.value
      });
    };
    return (
      <div className={styles.selector}>
        <Radio.Group
          disabled={disabled}
          onChange={onChangeMode}
          value={mode}
        >
          <Radio className={styles.option} value={ThemesPreferenceModes.url}>
            Download configuration file and save it manually
          </Radio>
          <Radio className={styles.option} value={ThemesPreferenceModes.payload}>
            Save themes configuration using <b>{this.deploymentName}</b> preference
          </Radio>
        </Radio.Group>
      </div>
    );
  };

  getSaveButtonCaption = () => {
    const {mode} = this.state;
    if (mode === ThemesPreferenceModes.url) {
      return 'DOWNLOAD CONFIGURATION FILE';
    }
    return 'SAVE AS PREFERENCE';
  };

  render () {
    const {
      disabled,
      onCancel,
      visible
    } = this.props;
    return (
      <Modal
        title="Save themes configuration"
        visible={visible}
        onCancel={onCancel}
        maskClosable={!disabled}
        closable={!disabled}
        footer={(
          <div
            className={styles.footer}
          >
            <Button
              disabled={disabled}
              className={styles.button}
              onClick={onCancel}
            >
              CANCEL
            </Button>
            <Button
              disabled={disabled}
              className={styles.button}
              type="primary"
              onClick={this.onSaveClicked}
            >
              {this.getSaveButtonCaption()}
            </Button>
          </div>
        )}
      >
        {this.renderDisclaimer()}
        {this.renderModeSelector()}
      </Modal>
    );
  }
}

SaveDialog.propTypes = {
  disabled: PropTypes.bool,
  onSave: PropTypes.func,
  onCancel: PropTypes.func,
  visible: PropTypes.bool
};

export default SaveDialog;
