/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer} from 'mobx-react';
import {observable, computed} from 'mobx';
import PropTypes from 'prop-types';
import {Button, Modal, Row} from 'antd';
import localization from '../../../../utils/localization';
import CommitRunForm from './CommitRunForm';
import HiddenObjects from '../../../../utils/hidden-objects';

@localization.localizedComponent
@inject('dockerRegistries')
@HiddenObjects.injectToolsFilters
@observer
export default class CommitRunDialog extends localization.LocalizedReactComponent {
  static propTypes = {
    runId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    onCancel: PropTypes.func,
    onSubmit: PropTypes.func,
    pending: PropTypes.bool,
    visible: PropTypes.bool,
    defaultDockerImage: PropTypes.string
  };

  @observable _commitRunForm = null;

  @computed
  get toolValid () {
    if (this._commitRunForm) {
      return this._commitRunForm.toolValid;
    }
    return false;
  }

  @computed
  get layersCheckPassed () {
    if (this._commitRunForm) {
      return this._commitRunForm.layersCheckPassed;
    }
    return false;
  }

  handleSubmit = async (e) => {
    e.preventDefault();
    if (this._commitRunForm) {
      const result = await this._commitRunForm.validate();
      if (result) {
        this.props.onSubmit && this.props.onSubmit(result);
      }
    }
  };

  @computed
  get registries () {
    if (!this.props.dockerRegistries.loaded) {
      return [];
    }
    return this.props.hiddenToolsTreeFilter(this.props.dockerRegistries.value)
      .registries;
  }

  onInitialize = (component) => {
    this._commitRunForm = component;
  };

  render () {
    const modalFooter = this.props.pending ? false : (
      <Row type="flex" justify="space-between">
        <Button
          id="commit-pipeline-run-form-cancel-button"
          onClick={this.props.onCancel}>CANCEL</Button>
        { this.registries.length && <Button
          id="commit-pipeline-run-form-commit-button"
          type="primary" htmlType="submit"
          disabled={!this.toolValid || !this.layersCheckPassed}
          onClick={this.handleSubmit}>COMMIT</Button> }
      </Row>
    );
    const onClose = () => {
      this._commitRunForm && this._commitRunForm.reset();
    };

    return (
      <Modal
        width="50%"
        maskClosable={!this.props.pending}
        afterClose={() => onClose()}
        closable={!this.props.pending}
        visible={this.props.visible}
        title={`Commit ${this.localizedString('pipeline')} run`}
        onCancel={this.props.onCancel}
        footer={modalFooter}
      >
        <CommitRunForm
          runId={this.props.runId}
          onInitialized={this.onInitialize}
          onPressEnter={this.handleSubmit}
          visible={this.props.visible}
          defaultDockerImage={this.props.defaultDockerImage}
          pending={this.props.pending}
        />
      </Modal>
    );
  }
}
