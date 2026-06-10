import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import type {FormInstance} from 'antd';
import {Form, message} from 'antd';
import {useQuery} from '@tanstack/react-query';
import {cloudRegionsQueryOptions} from '../../../../../queries';
import {useAuthenticatedUser} from '../../../../../stores/users/hooks.ts';
import {
  useBooleanPreferenceValue,
  useJsonPreferenceValue,
} from '../../../../../queries/preferences/hooks.ts';
import {CloudRegion} from '../../../../../@types/regions.ts';
import type {DataStorage} from '../../../../../@types/library.ts';
import {
  saveDataStorage,
  updateDataStorage,
} from '../../../../../api/datastorage/datastorage-api.ts';
import {
  extractFileShareMountList,
  parseFSMountPath,
} from '../../../../pipelines/browser/forms/data-storage-path-input/index.tsx';
import {parsePermissionsRestrictionsConfig} from '../../../../../models/preferences/utilities/parse-permissions-restrictions';
import dataStorageRestrictedAccessCheck from '../../../../../utils/data-storage-restricted-access';
import roleModel from '../../../../../utils/roleModel';
import {getErrorDescription} from '../../../../../utilities/errors.ts';
import {SERVICE_TYPES, type PermissionsRestrictions, type StorageServiceType} from './types.ts';

export type DataStorageEditControllerOptions = {
  form: FormInstance;
  dataStorage?: DataStorage;
  isNfsMount?: boolean;
  omicsStore?: boolean;
  policySupported?: boolean;
  storageOperationsEnabled?: boolean;
  onDone?: () => void;
  onClose?: (e?: React.MouseEvent | React.KeyboardEvent) => void;
};

export type DataStorageEditController = {
  isNew: boolean;
  isNfsMount: boolean;
  omicsStore: boolean;
  isReadOnly: boolean;
  restrictedAccess: boolean;
  restrictedAccessCheckInProgress: boolean;

  activeTab: string;
  setActiveTab: (tab: string) => void;

  mountDisabled: boolean;
  setMountDisabled: (v: boolean) => void;
  versioningEnabled: boolean;
  setVersioningEnabled: (v: boolean) => void;
  pathPermissionsEnabled: boolean;
  setPathPermissionsEnabled: (v: boolean) => void;
  sharingEnabled: boolean;
  setSharingEnabled: (v: boolean) => void;
  sensitive: boolean;
  setSensitive: (v: boolean) => void;
  skipPolicy: boolean;
  setSkipPolicy: (v: boolean) => void;
  omicsType: string | undefined;
  setOmicsType: (v: string | undefined) => void;

  nfsStoragePathValid: boolean;
  setNfsStoragePathValid: (v: boolean) => void;
  aliasValid: boolean;
  setAliasValid: (v: boolean) => void;

  submitPending: boolean;

  awsRegions: CloudRegion[];
  fileShareMountsList: ReturnType<typeof extractFileShareMountList>;
  currentRegion: CloudRegion | undefined;
  defaultAwsRegion: CloudRegion | undefined;
  currentRegionSupportsPolicy: boolean;
  currentRegionSupportsStoragePermissions: boolean;

  storageVersioningAllowed: boolean;
  isAdvancedUser: boolean;
  isUserDefaultStorage: boolean | undefined;
  permissionsRestrictions: PermissionsRestrictions;

  omicsTypes: [string, string][];

  initialValues: Record<string, unknown>;
  skipPolicyFlagVisible: boolean;

  transitionRulesAvailable: boolean;
  transitionRulesReadOnly: boolean;

  handleSubmit: (e?: React.MouseEvent | React.KeyboardEvent) => void;
  validateStoragePath: (rule: unknown, value: unknown) => Promise<void>;
  validateAlias: (rule: unknown, value: unknown) => Promise<void>;
  handleAfterClose: () => void;
};

