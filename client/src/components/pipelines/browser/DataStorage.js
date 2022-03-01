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
import {inject, observer} from 'mobx-react';
import connect from '../../../utils/connect';
import {Link} from 'react-router';
import classNames from 'classnames';
import {computed, observable} from 'mobx';
import {
  Alert,
  Button,
  Checkbox,
  Col,
  Icon,
  Input,
  message,
  Modal,
  Popover,
  Row,
  Spin,
  Table
} from 'antd';
import Dropdown from 'rc-dropdown';
import Menu, {MenuItem} from 'rc-menu';
import LoadingView from '../../special/LoadingView';
import Breadcrumbs from '../../special/Breadcrumbs';
import DataStorageRequest from '../../../models/dataStorage/DataStoragePage';
import dataStorages from '../../../models/dataStorage/DataStorages';
import folders from '../../../models/folders/Folders';
import pipelinesLibrary from '../../../models/folders/FolderLoadTree';
import DataStorageUpdate from '../../../models/dataStorage/DataStorageUpdate';
import DataStorageUpdateStoragePolicy
from '../../../models/dataStorage/DataStorageUpdateStoragePolicy';
import DataStorageItemRestore from '../../../models/dataStorage/DataStorageItemRestore';
import DataStorageDelete from '../../../models/dataStorage/DataStorageDelete';
import DataStorageItemUpdate from '../../../models/dataStorage/DataStorageItemUpdate';
import DataStorageItemUpdateContent from '../../../models/dataStorage/DataStorageItemUpdateContent';
import DataStorageItemDelete from '../../../models/dataStorage/DataStorageItemDelete';
import GenerateDownloadUrlRequest from '../../../models/dataStorage/GenerateDownloadUrl';
import GenerateDownloadUrlsRequest from '../../../models/dataStorage/GenerateDownloadUrls';
import GenerateFolderDownloadUrl from '../../../models/dataStorage/GenerateFolderDownloadUrl';
import DataStorageConvert from '../../../models/dataStorage/DataStorageConvert';
import EditItemForm from './forms/EditItemForm';
import {DataStorageEditDialog, ServiceTypes} from './forms/DataStorageEditDialog';
import DataStorageNavigation from './forms/DataStorageNavigation';
import RestrictedImagesInfo from './forms/restrict-docker-images/restricted-images-info';
import ConvertToVersionedStorage from './forms/convert-to-vs';
import {
  ContentMetadataPanel,
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY
} from '../../special/splitPanel';
import Metadata from '../../special/metadata/Metadata';
import PreviewModal from '../../search/preview/preview-modal';
import {getTiles, getTilesInfo} from '../../search/preview/vsi-preview';
import UploadButton from '../../special/UploadButton';
import AWSRegionTag from '../../special/AWSRegionTag';
import EmbeddedMiew from '../../applications/miew/EmbeddedMiew';
import parseQueryParameters from '../../../utils/queryParameters';
import displayDate from '../../../utils/displayDate';
import displaySize from '../../../utils/displaySize';
import roleModel from '../../../utils/roleModel';
import moment from 'moment-timezone';
import DataStorageCodeForm from './forms/DataStorageCodeForm';
import DataStorageGenerateSharedLink
from '../../../models/dataStorage/DataStorageGenerateSharedLink';
import {ItemTypes} from '../model/treeStructureFunctions';
import HiddenObjects from '../../../utils/hidden-objects';
import OpenInToolAction from '../../special/file-actions/open-in-tool';
import {
  METADATA_KEY as FS_MOUNTS_NOTIFICATIONS_ATTRIBUTE
} from '../../special/metadata/special/fs-notifications';
import {
  METADATA_KEY as REQUEST_DAV_ACCESS_ATTRIBUTE
} from '../../special/metadata/special/request-dav-access';
import StorageSize from '../../special/storage-size';
import BashCode from '../../special/bash-code';
import {extractFileShareMountList} from './forms/DataStoragePathInput';
import SharedItemInfo from './forms/data-storage-item-sharing/SharedItemInfo';
import {SAMPLE_SHEET_FILE_NAME_REGEXP} from '../../special/sample-sheet/utilities';
import {
  fastCheckPreviewAvailable as fastCheckHCSPreviewAvailable,
  checkPreviewAvailable as checkHCSPreviewAvailable
} from '../../special/hcs-image/utilities/check-preview-available';

import styles from './Browser.css';

const PAGE_SIZE = 40;

@connect({
  dataStorages, folders, pipelinesLibrary
})
@roleModel.authenticationInfo
@inject('awsRegions', 'pipelines')
@HiddenObjects.checkStorages(props => props.params.id)
@inject(({
  authenticatedUserInfo,
  routing,
  dataStorages,
  folders,
  pipelinesLibrary,
  preferences,
  dataStorageCache
}, {params, onReloadTree}) => {
  const queryParameters = parseQueryParameters(routing);
  const showVersions = (queryParameters.versions || 'false').toLowerCase() === 'true';
  return {
    authenticatedUserInfo,
    onReloadTree,
    dataStorageCache,
    storageId: params.id,
    path: decodeURIComponent(queryParameters.path || ''),
    showVersions: showVersions,
    storage: new DataStorageRequest(
      params.id,
      decodeURIComponent(queryParameters.path || ''),
      showVersions,
      PAGE_SIZE
    ),
    info: dataStorages.load(params.id),
    dataStorages,
    pipelinesLibrary,
    folders,
    preferences
  };
})
@observer
export default class DataStorage extends React.Component {
  state = {
    editDialogVisible: false,
    editDropdownVisible: false,
    convertToVSDialogVisible: false,
    downloadUrlModalVisible: false,
    downloadFolderUrlModal: false,
    generateFolderUrlWriteAccess: false,
    selectedItems: [],
    itemsToShare: [],
    shareDialogVisible: false,
    renameItem: null,
    createFolder: false,
    createFile: false,
    currentPage: 0,
    itemsToDelete: null,
    pageMarkers: [null],
    pagePerformed: false,
    selectedFile: null,
    editFile: null,
    shareStorageDialogVisible: false,
    previewModal: null,
    previewAvailable: false,
    previewPending: false
  };

  @observable
  _shareStorageLink = null;

  @observable generateDownloadUrls;

  @computed
  get showMetadata () {
    if (this.state.metadata === undefined && this.props.info.loaded) {
      return this.props.info.value.hasMetadata && roleModel.readAllowed(this.props.info.value);
    }
    return !!this.state.metadata;
  }

  @computed
  get region () {
    if (this.props.info && this.props.info.loaded && this.props.awsRegions.loaded) {
      const {regionId} = this.props.info.value;
      return (this.props.awsRegions.value || []).find(r => +r.id === +regionId);
    }
    return null;
  }

  @computed
  get provider () {
    const region = this.region;
    if (region) {
      return region.provider;
    }
    return null;
  }

  @computed
  get regionName () {
    const region = this.region;
    if (region) {
      return region.regionId || region.name;
    }
    return null;
  }

  @computed
  get generateFolderURLAvailable () {
    return /^azure$/i.test(this.provider) && this.storageAllowSignedUrls;
  }

  @computed
  get storageAllowSignedUrls () {
    return this.props.authenticatedUserInfo.loaded
      ? (
        this.props.authenticatedUserInfo.value.admin ||
        this.props.preferences.storageAllowSignedUrls
      )
      : false;
  }

  @computed
  get dataStorageShareLinkDisclaimer () {
    if (this.props.preferences.loaded) {
      let code = (this.props.preferences.getPreferenceValue('data.sharing.disclaimer') || '');
      code = code.replace(/\\n/g, '\n');
      code = code.replace(/\\r/g, '\r');
      return code;
    }
    return null;
  }

  @computed
  get toolsToMount () {
    const {info = {}} = this.props;
    if (info.loaded && info.value.toolsToMount && !info.value.mountDisabled) {
      return (info.value.toolsToMount || []).map(t => t);
    }
    return undefined;
  }

  @computed
  get fileShareMountList () {
    const {awsRegions} = this.props;
    if (awsRegions && awsRegions.loaded) {
      return extractFileShareMountList(awsRegions.value || []);
    }
    return [];
  }

  @computed
  get fileShareMount () {
    if (this.props.info && this.props.info.loaded) {
      const {
        fileShareMountId,
        type
      } = this.props.info.value || {};
      if (/^nfs$/i.test(type)) {
        return this.fileShareMountList.find(mount => mount.id === fileShareMountId);
      }
    }
    return undefined;
  }

