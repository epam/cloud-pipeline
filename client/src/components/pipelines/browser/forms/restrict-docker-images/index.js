/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {
  Button,
  Input,
  Modal,
  Radio
} from 'antd';
import classNames from 'classnames';
import DockerImagesEdit from './DockerImagesEdit';
import styles from './RestrictDockerDialog.css';

const KEYS = {
  mountAll: 'mountAll',
  limitMounts: 'limitMounts'
};

function toolsSorter (a, b) {
  return a.id - b.id;
}

function compareToolsToMount (a, b) {
  if (!a && !b) {
    return true;
  }
  const toolsA = (a || []).sort(toolsSorter);
  const toolsB = (b || []).sort(toolsSorter);
  if (toolsA.length !== toolsB.length) {
    return false;
  }
  for (let i = 0; i < toolsA.length; i++) {
    const tA = toolsA[i];
    const tB = toolsB[i];
    if (tA.id !== tB.id) {
      return false;
    }
    const vA = [...(new Set(tA.versions || []))].map(v => v.version).sort();
    const vB = [...(new Set(tB.versions || []))].map(v => v.version).sort();
    if (vA.length !== vB.length) {
      return false;
    }
    for (let v = 0; v < vA.length; v++) {
      if (vA[v] !== vB[v]) {
        return false;
      }
    }
  }
  return true;
}

class RestrictDockerDialog extends React.Component {
  state = {
    toolsToMount: [],
    limitMounts: false
  };

  componentDidMount () {
    this.updateStateFromProps();
  }

  componentDidUpdate (prevProps) {
    if (
      !compareToolsToMount(prevProps.toolsToMount, this.props.toolsToMount) ||
      (prevProps.visible !== this.props.visible && this.props.visible)
    ) {
      this.updateStateFromProps();
    }
  }

  updateStateFromProps = () => {
    const {toolsToMount = []} = this.props;
    this.setState({
      toolsToMount: toolsToMount.slice(),
      limitMounts: toolsToMount.length > 0
    });
  };

  get toolsToMount () {
    const {toolsToMount} = this.state;
    return toolsToMount;
  }

  onOk = () => {
    const {onOk} = this.props;
    const {toolsToMount, limitMounts} = this.state;
    const tools = limitMounts
      ? toolsToMount.slice().filter(tool => tool.id && tool.image && tool.registry)
      : [];
    onOk && onOk(tools);
  };

  onCancel = () => {
    const {onCancel} = this.props;
    onCancel && onCancel();
  };

  onChangeDockerImages = (tools) => {
    this.setState({toolsToMount: tools});
  };

  onChangeLimitMountsRadio = (event) => {
    const {value} = event.target;
    if (value === KEYS.mountAll) {
      return this.setState({limitMounts: false});
    }
    if (value === KEYS.limitMounts) {
      return this.setState({limitMounts: true});
    }
  };

  renderRadioBlock = () => {
    const {limitMounts} = this.state;
    return (
      <div className={classNames(styles.modalRadioContainer, 'cp-divider', 'bottom')}>
        <Radio.Group
          onChange={this.onChangeLimitMountsRadio}
          value={limitMounts ? KEYS.limitMounts : KEYS.mountAll}
          className={styles.modalRadioGroup}
        >
          <Radio
            className={styles.radioButton}
            value={KEYS.mountAll}
          >
            Mount to all available images
          </Radio>
          <Radio
            className={styles.radioButton}
            value={KEYS.limitMounts}
          >
            Limit mounts
          </Radio>
        </Radio.Group>
      </div>
    );
  };

  render () {
    const {visible} = this.props;
    const {toolsToMount, limitMounts} = this.state;
    return (
      <Modal
        title="Select docker images to limit mounts"
        visible={visible}
        onCancel={this.onCancel}
        width="60%"
        footer={(
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end'
            }}
          >
            <Button
              onClick={this.onCancel}
              style={{marginRight: 5}}
              id="docker-images-to-limit-mounts-modal-cancel-btn"
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              onClick={this.onOk}
              disabled={
                limitMounts &&
                (toolsToMount || [])
                  .filter(tool => tool.id && tool.image && tool.registry)
                  .length === 0
              }
              id="docker-images-to-limit-mounts-modal-ok-btn"
            >
              OK
            </Button>
          </div>
        )}
      >
        {this.renderRadioBlock()}
        <DockerImagesEdit
          toolsToMount={toolsToMount}
          onChange={this.onChangeDockerImages}
          disabled={!limitMounts}
        />
      </Modal>
    );
  }
}

RestrictDockerDialog.propTypes = {
  visible: PropTypes.bool,
  onOk: PropTypes.func,
  onCancel: PropTypes.func,
  toolsToMount: PropTypes.oneOfType([PropTypes.object, PropTypes.array])
};

class RestrictDockerImages extends React.Component {
  state = {
    visible: false
  };

  getStringPresentation = () => {
    const {value = []} = this.props;
    if (value.length === 0) {
      return 'All available docker images';
    }
    const getVersions = (tool) => {
      const {versions = []} = tool;
      if (versions.length === 0) {
        return false;
      }
      return `(${versions.map(o => o.version).join(', ')})`;
    };
    return value.map(tool => [tool.image, getVersions(tool)].filter(Boolean).join(' ')).join(', ');
  };

  open = () => {
    if (!this.props.disabled && !this.state.visible) {
      this.setState({visible: true});
    }
  };

  close = () => {
    if (this.state.visible) {
      this.setState({visible: false});
    }
  };

  onChange = (value) => {
    const {onChange} = this.props;
    onChange && onChange(value);
    this.close();
  };

  render () {
    const {
      className,
      style,
      value = [],
      disabled
    } = this.props;
    const {
      visible
    } = this.state;
    return (
      <div
        className={className}
        style={style}
      >
        <Input
          readOnly
          disabled={disabled}
          value={this.getStringPresentation()}
          onClick={this.open}
        />
        <RestrictDockerDialog
          visible={visible}
          toolsToMount={value}
          onCancel={this.close}
          onOk={this.onChange}
        />
      </div>
    );
  }
}

RestrictDockerImages.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  value: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  onChange: PropTypes.func,
  disabled: PropTypes.bool
};

export default RestrictDockerImages;
