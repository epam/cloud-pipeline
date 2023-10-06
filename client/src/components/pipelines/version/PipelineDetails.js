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
import {computed} from 'mobx';
import classNames from 'classnames';
import {Alert, Menu as TabMenu, message, Row, Button, Icon, Col} from 'antd';
import Menu, {MenuItem} from 'rc-menu';
import Dropdown from 'rc-dropdown';
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
import HiddenObjects from '../../../utils/hidden-objects';

@connect({
  pipelines,
  pipelinesLibrary,
  folders
})
@localization.localizedComponent
@HiddenObjects.checkPipelines(props => props?.params?.id)
@HiddenObjects.checkPipelineVersions(props => props?.params?.id, props => props?.params?.version)
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

  @computed
  get repositoryType () {
    const {pipeline} = this.props;
    if (pipeline && pipeline.loaded) {
      const {repositoryType} = pipeline.value || {};
      return repositoryType;
    }
    return undefined;
  }

  @computed
  get codePath () {
    const {pipeline} = this.props;
    if (pipeline && pipeline.loaded) {
      const {codePath} = pipeline.value || {};
      if (codePath && codePath.length) {
        return codePath;
      }
    }
    return undefined;
  }

  @computed
  get docsPath () {
    const {pipeline} = this.props;
    if (pipeline && pipeline.loaded) {
      const {docsPath} = pipeline.value || {};
      if (docsPath && docsPath.length) {
        return docsPath;
      }
    }
    return undefined;
  }

  @computed
  get displayGraph () {
    const {language} = this.props;
    if (language && language.loaded) {
      return graphIsSupportedForLanguage(language.value);
    }
    return false;
  }

  @computed
  get isCWLPipeline () {
    const {language} = this.props;
    if (language && language.loaded) {
      return /^cwl$/i.test(language.value);
    }
    return false;
  }

  get activeTabPath () {
    const {
      router: {location}
    } = this.props;
    const [,, activeTab] = location.pathname.split('/').filter(o => o.length);
    return activeTab ? activeTab.toLowerCase() : undefined;
  }

  @computed
  get tabs () {
    const {
      pipelineId: id,
      version
    } = this.props;
    const workflow = this.displayGraph && this.isCWLPipeline ? {
      key: 'workflow',
      title: 'Workflow',
      link: `/${id}/${version}/workflow`
    } : false;
    const documents = {
      key: 'documents',
      title: 'Documents',
      link: `/${id}/${version}/documents`
    };
    const code = {
      key: 'code',
      title: 'Code',
      link: `/${id}/${version}/code`
    };
    const configuration = {
      key: 'configuration',
      title: 'Configuration',
      link: `/${id}/${version}/configuration`
    };
    const graph = this.displayGraph && !this.isCWLPipeline ? {
      key: 'graph',
      title: 'Graph',
      link: `/${id}/${version}/graph`
    } : false;
    const history = {
      key: 'history',
      title: 'History',
      link: `/${id}/${version}/history`
    };
    const storage = {
      key: 'storage',
      title: 'Storage rules',
      link: `/${id}/${version}/storage`
    };
    switch (this.repositoryType) {
      case 'BITBUCKET':
        return [
          this.codePath ? code : false,
          configuration,
          history,
          storage
        ].filter(Boolean);
      default:
        return [
          workflow,
          this.docsPath ? documents : false,
          this.codePath ? code : false,
          configuration,
          graph,
          history,
          storage
        ].filter(Boolean);
    }
  }

  componentDidMount () {
    this.redirectIfRequired();
  }

  componentDidUpdate () {
    this.redirectIfRequired();
  }

  redirectIfRequired = () => {
    const {
      pipeline,
      language
    } = this.props;
    if (
      pipeline &&
      pipeline.loaded
    ) {
      const {id, pipelineType} = pipeline.value;
      if (/^versioned_storage$/i.test(pipelineType)) {
        this.props.router && this.props.router.push(`/vs/${id}`);
        return;
      }
      if (
        language &&
        (language.loaded || (language.error && !language.pending))
      ) {
        const currentTab = this.tabs.find(o => o.key === this.activeTabPath);
        const [first] = this.tabs;
        if (!currentTab && first) {
          this.props.router && this.props.router.push(first.link);
        }
      }
    }
  };

  toggleModal = () => {
    this.setState({isModalVisible: !this.state.isModalVisible}, () => {
      if (!this.state.isModalVisible) {
        this.props.pipeline.fetch();
      }
    });
  };

  reload = async () => {
    const {
      parentFolderId
    } = this.props.pipeline.value || {};
    await this.props.pipeline.fetch();
    if (this.props.onReloadTree) {
      if (parentFolderId) {
        this.props.folders.invalidateFolder(parentFolderId);
      } else {
        this.props.pipelinesLibrary.invalidateCache();
      }
      this.props.onReloadTree(
        !parentFolderId,
        parentFolderId
      );
    }
  };

  updatePipeline = (values) => {
    this.setState({updating: true}, async () => {
      const updatePipeline = new UpdatePipeline();
      await updatePipeline.send(
        {
          id: this.props.pipelineId,
          name: values.name,
          description: values.description,
          parentFolderId: this.props.pipeline.value.parentFolderId,
          branch: values.branch,
          configurationPath: values.configurationPath,
          visibility: values.visibility,
          codePath: values.codePath,
          docsPath: values.docsPath
        }
      );
      if (updatePipeline.error) {
        // eslint-disable-next-line
        message.error(`Error updating ${this.localizedString('pipeline')}: ${updatePipeline.error}`);
        this.setState({updating: false});
      } else {
        this.setState({updating: false, isModalVisible: false}, () => this.reload());
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
      await this.reload();
    }
  };

  deletePipeline = (keepRepository) => {
    this.setState({deleting: true}, async () => {
      const deletePipeline = new DeletePipeline(this.props.pipelineId, keepRepository);
      await deletePipeline.fetch();
      if (deletePipeline.error) {
        // eslint-disable-next-line
        message.error(`Error deleting ${this.localizedString('pipeline')}: ${deletePipeline.error}`);
        this.setState({deleting: false});
      } else {
        const {parentFolderId} = this.props.pipeline.value;
        if (parentFolderId) {
          this.props.folders.invalidateFolder(parentFolderId);
        } else {
          this.props.pipelinesLibrary.invalidateCache();
        }
        if (this.props.onReloadTree) {
          this.props.onReloadTree(
            !parentFolderId,
            parentFolderId
          );
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
    const baseUrl = `/launch/${this.props.pipelineId}/${this.props.version}`;
    this.props.router.push(`${baseUrl}/${this.props.currentConfiguration || 'default'}`);
  };

  runPipelineConfiguration = (configuration) => {
    const baseUrl = `/launch/${this.props.pipelineId}/${this.props.version}`;
    this.props.router.push(`${baseUrl}/${configuration || 'default'}`);
  };

  renderRunButton = () => {
    const configurations = this.configurations;
    if (configurations.length > 1) {
      const onSelectConfiguration = ({key}) => {
        this.runPipelineConfiguration(key);
      };
      const configurationsMenu = (
        <Menu
          onClick={onSelectConfiguration}
          style={{cursor: 'pointer'}}
          selectedKeys={[]}
        >
          {
            configurations.map(c => {
              return (
                <MenuItem key={c.name}>{c.name}</MenuItem>
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
              <Icon type="down" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
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
    const {
      pipeline,
      language
    } = this.props;
    if (
      (!pipeline.loaded && pipeline.pending) ||
      (!language.loaded && language.pending)
    ) {
      return <LoadingView />;
    }
    if (pipeline.error) {
      return <Alert type="error" message={this.props.pipeline.error} />;
    }

    const {description, pipelineType} = this.props.pipeline.value;
    if (/^versioned_storage$/i.test(pipelineType)) {
      return (
        <LoadingView />
      );
    }

    const {router: {location}} = this.props;
    const activeTab = this.activeTabPath;

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
              ssh={this.props.pipeline.value.repositorySsh}
              repositoryType={this.props.pipeline.value.repositoryType}
            />
          </Col>
        </Row>
        <Row>
          {description}
        </Row>
        <Row
          gutter={16}
          type="flex"
          justify="center"
          className={
            classNames(
              styles.rowMenu,
              styles[activeTab]
            )
          }
        >
          <TabMenu
            mode="horizontal"
            selectedKeys={[activeTab]}
            className={styles.tabsMenu}
          >
            {
              this.tabs.map((tab) => (
                <TabMenu.Item key={tab.key}>
                  <AdaptedLink
                    to={tab.link}
                    location={location}>
                    {tab.title}
                  </AdaptedLink>
                </TabMenu.Item>
              ))
            }
          </TabMenu>
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
}
