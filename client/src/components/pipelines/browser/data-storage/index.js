/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject, observer, Observer} from 'mobx-react';
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
import Menu, {MenuItem, Divider} from 'rc-menu';
import moment from 'moment-timezone';
import LoadingView from '../../../special/LoadingView';
import Breadcrumbs from '../../../special/Breadcrumbs';
import DataStorageUpdate from '../../../../models/dataStorage/DataStorageUpdate';
import UpdateStoragePolicy from '../../../../models/dataStorage/DataStorageUpdateStoragePolicy';
import DataStorageItemRestore from '../../../../models/dataStorage/DataStorageItemRestore';
import DataStorageDelete from '../../../../models/dataStorage/DataStorageDelete';
import DataStorageItemUpdate from '../../../../models/dataStorage/DataStorageItemUpdate';
import UpdateContent from '../../../../models/dataStorage/DataStorageItemUpdateContent';
import DataStorageItemDelete from '../../../../models/dataStorage/DataStorageItemDelete';
import GenerateDownloadUrlsRequest from '../../../../models/dataStorage/GenerateDownloadUrls';
import GenerateFolderDownloadUrl from '../../../../models/dataStorage/GenerateFolderDownloadUrl';
import DataStorageConvert from '../../../../models/dataStorage/DataStorageConvert';
import OmicsStoreImport from '../../../../models/dataStorage/OmicsStoreImport';
import OmicsActivate from '../../../../models/dataStorage/OmicsActivate';
// eslint-disable-next-line max-len
import LifeCycleEffectiveHierarchy from '../../../../models/dataStorage/lifeCycleRules/LifeCycleEffectiveHierarchy';
// eslint-disable-next-line max-len
import LifeCycleRestoreCreate from '../../../../models/dataStorage/lifeCycleRules/LifeCycleRestoreCreate';
import EditItemForm from '../forms/EditItemForm';
import {DataStorageEditDialog, ServiceTypes} from '../forms/DataStorageEditDialog';
import {OmicsStorageImportDialog} from '../forms/OmicsStorageImportDialog';
import {LifeCycleRestoreModal} from '../forms/life-cycle-rules/modals';
import DataStorageNavigation from '../forms/DataStorageNavigation';
import RestrictedImagesInfo from '../forms/restrict-docker-images/restricted-images-info';
import ConvertToVersionedStorage from '../forms/convert-to-vs';
import {
  ContentMetadataPanel,
  CONTENT_PANEL_KEY,
  METADATA_PANEL_KEY
} from '../../../special/splitPanel';
import Metadata from '../../../special/metadata/Metadata';
import LifeCycleCounter from '../forms/life-cycle-rules/components/life-cycle-counter';
import RestoreStatusIcon, {STATUS} from '../forms/life-cycle-rules/components/restore-status-icon';
import PreviewModal from '../../../search/preview/preview-modal';
import {getPreviewConfiguration} from '../../../search/preview/vsi-preview';
import UploadButton from '../../../special/UploadButton';
import AWSRegionTag from '../../../special/AWSRegionTag';
import EmbeddedMiew from '../../../applications/miew/EmbeddedMiew';
import parseQueryParameters from '../../../../utils/queryParameters';
import displayDate from '../../../../utils/displayDate';
import displaySize from '../../../../utils/displaySize';
import roleModel from '../../../../utils/roleModel';
import DataStorageCodeForm from './../forms/DataStorageCodeForm';
import {ItemTypes} from '../../model/treeStructureFunctions';
import HiddenObjects from '../../../../utils/hidden-objects';
import OpenInToolAction from '../../../special/file-actions/open-in-tool';
import {
  METADATA_KEY as FS_MOUNTS_NOTIFICATIONS_ATTRIBUTE
} from '../../../special/metadata/special/fs-notifications';
import {
  METADATA_KEY as REQUEST_DAV_ACCESS_ATTRIBUTE
} from '../../../special/metadata/special/request-dav-access';
import StorageSize from '../../../special/storage-size';
import {extractFileShareMountList} from '../forms/DataStoragePathInput';
import SharedItemInfo from '../forms/data-storage-item-sharing/SharedItemInfo';
import {SAMPLE_SHEET_FILE_NAME_REGEXP} from '../../../special/sample-sheet/utilities';
import {
  fastCheckPreviewAvailable as fastCheckHCSPreviewAvailable,
  checkPreviewAvailable as checkHCSPreviewAvailable
} from '../../../special/hcs-image/utilities/check-preview-available';
import {getStaticResourceUrl} from '../../../../models/static-resources';
import DataStorageListing from '../../../../models/dataStorage/data-storage-listing';
import LabelsRenderer from './components/labels-renderer';
import StoragePagination from './components/storage-pagination';
import StorageSharedLinkButton from './components/storage-shared-link-button';
import DownloadFileButton from './components/download-file-button';
import handleDownloadItems from '../../../special/download-storage-items';
import JobList from './components/imported-jobs';
import styles from '../Browser.css';

const STORAGE_CLASSES = {
  standard: 'STANDARD',
  intelligentTiering: 'INTELLIGENT_TIERING'
};

const standardClasses = [
  STORAGE_CLASSES.standard,
  STORAGE_CLASSES.intelligentTiering
];

const isStandardClass = (storageClass) =>
  standardClasses.includes((storageClass || '').toUpperCase());

const SUBMITTED_STATUS = 'SUBMITTED';

@roleModel.authenticationInfo
@inject(
  'awsRegions',
  'pipelines',
  'dataStorages',
  'folders',
  'pipelinesLibrary',
  'dataStorageCache',
  'preferences'
)
@HiddenObjects.checkStorages(props => props.params.id)
@inject(({routing}, {params, onReloadTree}) => {
  const queryParameters = parseQueryParameters(routing);
  const showVersions = (queryParameters.versions || 'false').toLowerCase() === 'true';
  const showArchives = (queryParameters.archives || 'false').toLowerCase() === 'true';
  const openPreview = queryParameters.preview && decodeURIComponent(queryParameters.preview);
  return {
    onReloadTree,
    storageId: params.id,
    path: decodeURIComponent(queryParameters.path || ''),
    showVersions,
    showArchives,
    restoreInfo: new LifeCycleEffectiveHierarchy(
      params.id,
      decodeURIComponent(queryParameters.path || ''),
      'FOLDER',
      false
    ),
    openPreview
  };
})
@observer
export default class DataStorage extends React.Component {
  state = {
    editDialogVisible: false,
    restoreDialogVisible: false,
    lifeCycleRestoreMode: undefined,
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
    itemsToDelete: null,
    selectedFile: null,
    editFile: null,
    previewModal: null,
    previewAvailable: false,
    previewPending: false,
    restorePending: false,
    omicsDialogVisible: false,
    importedJobs: false
  };

