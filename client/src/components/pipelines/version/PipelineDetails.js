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
import {Alert, Menu, message, Row, Dropdown, Button, Icon, Col} from 'antd';
import {graphIsSupportedForLanguage} from './graph/visualization';
import pipelines from '../../../models/pipelines/Pipelines';
import pipelinesLibrary from '../../../models/folders/FolderLoadTree';
import folders from '../../../models/folders/Folders';
import UpdatePipeline from '../../../models/pipelines/UpdatePipeline';
import DeletePipeline from '../../../models/pipelines/DeletePipeline';
import connect from '../../../utils/connect';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';
import AdaptedLink from '../../special/AdaptedLink';
import EditPipelineForm from './forms/EditPipelineForm';
import LoadingView from '../../special/LoadingView';
import Breadcrumbs from '../../special/Breadcrumbs';
import GitRepositoryControl from '../../special/git-repository-control';
import styles from './PipelineDetails.css';
import browserStyles from '../browser/Browser.css';
import {ItemTypes} from '../model/treeStructureFunctions';

@connect({
  pipelines,
  pipelinesLibrary,
  folders
})
@localization.localizedComponent
@inject(({pipelines, pipelinesLibrary, routing, folders}, {onReloadTree, params}) => ({
  onReloadTree,
  pipelines,
  pipeline: pipelines.getPipeline(params.id),
  language: pipelines.getLanguage(params.id, params.version),
  configurations: pipelines.getConfiguration(params.id, params.version),
  currentConfiguration: params.configuration,
  version: params.version,
  pipelineId: params.id,
  folders,
  pipelinesLibrary
}))
@observer
export default class PipelineDetails extends localization.LocalizedReactComponent {

  state = {isModalVisible: false, updating: false, deleting: false};

  @observable _graphIsSupported = null;

  toggleModal = () => {
    this.setState({isModalVisible: !this.state.isModalVisible}, () => {
      if (!this.state.isModalVisible) {
        this.props.pipeline.fetch();
      }
    });
  };

  updatePipeline = (values) => {
    this.setState({updating: true}, async () => {
      const updatePipeline = new UpdatePipeline();
      await updatePipeline.send(
        {
          id: this.props.pipelineId,
          name: values.name,
          description: values.description,
          parentFolderId: this.props.pipeline.value.parentFolderId
        }
      );
      if (updatePipeline.error) {
        message.error(`Error updating ${this.localizedString('pipeline')}: ${updatePipeline.error}`);
        this.setState({updating: false});
      } else {
        this.setState({updating: false, isModalVisible: false}, () => {
          this.props.pipeline.fetch();
          if (this.props.onReloadTree) {
            this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
          }
        });
      }
    });
  };

  renamePipeline = async (name) => {
    const hide = message.loading(`Renaming ${this.localizedString('pipeline')} ${name}...`, -1);
    const updatePipeline = new UpdatePipeline();
    await updatePipeline.send({
      id: this.props.pipeline.value.id,
      name: name,
      description: this.props.pipeline.value.description,
      parentFolderId: this.props.pipeline.value.parentFolderId
    });
    if (updatePipeline.error) {
      hide();
      message.error(updatePipeline.error, 5);
    } else {
      hide();
      this.setState({
        isModalVisible: false
      });
      await this.props.pipeline.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
    }
  };