export function useDataStorageEditController(
  options: DataStorageEditControllerOptions,
): DataStorageEditController {
  const {
    form,
    dataStorage,
    isNfsMount: isNfsMountProp,
    omicsStore: omicsStoreProp,
    policySupported,
    storageOperationsEnabled = true,
    onDone,
    onClose,
  } = options;

  const isNew = !dataStorage?.id;

  const isNfsMount = dataStorage ? dataStorage.type === 'NFS' : (isNfsMountProp ?? false);

  const omicsStore = dataStorage
    ? ['AWS_OMICS_SEQ', 'AWS_OMICS_REF'].includes(dataStorage.type ?? '')
    : (omicsStoreProp ?? false);

  // --- shared state ---
  const [activeTab, setActiveTab] = useState('info');
  const [mountDisabled, setMountDisabled] = useState(false);
  const [versioningEnabled, setVersioningEnabled] = useState(false);
  const [pathPermissionsEnabled, setPathPermissionsEnabled] = useState(false);
  const [sharingEnabled, setSharingEnabled] = useState(false);
  const [sensitive, setSensitive] = useState(false);
  const [skipPolicy, setSkipPolicy] = useState(false);
  const [omicsType, setOmicsType] = useState<string | undefined>(undefined);
  const [nfsStoragePathValid, setNfsStoragePathValid] = useState(false);
  const [aliasValid, setAliasValid] = useState(false);
  const [submitPending, setSubmitPending] = useState(false);
  const [restrictedAccess, setRestrictedAccess] = useState(true);
  const [restrictedAccessCheckInProgress, setRestrictedAccessCheckInProgress] = useState(false);

  // --- stores ---
  const {data: awsRegions = []} = useQuery(cloudRegionsQueryOptions());
  const user = useAuthenticatedUser();
  const storagePolicyBackupVisibleNonAdmins = useBooleanPreferenceValue(
    'storage.policy.backup.visible.non.admins',
  );
  const uiStoragesPermissionsRestrictionsRaw = useJsonPreferenceValue<unknown[]>(
    'ui.storages.permissions.restrictions',
  );

  // --- restricted access check ---
  const checkRestrictedAccessTokenRef = useRef(0);
  const checkRestrictedAccess = useCallback(() => {
    const id = dataStorage?.id;
    checkRestrictedAccessTokenRef.current += 1;
    const token = checkRestrictedAccessTokenRef.current;
    setRestrictedAccessCheckInProgress(true);
    setRestrictedAccess(true);
    async function run() {
      let access = true;
      try {
        access = id === undefined ? false : ((await dataStorageRestrictedAccessCheck(id)) ?? true);
      } catch {
        access = true;
      }
      if (token === checkRestrictedAccessTokenRef.current) {
        if (access) {
          console.log(`Storage #${id} is in the restricted access mode for current user`);
        }
        setRestrictedAccess(access);
        setRestrictedAccessCheckInProgress(false);
      }
    }
    run().catch(() => {});
  }, [dataStorage?.id]);

  useEffect(() => {
    checkRestrictedAccess();
    return () => {
      checkRestrictedAccessTokenRef.current += 1;
    };
  }, [checkRestrictedAccess]);

  // --- sync state from dataStorage prop ---
  const prevDataStorageIdRef = useRef<number | undefined>(undefined);
  useEffect(() => {
    const prevId = prevDataStorageIdRef.current;
    const curId = dataStorage?.id;
    if (prevId !== curId) {
      prevDataStorageIdRef.current = curId;
      setMountDisabled(dataStorage?.mountDisabled ?? false);
      setVersioningEnabled(dataStorage?.storagePolicy?.versioningEnabled ?? true);
      setPathPermissionsEnabled(dataStorage?.pathPermissionsEnabled ?? false);
      setSensitive(dataStorage?.sensitive ?? false);
      setSharingEnabled(!isNfsMount && (dataStorage?.shared ?? false));
      setSkipPolicy(false);
    }
  }, [dataStorage, isNfsMount]);

  // --- derived region values ---
  const fileShareMountsList = useMemo(() => extractFileShareMountList(awsRegions), [awsRegions]);

  const defaultAwsRegion = useMemo(
    () => awsRegions.find((r) => r.default) ?? undefined,
    [awsRegions],
  );

  const regionIdValue = Form.useWatch('regionId', form);

  const currentRegion = useMemo(() => {
    return awsRegions.find((r) => `${r.id}` === `${regionIdValue}`) ?? defaultAwsRegion;
  }, [awsRegions, defaultAwsRegion, regionIdValue]);

  const currentRegionSupportsPolicy = useMemo(
    () => !!currentRegion && ['AWS', 'GCP'].includes(currentRegion.provider),
    [currentRegion],
  );

  const currentRegionSupportsStoragePermissions = useMemo(
    () => !!currentRegion && currentRegion.provider === 'AWS',
    [currentRegion],
  );

  // --- user/role derived values ---
  const isAdmin = user.admin ?? false;
  const isAdvancedUser = useMemo(
    () => (user.roles ?? []).some((r) => /^ROLE_ADVANCED_USER$/i.test(r.name)),
    [user.roles],
  );

  const isUserDefaultStorage = useMemo(() => {
    if (!dataStorage) return undefined;
    return Number(dataStorage.id) === Number(user.defaultStorageId);
  }, [dataStorage, user.defaultStorageId]);

  const storageVersioningAllowed = useMemo(() => {
    if (!dataStorage) return true;
    const isOwner = roleModel.isOwner(dataStorage);
    const isStorageAdmin = roleModel.userIs.storageAdmin(user);
    if (isAdmin || isStorageAdmin) return true;
    const backupVisible = storagePolicyBackupVisibleNonAdmins ?? true;
    return isOwner && backupVisible;
  }, [dataStorage, isAdmin, storagePolicyBackupVisibleNonAdmins, user]);

  const permissionsRestrictions: PermissionsRestrictions = useMemo(() => {
    const isStorageAdmin = roleModel.userIs.storageAdmin(user);
    if (!isAdmin && !isAdvancedUser && !isStorageAdmin) {
      const restrictions = parsePermissionsRestrictionsConfig(
        Array.isArray(uiStoragesPermissionsRestrictionsRaw)
          ? uiStoragesPermissionsRestrictionsRaw
          : [],
      );
      const readOnlyRoles = restrictions
        .filter((r) => r.readonly)
        .filter((r) => (r.onlyDefaultStorage ? isUserDefaultStorage : true))
        .map((r) => r.role);
      const defaultMask = restrictions.map((rule) => ({role: rule.role, mask: rule.defaultMask}));
      const enabledMask = restrictions.map((rule) => ({role: rule.role, mask: rule.enabledMask}));
      return {defaultMask, enabledMask, readOnlyRoles};
    }
    return {defaultMask: [], enabledMask: [], readOnlyRoles: []};
  }, [isAdmin, isAdvancedUser, isUserDefaultStorage, uiStoragesPermissionsRestrictionsRaw, user]);

  const isReadOnly = dataStorage
    ? !!dataStorage.locked ||
      restrictedAccess ||
      (!roleModel.isOwner(dataStorage) && !roleModel.userIs.storageAdmin(user))
    : false;

  // --- user permissions for transition rules ---
  const userPermissions = useMemo(() => {
    if (!dataStorage) return {read: false, write: false};
    const readAllowed = roleModel.readAllowed(dataStorage);
    const writeAllowed = roleModel.writeAllowed(dataStorage);
    const isStorageAdmin = roleModel.userIs.storageAdmin(user);
    const isOwner = roleModel.isOwner(dataStorage);
    const isArchiveManager = roleModel.userIs.archiveManager(user);
    const isArchiveReader = roleModel.userIs.archiveReader(user);
    return {
      read: isStorageAdmin || ((isOwner || isArchiveManager || isArchiveReader) && readAllowed),
      write: isStorageAdmin || ((isOwner || isArchiveManager) && writeAllowed),
    };
  }, [dataStorage, user]);

  const transitionRulesAvailable = useMemo(
    () =>
      (userPermissions.read || userPermissions.write) &&
      !!dataStorage?.id &&
      /^s3$/i.test(dataStorage.type ?? '') &&
      storageOperationsEnabled,
    [dataStorage, storageOperationsEnabled, userPermissions],
  );

  const transitionRulesReadOnly = userPermissions.read && !userPermissions.write;

  // --- misc derived ---
  const omicsTypes: [string, string][] = useMemo(
    () => Object.entries({AWS_OMICS_REF: 'Reference store', AWS_OMICS_SEQ: 'Sequence store'}),
    [],
  );

  const initialValues = useMemo(
    () => ({
      path: dataStorage,
      name: dataStorage?.name,
      omicsType: dataStorage?.type,
      regionId:
        dataStorage?.regionId != null
          ? String(dataStorage.regionId)
          : defaultAwsRegion
            ? String(defaultAwsRegion.id)
            : undefined,
      description: dataStorage?.description,
      toolsToMount: dataStorage?.toolsToMount,
      backupDuration: dataStorage?.storagePolicy?.backupDuration,
      mountPoint: dataStorage?.mountPoint,
      mountOptions: dataStorage?.mountOptions,
    }),
    [dataStorage?.id, defaultAwsRegion?.id],
  );

  const skipPolicyFlagVisible = !dataStorage;

  // --- validators ---
  const validateStoragePath = useCallback(
    (_rule: unknown, value: unknown) => {
      if (value && isNfsMount) {
        const parseResult = parseFSMountPath(value, fileShareMountsList);
        if (!parseResult?.storagePath) {
          return Promise.reject(new Error('Storage path is required'));
        }
        if (!parseResult.storagePath.startsWith('/')) {
          return Promise.reject(new Error("Storage path must begin with '/'"));
        }
      } else if ((!value || !(value as Record<string, unknown>).path) && !omicsStore) {
        return Promise.reject(new Error('Storage path is required'));
      }
      return Promise.resolve();
    },
    [fileShareMountsList, isNfsMount, omicsStore],
  );

  const validateAlias = useCallback(
    (_rule: unknown, value: unknown) => {
      if (!value && omicsStore) {
        setAliasValid(false);
        return Promise.reject(new Error('Alias is required'));
      }
      setAliasValid(true);
      return Promise.resolve();
    },
    [omicsStore],
  );

  // --- submit ---
  const handleSubmit = useCallback(
    async (e?: React.MouseEvent | React.KeyboardEvent) => {
      if (e) e.preventDefault();
      try {
        const values = await form.validateFields();
        setSubmitPending(true);

        const serviceType: StorageServiceType = isNfsMount
          ? SERVICE_TYPES.fileShare
          : omicsStore
            ? (omicsType as StorageServiceType)
            : SERVICE_TYPES.objectStorage;

        const payload = {
          ...values,
          serviceType,
          mountDisabled,
          versioningEnabled: !isNfsMount && !omicsStore && !!policySupported && versioningEnabled,
          backupDuration:
            !isNfsMount && !omicsStore && policySupported && versioningEnabled
              ? values.backupDuration
              : undefined,
          pathPermissionsEnabled:
            !isNfsMount &&
            !omicsStore &&
            currentRegionSupportsStoragePermissions &&
            pathPermissionsEnabled,
          sensitive: !isNfsMount ? sensitive : undefined,
          skipPolicy: !isNfsMount && !omicsStore ? skipPolicy : undefined,
          shared: !isNfsMount && sharingEnabled,
          regionId: values.regionId ? Number(values.regionId) : undefined,
        };

        const pathValue = values.path as Record<string, unknown> | undefined;
        if (!omicsStore && pathValue) {
          payload.path = pathValue.path as string;
        }
        if (isNfsMount && pathValue) {
          payload.regionId = pathValue.regionId as number;
          payload.fileShareMountId = pathValue.fileShareMountId as number;
        }

        if (isNew) {
          await saveDataStorage(payload);
        } else {
          await updateDataStorage({...payload, id: dataStorage?.id});
        }
        onDone?.();
        onClose?.(e);
      } catch (error) {
        if (error && typeof error === 'object' && 'errorFields' in error) {
          // form validation error — antd handles display
          return;
        }
        message.error(
          `Error ${isNew ? 'creating' : 'updating'} storage: ${getErrorDescription(error)}`,
          5,
        );
      } finally {
        setSubmitPending(false);
      }
    },
    [
      isNfsMount,
      omicsStore,
      omicsType,
      mountDisabled,
      policySupported,
      versioningEnabled,
      currentRegionSupportsStoragePermissions,
      pathPermissionsEnabled,
      sensitive,
      skipPolicy,
      sharingEnabled,
      isNew,
      dataStorage?.id,
      onDone,
      onClose,
      form,
    ],
  );

  const handleAfterClose = useCallback(() => {
    form.resetFields();
    setActiveTab('info');
  }, [form]);

  return {
    isNew,
    isNfsMount,
    omicsStore,
    isReadOnly,
    restrictedAccess,
    restrictedAccessCheckInProgress,

    activeTab,
    setActiveTab,

    mountDisabled,
    setMountDisabled,
    versioningEnabled,
    setVersioningEnabled,
    pathPermissionsEnabled,
    setPathPermissionsEnabled,
    sharingEnabled,
    setSharingEnabled,
    sensitive,
    setSensitive,
    skipPolicy,
    setSkipPolicy,
    omicsType,
    setOmicsType,

    nfsStoragePathValid,
    setNfsStoragePathValid,
    aliasValid,
    setAliasValid,

    submitPending,

    awsRegions,
    fileShareMountsList,
    currentRegion,
    defaultAwsRegion,
    currentRegionSupportsPolicy,
    currentRegionSupportsStoragePermissions,

    storageVersioningAllowed,
    isAdvancedUser,
    isUserDefaultStorage,
    permissionsRestrictions,

    omicsTypes,

    initialValues,
    skipPolicyFlagVisible,

    transitionRulesAvailable,
    transitionRulesReadOnly,

    handleSubmit,
    validateStoragePath,
    validateAlias,
    handleAfterClose,
  };
}