  @observable storage = new DataStorageListing({
    keepPagesHistory: true
  });

  @observable generateDownloadUrls;

  get showMetadata () {
    if (this.state.metadata === undefined && this.storage.info) {
      return this.storage.info.hasMetadata &&
        this.storage.readAllowed;
    }
    return !!this.state.metadata;
  }

  get showJobs () {
    if (this.state.importedJobs) {
      return this.storage.info && this.storage.readAllowed;
    }
    return this.state.importedJobs;
  }

  get region () {
    if (this.storage.info && this.props.awsRegions.loaded) {
      const {regionId} = this.storage.info;
      return (this.props.awsRegions.value || []).find(r => +r.id === +regionId);
    }
    return null;
  }

  get provider () {
    const region = this.region;
    if (region) {
      return region.provider;
    }
    return null;
  }

  get regionName () {
    const region = this.region;
    if (region) {
      return region.regionId || region.name;
    }
    return null;
  }

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

  get toolsToMount () {
    if (
      this.storage.infoLoaded &&
      this.storage.info &&
      this.storage.info.toolsToMount &&
      !this.storage.info.mountDisabled
    ) {
      return (this.storage.info.toolsToMount || []).map(t => t);
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

  get fileShareMount () {
    if (this.storage.info) {
      const {
        fileShareMountId,
        type
      } = this.storage.info;
      if (/^nfs$/i.test(type)) {
        return this.fileShareMountList.find(mount => mount.id === fileShareMountId);
      }
    }
    return undefined;
  }

  get sharingEnabled () {
    const {preferences} = this.props;
    return this.storage.infoLoaded &&
      this.storage.info &&
      !/^nfs$/i.test(this.storage.info.type) &&
      !this.isOmicsStore &&
      preferences &&
      preferences.loaded &&
      preferences.sharedStoragesSystemDirectory &&
      preferences.dataSharingEnabled;
  }

  @computed
  get storageVersioningAllowed () {
    const {
      preferences,
      authenticatedUserInfo
    } = this.props;
    const loaded = preferences &&
      preferences.loaded &&
      this.storage.info &&
      this.storage.infoLoaded &&
      authenticatedUserInfo &&
      authenticatedUserInfo.loaded;
    if (loaded) {
      const isAdmin = authenticatedUserInfo.value.admin;
      const isOwner = roleModel.isOwner(this.storage.info);
      return isAdmin ||
        roleModel.isManager.storageAdmin(this) ||
        (isOwner && preferences.storagePolicyBackupVisibleNonAdmins);
    }
    return false;
  }

  get versionControlsEnabled () {
    return this.storageVersioningAllowed &&
      this.storage.info &&
      !/^nfs$/i.test(this.storage.info.type) &&
      this.storage.info.storagePolicy &&
      this.storage.info.storagePolicy.versioningEnabled;
  }

  get userLifeCyclePermissions () {
    if (!this.storage.infoLoaded || !this.storage.info) {
      return {read: false, write: false};
    }
    const isS3 = /^s3$/i.test(
      this.storage.info.storageType ||
      this.storage.info.type
    );
    const readAllowed = roleModel.readAllowed(this.storage.info);
    const writeAllowed = roleModel.writeAllowed(this.storage.info);
    return {
      read: roleModel.isManager.storageAdmin(this) || ((
        roleModel.isOwner(this.storage.info) ||
        roleModel.isManager.archiveManager(this) ||
        roleModel.isManager.archiveReader(this)
      ) && readAllowed && isS3),
      write: roleModel.isManager.storageAdmin(this) || ((
        roleModel.isOwner(this.storage.info) ||
        roleModel.isManager.archiveManager(this)
      ) && writeAllowed && isS3)
    };
  }

  get showVersions () {
    if (!this.storage.info) {
      return false;
    }
    return this.storage.info.type !== 'NFS' &&
      this.props.showVersions &&
      this.storage.isOwner;
  }

  get showArchives () {
    const {showArchives} = this.props;
    if (!this.storage.info) {
      return false;
    }
    return (
      this.userLifeCyclePermissions.read ||
      this.userLifeCyclePermissions.write
    ) && showArchives;
  }

  @computed
  get lifeCycleRestoreInfo () {
    const {
      restoreInfo,
      path
    } = this.props;
    if (restoreInfo && restoreInfo.loaded) {
      const [first, ...rest] = restoreInfo.value || [];
      const currentPath = path
        ? [
          !path.startsWith('/') && '/',
          path,
          !path.endsWith('/') && '/'
        ].filter(Boolean).join('')
        : '/';
      let parentRestore;
      let currentRestores;
      if (
        first &&
        first.type === 'FOLDER' &&
        currentPath.startsWith(first.path)
      ) {
        parentRestore = first;
        currentRestores = rest;
      } else {
        currentRestores = restoreInfo.value;
      }
      return {
        parentRestore,
        currentRestores: currentRestores || []
      };
    }
    return {
      parentRestore: undefined,
      currentRestores: []
    };
  }

  get restorableItems () {
    const {selectedItems} = this.state;
    return (selectedItems || [])
      .filter(item => (
        (item.labels && !isStandardClass(item.labels.StorageClass)) ||
        item.type === 'Folder'
      ));
  }

  get storageTagRestrictedAccess () {
    const {
      preferences
    } = this.props;
    return preferences.storageTagRestrictedAccess;
  }

  get metadataEditable () {
    const {
      authenticatedUserInfo
    } = this.props;
    const isAdmin = authenticatedUserInfo && authenticatedUserInfo.loaded
      ? authenticatedUserInfo.value.admin
      : false;
    // Whilst in the restricted tag access mode, only admins and users (including owners) with roles
    // STORAGE_MANAGER STORAGE_ADMIN or STORAGE_TAG_MANAGER are allowed to edit file's tags.
    const restrictedAccessCheck = isAdmin ||
      roleModel.isManager.storage(this) ||
      roleModel.isManager.storageTag(this) ||
      roleModel.isManager.storageAdmin(this);
    const storageFileTagsEditable = this.storageTagRestrictedAccess
      ? restrictedAccessCheck
      // If restricted tag access mode is off, all users with WRITE permissions are
      // allowed to edit file's tags.
      : this.storage.writeAllowed;
    return this.state.selectedFile
      ? storageFileTagsEditable
      : this.storage.writeAllowed;
  }

  get isOmicsStore () {
    const {type} = this.storage.info || {};
    return type === ServiceTypes.omicsSeq || type === ServiceTypes.omicsRef;
  }

  get isSequenceStorage () {
    const {type} = this.storage.info || {};
    return type === ServiceTypes.omicsSeq;
  }

  get isOmicsFolder () {
    return this.isOmicsStore && !this.storage.pagePath && this.storage.path === '/';
  }

  onDataStorageEdit = async (storage) => {
    if (!this.storage.info) {
      return;
    }
    const {
      parentFolderId,
      policySupported
    } = this.storage.info;
    const dataStorage = {
      id: this.props.storageId,
      parentFolderId,
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
        policySupported &&
        storage.serviceType !== ServiceTypes.fileShare &&
        (
          storage.backupDuration !== undefined ||
          !storage.versioningEnabled
        )
      ) {
        const updatePolicyRequest = new UpdateStoragePolicy();
        await updatePolicyRequest.send({
          id: this.props.storageId,
          storagePolicy: {
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
          this.storage.refreshStorageInfo();
          this.props.folders.invalidateFolder(parentFolderId);
          if (this.props.onReloadTree) {
            this.props.onReloadTree(!parentFolderId);
          }
        }
      } else {
        hide();
        this.closeEditDialog();
        this.storage.refreshStorageInfo();
        this.props.folders.invalidateFolder(parentFolderId);
        if (this.props.onReloadTree) {
          this.props.onReloadTree(!parentFolderId);
        }
      }
    }
  };

  onImportOmicsJob = async (job) => {
    if (!job || !this.storage.info) {
      return;
    }
    const {parentFolderId} = this.storage.info;
    const payload = {
      sources: [
        {...job}
      ]
    };
    const hide = message.loading('Importing omics job...');
    const request = new OmicsStoreImport(this.props.storageId);
    await request.send(payload);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      hide();
      this.closeOmicsDialog();
      this.storage.refreshStorageInfo();
      this.props.folders.invalidateFolder(parentFolderId);
      this.setState({
        importedJobs: true,
        updateJobsSearch: true
      }, () => {
        this.setState({
          updateJobsSearch: false
        });
      });
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!parentFolderId);
      }
    }
  }

