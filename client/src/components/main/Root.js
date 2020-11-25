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
import {hashHistory} from 'react-router';
import {Provider} from 'mobx-react';
import {RouterStore, syncHistoryWithStore} from 'mobx-react-router';
import GoogleApi from '../../models/google/GoogleApi';
import authenticatedUserInfo from '../../models/user/WhoAmI';
import preferences from '../../models/preferences/PreferencesLoad';
import notifications from '../../models/notifications/ActiveNotifications';
import pipelines from '../../models/pipelines/Pipelines';
import projects from '../../models/folders/FolderProjects';
import FireCloudMethods from '../../models/firecloud/FireCloudMethods';
import runDefaultParameters from '../../models/pipelines/PipelineRunDefaultParameters';
import configurations from '../../models/configuration/Configurations';
import pipelinesLibrary from '../../models/folders/FolderLoadTree';
import folders from '../../models/folders/Folders';
import dataStorages from '../../models/dataStorage/DataStorages';
import awsRegions from '../../models/cloudRegions/CloudRegions';
import cloudRegionsInfo from '../../models/cloudRegions/CloudRegionsInfo';
import availableCloudRegions from '../../models/cloudRegions/AvailableCloudRegions';
import cloudProviders from '../../models/cloudRegions/CloudProviders';
import dataStorageCache from '../../models/dataStorage/DataStorageCache';
import dataStorageAvailable from '../../models/dataStorage/DataStorageAvailable';
import dtsList from '../../models/dts/DTSList';
import InstanceTypes from '../../models/utils/InstanceTypes';
import ToolInstanceTypes from '../../models/utils/ToolInstanceTypes';
import FolderLoadWithMetadata from '../../models/folders/FolderLoadWithMetadata';
import dockerRegistries from '../../models/tools/DockerRegistriesTree';
import RunCount from '../../models/pipelines/RunCount';
import MyIssues from '../../models/issues/MyIssues';
import Users from '../../models/user/Users';
import UsersInfo from '../../models/user/UsersInfo';
import AppLocalization from '../../utils/localization';
import IssuesRenderer from '../../components/special/issues/utilities/IssueRenderer';
import NotificationRenderer from '../special/notifications/utilities/NotificationRenderer';
import AppRouter from './AppRouter';
import AllowedInstanceTypes from '../../models/utils/AllowedInstanceTypes';
import configurationSchedules from '../../models/configurationSchedule/ConfigurationSchedules';
import SystemDictionariesLoadAll from '../../models/systemDictionaries/SystemDictionariesLoadAll';
import GetMetadataKeys from '../../models/metadata/GetMetadataKeys';
import {Search} from '../../models/search';
import * as billing from '../../models/billing';

const routing = new RouterStore();
const history = syncHistoryWithStore(hashHistory, routing);
const counter = new RunCount();
const localization = new AppLocalization.Localization();
const issuesRenderer = new IssuesRenderer(pipelinesLibrary, dockerRegistries, preferences);
const notificationsRenderer = new NotificationRenderer();
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

(() => { return awsRegions.fetchIfNeededOrWait(); })();
(() => { return cloudRegionsInfo.fetchIfNeededOrWait(); })();
(() => { return allowedInstanceTypes.fetchIfNeededOrWait(); })();
(() => { return spotInstanceTypes.fetchIfNeededOrWait(); })();
(() => { return onDemandInstanceTypes.fetchIfNeededOrWait(); })();
(() => { return spotToolInstanceTypes.fetchIfNeededOrWait(); })();
(() => { return onDemandToolInstanceTypes.fetchIfNeededOrWait(); })();
(() => { return systemDictionaries.fetchIfNeededOrWait(); })();

const Root = () =>
  <Provider
    {...{
      routing,
      googleApi,
      fireCloudMethods,
      localization,
      history,
      preferences,
      pipelines,
      projects,
      runDefaultParameters,
      counter,
      configurations,
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
      authenticatedUserInfo,
      metadataCache: FolderLoadWithMetadata.metadataCache,
      dataStorageCache,
      dataStorageAvailable,
      dtsList,
      dockerRegistries,
      issuesRenderer,
      notificationsRenderer,
      myIssues,
      users,
      usersInfo,
      allowedInstanceTypes,
      searchEngine,
      configurationSchedules,
      quotaTemplates: billing.quotas.templates.list,
      billingCenters: new billing.FetchBillingCenters(),
      systemDictionaries,
      userMetadataKeys
    }}>
    <AppRouter />
  </Provider>;

export default Root;
