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
import {computed, observable} from 'mobx';
import connect from '../../../utils/connect';
import roleModel from '../../../utils/roleModel';
import localization from '../../../utils/localization';
import templates from '../../../models/pipelines/Templates';
import folderTemplates from '../../../models/folders/FolderTemplates';
import folders from '../../../models/folders/Folders';
import pipelines from '../../../models/pipelines/Pipelines';
import pipelinesLibrary from '../../../models/folders/FolderLoadTree';
import LoadingView from '../../special/LoadingView';
import AWSRegionTag from '../../special/AWSRegionTag';
import {
  Alert,
  Button,
  Checkbox,
  Col,
  Dropdown,
  Icon,
  Menu,
  message,
  Modal,
  Popover,
  Row,
  Table,
  Tooltip
} from 'antd';
import EditFolderForm from './forms/EditFolderForm';
import EditPipelineForm from '../version/forms/EditPipelineForm';
import {DataStorageEditDialog, ServiceTypes} from './forms/DataStorageEditDialog';
import CloneFolderForm from './forms/CloneFolderForm';
import EditDetachedConfigurationForm from '../configuration/forms/EditDetachedConfigurationForm';
import dataStorages from '../../../models/dataStorage/DataStorages';
import {FolderLock, FolderUnLock} from '../../../models/folders/FolderLock';
import FolderRegister from '../../../models/folders/FolderRegister';
import FolderClone from '../../../models/folders/FolderClone';
import FolderUpdate from '../../../models/folders/FolderUpdate';
import FolderDelete from '../../../models/folders/FolderDelete';
import CreatePipeline from '../../../models/pipelines/CreatePipeline';
import CheckPipelineRepository from '../../../models/pipelines/CheckPipelineRepository';
import DeletePipeline from '../../../models/pipelines/DeletePipeline';
import UpdatePipeline from '../../../models/pipelines/UpdatePipeline';
import UpdatePipelineToken from '../../../models/pipelines/UpdatePipelineToken';
import ConfigurationUpdate from '../../../models/configuration/ConfigurationUpdate';
import ConfigurationDelete from '../../../models/configuration/ConfigurationDelete';
import CreateDataStorage from '../../../models/dataStorage/DataStorageSave';
import UpdateDataStorage from '../../../models/dataStorage/DataStorageUpdate';
import DataStorageUpdateStoragePolicy from '../../../models/dataStorage/DataStorageUpdateStoragePolicy';
import DataStorageDelete from '../../../models/dataStorage/DataStorageDelete';
import Metadata from '../../special/metadata/Metadata';
import Issues from '../../special/issues/Issues';
import EditableField from '../../special/EditableField';
import {
  ContentIssuesMetadataPanel,
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY,
  ISSUES_PANEL_KEY
} from '../../special/splitPanel/SplitPanel';
import {generateTreeData, ItemTypes} from '../model/treeStructureFunctions';
import styles from './Browser.css';
import MetadataEntityUpload from '../../../models/folderMetadata/MetadataEntityUpload';
import UploadButton from '../../special/UploadButton';
import PreviewConfiguration from '../configuration/PreviewConfiguration';
import Breadcrumbs from '../../special/Breadcrumbs';

const MAX_INLINE_METADATA_KEYS = 10;

@localization.localizedComponent
@connect({
  templates,
  folderTemplates,
  pipelines,
  dataStorages,
  pipelinesLibrary,
  folders
})
@roleModel.authenticationInfo
@inject(({pipelines, dataStorages, folders}, params) => {
  let componentParameters = params;
  if (params.params) {
    componentParameters = params.params;
  }
  return {
    folder: componentParameters.id ? folders.load(componentParameters.id) : pipelinesLibrary,
    isRoot: !componentParameters.id,
    folderId: componentParameters.id,
    onReloadTree: params.onReloadTree,
    templates,
    folderTemplates,
    pipelines,
    dataStorages,
    folders,
    pipelinesLibrary
  };
})
@observer
export default class Folder extends localization.LocalizedReactComponent {

