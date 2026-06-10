/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Shared MobX store instances for legacy @inject components.
 * Used by Root.jsx and refactored pages that still render legacy panel widgets.
 */

import {RouterStore} from '../utils/routing-store';
import GoogleApi from '../models/google/GoogleApi';
import authenticatedUserInfo from '../models/user/WhoAmI';
import preferences from '../models/preferences/PreferencesLoad';
import notifications from '../models/notifications/ActiveNotifications';
import userNotifications from '../models/notifications/CurrentUserNotifications';
import pipelines from '../models/pipelines/Pipelines';
import projects from '../models/folders/FolderProjects';
import FireCloudMethods from '../models/firecloud/FireCloudMethods';
import runDefaultParameters from '../models/pipelines/PipelineRunDefaultParameters';
import configurations from '../models/configuration/Configurations';
import AllConfigurations from '../models/configuration/ConfigurationsLoadAll';
import pipelinesLibrary from '../models/folders/FolderLoadTree';
import folders from '../models/folders/Folders';
import dataStorages from '../models/dataStorage/DataStorages';
import awsRegions from '../models/cloudRegions/CloudRegions';
import cloudRegionsInfo from '../models/cloudRegions/CloudRegionsInfo';
import availableCloudRegions from '../models/cloudRegions/AvailableCloudRegions';
import cloudProviders from '../models/cloudRegions/CloudProviders';
import dataStorageCache from '../models/dataStorage/DataStorageCache';
import dataStorageAvailable from '../models/dataStorage/DataStorageAvailable';
import dtsList from '../models/dts/DTSList';
import InstanceTypes from '../models/utils/InstanceTypes';
import ToolInstanceTypes from '../models/utils/ToolInstanceTypes';
import FolderLoadWithMetadata from '../models/folders/FolderLoadWithMetadata';
import dockerRegistries from '../models/tools/DockerRegistriesTree';
import RunCount from '../models/pipelines/RunCount';
import MyIssues from '../models/issues/MyIssues';
import Users from '../models/user/Users';
import UsersInfo from '../models/user/UsersInfo';
import AppLocalization from '../utils/localization';
import AllowedInstanceTypes from '../models/utils/AllowedInstanceTypes';
import configurationSchedules from '../models/configurationSchedule/ConfigurationSchedules';
import SystemDictionariesLoadAll from '../models/systemDictionaries/SystemDictionariesLoadAll';
import GetMetadataKeys from '../models/metadata/GetMetadataKeys';
import {Search} from '../models/search';
import * as billing from '../models/billing';
import {cloudCredentialProfiles} from '../models/cloudCredentials';
import HiddenObjects from '../utils/hidden-objects';
import multiZoneManager from '../utils/multizone';
import UINavigation from '../utils/ui-navigation';
import {VsActionsAvailable} from '../components/versioned-storages/vs-actions';
import impersonation from '../models/user/impersonation';
import CurrentUserAttributes, {
  CURRENT_USER_ATTRIBUTES_STORE,
} from '../utils/current-user-attributes';
import CloudPipelineThemes from '../themes';
import ApplicationInfo from '../models/utils/application-info';
import SystemJobs from '../utils/system-jobs';
import uiLaunchParametersConfiguration from '../utils/ui-launch-parameters-configuration';
import {gcpSpotInstanceType} from '../models/utils/gcp-spot-instance-type';

const routing = new RouterStore();
const counter = new RunCount({usePreferenceValue: true, autoUpdate: true});
const localization = AppLocalization.localization;
const hiddenObjects = new HiddenObjects(preferences, authenticatedUserInfo);
const myIssues = new MyIssues();
const googleApi = new GoogleApi(preferences);
const fireCloudMethods = new FireCloudMethods(googleApi);
const users = new Users();
const usersInfo = new UsersInfo();
const allowedInstanceTypes = new AllowedInstanceTypes();
const searchEngine = new Search();

const spotInstanceTypes = new InstanceTypes(true);
const onDemandInstanceTypes = new InstanceTypes(false);
const allInstanceTypes = new InstanceTypes();
const spotToolInstanceTypes = new ToolInstanceTypes(true);
const onDemandToolInstanceTypes = new ToolInstanceTypes(false);

const systemDictionaries = new SystemDictionariesLoadAll();
const userMetadataKeys = new GetMetadataKeys('PIPELINE_USER');
const allConfigurations = new AllConfigurations();
const uiNavigation = new UINavigation(authenticatedUserInfo, preferences);
const vsActions = new VsActionsAvailable(pipelines);
const currentUserAttributes = new CurrentUserAttributes(
  authenticatedUserInfo,
  dataStorageAvailable,
);
const applicationInfo = new ApplicationInfo();
const themes = new CloudPipelineThemes();
const systemJobs = new SystemJobs();

(() => {
  return awsRegions.fetchIfNeededOrWait();
})();
(() => {
  return cloudRegionsInfo.fetchIfNeededOrWait();
})();
(() => {
  return allowedInstanceTypes.fetchIfNeededOrWait();
})();
(() => {
  return spotInstanceTypes.fetchIfNeededOrWait();
})();
(() => {
  return onDemandInstanceTypes.fetchIfNeededOrWait();
})();
(() => {
  return spotToolInstanceTypes.fetchIfNeededOrWait();
})();
(() => {
  return onDemandToolInstanceTypes.fetchIfNeededOrWait();
})();
(() => {
  return systemDictionaries.fetchIfNeededOrWait();
})();
(() => {
  return applicationInfo.fetchIfNeededOrWait();
})();

const legacyMobXStores = {
  routing,
  googleApi,
  fireCloudMethods,
  localization,
  preferences,
  pipelines,
  projects,
  runDefaultParameters,
  counter,
  configurations,
  allConfigurations,
  pipelinesLibrary,
  dataStorages,
  awsRegions,
  cloudRegionsInfo,
  availableCloudRegions,
  cloudProviders,
  folders,
  spotInstanceTypes,
  onDemandInstanceTypes,
  allInstanceTypes,
  spotToolInstanceTypes,
  onDemandToolInstanceTypes,
  notifications,
  userNotifications,
  authenticatedUserInfo,
  [CURRENT_USER_ATTRIBUTES_STORE]: currentUserAttributes,
  impersonation,
  metadataCache: FolderLoadWithMetadata.metadataCache,
  dataStorageCache,
  dataStorageAvailable,
  dtsList,
  dockerRegistries,
  myIssues,
  users,
  usersInfo,
  allowedInstanceTypes,
  searchEngine,
  configurationSchedules,
  billingCenters: new billing.FetchBillingCenters(),
  systemDictionaries,
  userMetadataKeys,
  cloudCredentialProfiles,
  [HiddenObjects.injectionName]: hiddenObjects,
  multiZoneManager,
  uiNavigation,
  vsActions,
  themes,
  applicationInfo,
  systemJobs,
  uiLaunchParametersConfiguration,
  gcpSpotInstanceType,
};

export {legacyMobXStores, routing, myIssues};