  deletePipeline = (keepRepository) => {
    this.setState({deleting: true}, async () => {
      const deletePipeline = new DeletePipeline(this.props.pipelineId, keepRepository);
      await deletePipeline.fetch();
      if (deletePipeline.error) {
        message.error(`Error deleting ${this.localizedString('pipeline')}: ${deletePipeline.error}`);
        this.setState({deleting: false});
      } else {
        const parentFolderId = this.props.pipeline.value.parentFolderId;
        if (parentFolderId) {
          this.props.folders.invalidateFolder(parentFolderId);
        } else {
          this.props.pipelinesLibrary.invalidateCache();
        }
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!parentFolderId);
        }
        if (parentFolderId) {
          this.props.router.push(`/folder/${parentFolderId}`);
        } else {
          this.props.router.push('/library');
        }
      }
    });
  };

  @computed
  get configurations () {
    if (this.props.configurations.loaded) {
      return (this.props.configurations.value || []).map(c => c).sort((cA, cB) => {
        if (cA.name > cB.name) {
          return 1;
        } else if (cA.name < cB.name) {
          return -1;
        }
        return 0;
      });
    }
    return [];
  }

  runPipeline = () => {
    if (this.props.currentConfiguration) {
      this.props.router.push(`/launch/${this.props.pipelineId}/${this.props.version}/${this.props.currentConfiguration}`);
    } else {
      this.props.router.push(`/launch/${this.props.pipelineId}/${this.props.version}/default`);
    }
  };

  runPipelineConfiguration = (configuration) => {
    if (configuration) {
      this.props.router.push(`/launch/${this.props.pipelineId}/${this.props.version}/${configuration}`);
    } else {
      this.props.router.push(`/launch/${this.props.pipelineId}/${this.props.version}/default`);
    }
  };

  renderRunButton = () => {
    const configurations = this.configurations;
    if (configurations.length > 1) {
      const onSelectConfiguration = ({key}) => {
        this.runPipelineConfiguration(key);
      };
      const configurationsMenu = (
        <Menu onClick={onSelectConfiguration}>
          {
            configurations.map(c => {
              return (
                <Menu.Item key={c.name}>{c.name}</Menu.Item>
              );
            })
          }
        </Menu>
      );
      return (
        <Button.Group style={{display: 'inline-flex'}}>
          <Button
            id="launch-pipeline-button"
            size="small"
            type="primary"
            style={{lineHeight: 1}}
            onClick={() => this.runPipeline()}>
            RUN
          </Button>
          <Dropdown overlay={configurationsMenu} placement="bottomRight">
            <Button size="small" id="run-dropdown-button" type="primary">
              <Icon type="down" style={{lineHeight: 'inherit', verticalAlign: 'middle'}}/>
            </Button>
          </Dropdown>
        </Button.Group>
      );
    } else {
      return (
        <Button
          id="launch-pipeline-button"
          size="small"
          type="primary"
          style={{lineHeight: 1}}
          onClick={() => this.runPipeline()}>
          RUN
        </Button>
      );
    }
  };

  render () {
    if (!this.props.pipeline.loaded && this.props.pipeline.pending) {
      return <LoadingView />;
    }
    if (this.props.pipeline.error) {
      return <Alert type="error" message={this.props.pipeline.error} />;
    }

    const {id, description} = this.props.pipeline.value;
    const {version} = this.props.params;

    const {router: {location}} = this.props;
    const activeTab = this.props.router.location.pathname.split('/').slice(3)[0];

    let displayGraph = false;
    if (!this.props.language.pending) {
      displayGraph = graphIsSupportedForLanguage(this.props.language.value);
    } else if (this._graphIsSupported !== null) {
      displayGraph = this._graphIsSupported;
    }

    return (
      <div
        className={styles.fullHeightContainer}>
        <Row type="flex" justify="space-between" align="middle" style={{minHeight: 41}}>
          <Col className={browserStyles.itemHeader}>
            <Breadcrumbs
              id={parseInt(this.props.pipelineId)}
              type={ItemTypes.pipeline}
              readOnlyEditableField={!roleModel.writeAllowed(this.props.pipeline.value)}
              onSaveEditableField={this.renamePipeline}
              editStyleEditableField={{flex: 1}}
              displayTextEditableField={`${this.props.pipeline.value.name} (${this.props.version})`}
              textEditableField={this.props.pipeline.value.name}
              icon="tag"
              iconClassName={browserStyles.editableControl}
              lock={this.props.pipeline.value.locked}
              lockClassName={browserStyles.editableControl}
              subject={this.props.pipeline.value}
            />
          </Col>
          <Col className={styles.actionButtons}>
            {
              roleModel.executeAllowed(this.props.pipeline.value) &&
              this.renderRunButton()
            }
            <Button
              id="edit-pipeline-button"
              onClick={this.toggleModal}
              style={{lineHeight: 1}}
              size="small">
              <Icon type="setting" />
            </Button>
            <GitRepositoryControl
              overlayClassName={browserStyles.gitRepositoryPopover}
              https={this.props.pipeline.value.repository}
              ssh={this.props.pipeline.value.repositorySsh} />
          </Col>
        </Row>
        <Row>
          {description}
        </Row>
        <Row gutter={16} type="flex" justify="center" className={`${styles.rowMenu} ${styles[activeTab] || ''}`}>
          <Menu mode="horizontal" selectedKeys={[activeTab]} className={styles.tabsMenu}>
            <Menu.Item key="documents">
              <AdaptedLink
                to={`/${id}/${version}/documents`}
                location={location}>
                Documents
              </AdaptedLink>
            </Menu.Item>
            <Menu.Item key="code">
              <AdaptedLink
                to={`/${id}/${version}/code`}
                location={location}>
                Code
              </AdaptedLink>
            </Menu.Item>
            <Menu.Item key="configuration">
              <AdaptedLink
                to={`/${id}/${version}/configuration`}
                location={location}>
                Configuration
              </AdaptedLink>
            </Menu.Item>
            {
              displayGraph &&
              <Menu.Item key="graph">
                <AdaptedLink
                  to={`/${id}/${version}/graph`}
                  location={location}>
                  Graph
                </AdaptedLink>
              </Menu.Item>
            }
            <Menu.Item key="history">
              <AdaptedLink
                to={`/${id}/${version}/history`}
                location={location}>
                History
              </AdaptedLink>
            </Menu.Item>
            <Menu.Item key="storage">
              <AdaptedLink
                to={`/${id}/${version}/storage`}
                location={location}>
                Storage rules
              </AdaptedLink>
            </Menu.Item>
          </Menu>
        </Row>
        <div
          className={styles.fullHeightContainer} style={{overflow: 'auto'}}>
          {
            React.Children.map(
              this.props.children,
              (child) => React.cloneElement(child, {onReloadTree: this.props.onReloadTree})
            )
          }
        </div>
        <EditPipelineForm
          onSubmit={this.updatePipeline}
          onCancel={this.toggleModal}
          onDelete={this.deletePipeline}
          visible={this.state.isModalVisible}
          pending={this.state.updating || this.state.deleting}
          pipeline={this.props.pipeline.value} />
      </div>
    );
  }

  componentDidUpdate () {
    if (!this.props.language.pending) {
      const graphIsSupported = graphIsSupportedForLanguage(this.props.language.value);
      if (graphIsSupported !== this._graphIsSupported) {
        this._graphIsSupported = graphIsSupported;
      }
    }
  }
}