  static propTypes = {
    id: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number
    ]),
    treatAsRootId: PropTypes.number,
    listingMode: PropTypes.bool,
    showConfigurationPreview: PropTypes.bool,
    readOnly: PropTypes.bool,
    supportedTypes: PropTypes.array,
    onSelectItem: PropTypes.func,
    highlightByClick: PropTypes.bool,
    filterItems: PropTypes.func
  };

  state = {
    editableFolder: null,
    createFolderDialog: false,
    folderTemplate: null,
    editablePipeline: null,
    createPipelineDialog: false,
    pipelineTemplate: null,
    editableStorage: null,
    createStorageDialog: false,
    createNewStorageFlag: false,
    createNFSFlag: false,
    editableConfiguration: null,
    createConfigurationDialog: false,
    createDropDownVisible: false,
    operationInProgress: false,
    cloneFolderDialogVisible: false,
    showDescription: true,
    issuesItem: null,
    showIssuesPanel: false,
    showConfigurationPreview: false,
    folderToDelete: null,
    forceFolderDeletion: false
  };
  _currentFolder = null;
  highlightedItem = null;

  @observable
  _folderToDeleteInfo;

  folderOperationWrapper = (operation) => (...props) => {
    this.setState({
      operationInProgress: true
    }, async () => {
      await operation(...props);
      this.setState({
        operationInProgress: false
      });
    });
  };
  renderTreeItemType = (item) => {
    switch (item.type) {
      case ItemTypes.pipeline: return <Icon type="fork" />;
      case ItemTypes.folder:
        let icon = 'folder';
        if (item.isProject || (item.objectMetadata && item.objectMetadata.type &&
          (item.objectMetadata.type.value || '').toLowerCase() === 'project')) {
          icon = 'solution';
        }

        return <Icon type={icon} />;
      case ItemTypes.version: return <Icon type="tag" />;
      case ItemTypes.storage:
        if (item.storageType && item.storageType.toLowerCase() !== 'nfs') {
          return <Icon type="inbox" />;
        } else {
          return <Icon type="hdd" />;
        }
      case ItemTypes.configuration: return <Icon type="setting" />;
      case ItemTypes.metadata: return <Icon type="appstore-o" />;
      case ItemTypes.metadataFolder: return <Icon type="appstore-o" />;
      case ItemTypes.projectHistory: return <Icon type="clock-circle-o" />;
      default: return <div />;
    }
  };
  openIssuesPanel = (item) => (event) => {
    if (event) {
      event.stopPropagation();
    }
    this.setState({
      issuesItem: item,
      showIssuesPanel: true
    });
  };

  showHideDescription = () => {
    this.setState({
      showDescription: !this.state.showDescription
    }, () => {
      try {
        const showDescriptionInfo = JSON.stringify({
          value: this.state.showDescription
        });
        localStorage.setItem('show_description', showDescriptionInfo);
      } catch (___) {}
    });
  };

  renderMetadataKeyValue = (key, value) => {
    return (
      <Tooltip key={key} overlay={
        <Row>
          <Row>{key}:</Row>
          <Row>{value}</Row>
        </Row>
      }>
        <div key={key} className={styles.metadataItemContainer}>
          <Row className={styles.metadataItemKey}>
            {key}
          </Row>
          <Row className={styles.metadataItemValue}>
            {value}
          </Row>
        </div>
      </Tooltip>
    );
  };

  renderMetadata = (metadata) => {
    if (!metadata) {
      return null;
    }
    const items = [];
    for (let key in metadata) {
      if (metadata.hasOwnProperty(key)) {
        items.push(this.renderMetadataKeyValue(key, metadata[key].value));
      }
    }
    if (items.length > MAX_INLINE_METADATA_KEYS) {
      return (
        <Row className="object-metadata">
          {items.slice(0, MAX_INLINE_METADATA_KEYS - 1)}
          <div style={{
            margin: 5,
            display: 'inline-block'
          }}>
            <Popover content={
              <div className={styles.metadataPopover} style={{minWidth: 300, display: 'flex', flexDirection: 'column'}}>
                {items}
              </div>
            }>
              <a>+{items.length - MAX_INLINE_METADATA_KEYS + 1} more</a>
            </Popover>
          </div>
        </Row>
      );
    } else {
      return (
        <Row className="object-metadata">
          {items}
        </Row>
      );
    }
  };

  columns = [
    {
      key: 'type',
      className: styles.treeItemType,
      render: (item) => this.renderTreeItemType(item)
    },
    {
      dataIndex: 'name',
      key: 'name',
      title: 'Name',
      className: styles.treeItemName,
      render: (name, item) => {
        let nameComponent;
        let subTitle;
        if (item.type === ItemTypes.storage) {
          subTitle = <AWSRegionTag regionId={item.regionId} />;
        }
        const boldClassName = this.state.showDescription ? styles.objectNameBold : '';
        if (item.locked) {
          nameComponent = <span><Icon type="lock" /> <span className={`object-name ${boldClassName}`}>{name}</span>{subTitle}</span>;
        } else {
          nameComponent = <span><span className={`object-name ${boldClassName}`}>{name}</span>{subTitle}</span>;
        }
        if (this.state.showDescription && (item.description || item.hasMetadata)) {
          nameComponent = (
            <Row>
              <Row style={{marginTop: 2}}>{nameComponent}</Row>
              <Row
                style={{
                  marginTop: 5
                }}>
                {
                  item.description &&
                  <Row
                    style={
                      item.hasMetadata
                        ? {marginBottom: 5, wordWrap: 'normal'}
                        : {wordWrap: 'normal'}
                    }
                    className="object-description">{item.description}</Row>
                }
                {
                  this.renderMetadata(item.objectMetadata)
                }
              </Row>
            </Row>
          );
        }
        return nameComponent;
      }
    },
    {
      key: 'actions',
      className: styles.folderTreeItemActions,
      render: (item) => this.renderTreeItemActions(item)
    }
  ];
  reloadIssues = async () => {
    await this.props.folder.fetch();
  };
  closeIssuesPanel = () => {
    this.setState({
      issuesItem: null,
      showIssuesPanel: false
    });
  };
  navigateBackToRootIssues = () => {
    this.setState({
      issuesItem: null
    });
  };
  renderTreeItemActions = (item) => {
    if (this.props.listingMode || this.props.readOnly) {
      return undefined;
    }
    const actions = [];
    const issuesButton = (
      <Button
        onClick={this.openIssuesPanel(item)}
        key="issues"
        id={`${item.type}-item-${item.key}-issues-button`}
        size="small">
        <Icon type="message" />{item.issuesCount > 0 ? ` ${item.issuesCount}` : undefined}
      </Button>
    );
    switch (item.type) {
      case ItemTypes.folder:
        if (!item.isParent) {
          actions.push(
            issuesButton
          );
        }
        if ((item.removable === undefined || item.removable) && roleModel.writeAllowed(item)) {
          actions.push(
            roleModel.manager.folder(
              <Button
                key="delete"
                id={`folder-item-${item.key}-delete-button`}
                size="small"
                type="danger"
                onClick={(event) => this.deleteFolderConfirm(item, event)}>
                <Icon type="delete" />
              </Button>,
              'delete'
            )
          );
        }
        break;
      case ItemTypes.pipeline:
        actions.push(
          issuesButton
        );
        if (roleModel.executeAllowed(item)) {
          actions.push(
            <Button
              key="launch"
              id={`folder-item-${item.key}-launch-button`}
              size="small"
              type="primary"
              onClick={(e) => this.launchPipeline(item, e)}>
              RUN
            </Button>
          );
        }
        if (roleModel.writeAllowed(item)) {
          actions.push(
            <Button
              key="edit"
              id={`folder-item-${item.key}-edit-button`}
              size="small"
              onClick={(event) => this.openEditPipelineDialog(item, event)}>
              <Icon type="edit" />
            </Button>
          );
        }
        break;
      case ItemTypes.storage:
        if (roleModel.writeAllowed(item)) {
          actions.push(
            <Button
              key="edit"
              id={`folder-item-${item.key}-edit-button`}
              size="small"
              onClick={(event) => this.openEditStorageDialog(item, event)}>
              <Icon type="edit" />
            </Button>
          );
        }
        break;
      case ItemTypes.configuration:
        if ((item.removable === undefined || item.removable) && roleModel.writeAllowed(item)) {
          actions.push(
            <Button
              key="edit"
              id={`folder-item-${item.key}-edit-button`}
              size="small"
              onClick={(event) => this.openEditConfigurationDialog(item, event)}>
              <Icon type="edit" />
            </Button>
          );
        }
        break;
    }
    if (actions.filter(action => !!action).length > 0) {
      return (
        <Row type="flex" justify="end">
          {actions}
        </Row>
      );
    } else {
      return <div />;
    }
  };
  openCreateStorageDialog = (createNew, createNFS = false) => {
    this.setState({
      createStorageDialog: true,
      createNewStorageFlag: createNew,
      createNFSFlag: createNFS
    });
  };
  closeCreateStorageDialog = () => {
    this.setState({createStorageDialog: false}, () => {
      this.setState({createNewStorageFlag: false, createNFSFlag: false});
    });
  };

  deleteStorage = async (cloud) => {
    const request = new DataStorageDelete(this.state.editableStorage.id, cloud);
    const hide = message.loading(`${cloud ? 'Deleting' : 'Unregistering'} storage ${this.state.editableStorage.name}...`, 0);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeEditStorageDialog();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this._currentFolder.folder.parentId);
      }
    }
  };

  @computed
  get deletingFolderIsEmpty () {
    if (!this._folderToDeleteInfo || !this._folderToDeleteInfo.loaded) {
      return true;
    }
    return !(
      (this._folderToDeleteInfo.value.childFolders &&
        this._folderToDeleteInfo.value.childFolders.length) ||
      (this._folderToDeleteInfo.value.configurations &&
        this._folderToDeleteInfo.value.configurations.length) ||
      (this._folderToDeleteInfo.value.pipelines &&
        this._folderToDeleteInfo.value.pipelines.length) ||
      (this._folderToDeleteInfo.value.storages &&
        this._folderToDeleteInfo.value.storages.length)
    );
  };

  closeDeleteFolderDialog = () => {
    this.setState({
      folderToDelete: null,
      forceFolderDeletion: false
    });
  };

  deleteFolderConfirm = (folder, event) => {
    if (event) {
      event.stopPropagation();
    }
    this.setState({
      folderToDelete: folder
    });
  };

  deleteFolder = async (force = false) => {
    if (!this.state.folderToDelete) {
      return;
    }
    const folder = this.state.folderToDelete;
    const isCurrentFolder = folder.id === this._currentFolder.folder.id;
    const request = new FolderDelete(folder.id, force);
    const hide = message.loading('Deleting folder...', 0);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      if (isCurrentFolder) {
        if (this._currentFolder.folder.parentId) {
          this.props.folders.invalidateFolder(this._currentFolder.folder.parentId);
          await this.props.folders.load(this._currentFolder.folder.parentId).fetchIfNeededOrWait();
        } else {
          this.props.pipelinesLibrary.invalidateCache();
          await this.props.pipelinesLibrary.fetchIfNeededOrWait();
        }
        if (this._currentFolder.folder.parentId) {
          this.props.router.push(`/folder/${this._currentFolder.folder.parentId}`);
        } else {
          this.props.router.push('/library');
        }
      } else {
        await this.props.folder.fetch();
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!this._currentFolder.folder.parentId);
        }
      }
    }
  };

  openRenameFolderDialog = (folder, event) => {
    if (event) {
      event.stopPropagation();
    }
    this.setState({editableFolder: folder});
  };

  closeRenameFolderDialog = () => {
    this.setState({editableFolder: null});
  };

  renameCurrentFolder = async (name) => {
    const request = new FolderUpdate();
    const hide = message.loading('Renaming folder...', 0);
    await request.send({
      id: this._currentFolder.folder.id,
      parentId: this._currentFolder.folder.parentId,
      name: name
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeRenameFolderDialog();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this._currentFolder.folder.parentId);
      }
    }
  };

  renameFolder = async ({name}) => {
    if (name === this.state.editableFolder.name) {
      this.closeRenameFolderDialog();
      return;
    }
    const request = new FolderUpdate();
    const hide = message.loading('Renaming folder...', 0);
    await request.send({
      id: this.state.editableFolder.id,
      parentId: this.state.editableFolder.parentId,
      name: name
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeRenameFolderDialog();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this._currentFolder.folder.parentId);
      }
    }
  };

  openAddFolderDialog = (template) => {
    this.setState({createFolderDialog: true, folderTemplate: template});
  };

  closeAddFolderDialog = () => {
    this.setState({createFolderDialog: false, folderTemplate: null});
  };

  addFolder = async ({name}) => {
    const request = new FolderRegister(this.state.folderTemplate ? this.state.folderTemplate.id : undefined);
    const hide = message.loading(
      this.state.folderTemplate
        ? `Creating ${this.state.folderTemplate.id.toLowerCase()} folder...`
        : 'Creating folder...',
      0);
    await request.send({
      parentId: this._currentFolder.folder.id,
      name: name
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeAddFolderDialog();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this._currentFolder.folder.parentId);
      }
    }
  };
  createStorage = async (storage) => {
    const request = new CreateDataStorage(this.state.createNewStorageFlag);
    const hide = message.loading('Creating storage...', 0);
    let path = storage.path;
    let name = storage.name;
    if (path.toLowerCase().startsWith('s3://')) {
      path = path.substring('s3://'.length);
    }
    if (path.toLowerCase().startsWith('nfs://')) {
      path = path.substring('nfs://'.length);
    }
    if (path.toLowerCase().startsWith('az://')) {
      path = path.substring('az://'.length);
    }
    if (!name || !name.length) {
      name = path;
    }
    await request.send({
      parentFolderId: this._currentFolder.folder.id,
      name: name,
      description: storage.description,
      path: path,
      shared: storage.serviceType === ServiceTypes.objectStorage && storage.shared,
      storagePolicy: {
        longTermStorageDuration: storage.longTermStorageDuration,
        shortTermStorageDuration: storage.shortTermStorageDuration,
        backupDuration: storage.backupDuration,
        versioningEnabled: storage.versioningEnabled
      },
      serviceType: storage.serviceType || ServiceTypes.objectStorage,
      mountPoint: storage.mountPoint,
      mountOptions: storage.mountOptions,
      regionId: storage.serviceType === ServiceTypes.objectStorage && storage.regionId
        ? storage.regionId
        : undefined
    });
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeCreateStorageDialog();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(true);
      }
    }
  };
  editStorage = async (storage) => {
    const request = new UpdateDataStorage();
    const hide = message.loading(`Updating storage ${storage.name}...`, 0);
    const payload = {
      id: this.state.editableStorage.id,
      parentFolderId: this.state.editableStorage.parentId,
      name: storage.name,
      description: storage.description,
      path: storage.path
    };
    if (storage.mountPoint) {
      payload.mountPoint = storage.mountPoint;
    }
    if (storage.mountOptions) {
      payload.mountOptions = storage.mountOptions;
    }
    await request.send(payload);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      if (this.state.editableStorage.policySupported &&
        storage.serviceType !== ServiceTypes.fileShare &&
        (storage.longTermStorageDuration !== undefined ||
        storage.shortTermStorageDuration !== undefined ||
        storage.backupDuration !== undefined || !storage.versioningEnabled)) {
        const updatePolicyRequest = new DataStorageUpdateStoragePolicy();
        await updatePolicyRequest.send({
          id: this.state.editableStorage.id,
          storagePolicy: {
            longTermStorageDuration: storage.longTermStorageDuration,
            shortTermStorageDuration: storage.shortTermStorageDuration,
            backupDuration: storage.backupDuration,
            versioningEnabled: storage.versioningEnabled
          }
        });
        if (updatePolicyRequest.error) {
          hide();
          message.error(updatePolicyRequest.error, 5);
        } else {
          hide();
          this.props.dataStorages.invalidateCache(this.state.editableStorage.id);
          this.closeEditStorageDialog();
          await this.props.folder.fetch();
          if (this.props.onReloadTree) {
            this.props.onReloadTree(!this._currentFolder.folder.parentId);
          }
        }
      } else {
        hide();
        this.props.dataStorages.invalidateCache(this.state.editableStorage.id);
        this.closeEditStorageDialog();
        await this.props.folder.fetch();
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!this._currentFolder.folder.parentId);
        }
      }
    }
  };
  cloneFolder = async (parentId, name) => {
    const hide = message.loading('Cloning folder...', -1);
    const request = new FolderClone(this.props.folder.value.id, parentId, name);
    await request.send({});
    hide();
    if (request.error) {
      message.error(request.error);
    } else {
      this.closeCloneFolderDialog();
      if (parentId) {
        this.props.folders.invalidateFolder(parentId);
        await this.props.folders.load(parentId).fetchIfNeededOrWait();
      }
      this.props.pipelinesLibrary.invalidateCache();
      await this.props.pipelinesLibrary.fetchIfNeededOrWait();
      if (this.props.onReloadTree) {
        await this.props.onReloadTree(true);
      }
      const folderId = request.value.id;
      if (folderId) {
        this.props.router.push(`/folder/${folderId}`);
      } else {
        this.props.router.push('/library');
      }
    }
  };

  openEditStorageDialog = (storage, event) => {
    event.stopPropagation();
    this.setState({editableStorage: storage});
  };

  closeEditStorageDialog = () => {
    this.setState({editableStorage: null});
  };
  highlightItem = (item) => {
    if (item.type === ItemTypes.configuration && this.props.showConfigurationPreview) {
      if (this.highlightedItem && this.highlightedItem.id === item.id &&
        this.state.showConfigurationPreview) {
        this.setState({showConfigurationPreview: false});
      } else {
        this.setState({showConfigurationPreview: true});
      }
    }
    this.highlightedItem = item;
  };

  launchPipeline = async (pipeline, event) => {
    if (event) {
      event.stopPropagation();
    }
    const hide = message.loading('Fetching versions info...', -1);
    const pipelineRequest = this.props.pipelines.getPipeline(pipeline.id);
    await pipelineRequest.fetchIfNeededOrWait();
    hide();
    if (pipelineRequest.error) {
      message.error(pipelineRequest.error, 5);
    } else if (pipelineRequest.value && pipelineRequest.value.currentVersion) {
      this.props.router.push(`/launch/${pipeline.id}/${pipelineRequest.value.currentVersion.name}`);
    } else {
      message.error('Error fetching last version info', 5);
    }
  };

  openCreatePipelineDialog = (template) => {
    this.setState({createPipelineDialog: true, pipelineTemplate: template});
  };

  closeCreatePipelineDialog = () => {
    this.setState({createPipelineDialog: false, pipelineTemplate: null});
  };

  createPipelineRequest = new CreatePipeline();

  createPipeline = async ({name, description, repository, token}) => {
    const hide = message.loading(`Creating ${this.localizedString('pipeline')} ${name}...`, 0);
    await this.createPipelineRequest.send({
      name: name,
      description: description,
      parentFolderId: this._currentFolder.folder.id,
      templateId: this.state.pipelineTemplate ? this.state.pipelineTemplate.id : undefined,
      repository: repository,
      repositoryToken: token
    });
    hide();
    if (this.createPipelineRequest.error) {
      message.error(this.createPipelineRequest.error, 5);
    } else {
      this.closeCreatePipelineDialog();
      this.props.pipelines.fetch();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(true);
      }
    }
  };

  checkRequest = new CheckPipelineRepository();

  onCreatePipeline = async ({name, description, repository, token}) => {
    if ((token && token.length) || (repository && repository.length)) {
      const hide = message.loading('Checking repository existence...', -1);
      await this.checkRequest.send({
        repository,
        token
      });
      hide();
      if (this.checkRequest.error) {
        message.error(this.checkRequest.error);
        return;
      } else if (!this.checkRequest.value.repositoryExists) {
        Modal.confirm({
          title: 'Repository does not exist. Create?',
          style: {
            wordWrap: 'break-word'
          },
          content: null,
          okText: 'OK',
          cancelText: 'Cancel',
          onOk: async () => {
            await this.createPipeline({name, description, repository, token});
          }
        });
        return;
      }
    }
    await this.createPipeline({name, description, repository, token});
  };

  openCloneFolderDialog = () => {
    this.setState({
      cloneFolderDialogVisible: true
    });
  };

  closeCloneFolderDialog = () => {
    this.setState({
      cloneFolderDialogVisible: false
    });
  };
  navigate = (item) => {
    if (this.props.onSelectItem) {
      this.props.onSelectItem(item);
    } else {
      switch (item.type) {
        case ItemTypes.folder:
          if (!item.id) {
            this.props.pipelinesLibrary.invalidateCache();
          } else {
            this.props.folders.invalidateFolder(item.id);
          }
          break;
      }
      this.props.router.push(item.url());
    }
  };

  openEditPipelineDialog = (pipeline, event) => {
    event.stopPropagation();
    this.setState({editablePipeline: pipeline});
  };

  closeEditPipelineDialog = () => {
    this.setState({editablePipeline: null});
  };

  updatePipelineRequest = new UpdatePipeline();
  updatePipelineTokenRequest = new UpdatePipelineToken();

  editPipeline = async ({name, description, token}) => {
    const hide = message.loading(`Updating ${this.localizedString('pipeline')} ${name}...`, 0);
    await this.updatePipelineRequest.send({
      id: this.state.editablePipeline.id,
      name: name,
      description: description,
      parentFolderId: this._currentFolder.folder.id
    });
    if (this.updatePipelineRequest.error) {
      hide();
      message.error(this.updatePipelineRequest.error, 5);
    } else {
      if (token !== undefined) {
        this.updatePipelineTokenRequest = new UpdatePipelineToken();
        await this.updatePipelineTokenRequest.send({
          id: this.state.editablePipeline.id,
          repositoryToken: token
        });
        hide();
        if (this.updatePipelineTokenRequest.error) {
          message.error(this.updatePipelineTokenRequest.error, 5);
        } else {
          this.closeEditPipelineDialog();
          await this.props.folder.fetch();
          if (this.props.onReloadTree) {
            this.props.onReloadTree(!this._currentFolder.folder.parentId);
          }
        }
      } else {
        hide();
        this.closeEditPipelineDialog();
        await this.props.folder.fetch();
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!this._currentFolder.folder.parentId);
        }
      }
    }
  };

  deletePipeline = async (keepRepository) => {
    const request = new DeletePipeline(this.state.editablePipeline.id, keepRepository);
    const hide = message.loading(`Deleting ${this.localizedString('pipeline')} ${this.state.editablePipeline.name}...`, 0);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeEditPipelineDialog();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this._currentFolder.folder.parentId);
      }
    }
  };

  openEditConfigurationDialog = (configuration, event) => {
    event.stopPropagation();
    this.setState({editableConfiguration: configuration});
  };

  closeEditConfigurationDialog = () => {
    this.setState({editableConfiguration: null});
  };

  editConfiguration = async (opts) => {
    const hide = message.loading(`Updating configuration '${this.state.editableConfiguration.name}'...`, 0);
    const payload = {
      id: this.state.editableConfiguration.id,
      name: opts.name,
      description: opts.description,
      parentId: this.state.editableConfiguration.parentId,
      entries: this.state.editableConfiguration.entries
    };
    const request = new ConfigurationUpdate();
    await request.send(payload);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      hide();
      this.closeEditConfigurationDialog();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this._currentFolder.folder.parentId);
      }
    }
  };

  deleteConfiguration = async () => {
    const hide = message.loading(`Removing configuration '${this.state.editableConfiguration.name}'...`, 0);
    const request = new ConfigurationDelete(this.state.editableConfiguration.id);
    await request.fetch();
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      hide();
      this.closeEditConfigurationDialog();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this._currentFolder.folder.parentId);
      }
    }
  };

  openCreateConfigurationDialog = () => {
    this.setState({createConfigurationDialog: true});
  };

  closeCreateConfigurationDialog = () => {
    this.setState({createConfigurationDialog: false});
  };

  createConfiguration = async (opts) => {
    const hide = message.loading(`Creating configuration '${opts.name}'...`, 0);
    const payload = {
      name: opts.name,
      description: opts.description,
      parentId: this.props.folderId,
      entries: [{
        name: 'default',
        default: true,
        configuration: {
          cmd_template: 'sleep infinity'
        }
      }]
    };
    const request = new ConfigurationUpdate();
    await request.send(payload);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      hide();
      this.closeCreateConfigurationDialog();
      await this.props.folder.fetch();
      if (this.props.onReloadTree) {
        this.props.onReloadTree(true);
      }
    }
  };
  renderContent = () => {
    const getRowClassName = (row) => {
      if (this.highlightedItem && this.props.highlightByClick && this.highlightedItem.id === row.id) {
        return `${styles.tableRowSelected} folder-item-${row.key}`;
      } else if (row.description || row.hasMetadata) {
        return `folder-item-${row.key} ${styles.pipelineWithDescriptionRow}`;
      } else {
        return `folder-item-${row.key}`;
      }
    };
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
      <ContentIssuesMetadataPanel onPanelClose={onPanelClose}>
        <Table
          key={CONTENT_PANEL_KEY}
          className={`${styles.childrenContainer} ${styles.childrenContainerLarger}`}
          dataSource={this._currentFolder.data}
          columns={this.columns}
          rowKey={(item) => item.key}
          title={null}
          showHeader={false}
          rowClassName={getRowClassName}
          expandedRowRender={null}
          loading={this.props.folder.pending}
          pagination={{pageSize: 40}}
          locale={{emptyText: 'Folder is empty'}}
          onRowClick={(item) => {
            this.highlightItem(item);
            this.navigate(item);
          }}
          size="small" />
        {
          this.showIssues &&
          <Issues
            key={ISSUES_PANEL_KEY}
            canNavigateBack={!!this.state.issuesItem}
            onNavigateBack={this.navigateBackToRootIssues}
            onCloseIssuePanel={this.closeIssuesPanel}
            onReloadIssues={this.reloadIssues}
            entityDisplayName={
              this.state.issuesItem
                ? this.state.issuesItem.name
                : this._currentFolder.folder.name
            }
            entityId={
              this.state.issuesItem
                ? this.state.issuesItem.entityId
                : this.props.folderId
            }
            entityClass={
              this.state.issuesItem
                ? this.state.issuesItem.entityClass
                : 'FOLDER'
            }
            entity={
              this.state.issuesItem
              ? this.state.issuesItem
              : this.props.folder.value
            } />
        }
        {
          (this.showMetadata && this.props.folderId !== undefined) &&
          <Metadata
            key={METADATA_PANEL_KEY}
            readOnly={!roleModel.isOwner(this.props.folder.value)}
            entityName={this.props.folder.value.name}
            entityId={this.props.folderId} entityClass="FOLDER" />
        }
        {
          this.showConfigurationPreview &&
          <PreviewConfiguration
            key="configuration-preview"
            configurationId={this.highlightedItem.id} />
        }
      </ContentIssuesMetadataPanel>
    );
  };
  renderActions = () => {
    const actions = [];
    const createActions = [];
    const pipelineKey = 'pipeline';
    const storageKey = 'storage';
    const nfsStorageKey = 'nfs';
    const configurationKey = 'configuration';
    const folderKey = 'folder';
    const onCreateActionSelect = ({key}) => {
      const parts = key.split('_');
      const type = parts[0];
      let identifier;
      if (parts.length > 1) {
        parts.splice(0, 1);
        identifier = parts.join('_');
      }
      this.setState({
        createDropDownVisible: false
      }, () => {
        switch (type) {
          case pipelineKey:
            if (identifier) {
              const [template] = this.props.templates.value.filter(t => t.id === identifier);
              this.openCreatePipelineDialog(template);
            } else {
              this.openCreatePipelineDialog(null);
            }
            break;
          case storageKey:
            const createNFS = identifier ? identifier === nfsStorageKey : false;
            const createNew = createNFS ? true : (identifier ? identifier === 'new' : true);
            this.openCreateStorageDialog(createNew, createNFS);
            break;
          case folderKey:
            this.openAddFolderDialog();
            if (identifier) {
              const [template] = this.props.folderTemplates.value.filter(t => t.id === identifier);
              this.openAddFolderDialog(template);
            } else {
              this.openAddFolderDialog(null);
            }
            break;
          case configurationKey:
            this.openCreateConfigurationDialog();
            break;
        }
      });
    };

    if (roleModel.writeAllowed(this.props.folder.value) && !this.props.readOnly &&
      this.props.folderId !== undefined && !this.props.listingMode && roleModel.isManager.entities(this)) {
      actions.push(
        <UploadButton
          key="upload-metadata"
          multiple={false}
          synchronous={true}
          onRefresh={async () => {
            await this.props.folder.fetch();
            if (this.props.onReloadTree) {
              this.props.onReloadTree(true);
            }
          }}
          title={'Upload metadata'}
          action={MetadataEntityUpload.uploadUrl(this.props.folderId)} />
      );
    }

    if (roleModel.writeAllowed(this.props.folder.value) && !this.props.readOnly && !this.props.listingMode) {
      let pipelineTemplatesMenu;
      if (!this.props.templates.pending && roleModel.isManager.pipeline(this)) {
        if (!this.props.templates.error && (this.props.templates.value || []).length > 0) {
          const templates = (this.props.templates.value || []).filter(template => !template.defaultTemplate);
          pipelineTemplatesMenu = [
            <Menu.Item
              id="create-pipeline-button"
              className="create-pipeline-button"
              key={pipelineKey}>
              <Row>
                DEFAULT
              </Row>
              <Row style={{fontSize: 'smaller'}}>
                Create {this.localizedString('pipeline')} without template
              </Row>
            </Menu.Item>,
            <Menu.Divider key="divider" />,
            ...templates.map(t => {
              return (
                <Menu.Item
                  id={`create-pipeline-by-template-button-${t.id.toLowerCase()}`}
                  className={`create-pipeline-by-template-button-${t.id.toLowerCase()}`}
                  key={`${pipelineKey}_${t.id}`}>
                  <Row>
                    {t.id.toUpperCase()}
                  </Row>
                  <Row style={{fontSize: 'smaller'}}>
                    {t.description}
                  </Row>
                </Menu.Item>
              );
            })
          ];
        }
      }
      if (roleModel.isManager.pipeline(this)) {
        if (pipelineTemplatesMenu) {
          createActions.push(
            <Menu.SubMenu
              id="create-pipeline-sub-menu-button"
              onTitleClick={() => {
                this.setState({
                  createDropDownVisible: false
                }, () => {
                  this.openCreatePipelineDialog(null);
                });
              }}
              key={pipelineKey}
              title={<span><Icon type="fork" /> {this.localizedString('Pipeline')}</span>}
              className={`${styles.actionsSubMenu} create-pipeline-sub-menu-button`}>
              {pipelineTemplatesMenu}
            </Menu.SubMenu>
          );
        } else {
          createActions.push(
            <Menu.Item
              id="create-pipeline-button"
              className="create-pipeline-button"
              key={pipelineKey}>
              <Icon type="fork" /> {this.localizedString('Pipeline')}
            </Menu.Item>
          );
        }
      }
      if (roleModel.isManager.storage(this)) {
        createActions.push(
          <Menu.SubMenu
            key={storageKey}
            onTitleClick={() => {
              this.setState({
                createDropDownVisible: false
              }, () => {
                this.openCreateStorageDialog(true);
              });
            }}
            title={<span><Icon type="hdd" /> Storages</span>}
            className={`${styles.actionsSubMenu} create-storage-sub-menu`}>
            <Menu.Item
              id="create-new-storage-button"
              className="create-new-storage-button"
              key={`${storageKey}_new`}>
              Create new object storage
            </Menu.Item>
            <Menu.Item
              id="add-existing-storage-button"
              className="add-existing-storage-button"
              key={`${storageKey}_existing`}>
              Add existing object storage
            </Menu.Item>
            <Menu.Divider key="storages_divider" />
            <Menu.Item
              id="create-new-nfs-mount"
              className="create-new-nfs-mount"
              key={`${storageKey}_${nfsStorageKey}`}>
              Create new FS mount
            </Menu.Item>
          </Menu.SubMenu>
        );
      }
      if (roleModel.isManager.configuration(this)) {
        createActions.push(
          <Menu.Item
            id="create-configuration-button"
            className="create-configuration-button"
            key={configurationKey}>
            <Icon type="setting" /> Configuration
          </Menu.Item>
        );
      }
      let folderTemplatesMenu;
      if (!this.props.folderTemplates.pending && roleModel.isManager.folder(this)) {
        if (!this.props.folderTemplates.error && (this.props.folderTemplates.value || []).length > 0) {
          folderTemplatesMenu =
            (this.props.folderTemplates.value || []).map(t => {
              return (
                <Menu.Item
                  id={`create-folder-by-template-button-${t.id.toLowerCase()}`}
                  className={`create-folder-by-template-button-${t.id.toLowerCase()}`}
                  key={`${folderKey}_${t.id}`}>
                  <Row>
                    {t.id.toUpperCase()}
                  </Row>
                  <Row style={{fontSize: 'smaller'}}>
                    {t.description}
                  </Row>
                </Menu.Item>
              );
            });
        }
      }
      if (roleModel.isManager.folder(this)) {
        createActions.push(
          <Menu.Item
            id="create-folder-button"
            className="create-folder-button"
            key={folderKey}>
            <Icon type="folder" /> Folder
          </Menu.Item>
        );
        if (folderTemplatesMenu) {
          createActions.push(<Menu.Divider key="divider one" />);
          createActions.push(...folderTemplatesMenu);
        }
      }
    }
    if (createActions.filter(action => !!action).length > 0) {
      actions.push(
        <Dropdown
          visible={this.state.createDropDownVisible}
          onVisibleChange={(visible) => this.setState({createDropDownVisible: visible})}
          placement="bottomRight"
          trigger={['hover']}
          overlay={
            <Menu
              selectedKeys={[]}
              onClick={onCreateActionSelect}
              style={{width: 200}}>
              {createActions}
            </Menu>
          }
          key="create actions">
          <Button
            type="primary"
            id="create-button"
            size="small">
            <Icon type="plus" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
            <span style={{lineHeight: 'inherit', verticalAlign: 'middle'}}> Create </span>
            <Icon type="down" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
          </Button>
        </Dropdown>
      );
    }
    const onSelectDisplayOption = ({key}) => {
      switch (key) {
        case 'descriptions': this.showHideDescription(); break;
        case 'metadata': this.setState({metadata: !this.showMetadata}); break;
        case 'issues':
          if (this.showIssues) {
            this.closeIssuesPanel();
          } else {
            this.openIssuesPanel(null)();
          }
          break;
      }
    };
    const displayOptionsMenuItems = [];
    if (!this.props.listingMode) {
      displayOptionsMenuItems.push(
        <Menu.Item
          id="show-hide-descriptions"
          key="descriptions">
          <Row type="flex" justify="space-between" align="middle">
            <span>Descriptions</span>
            <Icon type="check-circle" style={{display: this.state.showDescription ? 'inherit' : 'none'}} />
          </Row>
        </Menu.Item>
      );
    }
    if (this.props.folderId !== undefined && !this.props.listingMode) {
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
    }
    if ((this.showIssues || this.props.folderId !== undefined) && !this.props.listingMode) {
      displayOptionsMenuItems.push(
        <Menu.Item
          id={this.showIssues ? 'hide-issues-panel-button' : 'show-issues-panel-button'}
          key="issues">
          <Row type="flex" justify="space-between" align="middle">
            <span>{this.localizedString('Issue')}s</span>
            <Icon type="check-circle" style={{display: this.showIssues ? 'inherit' : 'none'}} />
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

      actions.push(
        <Dropdown
          key="display attributes"
          overlay={displayOptionsMenu}>
          <Button
            id="display-attributes"
            size="small">
            <Icon type="appstore" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
          </Button>
        </Dropdown>
      );
    }

    if (!this.props.isRoot && !this.props.listingMode) {
      const editActions = [];
      if (roleModel.readAllowed(this.props.folder.value)) {
        editActions.push(
          <Menu.Item id="edit-folder-button" key="edit">
            <Icon type="edit" /> {roleModel.writeAllowed(this.props.folder.value) ? 'Edit folder' : 'Permissions'}
          </Menu.Item>
        );
      }
      if (!this.props.readOnly && roleModel.isOwner(this.props.folder.value)) {
        editActions.push(
          <Menu.Item
            key="clone"
            id="clone-folder-button">
            <Icon type="copy" /> Clone
          </Menu.Item>
        );
      }
      const folderIsReadOnly = this.props.folder.value.locked;
      if (folderIsReadOnly && roleModel.isOwner(this.props.folder.value)) {
        editActions.push(
          <Menu.Item id="unlock-button" key="unlock">
            <Icon type="unlock" /> Unlock
          </Menu.Item>
        );
      } else if (!folderIsReadOnly && roleModel.writeAllowed(this.props.folder.value)) {
        editActions.push(
          <Menu.Item id="lock-button" key="lock">
            <Icon type="lock" /> Lock
          </Menu.Item>
        );
      }
      if (!this.props.readOnly && roleModel.writeAllowed(this.props.folder.value) && roleModel.isManager.folder(this)) {
        if (editActions.length > 0) {
          editActions.push(<Menu.Divider key="divider" />);
        }
        editActions.push(
          <Menu.Item id="delete-folder-button" key="delete">
            <Icon
              type="delete"
              style={{color: 'red'}} /> Delete
          </Menu.Item>
        );
      }
      if (editActions.length > 0) {
        const onClick = ({key}) => {
          switch (key) {
            case 'edit': this.openRenameFolderDialog(this._currentFolder.folder); break;
            case 'clone': this.openCloneFolderDialog(); break;
            case 'delete': this.deleteFolderConfirm(this._currentFolder.folder); break;
            case 'lock': this.lockUnLockFolderConfirm(true); break;
            case 'unlock': this.lockUnLockFolderConfirm(false); break;
          }
        };
        actions.push(
          <Dropdown
            placement="bottomRight"
            overlay={
              <Menu
                selectedKeys={[]}
                onClick={onClick}
                style={{width: 100}}>
                {editActions}
              </Menu>
            }
            key="edit">
            <Button
              key="edit"
              id="edit-folder-menu-button"
              size="small">
              <Icon type="setting" style={{lineHeight: 'inherit', verticalAlign: 'middle'}} />
            </Button>
          </Dropdown>
        );
      }
    }
    return actions.filter(action => !!action);
  };
  lockUnLockFolderConfirm = (lock) => {
    const onConfirm = () => {
      return this.lockUnLockFolder(lock);
    };
    Modal.confirm({
      title: `Are you sure you want to ${lock ? 'lock' : 'unlock'} folder ${this.props.folder.value.name}?`,
      style: {
        wordWrap: 'break-word'
      },
      onOk () {
        onConfirm();
      }
    });
  };
  lockUnLockFolder = async (lock) => {
    const hide = message.loading(lock ? 'Locking folder...' : 'Unlocking folder', -1);
    const request = lock ? new FolderLock(this.props.folderId) : new FolderUnLock(this.props.folderId);
    await request.send({});
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      if (this.props.folder.value.parentId) {
        this.props.folders.invalidateFolder(this.props.folder.value.parentId);
        await this.props.folders.load(this.props.folder.value.parentId).fetchIfNeededOrWait();
        await this.props.folder.fetch();
      } else {
        await this.props.folder.fetch();
        this.props.pipelinesLibrary.invalidateCache();
        await this.props.pipelinesLibrary.fetchIfNeededOrWait();
      }
      if (this.props.onReloadTree) {
        this.props.onReloadTree(true);
      }
      hide();
    }
  };

  @computed
  get showConfigurationPreview () {
    return !!(this.props.showConfigurationPreview &&
      this.state.showConfigurationPreview && this.highlightedItem &&
      this.highlightedItem.type === ItemTypes.configuration);
  }

  @computed
  get showMetadata () {
    if (this.props.listingMode) {
      return false;
    }
    if (this.state.metadata === undefined &&
      this.props.folder.loaded) {
      return this.props.folder.value.hasMetadata && roleModel.readAllowed(this.props.folder.value);
    }
    return !!this.state.metadata;
  }

  @computed
  get showIssues () {
    if (this.props.folderId !== undefined) {
      return !!this.state.issuesItem || this.state.showIssuesPanel;
    } else {
      return !!this.state.issuesItem;
    }
  }

  render () {
    if (!this.props.folder.pending && this.props.folder.error) {
      return <Alert message={this.props.folder.error} type="error" />;
    }
    if (this.props.folder.loaded) {
      if (!this.props.isRoot && roleModel.readDenied(this.props.folder.value) &&
        (!this.props.folder.value.childFolders || !this.props.folder.value.childFolders.length) &&
        (!this.props.folder.value.storages || !this.props.folder.value.storages.length) &&
        (!this.props.folder.value.pipelines || !this.props.folder.value.pipelines.length) &&
        (!this.props.folder.value.configurations || !this.props.folder.value.configurations.length)) {
        return <Alert message="Access denied" type="error" />;
      }
      let data = generateTreeData(
        this.props.folder.value,
        true,
        null,
        [],
        this.props.supportedTypes,
        this.props.filterItems
      );
      if (this.props.isRoot) {
        this._currentFolder = {
          data: data,
          folder: {
            name: 'Library',
            id: undefined,
            key: `${ItemTypes.folder}_root`,
            type: ItemTypes.folder,
            removable: false,
            editable: false,
            url () {
              return '/library';
            }
          }
        };
      } else {
        if (!this.props.treatAsRootId ||
          this.props.treatAsRootId !== this.props.folder.value.id) {
          if (this.props.folder.value.parentId) {
            const url = `/folder/${this.props.folder.value.parentId}`;
            data = [{
              id: this.props.folder.value.parentId,
              name: '..',
              key: `${ItemTypes.folder}_${this.props.folder.value.parentId}`,
              type: ItemTypes.folder,
              removable: false,
              editable: false,
              isParent: true,
              url () {
                return url;
              }
            }, ...data];
          } else {
            data = [{
              id: undefined,
              name: '..',
              key: `${ItemTypes.folder}_root`,
              type: ItemTypes.folder,
              removable: false,
              editable: false,
              isParent: true,
              url () {
                return '/library';
              }
            }, ...data];
          }
        }
        this._currentFolder = {
          data: data,
          folder: this.props.folder.value
        };
      }
    }
    if (!this._currentFolder) {
      return <LoadingView />;
    }
    const folderTitleClassName = this.props.folder.value.locked ? styles.readonly : undefined;
    const isProject = !!(this.props.folder.value && this.props.folder.value.objectMetadata &&
      this.props.folder.value.objectMetadata.type &&
      (this.props.folder.value.objectMetadata.type.value || '').toLowerCase() === 'project');
    return (
      <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
        <Row type="flex" justify="space-between" align="middle" style={{minHeight: 41}}>
          <Col className={styles.itemHeader}>
            <Icon type={isProject ? 'solution' : 'folder'} className={`${styles.editableControl} ${folderTitleClassName}`} />
            {
              this.props.folder.value.locked &&
              <Icon
                className={`${styles.editableControl} ${folderTitleClassName}`}
                type="lock" />
            }
            <Breadcrumbs
              id={parseInt(this.props.folderId)}
              type={ItemTypes.folder}
              textEditableField={this.props.folder.value.name}
              readOnlyEditableField={
                this.props.isRoot ||
                !roleModel.writeAllowed(this.props.folder.value) ||
                this.props.readOnly
              }
              classNameEditableField={folderTitleClassName}
              onSaveEditableField={this.renameCurrentFolder}
              editStyleEditableField={{flex: 1}}
            />
          </Col>
          <Col className={styles.currentFolderActions}>
            {this.renderActions()}
          </Col>
        </Row>
        {this.renderContent()}
        <EditFolderForm
          visible={this.state.createFolderDialog}
          title={this.state.folderTemplate ? `Create ${this.state.folderTemplate.id.toLowerCase()} folder` : 'Create folder'}
          pending={this.state.operationInProgress}
          onSubmit={this.folderOperationWrapper(this.addFolder)}
          onCancel={this.closeAddFolderDialog} />
        <EditFolderForm
          locked={this.state.editableFolder ? this.state.editableFolder.locked : false}
          visible={!!this.state.editableFolder}
          name={this.state.editableFolder && this.state.editableFolder.name}
          folderId={this.state.editableFolder && this.state.editableFolder.id}
          mask={this.state.editableFolder && this.state.editableFolder.mask}
          title="Rename folder"
          pending={this.state.operationInProgress}
          onSubmit={this.folderOperationWrapper(this.renameFolder)}
          onCancel={this.closeRenameFolderDialog} />
        <EditPipelineForm
          onSubmit={this.folderOperationWrapper(this.onCreatePipeline)}
          onCancel={this.closeCreatePipelineDialog}
          visible={this.state.createPipelineDialog}
          pipelineTemplate={this.state.pipelineTemplate}
          pending={this.state.operationInProgress} />
        <EditPipelineForm
          onSubmit={this.folderOperationWrapper(this.editPipeline)}
          onCancel={this.closeEditPipelineDialog}
          onDelete={this.folderOperationWrapper(this.deletePipeline)}
          visible={!!this.state.editablePipeline}
          pending={this.state.operationInProgress}
          pipeline={this.state.editablePipeline} />
        <DataStorageEditDialog
          onSubmit={this.folderOperationWrapper(this.createStorage)}
          onCancel={this.closeCreateStorageDialog}
          visible={this.state.createStorageDialog}
          isNfsMount={this.state.createNFSFlag}
          policySupported={!this.state.createNFSFlag}
          addExistingStorageFlag={!this.state.createNewStorageFlag}
          pending={this.state.operationInProgress} />
        <DataStorageEditDialog
          onSubmit={this.folderOperationWrapper(this.editStorage)}
          onDelete={this.folderOperationWrapper(this.deleteStorage)}
          onCancel={this.closeEditStorageDialog}
          visible={!!this.state.editableStorage}
          pending={this.state.operationInProgress}
          dataStorage={this.state.editableStorage}
          policySupported={this.state.editableStorage && this.state.editableStorage.policySupported} />
        <EditDetachedConfigurationForm
          configuration={this.state.editableConfiguration}
          onDelete={this.folderOperationWrapper(this.deleteConfiguration)}
          onSubmit={this.folderOperationWrapper(this.editConfiguration)}
          pending={this.state.operationInProgress}
          onCancel={this.closeEditConfigurationDialog}
          visible={!!this.state.editableConfiguration} />
        <EditDetachedConfigurationForm
          onSubmit={this.folderOperationWrapper(this.createConfiguration)}
          pending={this.state.operationInProgress}
          onCancel={this.closeCreateConfigurationDialog}
          visible={this.state.createConfigurationDialog} />
        <CloneFolderForm
          parentId={this.props.folder.value.parentId}
          visible={this.state.cloneFolderDialogVisible}
          pending={this.state.operationInProgress}
          onCancel={this.closeCloneFolderDialog}
          onSubmit={this.folderOperationWrapper(this.cloneFolder)} />
        <Modal
          visible={!!this.state.folderToDelete}
          load
          title={false}
          closable={false}
          footer={false}
          width={416}
          onCancel={this.closeDeleteFolderDialog}
          bodyStyle={{
            wordWrap: 'break-word',
            padding: '30px 40px'
          }}>
          <Row>
            <Row className={styles.configurationConfirmTitle}>
              <Icon type="question-circle" />
              {
                this.state.folderToDelete &&
                (!this.deletingFolderIsEmpty
                  ? `Folder '${this.state.folderToDelete.name}' contains sub-items, do you want to delete them?`
                  : `Are you sure you want to delete folder ${this.state.folderToDelete.name}`)
              }
            </Row>
            {(!this._folderToDeleteInfo || this._folderToDeleteInfo.pending) && <LoadingView />}
            {
              !this.deletingFolderIsEmpty &&
              <Row style={{
                marginLeft: '42px',
                fontSize: '12px',
                color: 'rgba(0,0,0,.65)',
                marginTop: '8px'
              }}>
                <Checkbox
                  checked={this.state.forceFolderDeletion}
                  onChange={(e) => {
                    this.setState({forceFolderDeletion: e.target.checked});
                  }}>Delete sub-items</Checkbox>
              </Row>
            }
            <Row className={styles.configurationConfirmBtn}>
              <Button
                key="cancel"
                size="large"
                onClick={this.closeDeleteFolderDialog}>Cancel</Button>
              <Button
                key="ok"
                type="primary"
                size="large"
                disabled={
                  !this._folderToDeleteInfo || this._folderToDeleteInfo.pending ||
                  (!this.deletingFolderIsEmpty && !this.state.forceFolderDeletion)
                }
                onClick={async () => {
                  const force = !this.deletingFolderIsEmpty && this.state.forceFolderDeletion;
                  await this.deleteFolder(force);
                  this.closeDeleteFolderDialog();
                }}>
                OK
              </Button>
            </Row>
          </Row>
        </Modal>
      </div>
    );
  }

  componentDidUpdate (prevProps, prevState) {
    if (prevProps.folderId !== this.props.folderId) {
      this.setState({
        metadata: undefined,
        issuesItem: null,
        showIssuesPanel: false
      });
    }
    if ((this.state.folderToDelete && !prevState.folderToDelete) ||
      (this.state.folderToDelete && this.state.folderToDelete.id !== prevState.folderToDelete.id)) {
      this._folderToDeleteInfo = this.props.folders.load(this.state.folderToDelete.id);
    }
  }

  componentWillMount () {
    let showDescriptionInfo = localStorage.getItem('show_description');
    if (showDescriptionInfo) {
      try {
        showDescriptionInfo = JSON.parse(showDescriptionInfo);
        this.setState({
          showDescription: showDescriptionInfo.value
        });
      } catch (___) {}
    }
  }
}
