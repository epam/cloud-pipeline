/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {Button, Input, Modal} from 'antd';
import styles from '../cell-profiler.css';

class SavePipelineModal extends React.Component {
  state = {
    name: undefined,
    description: undefined
  };

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.updateFromProps();
    }
  }

  updateFromProps = () => {
    const {
      pipeline
    } = this.props;
    if (pipeline) {
      this.setState({
        name: pipeline.name,
        description: pipeline.description
      });
    } else {
      this.setState({
        name: undefined,
        description: undefined
      });
    }
  };

  onNameChanged = (e) => {
    this.setState({
      name: e.target.value
    });
  };

  onDescriptionChanged = (e) => {
    this.setState({
      description: e.target.value
    });
  };

  onSaveClicked = () => {
    const {
      pipeline,
      onSave
    } = this.props;
    const {
      name,
      description
    } = this.state;
    if (pipeline) {
      pipeline.name = name;
      pipeline.description = description;
      if (typeof onSave === 'function') {
        onSave();
      }
    }
  };

  render () {
    const {
      visible,
      onClose
    } = this.props;
    const {
      name,
      description
    } = this.state;
    return (
      <Modal
        visible={visible}
        onCancel={onClose}
        title="Specify pipeline name"
        footer={(
          <div className={styles.modalFooter}>
            <Button
              onClick={onClose}
            >
              CANCEL
            </Button>
            <Button
              type="primary"
              disabled={!name}
              onClick={this.onSaveClicked}
            >
              SAVE
            </Button>
          </div>
        )}
      >
        <div
          className={styles.modalRow}
        >
          <span className={styles.title}>
            Name:
          </span>
          <Input
            value={name}
            onChange={this.onNameChanged}
            style={{flex: 1}}
          />
        </div>
        <div
          className={styles.modalRow}
        >
          <span className={styles.title}>
            Description:
          </span>
          <Input
            value={description}
            onChange={this.onDescriptionChanged}
            style={{flex: 1}}
          />
        </div>
      </Modal>
    );
  }
}

SavePipelineModal.propTypes = {
  pipeline: PropTypes.object,
  onClose: PropTypes.func,
  onSave: PropTypes.func,
  visible: PropTypes.bool
};

export default SavePipelineModal;