  get items () {
    const {
      pageElements: elements = [],
      pagePath: path,
      info,
      storageId,
      downloadEnabled: downloadable
    } = this.storage;
    if (!info) {
      return [];
    }
    const writeAllowed = this.storage.writeAllowed;
    const {sensitive} = info;
    const items = [];
    const documentPreviewAvailable = (item) => {
      const {preferences} = this.props;
      return /^file$/i.test(item.type) &&
        preferences.dataStorageItemPreviewMasks.some(mask => mask.test(item.path));
    };
    if (path) {
      const parentPathParts = path.split('/').slice(0, -1);
      const parentPath = parentPathParts.length ? parentPathParts.join('/') : undefined;
      items.push({
        key: `folder_${parentPath || '<parent>'}`,
        name: '..',
        path: parentPath,
        type: 'folder',
        downloadable: false,
        editable: false,
        selectable: false,
        shareAvailable: false,
        navigationBackward: true
      });
    }
    const getChildList = (item, versions, sensitive) => {
      if (!versions || !this.showVersions) {
        return undefined;
      }
      const childList = [];
      const restoreStatus = this.getRestoredStatus(item) || {};
      const fileRestored = restoreStatus.status === STATUS.SUCCEEDED;
      for (let version in versions) {
        if (versions.hasOwnProperty(version)) {
          const archived = versions[version].labels &&
            !isStandardClass(versions[version].labels['StorageClass']);
          const versionRestored = restoreStatus.restoreVersions &&
            restoreStatus.status === STATUS.SUCCEEDED;
          const latest = versions[version].version === item.version;
          childList.push({
            key: `${item.type}_${item.path}_${version}`,
            ...versions[version],
            downloadable: item.type.toLowerCase() === 'file' &&
              !versions[version].deleteMarker &&
              !sensitive &&
              (!archived || (latest ? fileRestored : versionRestored)) &&
              downloadable,
            editable: versions[version].version === item.version &&
              writeAllowed &&
              !versions[version].deleteMarker,
            deletable: writeAllowed,
            selectable: false,
            shareAvailable: false,
            latest,
            isVersion: true,
            archived,
            restored: latest
              ? fileRestored
              : versionRestored
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
    items.push(...elements.map(i => {
      const restored = (this.getRestoredStatus(i) || {}).status === STATUS.SUCCEEDED;
      const archived = i.labels && !isStandardClass(i.labels['StorageClass']);
      return {
        key: `${i.type}_${i.path}`,
        ...i,
        downloadable: i.type.toLowerCase() === 'file' &&
          !i.deleteMarker &&
          !sensitive &&
          (!archived || restored) &&
          downloadable,
        editable: writeAllowed && !i.deleteMarker,
        shareAvailable: !i.deleteMarker && this.sharingEnabled,
        deletable: writeAllowed,
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
          fastCheckHCSPreviewAvailable({path: i.path, storageId}),
        documentPreview: !i.deleteMarker &&
          documentPreviewAvailable(i),
        archived,
        restored
      };
    }));
    return items;
  };

  refreshList = async (keepCurrentPage = false) => {
    await Promise.all([
      this.storage.refreshStorageInfo(),
      this.storage.refreshCurrentPath(keepCurrentPage)
    ]);
  };

  afterDataStorageEdit = () => {
    this.storage.refreshStorageInfo();
  };

  openEditDialog = () => {
    this.setState({editDialogVisible: true});
  };

  closeEditDialog = () => {
    this.setState({editDialogVisible: false}, () => {
      this.storage.refreshStorageInfo();
    });
  };

  openOmicsDialog = () => {
    this.setState({omicsDialogVisible: true});
  };

  closeOmicsDialog = () => {
    this.setState({omicsDialogVisible: false}, () => {
      this.storage.refreshStorageInfo();
    });
  };

  renameDataStorage = async (name) => {
    if (!this.storage.info) {
      return;
    }
    const {
      parentFolderId,
      description,
      path
    } = this.storage.info;
    const dataStorage = {
      id: this.props.storageId,
      parentFolderId,
      name: name,
      description,
      path
    };
    const hide = message.loading('Renaming data storage...');
    const request = new DataStorageUpdate();
    await request.send(dataStorage);
    if (request.error) {
      hide();
      message.error(request.error, 5);
    } else {
      await this.storage.refreshStorageInfo();
      if (parentFolderId) {
        this.props.folders.invalidateFolder(parentFolderId);
      } else {
        this.props.pipelinesLibrary.invalidateCache();
      }
      if (this.props.onReloadTree) {
        this.props.onReloadTree(!parentFolderId);
      }
      hide();
      this.closeEditDialog();
    }
  };

  deleteStorage = async (cloud) => {
    if (!this.storage.info) {
      return;
    }
    const {
      name,
      parentFolderId
    } = this.storage.info;
    const request = new DataStorageDelete(this.props.storageId, cloud);
    const hide = message.loading(`${cloud ? 'Deleting' : 'Unregistering'} storage ${name}...`, 0);
    await request.fetch();
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      await this.props.dataStorages.fetch();
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

  navigate = (id, path, options = {}) => {
    const {
      showVersions = this.showVersions,
      showArchives = this.showArchives,
      clearPathMarkers = true
    } = options;
    if (path && path.endsWith('/')) {
      path = path.substring(0, path.length - 1);
    }
    this.storage.clearMarkersForPath(path, clearPathMarkers);
    const params = [
      path ? `path=${encodeURIComponent(path)}` : false,
      this.versionControlsEnabled
        ? `versions=${showVersions}`
        : null,
      this.userLifeCyclePermissions.read || this.userLifeCyclePermissions.write
        ? `archives=${showArchives}`
        : null
    ].filter(Boolean).join('&');
    this.props.router.push(`/storage/${id}?${params}`);
    this.setState({
      selectedItems: [],
      selectedFile: null
    });
  };

  navigateFull = (path) => {
    if (path && this.storage.info) {
      let {
        pathMask,
        path: bucketPath,
        delimiter = '/'
      } = this.storage.info;
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
        this.navigate(this.props.storageId, relativePath);
      } else {
        message.error('You cannot navigate to another storage.', 3);
      }
    }
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
    if (this.storage.infoLoaded && this.generateFolderURLAvailable) {
      this.setState({
        generateFolderUrlWriteAccess: this.storage.writeAllowed,
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

  openRestoreFilesDialog = (mode = 'file') => {
    this.setState({
      restoreDialogVisible: true,
      lifeCycleRestoreMode: mode
    });
  };

  closeRestoreFilesDialog = () => {
    this.setState({
      restoreDialogVisible: false,
      lifeCycleRestoreMode: undefined
    });
  };

  restoreFiles = (payload) => {
    const {storageId, restoreInfo} = this.props;
    const request = new LifeCycleRestoreCreate(storageId);
    this.setState({restorePending: true}, async () => {
      await request.send(payload);
      if (request.error) {
        message.error(request.error, 5);
        return this.setState({restorePending: false});
      }
      restoreInfo.fetch()
        .then(() => {
          this.setState({restorePending: false});
          this.closeRestoreFilesDialog();
        });
    });
  };

  restoreOmics = async () => {
    const payload = {
      readSetIds: this.restorableItems.map(item => item.path)
    };
    const request = new OmicsActivate(this.props.storageId);
    this.setState({restorePending: true}, async () => {
      await request.send(payload);
      if (request.error) {
        message.error(request.error, 5);
        return this.setState({restorePending: false});
      }
      if (request.value && request.value.status === SUBMITTED_STATUS) {
        message.info('Restoring was successfully initialized', 5);
      }
      this.setState({restorePending: false});
    });
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
    const request = new UpdateContent(this.props.storageId, path);
    const hide = message.loading('Uploading changes...');

    await request.send(content);
    hide();
    if (request.error) {
      message.error(request.error, 5);
    } else {
      await currentItemContent.fetch();
      this.closeEditFileForm();
      await this.refreshList(true);
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
        // eslint-disable-next-line max-len
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
        // eslint-disable-next-line max-len
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
          const selectedItem = selectedItems
            .find(s => s.name === item.name && s.type === item.type);
          if (selectedItem) {
            const index = selectedItems.indexOf(selectedItem);
            selectedItems.splice(index, 1);
          }
        };
        items.forEach(item => removeItemFromSelectedList(item));
        this.setState({selectedItems});
      }
      this.setState({
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
    if (event) {
      event.stopPropagation();
    }
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

  canRestoreVersion = (item) => {
    if (!this.showVersions) {
      return false;
    }
    if (
      (item.type && item.type.toLowerCase() === 'folder') ||
      !item.isVersion ||
      item.deleteMarker ||
      (item.isVersion && item.archived && !item.restored)
    ) {
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
    if (item.downloadable && !this.isOmicsStore) {
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
        <DownloadFileButton
          key={`download-${item.path}`}
          storageId={this.props.storageId}
          path={item.path}
          version={item.version}
        />
      );
    }
    if (
      (item.isVersion
        ? item.editable && this.versionControlsEnabled
        : item.editable
      ) && (
        !item.archived || item.restored
      ) && !this.isOmicsStore
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
    if (this.versionControlsEnabled && this.canRestoreVersion(item)) {
      actions.push(
        <Button
          id={`restore ${item.name}`}
          key="restore"
          size="small"
          onClick={() => this.onRestoreClicked(item, item.isVersion ? item.version : undefined)}
        >
          <Icon type="reload" />
        </Button>
      );
    }
    if ((item.isVersion
      ? item.deletable && this.versionControlsEnabled
      : item.deletable) &&
      (!this.isOmicsStore || this.isOmicsFolder)
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
    return !!this.state.selectedItems
      .find(s => s.name === item.name && s.type === item.type);
  };

  selectFile = (item) => () => {
    const selectedItems = this.state.selectedItems;
    const selectedItem = this.state.selectedItems
      .find(s => s.name === item.name && s.type === item.type);
    if (selectedItem) {
      const index = selectedItems.indexOf(selectedItem);
      selectedItems.splice(index, 1);
    } else {
      selectedItems.push(item);
    }
    this.setState({selectedItems});
  };

  openShareCurrentFolderDialog = (event) => {
    const {storageId, path} = this.props;
    event && event.stopPropagation();
    if (path) {
      this.setState({
        itemsToShare: [{
          type: 'folder',
          path,
          storageId
        }],
        shareDialogVisible: true
      });
    }
  };

  openShareItemDialog = (event) => {
    const {selectedItems = []} = this.state;
    const items = selectedItems
      .filter(o => o.shareAvailable);
    const {storageId} = this.props;
    event && event.stopPropagation();
    if (items && items.length > 0) {
      this.setState({
        itemsToShare: items ? items.slice().map((o) => ({...o, storageId})) : [],
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

  openEditFileForm = async (item) => {
    if (!this.storage.infoLoaded) {
      await this.storage.refreshStorageInfo(false);
    }
    const {
      sensitive
    } = this.storage.info || {};
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

  openDataStorageItemPreview = async (item, event) => {
    if (event) {
      event.stopPropagation();
    }
    try {
      await this.storage.refreshStorageInfo(false);
      const {
        name
      } = this.storage.info || {};
      const url = getStaticResourceUrl(name, item.path);
      window.open(url, '_blank');
    } catch (_) {}
  };

  checkWsiPreviewAvailability = (file) => {
    if (!file) {
      return;
    }
    const {storageId} = this.props;
    this.setState({previewPending: true}, () => {
      getPreviewConfiguration(storageId, file.path)
        .then((configuration) => this.setState({
          previewPending: false,
          previewAvailable: !!configuration
        }));
    });
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

  setDefaultPreview = () => this.setState({
    previewPending: false,
    previewAvailable: false
  });

  getRestoredStatus = (item) => {
    const {
      parentRestore,
      currentRestores
    } = this.lifeCycleRestoreInfo;
    if (!item) {
      return null;
    }
    const itemRestoreStatus = (currentRestores || [])
      .find(({path = ''}) => item.name === path.split('/')
        .filter(Boolean)
        .pop()
      );
    if (itemRestoreStatus) {
      return itemRestoreStatus;
    }
    return item.type === 'File'
      ? parentRestore
      : null;
  };

  get columns () {
    const tableData = this.items;
    const hasAppsColumn = tableData.some(o => o.miew || o.vsi || o.hcs || o.documentPreview);
    const hasVersions = tableData.some(o => o.versions);
    const getItemIcon = (item) => {
      if (!item) {
        return null;
      }
      let restoredStatus = this.getRestoredStatus(item);
      if (
        restoredStatus &&
        item.type !== 'Folder' &&
        (!item.archived || (item.isVersion && !restoredStatus.restoreVersions))
      ) {
        restoredStatus = null;
      }
      if (/^file$/i.test(item.type) && SAMPLE_SHEET_FILE_NAME_REGEXP.test(item.name)) {
        return (
          <RestoreStatusIcon restoreInfo={restoredStatus}>
            <Icon
              className={classNames(styles.itemType, 'cp-primary')}
              type="appstore-o"
            />
          </RestoreStatusIcon>
        );
      }
      return (
        <RestoreStatusIcon restoreInfo={restoredStatus}>
          <Icon
            className={styles.itemType}
            type={item.type.toLowerCase()}
          />
        </RestoreStatusIcon>
      );
    };
    const selectionColumn = {
      key: 'selection',
      title: '',
      className: (this.showVersions || hasVersions)
        ? styles.checkboxCellVersions
        : styles.checkboxCell,
      render: (item) => {
        if (item.selectable &&
          (item.downloadable || item.editable || item.shareAvailable) &&
          (!this.isOmicsStore || this.isOmicsFolder)) {
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
      render: (text, item) => (
        <Observer>
          {() => getItemIcon(item)}
        </Observer>
      )
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
                    // eslint-disable-next-line max-len
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
        if (item.documentPreview) {
          apps.push(
            <div
              className={styles.appLink}
              onClick={(event) => this.openDataStorageItemPreview(item, event)}
              key={item.key}
            >
              <Icon
                type="export"
                className="cp-primary"
                style={{fontSize: 'larger'}}
              />
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
      render: (labels) => (
        <LabelsRenderer
          labelClassName={styles.label}
          labels={labels}
        />
      ),
      onCellClick: (item) => this.didSelectDataStorageItem(item)
    };
    const actionsColumn = {
      key: 'actions',
      className: styles.itemActions,
      render: this.actionsRenderer
    };

    return [
      selectionColumn,
      typeColumn,
      hasAppsColumn && appsColumn,
      nameColumn,
      sizeColumn,
      changedColumn,
      labelsColumn,
      actionsColumn
    ].filter(Boolean);
  };

  didSelectDataStorageItem = (item) => {
    if (item.type.toLowerCase() === 'folder') {
      this.navigate(this.props.storageId, item.path, {clearPathMarkers: false});
      if (this.state.metadata) {
        this.setState({
          importedJobs: this.isOmicsStore
        });
      }
      return;
    }
    if (item.type.toLowerCase() === 'file' && !item.deleteMarker) {
      const extension = (item.path || '')
        .split('.')
        .pop()
        .toLowerCase();
      this.setState({
        selectedFile: item,
        metadata: true,
        importedJobs: this.isOmicsStore
      }, () => {
        switch (extension) {
          case 'vsi':
          case 'mrxs':
            this.checkWsiPreviewAvailability(item);
            break;
          case 'hcs':
            this.checkHcsPreviewAvailability(item);
            break;
          default:
            this.setDefaultPreview();
            break;
        }
      });
    }
  };

  get bulkDownloadEnabled () {
    if (this.storage.info && this.storage.info.sensitive) {
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
    return !!this.storage.pageElements &&
      this.storage.pageElements.length > 0 &&
      this.storage.pageElements.some(o => !this.fileIsSelected(o));
  }

  get clearSelectionVisible () {
    return this.state.selectedItems.length > 0;
  }

  selectAll = (type) => {
    const selectedItems = this.items.filter(item => {
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
  };

  clearSelection = () => {
    this.setState({selectedItems: []});
  };

  showFilesVersionsChanged = (showVersions) => {
    this.navigate(
      this.props.storageId,
      this.props.path,
      {
        showVersions
      }
    );
  };

  showArchivedFilesChanged = (showArchives) => {
    this.navigate(
      this.props.storageId,
      this.props.path,
      {
        showVersions: this.props.showVersions,
        showArchives
      }
    );
  };

  get isFileSelectedEmpty () {
    if (!this.state.selectedFile) {
      return false;
    }
    const selectedFile = this.items
      .find(file => file.name === this.state.selectedFile.name);

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
    if (!this.storage.info) {
      return;
    }
    const {
      parentFolderId
    } = this.storage.info;
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
            if (parentFolderId) {
              this.props.folders.invalidateFolder(parentFolderId);
            }
            this.props.pipelinesLibrary.invalidateCache();
            if (this.props.onReloadTree) {
              this.props.onReloadTree(true, parentFolderId);
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
    const convertToVersionedStorageActionAvailable = this.storage.infoLoaded &&
      this.storage.isOwner &&
      this.storage.info &&
      /^nfs$/i.test(this.storage.info.type) &&
      (
        // we've navigated to some path - storage is not empty
        (this.props.path && !this.storage.pageError) ||
        // or we have elements on current path - storage is not empty as well
        (this.storage.pageElements || []).length > 0 ||
        // or we've navigated to second/third/etc. page,
        // event if it is empty - the first page exists,
        // so - storage is not empty as well
        this.storage.currentPagination.page > 0
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

  renderSelectionActionsButton = () => {
    const {
      preferences,
      storageId
    } = this.props;
    const {selectedItems = []} = this.state;
    const itemsAvailableForShare = selectedItems
      .filter(o => o.shareAvailable);
    const itemsAvailableForDownload = selectedItems
      .filter(o => o.downloadable && /^file$/i.test(o.type))
      .map(o => ({
        storageId,
        path: o.path,
        name: o.name
      }));
    const Keys = {
      clear: 'clear',
      restore: 'restore',
      share: 'share',
      generateUrl: 'generate-url',
      removeAll: 'remove-all',
      download: 'download',
      restoreOmics: 'restoreOmics'
    };
    const clearAction = {
      key: Keys.clear,
      title: 'Clear selection',
      available: this.clearSelectionVisible
    };
    const downloadAction = {
      key: Keys.download,
      title: 'Download',
      icon: 'download',
      available: !this.isOmicsStore && itemsAvailableForDownload.length > 0
    };
    const restoreAction = {
      key: Keys.restore,
      title: `Restore transferred item${this.restorableItems.length > 1 ? 's' : ''}`,
      available: this.userLifeCyclePermissions.write &&
        this.restorableItems.length > 0 && !this.isOmicsStore,
      icon: 'reload'
    };
    const restoreOmicsAction = {
      key: Keys.restoreOmics,
      title: `Restore transferred item${this.restorableItems.length > 1 ? 's' : ''}`,
      available: this.userLifeCyclePermissions.write &&
        this.restorableItems.length > 0 && this.isSequenceStorage && this.isOmicsFolder,
      icon: 'reload'
    };
    const getShareActionTitle = () => {
      if (itemsAvailableForShare.length === 1) {
        return (
          <span
            className="cp-ellipsis-text"
            style={{maxWidth: 300}}
          >
            Share <b>{itemsAvailableForShare[0].name}</b> {itemsAvailableForShare[0].type}
          </span>
        );
      }
      return (
        <span>
          Share <b>{itemsAvailableForShare.length}</b> items
        </span>
      );
    };
    const shareAction = {
      key: Keys.share,
      title: getShareActionTitle(),
      available: this.sharingEnabled && itemsAvailableForShare.length > 0,
      icon: 'export'
    };
    const generateURLAction = {
      key: Keys.generateUrl,
      title: 'Generate URL',
      available: this.bulkDownloadEnabled &&
        this.storageAllowSignedUrls,
      icon: 'link'
    };
    const removeAllAction = {
      key: Keys.removeAll,
      title: 'Remove',
      available: this.removeAllSelectedItemsEnabled,
      className: 'cp-danger',
      icon: 'delete'
    };
    const divider = {};
    const actions = [];
    const appendAction = (action) => {
      if (action && action.available) {
        actions.push(action);
      }
    };
    const lastAction = () => actions.length > 0
      ? actions[actions.length - 1]
      : undefined;
    const appendDivider = () => {
      if (lastAction() !== divider) {
        actions.push(divider);
      }
    };
    appendAction(shareAction);
    appendAction(restoreAction);
    appendAction(restoreOmicsAction);
    appendAction(generateURLAction);
    appendAction(downloadAction);
    appendDivider();
    appendAction(clearAction);
    appendDivider();
    appendAction(removeAllAction);
    if (lastAction() === divider) {
      actions.pop();
    }
    if (actions.filter((action) => action !== divider).length === 0) {
      return null;
    }
    const doAction = (action, event) => {
      if (!action) {
        return;
      }
      switch (action.key) {
        case Keys.clear:
          this.clearSelection();
          break;
        case Keys.share:
          this.openShareItemDialog(event);
          break;
        case Keys.restore:
          this.openRestoreFilesDialog('file');
          break;
        case Keys.restoreOmics:
          this.restoreOmics();
          break;
        case Keys.generateUrl:
          this.toggleGenerateDownloadUrlsModalFn(event);
          break;
        case Keys.removeAll:
          this.removeSelectedItemsConfirm(event);
          break;
        case Keys.download:
          handleDownloadItems(preferences, itemsAvailableForDownload);
          break;
        default:
          break;
      }
    };
    if (actions.length === 1) {
      const action = actions[0];
      return (
        <Button
          size="small"
          id={`selection-action-${action.key}`}
          onClick={(event) => doAction(action, event)}
          style={{lineHeight: 1}}
        >
          {action.icon && (<Icon type={action.icon} style={{marginRight: 5}} />)}
          {action.title}
        </Button>
      );
    }
    const title = selectedItems && selectedItems.length > 0
      ? `${selectedItems.length} item${selectedItems.length === 1 ? '' : 's'} selected`
      : 'Selection';
    const renderAction = (action, idx) => {
      if (action === divider) {
        return (
          <Divider key={`divider-${idx}`} />
        );
      }
      return (
        <MenuItem
          id={`selection-action-${action.key}`}
          key={action.key}
          className={classNames(action.className, `selection-action-${action.key}`)}
        >
          <div style={{display: 'flex', alignItems: 'center'}}>
            {action.icon && (<Icon type={action.icon} style={{marginRight: 5}} />)}
            {action.title}
          </div>
        </MenuItem>
      );
    };
    return (
      <Dropdown
        placement="bottomRight"
        trigger={['click']}
        overlayClassName="selection-actions-dropdown"
        overlay={
          <Menu
            selectedKeys={[]}
            onClick={doAction}
            style={{width: 200, cursor: 'pointer'}}
            className="selection-actions-menu"
          >
            {
              actions.map(renderAction)
            }
          </Menu>
        }
        key="create actions">
        <Button
          id="selection-actions"
          size="small"
        >
          {title}
          <Icon type="down" />
        </Button>
      </Dropdown>
    );
  };

  renderShareCurrentFolderButton = () => {
    const {
      path
    } = this.props;
    if (!path || this.isOmicsStore) {
      return undefined;
    }
    return (
      <Button
        id="share-selected-folder"
        size="small"
        onClick={this.openShareCurrentFolderDialog}
      >
        Share <b>current</b> folder
      </Button>
    );
  };

  renderContent = () => {
    if (this.storage.pageError) {
      return (
        <div
          style={{flex: 1}}
        >
          <br />
          <Alert
            message={`Error retrieving data storage items: ${this.storage.pageError}`}
            type="error"
          />
        </div>
      );
    }
    const {
      type
    } = this.storage.info || {};
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
              (!this.isOmicsStore || this.isOmicsFolder) && (
                <Button
                  id="select-all-button"
                  size="small" onClick={() => this.selectAll(undefined)}
                  disabled={!this.selectAllAvailable}
                >
                  Select page
                </Button>
              )
            }
            {
              (!this.isOmicsStore || this.isOmicsFolder) && (
                this.renderSelectionActionsButton()
              )
            }
          </div>
          <div style={{paddingRight: 8}}>
            {this.renderShareCurrentFolderButton()}
            {
              this.storage.writeAllowed &&
              !this.isOmicsStore && (
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
              )
            }
            {
              this.storage.writeAllowed &&
              !this.isOmicsStore && (
                <UploadButton
                  multiple
                  onRefresh={() => this.refreshList()}
                  title={'Upload'}
                  storageId={this.props.storageId}
                  path={this.props.path}
                  storageInfo={this.storage.info}
                  region={this.regionName}
                  // synchronous
                  uploadToS3={/^s3$/i.test(type)}
                  uploadToNFS={/^nfs$/i.test(type)}
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
            {
              this.storage.writeAllowed &&
              this.isOmicsStore &&
              this.isOmicsFolder && (
                <Button
                  id="import-button"
                  size="small"
                  type="primary"
                  onClick={() => this.openOmicsDialog()}
                >
                  Import
                </Button>
              )
            }
          </div>
        </Row>
      );
    };

    return (
      <Table
        className={styles.table}
        style={{flex: 1}}
        key="table"
        dataSource={this.items}
        columns={this.columns}
        loading={this.storage.pagePending}
        title={title}
        rowKey="key"
        pagination={false}
        rowClassName={(item) => classNames({
          [styles[item.type.toLowerCase()]]: true,
          'cp-storage-deleted-row': !!item.deleteMarker
        })}
        locale={{emptyText: 'Folder is empty'}}
        size="small"
      />
    );
  };

  onToggleMetadata = () => {
    if (this.showMetadata) {
      this.setState({metadata: !this.showMetadata, selectedFile: null});
    } else {
      this.setState({metadata: !this.showMetadata});
    }
  };

  onToggleJobs = () => {
    this.setState({
      importedJobs: !this.showJobs
    });
  };

  onPanelClose = (key) => {
    switch (key) {
      case METADATA_PANEL_KEY:
        this.setState({
          metadata: false,
          importedJobs: false
        });
        break;
    }
  };

  renderPresentationConfiguration = () => {
    const metadataAction = {
      key: 'attributes',
      title: 'Show attributes',
      checked: this.showMetadata,
      available: true
    };
    const jobAction = {
      key: 'jobs',
      title: 'Show jobs',
      checked: this.showJobs,
      available: this.isOmicsStore
    };
    const archivedFilesAction = {
      key: 'archive',
      title: 'Show archived files',
      checked: this.showArchives,
      available: (!this.isOmicsStore &&
        (this.userLifeCyclePermissions.read || this.userLifeCyclePermissions.write))
    };
    const versionsAction = {
      key: 'version',
      title: 'Show file versions',
      checked: this.showVersions,
      available: this.versionControlsEnabled
    };
    const doAction = (action) => {
      if (!action) {
        return;
      }
      switch (action.key) {
        case 'attributes': this.onToggleMetadata(); break;
        case 'jobs': this.onToggleJobs(); break;
        case 'archive': this.showArchivedFilesChanged(!this.showArchives); break;
        case 'version': this.showFilesVersionsChanged(!this.showVersions); break;
        default:
          break;
      }
    };
    const actions = [];
    const appendAction = (action) => {
      if (action.available) {
        actions.push(action);
      }
    };
    appendAction(metadataAction);
    appendAction(jobAction);
    appendAction(archivedFilesAction);
    appendAction(versionsAction);
    if (actions.length === 0) {
      return null;
    }
    const renderAction = (action) => (
      <MenuItem
        id={`presentation-action-${action.key}`}
        key={action.key}
        className={classNames(action.className, `presentation-action-${action.key}`)}
      >
        <div style={{display: 'flex', alignItems: 'center'}}>
          {action.icon && (<Icon type={action.icon} style={{marginRight: 5}} />)}
          {action.title}
          {
            action.checked && (
              <Icon
                type="check"
                style={{marginLeft: 'auto'}}
              />
            )
          }
        </div>
      </MenuItem>
    );
    return (
      <Dropdown
        placement="bottomRight"
        trigger={['click']}
        overlayClassName="presentation-actions-dropdown"
        overlay={
          <Menu
            selectedKeys={[]}
            onClick={doAction}
            style={{width: 150, cursor: 'pointer'}}
            className="presentation-actions-menu"
          >
            {
              actions.map(renderAction)
            }
          </Menu>
        }
        key="create actions">
        <Button
          id="presentation-actions"
          size="small"
        >
          <Icon type="appstore" />
        </Button>
      </Dropdown>
    );
  };

  render () {
    if (
      (this.storage.pending && !this.storage.infoLoaded) ||
      (!this.props.authenticatedUserInfo.loaded && this.props.authenticatedUserInfo.pending)
    ) {
      return <LoadingView />;
    }
    if (this.storage.infoError) {
      return <Alert message={this.storage.infoError} type="error" />;
    }
    if (this.props.authenticatedUserInfo.error) {
      return <Alert message={this.props.authenticatedUserInfo.error} type="error" />;
    }
    const {
      name,
      description,
      type,
      locked,
      sensitive,
      regionId,
      mountStatus,
      mask,
      policySupported
    } = this.storage.info || {};

    const restoreClassChangeDisclaimer = (item, operation) => {
      if (!item) {
        return '';
      }
      const activeRestore = item.archived
        ? this.getRestoredStatus(item)
        : null;
      let disclaimer;
      if (item.type === 'Folder' && activeRestore) {
        disclaimer = [
          'Files in this folder will be recursively converted',
          ' to the "STANDARD" storage class',
          ...(operation ? [` after ${operation}.`] : ['.'])
        ];
      }
      if (item.type === 'File' && activeRestore) {
        disclaimer = [
          'This file will be converted to the "STANDARD" storage class',
          ...(operation ? [` after ${operation}.`] : ['.'])
        ];
      }
      return disclaimer
        ? disclaimer.filter(Boolean).join('')
        : '';
    };

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
              textEditableField={name}
              onSaveEditableField={this.renameDataStorage}
              readOnlyEditableField={!this.storage.writeAllowed || this.isOmicsStore}
              editStyleEditableField={{flex: 1}}
              icon={!/^nfs$/i.test(type) ? 'inbox' : 'hdd'}
              iconClassName={
                classNames(
                  styles.editableControl,
                  {
                    [styles.readonly]: locked
                  }
                )
              }
              lock={locked}
              lockClassName={
                classNames(
                  styles.editableControl,
                  {
                    [styles.readonly]: locked
                  }
                )
              }
              sensitive={sensitive}
              displayTextEditableField={
                <span>
                  {name}
                  <AWSRegionTag
                    className={classNames(
                      styles.storageRegion
                    )}
                    displayName
                    flagStyle={{fontSize: 'smaller'}}
                    providerStyle={{fontSize: 'smaller'}}
                    regionId={regionId}
                    style={{marginLeft: 5, fontSize: 'medium'}}
                  />
                </span>
              }
              subject={this.storage.info}
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
                status={mountStatus}
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
              {
                this.renderPresentationConfiguration()
              }
              <StorageSharedLinkButton
                storageId={this.props.storageId}
              />
              {this.renderEditAction()}
              <Button
                id="refresh-storage-button"
                size="small"
                onClick={() => this.refreshList(true)}
                disabled={this.storage.pagePending}>Refresh</Button>
            </Row>
          </Col>
        </Row>
        <ContentMetadataPanel
          style={{flex: 1, overflow: 'auto'}}
          onPanelClose={this.onPanelClose}
          contentContainerStyle={{overflow: 'inherit'}}>
          <div
            key={CONTENT_PANEL_KEY}
            style={{flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto'}}>
            <Row className={styles.dataStorageInfoContainer}>
              {
                description && (
                  <Row>
                    <b>Description: </b>
                    {description}
                  </Row>
                )
              }
            </Row>
            <Row style={{marginLeft: 5}}>
              <DataStorageNavigation
                path={this.props.path}
                storage={this.storage.info}
                navigate={this.navigate}
                navigateFull={this.navigateFull} />
            </Row>
            {
              this.renderContent()
            }
            <StoragePagination
              storage={this.storage}
              style={{marginTop: 10, marginBottom: 10, paddingRight: 15}}
            />
          </div>
          {
            (this.showMetadata || this.showJobs) &&
            <Metadata
              pending={this.state.previewPending}
              key={METADATA_PANEL_KEY}
              readOnly={!this.metadataEditable}
              downloadable={!sensitive}
              showContent={
                !sensitive &&
                (
                  this.state.selectedFile && this.state.selectedFile.archived
                    ? this.state.selectedFile.restored
                    : true
                )
              }
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
                  : name
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
                (/^nfs$/i.test(type) && !this.isOmicsStore)
                  ? FS_MOUNTS_NOTIFICATIONS_ATTRIBUTE
                  : false,
                ((!/^nfs$/i.test(type) && !this.state.selectedFile) && !this.isOmicsStore)
                  ? REQUEST_DAV_ACCESS_ATTRIBUTE
                  : false
              ].filter(Boolean)}
              extraInfo={!this.isOmicsStore ? [
                <LifeCycleCounter
                  storage={this.storage.info}
                  path={this.props.path}
                  onClickRestore={() => this.openRestoreFilesDialog('folder')}
                  restoreInfo={this.lifeCycleRestoreInfo}
                  restoreEnabled={this.userLifeCyclePermissions.write}
                  visible={!this.state.selectedFile && (
                    this.userLifeCyclePermissions.read ||
                    this.userLifeCyclePermissions.write
                  )}
                />,
                <StorageSize storage={this.storage.info} />
              ] : []}
              specialTagsProperties={{
                storageType: this.fileShareMount ? this.fileShareMount.mountType : undefined,
                storageMask: mask,
                storageId: Number(this.props.storageId)
              }}
              jobList={
                this.isOmicsStore && this.state.importedJobs ? (
                  <JobList
                    storageId={this.props.storageId}
                    updateJobsSearch={this.state.updateJobsSearch}
                  />
                ) : null
              }
              showMetadata={this.showMetadata}
            />
          }
        </ContentMetadataPanel>
        <DataStorageEditDialog
          visible={this.state.editDialogVisible}
          dataStorage={this.storage.info}
          pending={this.storage.infoPending}
          policySupported={policySupported}
          onDelete={this.deleteStorage}
          onCancel={this.closeEditDialog}
          onSubmit={this.onDataStorageEdit} />
        <OmicsStorageImportDialog
          visible={this.state.omicsDialogVisible}
          dataStorage={this.storage.info}
          pending={this.storage.infoPending}
          policySupported={policySupported}
          onCancel={this.closeOmicsDialog}
          onSubmit={this.onImportOmicsJob} />
        <ConvertToVersionedStorage
          storageName={name}
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
                  disabled={!this.storage.writeAllowed}
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
          onSubmit={this.renameItem}
          disclaimerFn={() => {
            const text = restoreClassChangeDisclaimer(
              this.state.renameItem,
              'rename'
            );
            return text ? (
              <Alert
                message={text}
                type="info"
              />
            ) : null;
          }}
        />
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
        <LifeCycleRestoreModal
          visible={this.state.restoreDialogVisible}
          items={this.restorableItems}
          restoreInfo={this.lifeCycleRestoreInfo}
          onCancel={this.closeRestoreFilesDialog}
          onOk={this.restoreFiles}
          folderPath={this.props.path}
          pending={this.state.restorePending}
          mode={this.state.lifeCycleRestoreMode}
          versioningEnabled={this.versionControlsEnabled}
        />
        <DataStorageCodeForm
          file={this.state.editFile}
          downloadable={!sensitive}
          storageId={this.props.storageId}
          cancel={this.closeEditFileForm}
          save={this.saveEditableFile}
          onSaveDisclaimer={restoreClassChangeDisclaimer(this.state.editFile, 'save')}
        />
        {this.renderPreviewModal()}
      </div>
    );
  }

  componentWillUnmount () {
    message.destroy();
    this.storage.destroy();
  }

  componentDidMount () {
    const {openPreview, path} = this.props;
    if (openPreview) {
      const file = {
        path: `${path ? `${path}/` : ''}${openPreview}`,
        name: openPreview
      };
      this.openPreviewModal(file);
    }
    this.updateStorageIfRequired();
    if (!this.isOmicsStore) {
      this.closeImportedJobsIfRequired();
    }
  }

  componentDidUpdate (prevProps) {
    this.clearSelectedItemsIfRequired();
    this.updateStorageIfRequired();
    this.closeImportedJobsIfRequired(prevProps.storageId);
  }

  updateStorageIfRequired = () => {
    this.storage.initialize(
      this.props.storageId,
      this.props.path,
      this.props.showVersions,
      this.props.showArchives
    );
  };

  clearSelectedItemsIfRequired = () => {
    if (this.storage.storageId !== this.props.storageId) {
      this.clearSelection();
    }
  };

  componentWillReceiveProps (nextProps, nextState) {
    if (nextProps.storageId !== this.props.storageId) {
      this.setState({selectedFile: null});
    }
  }

  closeImportedJobsIfRequired = (prevStorageId) => {
    if (this.state.importedJobs) {
      if (!this.isOmicsStore && prevStorageId !== this.props.storageId) {
        this.setState({importedJobs: false});
      }
    }
  }
}

export {STORAGE_CLASSES, isStandardClass};
