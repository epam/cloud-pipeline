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
import {inject, observer} from 'mobx-react';
import connect from '../../../utils/connect';
import {computed} from 'mobx';
import LoadingView from '../../special/LoadingView';
import Issues from '../../special/issues/Issues';
import Metadata from '../../special/metadata/Metadata';
import {
  ContentIssuesMetadataPanel,
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY,
  ISSUES_PANEL_KEY
} from '../../special/splitPanel/SplitPanel';
import Breadcrumbs from '../../special/Breadcrumbs';
import GitRepositoryControl from '../../special/git-repository-control';
import {Alert, Button, Col, Dropdown, Icon, Menu, message, Row, Select, Table} from 'antd';
import EditPipelineForm from '../version/forms/EditPipelineForm';
import PipelineConfigurations from '../../../models/pipelines/PipelineConfigurations';
import folders from '../../../models/folders/Folders';
import pipelines from '../../../models/pipelines/Pipelines';
import pipelinesLibrary from '../../../models/folders/FolderLoadTree';
import DeletePipeline from '../../../models/pipelines/DeletePipeline';
import UpdatePipeline from '../../../models/pipelines/UpdatePipeline';
import UpdatePipelineToken from '../../../models/pipelines/UpdatePipelineToken';
import RegisterVersion from '../../../models/pipelines/RegisterVersion';
import displayDate from '../../../utils/displayDate';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';
import {generateTreeData, ItemTypes} from '../model/treeStructureFunctions';
import RegisterVersionFormDialog from './forms/RegisterVersionFormDialog';
import styles from './Browser.css';

@connect({
  pipelinesLibrary,
  folders,
  pipelines
})
@localization.localizedComponent
@inject(({pipelines, folders, pipelinesLibrary}, params) => {
  let componentParameters = params;
  if (params.params) {
    componentParameters = params.params;
  }
  return {
    versions: pipelines.versionsForPipeline(componentParameters.id),
    pipelineId: componentParameters.id,
    pipeline: pipelines.getPipeline(componentParameters.id),
    pipelines,
    pipelinesLibrary,
    folders
  };
})
@observer
export default class Pipeline extends localization.LocalizedReactComponent {

  _versions = null;