  get sharingEnabled () {
    const {preferences, info} = this.props;
    if (info && info.loaded && info.value) {
      return info.value.type !== 'NFS' &&
        preferences &&
        preferences.loaded &&
        preferences.sharedStoragesSystemDirectory &&
        preferences.dataSharingEnabled;
    }
    return false;
  }

  @computed
  get versionControlsEnabled () {
    const {
      preferences,
      authenticatedUserInfo,
      info
    } = this.props;
    const loaded = info &&
      info.loaded &&
      preferences &&
      preferences.loaded &&
      authenticatedUserInfo &&
      authenticatedUserInfo.loaded;
    if (
      loaded &&
      info.value.type !== 'NFS' &&
      info.value.storagePolicy &&
      info.value.storagePolicy.versioningEnabled
    ) {
      const isAdmin = authenticatedUserInfo.value.admin;
      return isAdmin || preferences.storagePolicyBackupVisibleNonAdmins;
    }
    return false;
  }

  @computed
  get showVersions () {
    if (this.props.info.pending) {
      return false;
    }
    return this.props.info.value.type !== 'NFS' &&
      this.props.showVersions &&
      roleModel.isOwner(this.props.info.value);
  }

  onDataStorageEdit = async (storage) => {
    const dataStorage = {
      id: this.props.storageId,
      parentFolderId: this.props.info.value.parentFolderId,
      name: storage.name,
      description: storage.description,
      path: storage.path,
      mountDisabled: storage.mountDisabled,
      mountPoint: !storage.mountDisabled ? storage.mountPoint : undefined,
      mountOptions: !storage.mountDisabled ? storage.mountOptions : undefined,
      sensitive: storage.sensitive,
      toolsToMount: !storage.mountDisabled ? storage.toolsToMount : undefined
    };
    const hide = message.loading('Updating data storage...');
    const request = new DataStorageUpdate();
    await request.send(dataStorage);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      if (
        this.props.info.value.policySupported &&
        storage.serviceType !== ServiceTypes.fileShare &&
        (
          storage.longTermStorageDuration !== undefined ||
          storage.shortTermStorageDuration !== undefined ||
          storage.backupDuration !== undefined ||
          !storage.versioningEnabled
        )
      ) {
        const updatePolicyRequest = new DataStorageUpdateStoragePolicy();
        await updatePolicyRequest.send({
          id: this.props.storageId,
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
          this.closeEditDialog();
          this.props.info.fetch();
          this.props.folders.invalidateFolder(this.props.info.value.parentFolderId);
          if (this.props.onReloadTree) {
            this.props.onReloadTree(!this.props.info.value.parentFolderId);
          }
        }
      } else {
        hide();
        this.closeEditDialog();
        this.props.info.fetch();
        this.props.folders.invalidateFolder(this.props.info.value.parentFolderId);
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!this.props.info.value.parentFolderId);
        }
      }
    }
  };

  refreshList = async () => {
    await this.props.info.fetch();
    await this.props.storage.fetchPage(null);
    this.setState({
      currentPage: 0,
      pageMarkers: [null],
      pagePerformed: false
    });
  };

  isDataRefreshing = () => {
    return this.props.storage.pending;
  };

  afterDataStorageEdit = () => {
    this.props.info.fetch();
  };

  openEditDialog = () => {
    this.setState({editDialogVisible: true});
  };

  closeEditDialog = () => {
    this.setState({editDialogVisible: false}, () => {
      this.props.info.fetch();
    });
  };

  renameDataStorage = async (name) => {
    const dataStorage = {
      id: this.props.storageId,
      parentFolderId: this.props.info.value.parentFolderId,
      name: name,
      description: this.props.info.value.description,
      path: this.props.info.value.path
    };
    const hide = message.loading('Renaming data storage...');
    const request = new DataStorageUpdate();
    await request.send(dataStorage);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.props.info.fetch();
      if (this.props.info.value.parentFolderId) {
        this.props.folders.invalidateFolder(this.props.info.value.parentFolderId);
      } else {
        this.props.pipelinesLibrary.invalidateCache();
      }
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this.props.info.value.parentFolderId);
      }
      hide();
      this.closeEditDialog();
    }
  };

  deleteStorage = async (cloud) => {
    const request = new DataStorageDelete(this.props.storageId, cloud);
    const hide = message.loading(`${cloud ? 'Deleting' : 'Unregistering'} storage ${this.props.info.value.name}...`, 0);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      await this.props.dataStorages.fetch();
      if (this.props.info.value.parentFolderId) {
        this.props.folders.invalidateFolder(this.props.info.value.parentFolderId);
      } else {
        this.props.pipelinesLibrary.invalidateCache();
      }
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!this.props.info.value.parentFolderId);
      }
      if (this.props.info.value.parentFolderId) {
        this.props.router.push(`/folder/${this.props.info.value.parentFolderId}`);
      } else {
        this.props.router.push('/library');
      }
    }
  };

  navigate = (id, path) => {
    if (path && path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }
    if (path) {
      this.props.router.push(`/storage/${id}?path=${encodeURIComponent(path)}&versions=${this.showVersions}`);
    } else {
      this.props.router.push(`/storage/${id}?versions=${this.showVersions}`);
    }
    this.setState({currentPage: 0, pageMarkers: [null], pagePerformed: false, selectedItems: [], selectedFile: null});
  };

  navigateFull = (path) => {
    if (path && !this.props.info.pending && !this.props.info.error) {
      let {pathMask, path: bucketPath, delimiter = '/'} = this.props.info.value;
      let bucketPathMask = `^[^:/]+://[^${delimiter}]+(|${delimiter}(.*))$`;
      if (pathMask) {
        if (pathMask.endsWith(delimiter)) {
          pathMask = pathMask.substr(0, pathMask.length - 1);
        }
        bucketPathMask = `^${pathMask}(|${delimiter}(.*))$`;
      } else if (bucketPath) {
        if (bucketPath.endsWith(delimiter)) {
          bucketPath = bucketPath.substr(0, bucketPath.length - 1);
        }
        bucketPathMask = `^[^:/]+://${bucketPath}(|${delimiter}(.*))$`;
      }
      const regExp = new RegExp(bucketPathMask, 'i');
      const execResult = regExp.exec(path);
      if (execResult && execResult.length === 3) {
        let relativePath = execResult[2] || '';
        if (relativePath && relativePath.endsWith(delimiter)) {
          relativePath = relativePath.substr(0, relativePath.length - 1);
        }
        if (relativePath) {
          this.props.router.push(`/storage/${this.props.storageId}?path=${encodeURIComponent(relativePath)}&versions=${this.showVersions}`);
        } else {
          this.props.router.push(`/storage/${this.props.storageId}?versions=${this.showVersions}`);
        }
        this.setState({currentPage: 0, pageMarkers: [null], pagePerformed: false, selectedItems: [], selectedFile: null});
      } else {
        message.error('You cannot navigate to another storage.', 3);
      }
    }
  };

  canGoToParent () {
    return this.props.path;
  }

  parentDirectory () {
    if (this.props.path) {
      const parts = this.props.path.split('/');
      if (parts.length > 1) {
        parts.pop();
        return parts.join('/');
      }
    }
    return undefined;
  }

  labelsRenderer = (labels, item) => {
    const labelsList = [];
    for (let key in labels) {
      if (labels.hasOwnProperty(key)) {
        labelsList.push({
          key: key,
          value: labels[key]
        });
      }
    }
    return labelsList.map(l => (<span className={styles.label} key={l.key}>{l.value}</span>));
  };

  downloadSingleFile = async (event, item) => {
    event.stopPropagation();
    event.preventDefault();
    const hide = message.loading(`Fetching ${item.name} url...`, 0);
    const request = new GenerateDownloadUrlRequest(this.props.storageId, item.path, item.version);
    await request.fetch();
    if (request.error) {
      hide();
      message.error(request.error);
    } else {
      hide();
      const a = document.createElement('a');
      a.href = request.value.url;
      a.download = item.name;
      a.style.display = 'none';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }
    return false;
  };

  toggleGenerateDownloadUrlsModalFn = () => {
    let downloadUrlModalVisible = !this.state.downloadUrlModalVisible;
    if (downloadUrlModalVisible && this.state.selectedItems) {
      this.generateDownloadUrls = new GenerateDownloadUrlsRequest(this.props.storageId);
      this.generateDownloadUrls.send({
        paths: this.state.selectedItems.map(i => i.path)
      });
    } else {
      this.generateDownloadUrls = null;
    }
    this.setState({downloadUrlModalVisible, downloadFolderUrlModal: false});
  };

  showGenerateFolderDownloadUrlsModalFn = () => {
    if (this.props.info && this.props.info.loaded && this.generateFolderURLAvailable) {
      const writeAllowed = roleModel.writeAllowed(this.props.info.value);
      this.setState({
        generateFolderUrlWriteAccess: writeAllowed,
        downloadUrlModalVisible: true,
        downloadFolderUrlModal: true
      }, this.generateFolderDownloadUrl);
    }
  };

  generateFolderDownloadUrl = async () => {
    const hide = message.loading('Generating url...', 0);
    const {generateFolderUrlWriteAccess} = this.state;
    this.generateDownloadUrls = new GenerateFolderDownloadUrl(this.props.storageId);
    let path = this.props.path || '';
    if (path.length > 0 && !path.endsWith('/')) {
      path += '/';
    }
    await this.generateDownloadUrls.send({
      paths: [path],
      permissions: ['READ', generateFolderUrlWriteAccess ? 'WRITE' : undefined].filter(Boolean)
    });
    hide();
  };

  closeDownloadUrlModal = () => {
    this.setState({downloadUrlModalVisible: false, downloadFolderUrlModal: false});
  };

  openRenameItemDialog = (event, item) => {
    event.stopPropagation();
    this.setState({renameItem: item});
  };

  closeRenameItemDialog = (clearSelection = false) => {
    if (clearSelection) {
      this.setState({
        renameItem: null,
        selectedFile: null
      });
    } else {
      this.setState({renameItem: null});
    }
  };

  renameItem = async ({name}) => {
    const hide = message.loading(`Renaming ${this.state.renameItem.name}...`, 0);
    const request = new DataStorageItemUpdate(this.props.storageId);
    let path = this.props.path || '';
    if (path.length > 0 && !path.endsWith('/')) {
      path += '/';
    }
    const payload = [{
      oldPath: this.state.renameItem.path,
      path: `${path}${name}`,
      type: this.state.renameItem.type,
      action: 'Move'
    }];
    await request.send(payload);
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      const {renameItem, selectedFile} = this.state;
      this.closeRenameItemDialog(
        renameItem && selectedFile && renameItem.path === selectedFile.path
      );
      await this.refreshList();
    }
  };

  openCreateFolderDialog = () => {
    this.setState({createFolder: true});
  };

  closeCreateFolderDialog = () => {
    this.setState({createFolder: false});
  };

  openCreateFileDialog = () => {
    this.setState({createFile: true});
  };

  closeCreateFileDialog = () => {
    this.setState({createFile: false});
  };

  createFolder = async ({name}) => {
    const trimmedName = name.trim();
    const hide = message.loading(`Creating folder '${trimmedName}'...`, 0);
    const request = new DataStorageItemUpdate(this.props.storageId);
    let path = this.props.path || '';
    if (path.length > 0 && !path.endsWith('/')) {
      path += '/';
    }
    const payload = [{
      path: `${path}${trimmedName}`,
      type: 'Folder',
      action: 'Create'
    }];
    await request.send(payload);
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeCreateFolderDialog();
      await this.refreshList();
    }
  };

  createFile = async ({name, content}) => {
    const trimmedName = name.trim();
    const hide = message.loading(`Creating file '${trimmedName}'...`, 0);
    const request = new DataStorageItemUpdate(this.props.storageId);
    let path = this.props.path || '';
    if (path.length > 0 && !path.endsWith('/')) {
      path += '/';
    }
    const payload = [{
      path: `${path}${trimmedName}`,
      type: 'File',
      contents: content ? btoa(content) : '',
      action: 'Create'
    }];
    await request.send(payload);
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      this.closeCreateFileDialog();
      await this.refreshList();
    }
  };

  saveEditableFile = async (path, content) => {
    const currentItemContent = this.props.dataStorageCache.getContent(
      this.props.storageId,
      this.state.selectedFile.path,
      this.state.selectedFile.version
    );
    const request = new DataStorageItemUpdateContent(this.props.storageId, path);
    const hide = message.loading('Uploading changes...');

    await request.send(content);
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      await this.props.storage.fetch();
      await currentItemContent.fetch();
      this.closeEditFileForm();
      await this.refreshList();
    }
  };

  openDeleteModal = (items) => {
    this.setState({itemsToDelete: items});
  };

  closeDeleteModal = () => {
    this.setState({itemsToDelete: null});
  };

  removeItemConfirm = (event, item) => {
    event.stopPropagation();
    if (this.showVersions) {
      if (item.isVersion) {
        const removeItem = () => this.removeItems([item], true, true);
        let content = `Are you sure you want to delete selected version of the ${item.type.toLowerCase()} '${item.name}' from object storage?`;
        Modal.confirm({
          title: `Remove ${item.type.toLowerCase()}'s version`,
          content: content,
          style: {
            wordWrap: 'break-word'
          },
          onOk () {
            removeItem();
          }
        });
      } else if (item.deleteMarker) {
        const removeItem = () => this.removeItems([item], true, true);
        let content = `Are you sure you want to delete ${item.type.toLowerCase()} '${item.name}' from object storage?`;
        if (item.type.toLowerCase() === 'folder') {
          content = (
            <div>
              <Row>
                {content}
              </Row>
              <Row>
                All child folders and files will be removed.
              </Row>
            </div>
          );
        }
        Modal.confirm({
          title: `Remove ${item.type.toLowerCase()}`,
          content: content,
          style: {
            wordWrap: 'break-word'
          },
          onOk () {
            removeItem();
          }
        });
      } else {
        this.openDeleteModal([item]);
      }
    } else {
      const removeItem = () => this.removeItems([item], false, true);
      let content = `Are you sure you want to delete ${item.type.toLowerCase()} '${item.name}'?`;
      if (item.type.toLowerCase() === 'folder') {
        content = (
          <div>
            <Row>
              {content}
            </Row>
            <Row>
              All child folders and files will be removed.
            </Row>
          </div>
        );
      }
      Modal.confirm({
        title: `Remove ${item.type.toLowerCase()}`,
        content: content,
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          removeItem();
        }
      });
    }
  };

  removeItems = async (items, totally, clearSelectedItems, afterRemove) => {
    let removeMessage;
    if (items && items.length === 1) {
      removeMessage = `Removing '${items[0].name || items[0].path}'...`;
    } else if (items && items.length > 1) {
      removeMessage = `Removing '${items.length} items'...`;
    } else {
      return;
    }
    const hide = message.loading(removeMessage);
    const request = new DataStorageItemDelete(this.props.storageId, totally);
    await request.send(items.map(item => {
      return {
        path: item.path,
        type: item.type,
        version: item.isVersion ? item.version : undefined
      };
    }));
    hide();
    if (request.error) {
      message.error(request.error, 3);
    } else {
      let selectedFile = this.state.selectedFile;
      if (selectedFile && items.filter(i => i.path === selectedFile.path).length > 0) {
        selectedFile = null;
      }
      if (clearSelectedItems) {
        const selectedItems = this.state.selectedItems;
        const removeItemFromSelectedList = (item) => {
          const [selectedItem] = selectedItems.filter(s => s.name === item.name && s.type === item.type);
          if (selectedItem) {
            const index = selectedItems.indexOf(selectedItem);
            selectedItems.splice(index, 1);
          }
        };
        items.forEach(item => removeItemFromSelectedList(item));
        this.setState({selectedItems});
      }
      this.setState({
        pagePerformed: false,
        selectedFile
      }, async () => {
        await this.refreshList();
        afterRemove && afterRemove();
      });
    }
  };

  removeSelectedItemsConfirm = (event) => {
    const items = this.state.selectedItems.map(item => {
      return {
        path: item.path,
        type: item.type
      };
    });
    event.stopPropagation();
    if (this.showVersions) {
      this.openDeleteModal(items);
    } else {
      const removeItems = () => this.removeItems(items, false, false, () => {
        this.setState({selectedItems: []});
      });
      Modal.confirm({
        title: 'Remove all selected items?',
        style: {
          wordWrap: 'break-word'
        },
        onOk () {
          removeItems();
        }
      });
    }
  };

  onRestoreClicked = async (item, version) => {
    const hide = message.loading('Restoring item...');
    const request = new DataStorageItemRestore(this.props.storageId, item.path, version);
    await request.send();
    hide();
    if (request.error) {
      message.error(request.error, 3);
    } else {
      await this.refreshList();
    }
  };

  canRestoreItem = (item) => {
    if (!this.showVersions) {
      return false;
    }
    if ((item.type && item.type.toLowerCase() === 'folder') || !item.isVersion || item.deleteMarker) {
      return false;
    }
    return !item.latest;
  };

  actionsRenderer = (type, item) => {
    const actions = [];
    let separatorIndex = 0;
    const separator = () => {
      separatorIndex += 1;
      return (
        <div
          key={`separator_${separatorIndex}`}
          style={{
            marginLeft: 5,
            width: 3,
            height: 12,
            display: 'inline-block'
          }} />
      );
    };
    if (item.downloadable) {
      actions.push((
        <OpenInToolAction
          key="open-in-tool"
          file={item.path}
          storageId={this.props.storageId}
          className="cp-button"
          style={{
            display: 'flex',
            textDecoration: 'none',
            alignItems: 'center'
          }}
        />
      ));
      actions.push(
        <a
          key="download"
          id={`download ${item.name}`}
          className="cp-button"
          href={GenerateDownloadUrlRequest.getRedirectUrl(this.props.storageId, item.path, item.version)}
          target="_blank"
          download={item.name}
          onClick={(e) => this.downloadSingleFile(e, item)}
        >
          <Icon type="download" />
        </a>
      );
    }
    if (item.isVersion
      ? item.editable && this.versionControlsEnabled
      : item.editable
    ) {
      actions.push(
        <Button
          id={`edit ${item.name}`}
          key="rename"
          size="small"
          onClick={(event) => this.openRenameItemDialog(event, item)}>
          <Icon type="edit" />
        </Button>
      );
    }
    if (this.versionControlsEnabled && this.canRestoreItem(item)) {
      actions.push(
        <Button id={`restore ${item.name}`} key="restore" size="small" onClick={() => this.onRestoreClicked(item, item.isVersion ? item.version : undefined)}>
          <Icon type="reload" />
        </Button>
      );
    }
    if (item.isVersion
      ? item.deletable && this.versionControlsEnabled
      : item.deletable
    ) {
      actions.push(separator());
      actions.push(
        <Button
          id={`remove ${item.name}`}
          key="remove"
          type="danger"
          size="small"
          onClick={(event) => this.removeItemConfirm(event, item)}>
          <Icon type="delete" />
        </Button>
      );
    }
    return (
      <div className={styles.itemActionsContainer}>
        {actions}
      </div>
    );
  };

  fileIsSelected = (item) => {
    return this.state.selectedItems.filter(s => s.name === item.name && s.type === item.type).length === 1;
  };

  selectFile = (item) => () => {
    const selectedItems = this.state.selectedItems;
    const [selectedItem] = this.state.selectedItems.filter(s => s.name === item.name && s.type === item.type);
    if (selectedItem) {
      const index = selectedItems.indexOf(selectedItem);
      selectedItems.splice(index, 1);
    } else {
      selectedItems.push(item);
    }
    this.setState({selectedItems});
  };

  openShareStorageDialog = () => {
    this.setState({shareStorageDialogVisible: true});
  };

  closeShareStorageDialog = () => {
    this._shareStorageLink = null;
    this.setState({shareStorageDialogVisible: false});
  };

  openShareItemDialog = (event) => {
    const {selectedItems = []} = this.state;
    const items = selectedItems
      .filter(o => o.shareAvailable);
    const {storageId} = this.props;
    event && event.stopPropagation();
    if (!items || items.length === 0) {
      this.setState({
        itemsToShare: [{
          type: 'folder',
          path: this.props.path,
          storageId
        }],
        shareDialogVisible: true
      });
    } else {
      this.setState({
        itemsToShare: items ? items.slice().map((o, i) => ({...o, storageId})) : [],
        shareDialogVisible: true
      });
    }
  };

  closeShareItemDialog = () => {
    return this.setState({
      itemsToShare: [],
      shareDialogVisible: false
    });
  };

  openEditFileForm = (item) => {
    const sensitive = this.props.info.loaded
      ? this.props.info.value.sensitive
      : false;
    if (sensitive) {
      message.info('This operation is forbidden for sensitive storages', 5);
    } else {
      this.setState({editFile: item});
    }
  };

  closeEditFileForm = () => {
    this.setState({editFile: null});
  };

  renderPreviewModal = () => {
    const {previewModal} = this.state;
    if (!previewModal) {
      return null;
    }
    return (
      <PreviewModal
        preview={previewModal}
        onClose={this.closePreviewModal}
      />
    );
  };

  metadataRenderFn = () => {
    const {selectedFile} = this.state;
    if (!selectedFile) {
      return null;
    }
    const extension = (selectedFile.path || '')
      .split('.')
      .pop()
      .toLowerCase();
    if (extension === 'vsi' || extension === 'mrxs' || extension === 'hcs') {
      return (
        <Row
          key="preview body"
          style={{
            color: '#777',
            marginTop: 5,
            marginBottom: 5
          }}
        >
          <span
            onClick={() => this.openPreviewModal(selectedFile)}
            className={classNames('cp-link', styles.metadataPreviewBtn)}
          >
            Click
          </span>
          {`to preview ${extension.toUpperCase()} file.`}
        </Row>
      );
    }
    return null;
  };

  openPreviewModal = (file, event) => {
    const {storageId} = this.props;
    event && event.stopPropagation();
    if (storageId && file) {
      this.setState({previewModal: {
        id: file.path,
        name: file.name,
        parentId: storageId,
        type: 'S3_FILE'
      }});
    }
  };

  closePreviewModal = () => {
    this.setState({previewModal: null});
  };

  checkWsiPreviewAvailability = (file) => {
    if (!file) {
      return;
    }
    const {storageId} = this.props;
    const info = getTilesInfo(file.path);
    if (info) {
      this.setState({previewPending: true}, () => {
        getTiles(storageId, info.tilesFolders)
          .then((tiles) => {
            if (tiles) {
              this.setState({
                previewPending: false,
                previewAvailable: true
              });
            } else {
              this.setState({
                previewPending: false,
                previewAvailable: false
              });
            }
          });
      });
    }
  };

  checkHcsPreviewAvailability = (file) => {
    if (!file) {
      return;
    }
    const {storageId} = this.props;
    this.setState({
      previewPending: true
    }, async () => {
      let error;
      try {
        const available = await checkHCSPreviewAvailable({path: file.path, storageId});
        if (!available) {
          throw new Error('HCS preview not available');
        }
      } catch (e) {
        error = true;
      } finally {
        this.setState({
          previewPending: false,
          previewAvailable: !error
        });
      }
    });
  };

  getStorageItemsTable = () => {
    const getList = () => {
      const items = [];
      if (this.canGoToParent()) {
        items.push({
          key: `folder_${this.parentDirectory()}`,
          name: '..',
          path: this.parentDirectory(),
          type: 'folder',
          downloadable: false,
          editable: false,
          selectable: false,
          shareAvailable: false
        });
      }
      const getChildList = (item, versions, sensitive) => {
        if (!versions || !this.showVersions) {
          return undefined;
        }
        const childList = [];
        for (let version in versions) {
          if (versions.hasOwnProperty(version)) {
            childList.push({
              key: `${item.type}_${item.path}_${version}`,
              ...versions[version],
              downloadable: item.type.toLowerCase() === 'file' &&
                !versions[version].deleteMarker &&
                !sensitive &&
                (
                  !item.labels ||
                  !item.labels['StorageClass'] ||
                  item.labels['StorageClass'].toLowerCase() !== 'glacier'
                ),
              editable: versions[version].version === item.version &&
              roleModel.writeAllowed(this.props.info.value) &&
              !versions[version].deleteMarker,
              deletable: roleModel.writeAllowed(this.props.info.value),
              selectable: false,
              shareAvailable: false,
              latest: versions[version].version === item.version,
              isVersion: true
            });
          }
        }
        childList.sort((a, b) => {
          const dateA = moment(a.changed);
          const dateB = moment(b.changed);
          if (dateA > dateB) {
            return -1;
          } else if (dateA < dateB) {
            return 1;
          }
          return 0;
        });
        return childList;
      };
      let results = [];
      const sensitive = this.props.info.loaded
        ? this.props.info.value.sensitive
        : false;
      if (this.props.storage.loaded) {
        results = this.props.storage.value.results || [];
      }
      items.push(...results.map(i => {
        return {
          key: `${i.type}_${i.path}`,
          ...i,
          downloadable: i.type.toLowerCase() === 'file' &&
            !i.deleteMarker &&
            !sensitive &&
            (
              !i.labels ||
              !i.labels['StorageClass'] ||
              i.labels['StorageClass'].toLowerCase() !== 'glacier'
            ),
          editable: roleModel.writeAllowed(this.props.info.value) && !i.deleteMarker,
          shareAvailable: !i.deleteMarker && this.sharingEnabled,
          deletable: roleModel.writeAllowed(this.props.info.value),
          children: getChildList(i, i.versions, sensitive),
          selectable: !i.deleteMarker,
          miew: !i.deleteMarker &&
                i.type.toLowerCase() === 'file' &&
                i.path.toLowerCase().endsWith('.pdb'),
          vsi: !i.deleteMarker && i.type.toLowerCase() === 'file' && (
            i.path.toLowerCase().endsWith('.vsi') ||
            i.path.toLowerCase().endsWith('.mrxs')
          ),
          hcs: !i.deleteMarker &&
            i.type.toLowerCase() === 'file' &&
            fastCheckHCSPreviewAvailable({path: i.path, storageId: this.props.storageId})
        };
      }));
      return items;
    };

    this.tableData = this.props.storage.pending ? (this.tableData || []) : getList();
    let hasAppsColumn = false;
    let hasVersions = false;
    for (
      let i = 0; i < this.tableData.length; i++) {
      const item = this.tableData[i];
      if (item.miew || item.vsi || item.hcs) {
        hasAppsColumn = true;
      }
      if (item.versions) {
        hasVersions = true;
      }
    }
    const getItemIcon = (item) => {
      if (!item) {
        return null;
      }
      if (/^file$/i.test(item.type) && SAMPLE_SHEET_FILE_NAME_REGEXP.test(item.name)) {
        return (
          <Icon
            className={classNames(styles.itemType, 'cp-primary')}
            type="appstore-o"
          />
        );
      }
      return (
        <Icon
          className={styles.itemType}
          type={item.type.toLowerCase()}
        />
      );
    };
    const selectionColumn = {
      key: 'selection',
      title: '',
      className: (this.showVersions || hasVersions) ? styles.checkboxCellVersions : styles.checkboxCell,
      render: (item) => {
        if (item.selectable && (item.downloadable || item.editable || item.shareAvailable)) {
          return (
            <Checkbox
              checked={this.fileIsSelected(item)}
              onChange={this.selectFile(item)} />
          );
        } else {
          return <span />;
        }
      }
    };
    const typeColumn = {
      dataIndex: 'type',
      key: 'type',
      title: '',
      className: styles.itemTypeCell,
      onCellClick: (item) => this.didSelectDataStorageItem(item),
      render: (text, item) => getItemIcon(item)
    };
    const appsColumn = {
      key: 'apps',
      className: styles.appCell,
      render: (item) => {
        const apps = [];
        if (item.miew) {
          apps.push(
            <Popover
              mouseEnterDelay={1}
              key="miew"
              content={
                <div className={styles.miewPopoverContainer}>
                  <EmbeddedMiew
                    previewMode
                    s3item={{
                      storageId: this.props.storageId,
                      path: item.path,
                      version: item.version
                    }} />
                </div>
              }
              trigger="hover">
              <Link
                className={styles.appLink}
                to={
                  item.version
                    ? `miew?storageId=${this.props.storageId}&path=${item.path}&version=${item.version}`
                    : `miew?storageId=${this.props.storageId}&path=${item.path}`
                }
                target="_blank">
                <img src="miew_logo.png" />
              </Link>
            </Popover>
          );
        }
        if (item.vsi) {
          apps.push(
            <div
              className={styles.appLink}
              onClick={(event) => this.openPreviewModal(item, event)}
              key={item.key}
            >
              <img src="icons/file-extensions/vsi.png" />
            </div>
          );
        }
        if (item.hcs) {
          apps.push(
            <div
              className={styles.appLink}
              onClick={(event) => this.openPreviewModal(item, event)}
              key={item.key}
            >
              <img src="icons/file-extensions/hcs.png" />
            </div>
          );
        }
        return apps;
      }
    };
    const nameColumn = {
      dataIndex: 'name',
      key: 'name',
      title: 'Name',
      className: styles.nameCell,
      render: (text, item) => {
        if (item.latest) {
          return `${text} (latest)`;
        }
        return text;
      },
      onCellClick: (item) => this.didSelectDataStorageItem(item)
    };
    const sizeColumn = {
      dataIndex: 'size',
      key: 'size',
      title: 'Size',
      className: styles.sizeCell,
      render: size => displaySize(size),
      onCellClick: (item) => this.didSelectDataStorageItem(item)
    };
    const changedColumn = {
      dataIndex: 'changed',
      key: 'changed',
      title: 'Date changed',
      className: styles.changedCell,
      render: (date) => date ? displayDate(date) : '',
      onCellClick: (item) => this.didSelectDataStorageItem(item)
    };
    const labelsColumn = {
      dataIndex: 'labels',
      key: 'labels',
      title: '',
      className: styles.labelsCell,
      render: this.labelsRenderer,
      onCellClick: (item) => this.didSelectDataStorageItem(item)
    };
    const actionsColumn = {
      key: 'actions',
      className: styles.itemActions,
      render: this.actionsRenderer
    };

    const columns = [];
    columns.push(selectionColumn);
    columns.push(typeColumn);
    if (hasAppsColumn) {
      columns.push(appsColumn);
    }
    columns.push(nameColumn);
    columns.push(sizeColumn);
    columns.push(changedColumn);
    columns.push(labelsColumn);
    columns.push(actionsColumn);

    return {
      columns,
      data: this.tableData
    };
  };

  didSelectDataStorageItem = (item) => {
    if (item.type.toLowerCase() === 'folder') {
      let path = item.path;
      if (path && path.endsWith('/')) {
        path = path.substring(0, path.length - 1);
      }
      if (path) {
        this.props.router.push(`/storage/${this.props.storageId}?path=${encodeURIComponent(path)}&versions=${this.showVersions}`);
      } else {
        this.props.router.push(`/storage/${this.props.storageId}?versions=${this.showVersions}`);
      }
      this.setState({
        selectedFile: null,
        currentPage: 0,
        pageMarkers: [null],
        pagePerformed: false,
        selectedItems: []
      });
    } else if (item.type.toLowerCase() === 'file' && !item.deleteMarker) {
      const extension = (item.path || '')
        .split('.')
        .pop()
        .toLowerCase();
      this.setState({
        selectedFile: item,
        metadata: true
      }, () => {
        switch (extension) {
          case 'vsi':
          case 'mrxs':
            this.checkWsiPreviewAvailability(item);
            break;
          case 'hcs':
            this.checkHcsPreviewAvailability(item);
            break;
          default: return false;
        }
      });
    }
  };

  get bulkDownloadEnabled () {
    if (this.props.info.loaded && this.props.info.value.sensitive) {
      return false;
    }
    const selectedItemsLength = this.state.selectedItems.length;
    const downloadableSelectedItemsLength = this.state.selectedItems
      .filter(item => item.downloadable).length;
    return selectedItemsLength > 0 &&
      selectedItemsLength === downloadableSelectedItemsLength;
  }

  get removeAllSelectedItemsEnabled () {
    const selectedItemsLength = this.state.selectedItems.length;
    const editableSelectedItemsLength = this.state.selectedItems
      .filter(item => item.editable).length;
    return selectedItemsLength > 0 &&
      selectedItemsLength === editableSelectedItemsLength;
  }

  get selectAllAvailable () {
    if (this.props.storage.loaded &&
      this.props.storage.value &&
      this.props.storage.value.results) {
      let allSelected = false;
      if (this.state.selectedItems) {
        allSelected = true;
        const values = (this.props.storage.value.results || []);
        for (let i = 0; i < values.length; i++) {
          const value = values[i];
          if (this.state.selectedItems.filter(si => si.path === value.path && si.type === value.type).length === 0) {
            allSelected = false;
            break;
          }
        }
      }
      return !allSelected;
    }
    return false;
  }

  get clearSelectionVisible () {
    return this.state.selectedItems.length > 0;
  }

  selectAll = (type) => {
    if (this.props.storage.loaded && this.tableData) {
      const selectedItems = this.tableData.filter(item => {
        if (!item.editable && !item.downloadable) {
          return false;
        }
        if (type) {
          return item.type.toLowerCase() === type.toLowerCase();
        } else {
          return true;
        }
      });
      this.setState({selectedItems});
    }
  };

  clearSelection = () => {
    this.setState({selectedItems: []});
  };

  showFilesVersionsChanged = (e) => {
    if (this.props.path) {
      this.props.router.push(`/storage/${this.props.storageId}?path=${encodeURIComponent(this.props.path)}&versions=${e.target.checked}`);
    } else {
      this.props.router.push(`/storage/${this.props.storageId}?versions=${e.target.checked}`);
    }
  };

  prevPage = () => {
    if (this.state.currentPage > 0) {
      const currentPage = this.state.currentPage - 1;
      const marker = this.state.pageMarkers[currentPage];
      this.props.storage.fetchPage(marker);
      this.setState({
        currentPage,
        pagePerformed: false
      });
    }
  };

  nextPage = () => {
    if (this.state.currentPage + 1 < this.state.pageMarkers.length) {
      const currentPage = this.state.currentPage + 1;
      const marker = this.state.pageMarkers[currentPage];
      this.props.storage.fetchPage(marker);
      this.setState({
        currentPage,
        pagePerformed: false
      });
    }
  };

  @computed
  get isFileSelectedEmpty () {
    if (!this.state.selectedFile) {
      return false;
    }
    const [selectedFile] = this.tableData
      .filter(file => file.name === this.state.selectedFile.name);

    return !selectedFile || selectedFile.size === 0 || !(selectedFile.size);
  };

  openConvertToVersionedStorageDialog = (callback) => {
    this.setState({
      convertToVSDialogVisible: true
    }, callback);
  };

  closeConvertToVersionedStorageDialog = (callback) => {
    this.setState({
      convertToVSDialogVisible: false
    }, callback);
  };

  onConvertStorageToVS = () => {
    return new Promise((resolve) => {
      const hide = message.loading('Converting to Versioned Storage...', 0);
      const request = new DataStorageConvert(this.props.storageId);
      request.send({})
        .then(() => {
          if (request.loaded) {
            return Promise.resolve(request.value);
          } else {
            throw new Error(request.error || 'Error converting to Versioned Storage');
          }
        })
        .catch(e => {
          message.error(e.message, 5);
          return Promise.resolve();
        })
        .then((vs) => {
          if (vs) {
            this.props.pipelines.fetch();
            if (this.props.info.value.parentFolderId) {
              this.props.folders.invalidateFolder(this.props.info.value.parentFolderId);
            }
            this.props.pipelinesLibrary.invalidateCache();
            if (this.props.onReloadTree) {
              this.props.onReloadTree(
                true,
                this.props.info.value.parentFolderId
              );
            }
          }
          hide();
          if (vs) {
            let hideInfo;
            const openLink = () => {
              const {router} = this.props;
              router && router.push(`/vs/${vs.id}`);
              hideInfo && hideInfo();
            };
            hideInfo = message.info(
              (
                <div style={{display: 'inline'}}>
                  <a onClick={openLink}><b>{vs.name}</b> versioned storage</a> was created.
                </div>
              ),
              5
            );
          }
          this.closeConvertToVersionedStorageDialog(resolve);
        });
    });
  };

  renderEditAction = () => {
    const convertToVersionedStorageActionAvailable = this.props.info &&
      this.props.info.loaded &&
      roleModel.isOwner(this.props.info.value) &&
      this.props.info.value.type === 'NFS' &&
      (
        this.props.path ||
        (
          this.props.storage.loaded &&
          ((this.props.storage.value || {}).results || []).length > 0
        ) ||
        (this.state.pageMarkers || []).length > 1
      );
    if (convertToVersionedStorageActionAvailable) {
      const {editDropdownVisible} = this.state;
      const editActionSelect = ({key}) => {
        this.setState({
          editDropdownVisible: false
        }, () => {
          switch (key) {
            case 'edit':
              this.openEditDialog();
              break;
            case 'convert':
              this.openConvertToVersionedStorageDialog();
              break;
          }
        });
      };
      return (
        <Dropdown
          placement="bottomRight"
          trigger={['click']}
          visible={editDropdownVisible}
          onVisibleChange={visible => this.setState({editDropdownVisible: visible})}
          overlay={
            <Menu
              selectedKeys={[]}
              onClick={editActionSelect}
              style={{width: 200, cursor: 'pointer'}}>
              <MenuItem
                id="edit-storage-action-button"
                className="edit-storage-button"
                key="edit"
              >
                <Icon type="edit" /> Edit
              </MenuItem>
              <MenuItem
                id="convert-storage-button"
                className="convert-storage-action-button"
                key="convert"
              >
                <Icon type="inbox" className="cp-versioned-storage" /> Convert to Versioned Storage
              </MenuItem>
            </Menu>
          }
          key="edit actions"
        >
          <Button
            id="edit-storage-button"
            size="small"
          >
            <Icon
              type="setting"
              style={{
                lineHeight: 'inherit',
                verticalAlign: 'middle'
              }}
            />
          </Button>
        </Dropdown>
      );
    }
    return (
      <Button
        id="edit-storage-button"
        size="small"
        onClick={() => this.openEditDialog()}>
        <Icon
          type="setting"
          style={{
            lineHeight: 'inherit',
            verticalAlign: 'middle'
          }}
        />
      </Button>
    );
  };

  renderShareButton = () => {
    const {selectedItems = []} = this.state;
    const itemsAvailableForShare = selectedItems
      .filter(o => o.shareAvailable);
    if (!this.sharingEnabled || (itemsAvailableForShare.length === 0 && !this.props.path)) {
      return undefined;
    }
    let buttonText = (
      <span>
        Share <b>current</b> folder
      </span>
    );
    if (itemsAvailableForShare.length === 1) {
      buttonText = (
        <span
          className="cp-ellipsis-text"
          style={{maxWidth: 300, display: 'block'}}
        >
          Share <b>{itemsAvailableForShare[0].name}</b> {itemsAvailableForShare[0].type}
        </span>
      );
    }
    if (itemsAvailableForShare.length > 1) {
      buttonText = (
        <span>
          Share <b>{itemsAvailableForShare.length}</b> items
        </span>
      );
    }
    return (
      <Button
        id="share-selected-button"
        size="small"
        onClick={(e) => this.openShareItemDialog(e)
        }
      >
        {buttonText}
      </Button>
    );
  };

  render () {
    if (
      (!this.props.info.loaded && this.props.info.pending) ||
      (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending)
    ) {
      return <LoadingView />;
    }
    if (this.props.info.error) {
      return <Alert message={this.props.info.error} type="error" />;
    }
    if (this.props.authenticatedUserInfo.error) {
      return <Alert message={this.props.authenticatedUserInfo.error} type="error" />;
    }
    let contents;
    if (!this.props.storage.error) {
      const table = this.getStorageItemsTable();
      const folderKey = 'folder';
      const fileKey = 'file';
      const onCreateActionSelect = ({key}) => {
        const type = key.split('_').shift();
        switch (type) {
          case folderKey:
            this.openCreateFolderDialog();
            break;
          case fileKey:
            this.openCreateFileDialog();
            break;
        }
      };

      const title = () => {
        return (
          <Row
            className={styles.storageActions}
            type="flex"
            justify="space-between">
            <div>
              {
                this.selectAllAvailable &&
                (
                  <Button
                    id="select-all-button"
                    size="small" onClick={() => this.selectAll(undefined)}>
                    Select page
                  </Button>
                )
              }
              {
                this.clearSelectionVisible &&
                <Button
                  style={{marginLeft: 5}}
                  id="clear-selection-button"
                  size="small" onClick={() => this.clearSelection()}>
                  Clear selection
                </Button>
              }
              {
                this.versionControlsEnabled
                  ? (
                    <Checkbox
                      checked={this.showVersions}
                      onChange={this.showFilesVersionsChanged}
                      style={{marginLeft: 10}}>
                      Show files versions
                    </Checkbox>
                  ) : undefined
              }
            </div>
            <div style={{paddingRight: 8}}>
              {this.renderShareButton()}
              {
                this.bulkDownloadEnabled &&
                this.storageAllowSignedUrls &&
                <Button
                  id="bulk-url-button"
                  size="small"
                  onClick={this.toggleGenerateDownloadUrlsModalFn}>
                  Generate URL
                </Button>
              }
              {
                this.removeAllSelectedItemsEnabled &&
                roleModel.writeAllowed(this.props.info.value) &&
                <Button
                  id="remove-all-selected-button"
                  size="small"
                  onClick={(e) => this.removeSelectedItemsConfirm(e)}
                  type="danger">
                  Remove all selected
                </Button>
              }
              {
                this.removeAllSelectedItemsEnabled && this.bulkDownloadEnabled &&
                <div style={{display: 'inline-block', marginLeft: 10, width: 2, height: 2}} />
              }
              {
                roleModel.writeAllowed(this.props.info.value) &&
                <Dropdown
                  placement="bottomRight"
                  trigger={['hover']}
                  overlay={
                    <Menu
                      selectedKeys={[]}
                      onClick={onCreateActionSelect}
                      style={{width: 200, cursor: 'pointer'}}>
                      <MenuItem
                        id="create-folder-button"
                        className="create-folder-button"
                        key={folderKey}>
                        <Icon type="folder" /> Folder
                      </MenuItem>
                      <MenuItem
                        id="create-file-button"
                        className="create-file-button"
                        key={fileKey}>
                        <Icon type="file" /> File
                      </MenuItem>
                    </Menu>
                  }
                  key="create actions">
                  <Button
                    type="primary"
                    id="create-button"
                    size="small">
                    <Icon type="plus" /> Create <Icon type="down" />
                  </Button>
                </Dropdown>
              }
              {
                roleModel.writeAllowed(this.props.info.value) && (
                  <UploadButton
                    multiple
                    onRefresh={this.refreshList}
                    title={'Upload'}
                    storageId={this.props.storageId}
                    path={this.props.path}
                    storageInfo={this.props.info.value}
                    region={this.regionName}
                    // synchronous
                    uploadToS3={this.props.info.value.type === 'S3'}
                    uploadToNFS={this.props.info.value.type === 'NFS'}
                    action={
                      DataStorageItemUpdate.uploadUrl(
                        this.props.storageId,
                        this.props.path
                      )
                    }
                    owner={
                      this.props.authenticatedUserInfo.loaded
                        ? this.props.authenticatedUserInfo.value.userName
                        : undefined
                    }
                  />
                )
              }
            </div>
          </Row>
        );
      };

      contents = [
        <Table
          className={styles.table}
          style={{flex: 1}}
          key="table"
          dataSource={table.data}
          columns={table.columns}
          loading={this.props.storage.pending}
          title={title}
          rowKey="key"
          pagination={false}
          rowClassName={(item) => classNames({[styles[item.type.toLowerCase()]]: true}, {'cp-storage-deleted-row': !!item.deleteMarker})}
          locale={{emptyText: 'Folder is empty'}}
          size="small" />,
        <Row key="pagination" type="flex" justify="end" style={{marginTop: 10, marginBottom: 10, paddingRight: 15}}>
          <Button
            id="prev-page-button"
            onClick={this.prevPage}
            disabled={this.state.currentPage === 0}
            style={{margin: 3}} size="small"><Icon type="caret-left" /></Button>
          <Button
            id="next-page-button"
            onClick={this.nextPage}
            disabled={this.state.pageMarkers.length <= this.state.currentPage + 1}
            style={{margin: 3}} size="small"><Icon type="caret-right" /></Button>
        </Row>
      ];
    } else if (this.props.storage.error) {
      contents = (
        <div>
          <br />
          <Alert
            message={`Error retrieving data storage items: ${this.props.storage.error}`} type="error" />
        </div>
      );
    } else {
      contents = (
        <div>
          <Row type="flex" justify="center">
            <br />
            <Spin />
          </Row>
        </div>
      );
    }

    const onToggleMetadata = () => {
      if (this.showMetadata) {
        this.setState({metadata: !this.showMetadata, selectedFile: null});
      } else {
        this.setState({metadata: !this.showMetadata});
      }
    };

    const onPanelClose = (key) => {
      switch (key) {
        case METADATA_PANEL_KEY:
          this.setState({metadata: false});
          break;
      }
    };

    const storageTitleClassName = this.props.info.value.locked ? styles.readonly : undefined;

    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        height: 'calc(100vh - 25px)'
      }}>
        <Row type="flex" justify="space-between" align="middle">
          <Col className={styles.itemHeader}>
            <Breadcrumbs
              style={{height: '31px'}}
              id={parseInt(this.props.storageId)}
              type={ItemTypes.storage}
              textEditableField={this.props.info.value.name}
              onSaveEditableField={this.renameDataStorage}
              readOnlyEditableField={!roleModel.writeAllowed(this.props.info.value)}
              editStyleEditableField={{flex: 1}}
              icon={
                this.props.info.value && this.props.info.value.type.toLowerCase() !== 'nfs'
                  ? 'inbox'
                  : 'hdd'
              }
              iconClassName={`${styles.editableControl} ${storageTitleClassName}`}
              lock={this.props.info.value.locked}
              lockClassName={`${styles.editableControl} ${storageTitleClassName}`}
              sensitive={this.props.info.value.sensitive}
              displayTextEditableField={
                <span>
                  {this.props.info.value.name}
                  <AWSRegionTag
                    className={classNames(
                      styles.storageRegion
                    )}
                    displayName
                    flagStyle={{fontSize: 'smaller'}}
                    providerStyle={{fontSize: 'smaller'}}
                    regionId={this.props.info.value.regionId}
                    style={{marginLeft: 5, fontSize: 'medium'}}
                  />
                </span>
              }
              subject={this.props.info.value}
            />
          </Col>
          <Col>
            <Row
              type="flex"
              justify="end"
              align="middle"
              className={styles.currentFolderActions}
            >
              <RestrictedImagesInfo
                toolsToMount={this.toolsToMount}
                status={this.props.info.value.mountStatus}
              />
              {
                this.generateFolderURLAvailable && (
                  <Button
                    id="generate-folder-url"
                    size="small"
                    onClick={this.showGenerateFolderDownloadUrlsModalFn}>
                    Generate URL
                  </Button>
                )
              }
              <Button
                id={this.showMetadata ? 'hide-metadata-button' : 'show-metadata-button'}
                size="small"
                onClick={onToggleMetadata}>
                {
                  this.showMetadata ? 'Hide attributes' : 'Show attributes'
                }
              </Button>
              {
                roleModel.writeAllowed(this.props.info.value) &&
                this.props.info.value.type !== 'NFS' &&
                this.props.info.value.shared &&
                <Button
                  id="share-storage-button"
                  size="small"
                  onClick={this.openShareStorageDialog}>
                  Share
                </Button>
              }
              {this.renderEditAction()}
              <Button
                id="refresh-storage-button"
                size="small"
                onClick={() => this.refreshList()}
                disabled={this.isDataRefreshing()}>Refresh</Button>
            </Row>
          </Col>
        </Row>
        <ContentMetadataPanel
          style={{flex: 1, overflow: 'auto'}}
          onPanelClose={onPanelClose}
          contentContainerStyle={{overflow: 'inherit'}}>
          <div
            key={CONTENT_PANEL_KEY}
            style={{flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto'}}>
            <Row className={styles.dataStorageInfoContainer}>
              {
                this.props.info.value.description &&
                <Row><b>Description: </b>{this.props.info.value.description}</Row>
              }
              {
                this.props.info.value.storagePolicy &&
                this.props.info.value.storagePolicy.shortTermStorageDuration !== undefined
                  ? (
                    <Row>
                      <b>Short-Term Storage duration: </b>
                      {`${this.props.info.value.storagePolicy.shortTermStorageDuration} days`}
                    </Row>
                  ) : undefined
              }
              {
                this.props.info.value.storagePolicy &&
                this.props.info.value.storagePolicy.longTermStorageDuration !== undefined
                  ? (
                    <Row>
                      <b>Long-Term Storage duration: </b>
                      {`${this.props.info.value.storagePolicy.longTermStorageDuration} days`}
                    </Row>
                  ) : undefined
              }
            </Row>
            <Row style={{marginLeft: 5}}>
              <DataStorageNavigation
                path={this.props.path}
                storage={this.props.info.value}
                navigate={this.navigate}
                navigateFull={this.navigateFull} />
            </Row>
            {contents}
          </div>
          {
            this.showMetadata &&
            <Metadata
              pending={this.state.previewPending}
              key={METADATA_PANEL_KEY}
              readOnly={!roleModel.isOwner(this.props.info.value)}
              downloadable={!this.props.info.value.sensitive}
              showContent={!this.props.info.value.sensitive}
              hideMetadataTags={this.props.info.value.type === 'NFS'}
              canNavigateBack={!!this.state.selectedFile}
              onNavigateBack={() => this.setState({selectedFile: null})}
              metadataRenderFn={
                this.state.previewAvailable
                  ? this.metadataRenderFn
                  : undefined
              }
              entityName={
                this.state.selectedFile
                  ? this.state.selectedFile.name
                  : this.props.info.value.name
              }
              entityId={
                this.state.selectedFile
                  ? this.state.selectedFile.path
                  : this.props.storageId
              }
              entityParentId={
                this.state.selectedFile
                  ? this.props.storageId
                  : undefined
              }
              entityVersion={
                this.state.selectedFile
                  ? this.state.selectedFile.version
                  : undefined
              }
              entityClass={
                this.state.selectedFile
                  ? 'DATA_STORAGE_ITEM'
                  : 'DATA_STORAGE'
              }
              openEditFileForm={
                this.state.selectedFile
                  ? () => this.openEditFileForm(this.state.selectedFile)
                  : undefined
              }
              fileIsEmpty={this.isFileSelectedEmpty}
              extraKeys={[
                this.props.info.value.type === 'NFS'
                  ? FS_MOUNTS_NOTIFICATIONS_ATTRIBUTE
                  : false,
                this.props.info.value.type !== 'NFS' && !this.state.selectedFile
                  ? REQUEST_DAV_ACCESS_ATTRIBUTE
                  : false
              ].filter(Boolean)}
              extraInfo={[
                <StorageSize storage={this.props.info.value} />
              ]}
              specialTagsProperties={{
                storageType: this.fileShareMount ? this.fileShareMount.mountType : undefined,
                storageMask: this.props.info.value.mask,
                storageId: Number(this.props.storageId)
              }}
            />
          }
        </ContentMetadataPanel>
        <DataStorageEditDialog
          visible={this.state.editDialogVisible}
          dataStorage={this.props.info.value}
          pending={this.props.info.pending}
          policySupported={this.props.info.value.policySupported}
          versionControlsEnabled={this.versionControlsEnabled}
          onDelete={this.deleteStorage}
          onCancel={this.closeEditDialog}
          onSubmit={this.onDataStorageEdit} />
        <ConvertToVersionedStorage
          storageName={this.props.info.loaded ? this.props.info.value.name : undefined}
          visible={this.state.convertToVSDialogVisible}
          onCancel={() => this.closeConvertToVersionedStorageDialog()}
          onConvert={this.onConvertStorageToVS}
        />
        <Modal
          title="Download file url"
          width="80%"
          visible={this.state.downloadUrlModalVisible}
          onOk={() => this.closeDownloadUrlModal(true)}
          onCancel={() => this.closeDownloadUrlModal()}
          afterClose={() => { this.generateDownloadUrls = null; }}
          footer={
            <Button
              type="primary"
              onClick={() => this.closeDownloadUrlModal(true)}>
              OK
            </Button>
          }>
          {
            this.generateDownloadUrls &&
            this.generateDownloadUrls.pending &&
            !this.generateDownloadUrls.loaded && (
              <div>
                <Row type="flex" justify="center">
                  <br />
                  <Spin />
                </Row>
              </div>
            )
          }
          {
            this.generateDownloadUrls && this.generateDownloadUrls.loaded && (
              <Input
                type="textarea"
                className={styles.generateDownloadUrlInput}
                rows={10}
                value={this.generateDownloadUrls.value.map(u => u.url).join('\n')} />
            )
          }
          {
            this.generateDownloadUrls && this.generateDownloadUrls.error && (
              <Alert type="error" message={this.generateDownloadUrls.error} />
            )
          }
          {
            this.generateFolderURLAvailable && this.state.downloadFolderUrlModal && (
              <Row style={{marginTop: 10}}>
                <Checkbox
                  checked={this.state.generateFolderUrlWriteAccess}
                  disabled={!roleModel.writeAllowed(this.props.info.value)}
                  onChange={
                    (e) => this.setState({
                      generateFolderUrlWriteAccess: e.target.checked
                    }, this.generateFolderDownloadUrl
                    )
                  }
                >
                  Write access
                </Checkbox>
              </Row>
            )
          }
        </Modal>
        <Modal
          title="Share storage link"
          width="80%"
          visible={this.state.shareStorageDialogVisible}
          onOk={this.closeShareStorageDialog}
          onCancel={this.closeShareStorageDialog}
          footer={
            <Button
              type="primary"
              onClick={this.closeShareStorageDialog}>
              OK
            </Button>
          }>
          <div>
            {
              this._shareStorageLink && (!this._shareStorageLink.error
                ? (!this._shareStorageLink.pending
                  ? (
                    <Input
                      autosize
                      type="textarea"
                      value={this._shareStorageLink.value}
                    />
                  )
                  : <LoadingView />)
                : <Alert message={this._shareStorageLink.error} type="error" />)
            }
            {
              this.dataStorageShareLinkDisclaimer && (
                <BashCode
                  id="data-sharing-disclaimer"
                  className={styles.dataSharingDisclaimer}
                  code={this.dataStorageShareLinkDisclaimer}
                />
              )
            }
          </div>
        </Modal>
        <EditItemForm
          pending={false}
          title="Create folder"
          visible={this.state.createFolder}
          onCancel={this.closeCreateFolderDialog}
          onSubmit={this.createFolder} />
        <EditItemForm
          pending={false}
          title="Create file"
          includeFileContentField
          visible={this.state.createFile}
          onCancel={this.closeCreateFileDialog}
          onSubmit={this.createFile} />
        <EditItemForm
          pending={false}
          title={this.state.renameItem
            ? (
              this.state.renameItem.type.toLowerCase() === 'file'
                ? 'Rename file'
                : 'Rename folder'
            )
            : null
          }
          name={this.state.renameItem ? this.state.renameItem.name : null}
          visible={!!this.state.renameItem}
          onCancel={() => this.closeRenameItemDialog()}
          onSubmit={this.renameItem} />
        <SharedItemInfo
          visible={this.state.shareDialogVisible}
          shareItems={this.state.itemsToShare}
          close={this.closeShareItemDialog}
        />
        <Modal
          visible={!!this.state.itemsToDelete}
          onCancel={this.closeDeleteModal}
          title="Do you want to delete item(s) from object storage or set 'Deletion' marker?"
          footer={
            <Row type="flex" justify="space-between">
              <Col span={8}>
                <Row type="flex" justify="start">
                  <Button
                    id="delete-bucket-item-modal-cancel-button"
                    onClick={this.closeDeleteModal}>Cancel</Button>
                </Row>
              </Col>
              <Col span={16}>
                <Row type="flex" justify="end">
                  <Button
                    id="delete-bucket-item-modal-set-deletion-marker-button"
                    type="danger"
                    onClick={() => this.removeItems(this.state.itemsToDelete, false, false, () => {
                      this.closeDeleteModal();
                      this.setState({selectedItems: []});
                      this.afterDataStorageEdit();
                    })}>Set deletion marker</Button>
                  <Button
                    id="delete-bucket-item-modal-delete-from-bucket-button"
                    type="danger"
                    onClick={() => this.removeItems(this.state.itemsToDelete, true, false, () => {
                      this.closeDeleteModal();
                      this.setState({selectedItems: []});
                      this.afterDataStorageEdit();
                    })}>Delete from object storage</Button>
                </Row>
              </Col>
            </Row>
          }>
          {
            (this.state.itemsToDelete || []).map(item => {
              return (
                <Row key={item.name}>{item.name}</Row>
              );
            })
          }
        </Modal>
        <DataStorageCodeForm
          file={this.state.editFile}
          downloadable={!this.props.info.value.sensitive}
          storageId={this.props.storageId}
          cancel={this.closeEditFileForm}
          save={this.saveEditableFile}
        />
        {this.renderPreviewModal()}
      </div>
    );
  }

  performPage = () => {
    const pageMarkers = this.state.pageMarkers;
    if (!this.props.storage.error) {
      if (this.props.storage.value.nextPageMarker) {
        if (pageMarkers.length > this.state.currentPage + 1) {
          pageMarkers[this.state.currentPage + 1] = this.props.storage.value.nextPageMarker;
        } else {
          pageMarkers.push(this.props.storage.value.nextPageMarker);
        }
      } else {
        pageMarkers.splice(this.state.currentPage + 1, pageMarkers.length - this.state.currentPage - 1);
      }
    }
    this.setState({
      pagePerformed: true,
      pageMarkers
    });
  };

  componentWillUnmount () {
    message.destroy();
  }

  componentDidUpdate (prevProps) {
    if (prevProps.storageId !== this.props.storageId) {
      this.setState({
        metadata: undefined,
        pageMarkers: [null],
        currentPage: 0,
        pagePerformed: false,
        selectedItems: [],
        selectedFile: null,
        editFile: null
      });
    } else if (!this.props.storage.pending && !this.state.pagePerformed) {
      this.performPage();
    } else if (this.props.showVersions !== prevProps.showVersions) {
      this.refreshList();
    }
    if (this.state.shareStorageDialogVisible &&
      (!this._shareStorageLink || this._shareStorageLink.storageId !== this.props.storageId ||
      (!this._shareStorageLink.loaded && !this._shareStorageLink.pending))) {
      this._shareStorageLink = new DataStorageGenerateSharedLink(this.props.storageId);
      this._shareStorageLink.fetch();
    }
  }
}
