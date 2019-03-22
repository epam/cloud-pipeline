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
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {observable} from 'mobx';
import {Row, Col, Icon, message} from 'antd';
import styles from './Graph.css';
import CodeFileCommitForm from '../../code/forms/CodeFileCommitForm';
import PipelineFilesUpdate from '../../../../../models/pipelines/PipelineFilesUpdate';
import PipelineConfigurationUpdate from '../../../../../models/pipelines/PipelineConfigurationUpdate';
import roleModel from '../../../../../utils/roleModel';

@observer
export default class Graph extends React.Component {
  static propTypes = {
    canEdit: PropTypes.bool,
    pipelineId: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ]),
    pipeline: PropTypes.object,
    routing: PropTypes.object,
    version: PropTypes.string,
    selectedTaskId: PropTypes.string,
    onSelect: PropTypes.func,
    className: PropTypes.string,
    fitAllSpace: PropTypes.bool,
    onGraphReady: PropTypes.func,
    getNodeInfo: PropTypes.func,
    onGraphUpdated: PropTypes.func,
    configurations: PropTypes.object
  };

  state = {
    fullScreen: false,
    canZoomIn: false,
    canZoomOut: false,
    modified: false,
    commitDialogVisible: false,
    configChanged: false
  };
  @observable _error = false;

  get canModifySources () {
    if (this.props.pipeline.pending) {
      return false;
    }
    return this.props.canEdit &&
      roleModel.writeAllowed(this.props.pipeline.value) &&
      this.props.version === this.props.pipeline.value.currentVersion.name;
  };

  toggleFullScreen = () => {
    this.setState({fullScreen: !this.state.fullScreen},
      () => this.onFullScreenChanged());
  };
  // Override 'onFullScreenChanged' method to perform
  // any after-fullscreen change logic.
  onFullScreenChanged () {};
  // override zoomIn method to perform 'zoom in' action
  // for specific graph visualization component
  zoomIn () {}
  // override zoomOut method to perform 'zoom out' action
  // for specific graph visualization component
  zoomOut () {}

  renderGraph () {
    return <div />;
  }

  base64Image () {
    return '';
  };

  get imageSize () {
    return {
      width: 1,
      height: 1
    };
  };

  updateData () {}

  draw () {}

  revertChanges () {

  }

  openCommitFormDialog = () => {
    const modifiedParameters = this.getModifiedParameters();
    this.setState({commitDialogVisible: true, configChanged: !!modifiedParameters});
  };

  closeCommitFormDialog = (modified) => {
    if (modified === undefined) {
      this.setState({commitDialogVisible: false, configChanged: false});
    } else {
      this.setState({commitDialogVisible: false, modified, configChanged: false});
    }
  };

  async afterSaveChanges () {}
  async afterCommit () {}
  async beforeSaveChanges () {}

  saveChanges = async (opts) => {
    await this.beforeSaveChanges();

    const commitMessage = opts.message;
    let commitId;
    if (this.props.pipeline && this.props.pipeline.value && this.props.pipeline.value.currentVersion) {
      commitId = this.props.pipeline.value.currentVersion.commitId;
    }
    const contents = this.getModifiedCode();
    const filePath = this.getFilePath();
    if (!filePath) {
      this.closeCommitFormDialog();
      return;
    }
    const items = [{
      contents: contents,
      path: filePath
    }];
    const modifiedParameters = this.getModifiedParameters();
    const doCommit = async (configuration) => {
      const request = new PipelineFilesUpdate(this.props.pipelineId);
      const hide = message.loading('Committing changes...');
      await request.send({
        comment: commitMessage,
        lastCommitId: commitId,
        items
      });
      if (request.error) {
        hide();
        message.error(request.error, 5);
      } else {
        await this.afterCommit();
        if (configuration) {
          const updateConfigRequest = new PipelineConfigurationUpdate(this.props.pipelineId);
          await updateConfigRequest.send(configuration);
          if (updateConfigRequest.error) {
            hide();
            message.error(updateConfigRequest.error, 5);
          } else {
            hide();
          }
        }
        let pipeline;
        if (this.props.onGraphUpdated) {
          pipeline = await this.props.onGraphUpdated();
        } else {
          await this.props.pipeline.fetch();
          pipeline = this.props.pipeline.value;
        }
        await this.afterSaveChanges();
        hide();
        this.props.routing.push(`${pipeline.id}/${pipeline.currentVersion.name}/graph`);
      }
    };
    if (modifiedParameters &&
      opts.updateConfig &&
      this.props.configurations &&
      !this.props.configurations.pending &&
      !this.props.configurations.error && opts.configName) {
      const [configuration] = this.props.configurations.value.filter(c => c.name.toLowerCase() === opts.configName.toLowerCase());
      if (configuration) {
        configuration.configuration.parameters = modifiedParameters;
        await doCommit(configuration);
      }
    } else {
      await doCommit();
    }
    this.closeCommitFormDialog();
  };

  getModifiedCode () {
    return null;
  }

  getFilePath () {
    return null;
  }

  getModifiedParameters () {
    return null;
  };

  renderBottomGraphControlls = () => {
    if (this._error) {
      return null;
    }
    const getIconClassName = (enabled) => {
      if (enabled) {
        return styles.graphInterfaceButtonIcon;
      }
      return styles.graphInterfaceButtonIconDisabled;
    };

    return (
      <Row type="flex" justify="end" className={styles.graphInterface}>
        <Col className={styles.graphInterfaceButton}>
          <Icon
            onClick={() => this.zoomOut()}
            className={getIconClassName(this.state.canZoomOut)} type="minus-circle-o" />
        </Col>
        <Col className={styles.graphInterfaceButton}>
          <Icon
            onClick={() => this.zoomIn()}
            className={getIconClassName(this.state.canZoomIn)} type="plus-circle-o" />
        </Col>
        <Col className={styles.graphInterfaceButton}>
          <Icon
            onClick={this.toggleFullScreen}
            className={getIconClassName(true)}
            type={this.state.fullScreen ? 'shrink' : 'arrows-alt'} />
        </Col>
      </Row>
    );
  };

  render () {
    let containerStyle = this.state.fullScreen
      ? styles.graphContainerFullScreen
      : styles.graphContainer;
    if (this.props.className) {
      containerStyle = this.props.className;
    }
    return (
      <div className={containerStyle}>
        {this.renderGraph()}
        {this.renderBottomGraphControlls()}
        <CodeFileCommitForm
          configChangedWarning={this.state.configChanged
            ? 'Input or output parameters was changed for the workflow'
            : undefined
          }
          configChanged={this.state.configChanged}
          configurations={this.props.configurations}
          visible={this.state.commitDialogVisible}
          pending={false}
          onSubmit={this.saveChanges}
          onCancel={this.closeCommitFormDialog} />
      </div>
    );
  }
  componentDidMount () {}
  componentWillUnmount () {}
}