  static propTypes = {
    id: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ]),
    listingMode: PropTypes.bool,
    readOnly: PropTypes.bool,
    onSelectItem: PropTypes.func,
    onReloadTree: PropTypes.func,
    selectedVersion: PropTypes.string,
    selectedConfiguration: PropTypes.string,
    configurationSelectionMode: PropTypes.bool
  };

  state = {
    editPipeline: false,
    releaseCandidate: null,
    configurations: undefined,
    showIssuesPanel: false
  };

  @computed
  get showMetadata () {
    if (this.props.listingMode) {
      return false;
    }
    if (this.state.metadata === undefined && this.props.pipeline.loaded) {
      return this.props.pipeline.value.hasMetadata && roleModel.readAllowed(this.props.pipeline.value);
    }
    return !!this.state.metadata;
  }

  columns = [
    {
      key: 'type',
      className: styles.treeItemType,
      render: (item) => this.renderTreeItemType(item),
      onCellClick: (item) => this.navigate(item)
    },
    {
      dataIndex: 'name',
      key: 'name',
      title: 'Name',
      className: styles.treeItemName,
      render: this.renderTreeItemText,
      onCellClick: (item) => this.navigate(item)
    },
    {
      dataIndex: 'description',
      key: 'description',
      className: styles.treeItemName,
      render: this.renderTreeItemText,
      onCellClick: (item) => this.navigate(item)
    },
    {
      dataIndex: 'createdDate',
      key: 'createdDate',
      className: styles.treeItemName,
      render: (text, item) => this.renderTreeItemText(`Last updated: ${displayDate(text)}`, item),
      onCellClick: (item) => this.navigate(item)
    },
    {
      key: 'actions',
      className: styles.treeItemActions,
      render: (item) => this.renderTreeItemActions(item),
      onCellClick: (item) => this.navigate(item)
    }
  ];

  listingColumns = [
    {
      key: 'selection',
      className: styles.treeItemSelection,
      render: (item) => this.renderTreeItemSelection(item),
      onCellClick: (item) => this.navigate(item)
    },
    {
      key: 'type',
      className: styles.treeItemType,
      render: (item) => this.renderTreeItemType(item),
      onCellClick: (item) => this.navigate(item)
    },
    {
      dataIndex: 'name',
      key: 'name',
      title: 'Name',
      className: styles.treeItemName,
      render: this.renderTreeItemText,
      onCellClick: (item) => this.navigate(item)
    },
    {
      dataIndex: 'description',
      key: 'description',
      className: styles.treeItemName,
      render: this.renderTreeItemText,
      onCellClick: (item) => this.navigate(item)
    },
    {
      dataIndex: 'createdDate',
      key: 'createdDate',
      className: styles.treeItemName,
      render: (text, item) => this.renderTreeItemText(`Last updated: ${displayDate(text)}`, item),
      onCellClick: (item) => this.navigate(item)
    },
    {
      key: 'actions',
      className: styles.treeItemActions,
      render: (item) => this.renderTreeItemActions(item)
    }
  ];

  renderTreeItemType = (item) => {
    const style = {};
    if (item.draft) {
      style.color = '#999';
    }
    switch (item.type) {
      case ItemTypes.pipeline: return <Icon type="fork" style={style} />;
      case ItemTypes.folder: return <Icon type="folder" style={style} />;
      case ItemTypes.version: return <Icon type="tag" style={style} />;
      default: return <div />;
    }
  };

  renderTreeItemText = (text, item) => {
    const style = {};
    if (item.draft) {
      style.color = '#999';
    }
    return <span style={style}>{text}</span>;
  };

  rowClassName = (baseClassName, item) => {
    if (item.draft) {
      return `${baseClassName}-draft`;
    } else {
      return baseClassName;
    }
  };

  runPipeline = (event, version) => {
    event.stopPropagation();
    this.props.router.push(`/launch/${this.props.pipelineId}/${version}/default`);
  };

  renderTreeItemSelection = (item) => {
    if ((this.props.listingMode || this.props.readOnly) && item.name === this.props.selectedVersion) {
      return (
        <Row type="flex" justify="end">
          <Icon type="check-circle" />
        </Row>
      );
    }
    return undefined;
  };

  onSelectConfiguration = (item) => (configuration) => {
    const configurations = this.state.configurations;
    if (configurations[item.id]) {
      configurations[item.id].selected = configuration;
      this.setState({configurations}, () => {
        this.navigate(item);
      });
    }
  };

  renderTreeItemActions = (item) => {
    if (this.props.listingMode || this.props.readOnly) {
      if (this.props.configurationSelectionMode) {
        if (!this.state.configurations || !this.state.configurations[item.id]) {
          return undefined;
        } else {
          const configurations = this.state.configurations[item.id].list;
          return (
            <Select
              style={{width: '100%'}}
              value={this.state.configurations[item.id].selected}
              onSelect={this.onSelectConfiguration(item)}>
              {
                configurations.map(c => {
                  return (
                    <Select.Option key={c.name}>{c.name}</Select.Option>
                  );
                })
              }
            </Select>
          );
        }
      }
      return undefined;
    }
    switch (item.type) {
      case ItemTypes.version: {
        if (item.draft) {
          return (
            <Row type="flex" justify="end">
              {
                roleModel.writeAllowed(this.props.pipeline.value) &&
                  roleModel.manager.pipeline(
                    <Button
                      id={`folder-item-${item.key}-release-button`}
                      size="small"
                      onClick={(event) => this.openRegisterVersionDialog(item, event)}>
                      RELEASE
                    </Button>
                  )
              }
              {
                roleModel.executeAllowed(this.props.pipeline.value) &&
                <Button
                  id={`folder-item-${item.key}-run-button`}
                  size="small"
                  type="primary" onClick={(event) => this.runPipeline(event, item.name)}>
                  RUN
                </Button>
              }
            </Row>
          );
        } else {
          return (
            <Row type="flex" justify="end">
              {
                roleModel.executeAllowed(this.props.pipeline.value) &&
                <Button
                  id={`folder-item-${item.key}-run-button`}
                  size="small"
                  type="primary" onClick={(event) => this.runPipeline(event, item.name)}>
                  RUN
                </Button>
              }
            </Row>
          );
        }
      }
      default: return <div />;
    }
  };

  navigate = (item) => {
    if (this.props.onSelectItem) {
      if (this.props.configurationSelectionMode && this.state.configurations && this.state.configurations[item.id]) {
        this.props.onSelectItem(item, this.state.configurations[item.id].selected);
      } else {
        this.props.onSelectItem(item);
      }
    } else {
      switch (item.type) {
        case ItemTypes.folder:
          this.props.router.push(`/folder/${item.id}`);
          break;
        case ItemTypes.pipeline:
          this.props.router.push(`/${item.id}`);
          break;
        case ItemTypes.version:
          this.props.router.push(`/${this.props.pipelineId}/${item.name}`);
          break;
      }
    }
  };

  openEditPipelineDialog = () => {
    this.setState({editPipeline: true});
  };

  closeEditPipelineDialog = () => {
    this.setState({editPipeline: false});
  };

  updatePipelineRequest = new UpdatePipeline();
  updatePipelineTokenRequest = new UpdatePipelineToken();

  editPipeline = async ({name, description, token}) => {
    const hide = message.loading(`Updating ${this.localizedString('pipeline')} ${name}...`, -1);
    await this.updatePipelineRequest.send({
      id: this.props.pipeline.value.id,
      name: name,
      description: description,
      parentFolderId: this.props.pipeline.value.parentFolderId
    });
    if (this.updatePipelineRequest.error) {
      hide();
      message.error(this.updatePipelineRequest.error, 5);
    } else {
      if (token !== undefined) {
        this.updatePipelineTokenRequest = new UpdatePipelineToken();
        await this.updatePipelineTokenRequest.send({
          id: this.props.pipeline.value.id,
          repositoryToken: token
        });
        hide();
        if (this.updatePipelineTokenRequest.error) {
          message.error(this.updatePipelineTokenRequest.error, 5);
        } else {
          this.closeEditPipelineDialog();
          this.props.pipeline.fetch();
          if (this.props.onReloadTree) {
            this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
          }
        }
      } else {
        hide();
        this.closeEditPipelineDialog();
        this.props.pipeline.fetch();
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
        }
      }
    }
  };

  renamePipeline = async (name) => {
    const hide = message.loading(`Renaming ${this.localizedString('pipeline')} ${name}...`, -1);
    await this.updatePipelineRequest.send({
      id: this.props.pipeline.value.id,
      name: name,
      description: this.props.pipeline.value.description,
      parentFolderId: this.props.pipeline.value.parentFolderId
    });
    if (this.updatePipelineRequest.error) {
      hide();
      message.error(this.updatePipelineRequest.error, 5);
    } else {
      hide();
      this.closeEditPipelineDialog();
      const parentFolderId = this.props.pipeline.value.parentFolderId;
      if (parentFolderId) {
        this.props.folders.invalidateFolder(parentFolderId);
      } else {
        this.props.pipelinesLibrary.invalidateCache();
      }
      await this.props.pipeline.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
    }
  };

  deletePipeline = async (keepRepository) => {
    const request = new DeletePipeline(this.props.pipeline.value.id, keepRepository);
    const hide = message.loading(`Deleting ${this.localizedString('pipeline')} ${this.props.pipeline.value.name}...`, 0);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeEditPipelineDialog();
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
  };

  openRegisterVersionDialog = (releaseCandidate, event) => {
    event.stopPropagation();
    this.setState({releaseCandidate: releaseCandidate});
  };

  closeRegisterVersionDialog = () => {
    this.setState({releaseCandidate: null});
  };

  registerVersion = async ({version, description}) => {
    const params = {
      pipelineId: this.props.pipeline.value.id,
      commit: this.state.releaseCandidate.id,
      versionName: version,
      message: description
    };
    const request = new RegisterVersion(params);
    const hide = message.loading(`Registering version ${version}...`, 0);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeRegisterVersionDialog();
      await this.props.pipeline.fetch();
      await this.props.versions.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this.props.pipeline.value.parentFolderId);
      }
    }
  };

  openIssuesPanel = () => {
    this.setState({
      showIssuesPanel: true
    });
  };

  closeIssuesPanel = () => {
    this.setState({
      showIssuesPanel: false
    });
  };

  renderActions = () => {
    const onSelectDisplayOption = ({key}) => {
      switch (key) {
        case 'metadata': this.setState({metadata: !this.showMetadata}); break;
        case 'issues':
          if (this.state.showIssuesPanel) {
            this.closeIssuesPanel();
          } else {
            this.openIssuesPanel();
          }
          break;
      }
    };
    const displayOptionsMenuItems = [];
    if (!this.props.listingMode) {
      displayOptionsMenuItems.push(
        <Menu.Item
          id={this.showMetadata ? 'hide-metadata-button' : 'show-metadata-button'}
          key="metadata">
          <Row type="flex" justify="space-between" align="middle">
            <span>Attributes</span>
            <Icon type="check-circle" style={{display: this.showMetadata ? 'inherit' : 'none'}} />
          </Row>
        </Menu.Item>
      );
      displayOptionsMenuItems.push(
        <Menu.Item
          id={this.state.showIssuesPanel ? 'hide-issues-panel-button' : 'show-issues-panel-button'}
          key="issues">
          <Row type="flex" justify="space-between" align="middle">
            <span>{this.localizedString('Issue')}s</span>
            <Icon type="check-circle" style={{display: this.state.showIssuesPanel ? 'inherit' : 'none'}} />
          </Row>
        </Menu.Item>
      );
    }
    if (displayOptionsMenuItems.length > 0) {
      const displayOptionsMenu = (
        <Menu onClick={onSelectDisplayOption} style={{width: 125}}>
          {displayOptionsMenuItems}
        </Menu>
      );
      return (
        <Dropdown
          key="display attributes"
          overlay={displayOptionsMenu}>
          <Button
            id="display-attributes"
            style={{lineHeight: 1}}
            size="small">
            <Icon type="appstore" />
          </Button>
        </Dropdown>
      );
    }
    return undefined;
  };

  render () {
    let versionsContent;
    if (this.props.versions.loaded) {
      this._versions = generateTreeData({versions: this.props.versions.value}, true);
      versionsContent = (
        <Table
          key={CONTENT_PANEL_KEY}
          className={styles.childrenContainer}
          dataSource={this._versions}
          columns={this.props.listingMode ? this.listingColumns : this.columns}
          rowKey="key"
          title={null}
          showHeader={false}
          rowClassName={(item) => `folder-item-${item.key}`}
          expandedRowRender={null}
          loading={this.props.versions.pending}
          pagination={{pageSize: 40}}
          size="small" />
      );
    } else if (this.props.versions.error) {
      this._versions = [];
      versionsContent = (
        <Alert key={CONTENT_PANEL_KEY} type="error" message={this.props.versions.error} />
      );
    }
    if (!this._versions || (!this.props.versions.loaded && !this.props.pipeline.pending && this.props.versions.pending)) {
      return <LoadingView />;
    }
    if (this.props.pipeline.error) {
      return <Alert message={this.props.pipeline.error} type="error" />;
    }

    const pipelineTitleClassName = this.props.pipeline.value.locked ? styles.readonly : undefined;

    const onPanelClose = (key) => {
      switch (key) {
        case METADATA_PANEL_KEY:
          this.setState({metadata: false});
          break;
        case ISSUES_PANEL_KEY:
          this.closeIssuesPanel();
          break;
      }
    };

    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <div>
          <Row type="flex" justify="space-between" align="middle" style={{minHeight: 41}}>
            <Col className={styles.itemHeader}>
                    <Icon type="fork" className={`${styles.editableControl} ${pipelineTitleClassName}`} />
                    {
                      this.props.pipeline.value.locked &&
                      <Icon
                        className={`${styles.editableControl} ${pipelineTitleClassName}`}
                        type="lock" />
                    }
                    <Breadcrumbs
                      id={parseInt(this.props.pipelineId)}
                      type={ItemTypes.pipeline}
                      readOnlyEditableField={!roleModel.writeAllowed(this.props.pipeline.value) || this.props.readOnly}
                      textEditableField={this.props.pipeline.value.name}
                      onSaveEditableField={this.renamePipeline}
                      editStyleEditableField={{flex: 1}} />
            </Col>
            <Col className={styles.currentFolderActions}>
                  {
                    this.renderActions()
                  }
                  {
                    !this.props.readOnly &&
                    <Button
                      id="edit-pipeline-button"
                      onClick={this.openEditPipelineDialog}
                      style={{lineHeight: 1}}
                      size="small">
                      <Icon type="setting" />
                    </Button>
                  }
                  {
                    !this.props.listingMode
                      ? (
                        <GitRepositoryControl
                          overlayClassName={styles.gitRepositoryPopover}
                          https={this.props.pipeline.value.repository}
                          ssh={this.props.pipeline.value.repositorySsh} />
                        ) : undefined
                  }
            </Col>
          </Row>
          <Row type="flex">
            {this.props.pipeline.value.description}
          </Row>
        </div>
        <ContentIssuesMetadataPanel
          style={{flex: 1, overflow: 'auto'}}
          onPanelClose={onPanelClose}>
          {versionsContent}
          {
            this.state.showIssuesPanel &&
            <Issues
              key={ISSUES_PANEL_KEY}
              onCloseIssuePanel={this.closeIssuesPanel}
              entityId={this.props.pipelineId}
              entityClass="PIPELINE"
              entity={this.props.pipeline.value} />
          }
          {
            this.showMetadata &&
            <Metadata
              key={METADATA_PANEL_KEY}
              readOnly={!roleModel.isOwner(this.props.pipeline.value)}
              entityName={this.props.pipeline.value.name}
              entityId={this.props.pipelineId} entityClass="PIPELINE" />
          }
        </ContentIssuesMetadataPanel>
        <EditPipelineForm
          onSubmit={this.editPipeline}
          onCancel={this.closeEditPipelineDialog}
          onDelete={this.deletePipeline}
          visible={this.state.editPipeline}
          pending={this.updatePipelineTokenRequest.pending || this.updatePipelineRequest.pending}
          pipeline={this.props.pipeline.value} />
        <RegisterVersionFormDialog
          visible={!!this.state.releaseCandidate}
          title="Create new version"
          onCancel={this.closeRegisterVersionDialog}
          onSubmit={this.registerVersion}
          pending={false} />
      </div>
    );
  }

  loadConfigurations = async () => {
    const versions = this.props.versions.value.map(v => v);
    const configurations = {};
    for (let i = 0; i < versions.length; i++) {
      const version = versions[i];
      const request = new PipelineConfigurations(this.props.pipelineId, version.name);
      await request.fetch();
      const list = request.loaded ? (request.value || []).map(c => c) : [];
      let [selected] = list.filter(c => c.default).map(c => c.name);
      if (!selected && list.length === 1) {
        selected = list[0].name;
      }
      if (version.name === this.props.selectedVersion && this.props.selectedConfiguration) {
        selected = this.props.selectedConfiguration;
      }
      configurations[version.commitId] = {list, selected};
    }
    this.setState({configurations});
  };

  componentDidUpdate (prevProps) {
    if (prevProps.pipelineId !== this.props.pipelineId) {
      this.setState({metadata: undefined, configurations: undefined, showIssuesPanel: false});
    }
    if (!this.props.versions.pending && !this.props.versions.error && this.props.configurationSelectionMode) {
      if (!this.state.configurations) {
        this.loadConfigurations();
      }
    }
  }

  componentDidMount () {
    if (!this.props.versions.pending && !this.props.versions.error && this.props.configurationSelectionMode) {
      if (!this.state.configurations) {
        this.loadConfigurations();
      }
    }
  }

}
