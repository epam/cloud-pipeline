/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {
  createHashRouter,
  createRoutesFromElements,
  Navigate,
  Route,
  RouterProvider,
  useOutletContext,
  useParams
} from 'react-router-dom';
import {inject, observer} from 'mobx-react';
import HomePageLoader from './home/HomePageLoader';
import LoadingView from '../special/LoadingView';
import PipelinesLibrary from '../pipelines/PipelinesLibrary';
import Browser from '../pipelines/browser/Browser';
import FolderBrowser from '../pipelines/browser/Folder';
import StorageBrowser from '../pipelines/browser/data-storage';
import PipelineBrowser from '../pipelines/browser/Pipeline';
import VersionedStorageBrowser from '../pipelines/browser/versioned-storage';
import PipelineLatestVersion from '../pipelines/browser/redirections/PipelineLatestVersion';
import MetadataClassEntityRedirection from '../pipelines/browser/redirections/MetadataClassEntity';
import MetadataFolderBrowser from '../pipelines/browser/MetadataFolder';
import MetadataBrowser from '../pipelines/browser/Metadata';
import PipelineDetails from '../pipelines/version/PipelineDetails';
import PipelineHistory from '../pipelines/version/history/PipelineHistory';
import PipelineCode from '../pipelines/version/code/PipelineCode';
import PipelineConfiguration from '../pipelines/version/configuration/PipelineConfiguration';
import DetachedConfiguration from '../pipelines/configuration/DetachedConfiguration';
import PipelineGraph from '../pipelines/version/graph/PipelineGraph';
import PipelineDocuments from '../pipelines/version/documents/PipelineDocuments';
import PipelineStorageRules from '../pipelines/version/storageRules/PipelineStorageRules';
import LaunchPipeline from '../pipelines/launch/LaunchPipeline';
import ClusterRoot from '../cluster';
import Cluster from '../cluster/Cluster';
import CoreNodes from '../cluster/core-nodes';
import HotCluster from '../cluster/hot-node-pool';
import HotClusterUsage from '../cluster/hot-node-pool/hot-cluster-usage';
import ClusterNode from '../cluster/ClusterNode';
import ClusterNodeGeneralInfo from '../cluster/ClusterNodeGeneralInfo';
import ClusterNodePods from '../cluster/ClusterNodePods';
import ClusterNodeMonitor from '../cluster/cluster-node-monitor';
import Tool from '../tools/Tool';
import Tools from '../tools/Tools';
import SettingsForm from '../settings';
import CLIForm from '../settings/CLIForm';
import UserManagementForm from '../settings/UserManagementForm';
import EmailNotificationSettings from '../settings/EmailNotificationSettings';
import Preferences from '../settings/Preferences';
import AWSRegionsForm from '../settings/AWSRegionsForm';
import SystemManagement from '../settings/system-management/system-management';
import SystemEvents from '../settings/SystemEvents';
import SystemDictionaries from '../settings/SystemDictionaries';
import UserProfile from '../settings/user-profile';
import AllRuns from '../runs/AllRuns';
import RunsFilter from '../runs/RunsFilter';
import RunsSearch from '../runs/RunsSearch';
import Billing, {
  BillingQuotas,
  BillingReports
} from '../billing';
import MiewPage from '../applications/miew/MiewPage';
import VSIPreviewPage from '../applications/vsi-preview';
import LogsRedirect from '../runs/logs/logs-redirect';
import App from './App';
import ToolVersion from '../tools/tool-version';
import ToolScanningInfo from '../tools/tool-version/scanning-info';
import ToolSettings from '../tools/tool-version/settings';
import ToolPackages from '../tools/tool-version/packages';
import ToolHistory from '../tools/tool-version/history';
import ProjectHistory from '../pipelines/browser/ProjectHistory';
import {FacetedSearchPage} from '../search';
import {HcsImagePage} from '../special/hcs-image';
import NotificationBrowser from './notification/NotificationBrowser';
import TicketsBrowser from '../special/tickets/tickets-list';
import TicketPage from '../special/tickets/ticket';
import NewTicketPage from '../special/tickets/new-ticket-page';
import CloudNodes from '../cluster/cloud-nodes';
import AIChatPage from '../ai-chat';
import {
  GeneralReport,
  InstanceReport,
  StorageReport
} from '../billing/reports';
import {withRouter} from '../../utils/with-router';

function withContext (Component) {
  function WithContext () {
    const context = useOutletContext();
    const params = useParams();
    return <Component {...context} params={params} />;
  }
  WithContext.displayName = `WithContext(${Component.displayName || Component.name || 'Component'})`;
  return WithContext;
}

function withRedirect (pathFn) {
  function WithRedirect () {
    const params = useParams();
    return <Navigate to={pathFn(params)} replace />;
  }
  return WithRedirect;
}

const ClusterNodeRedirect = withRedirect(({nodeName}) => `/cluster/${nodeName}/info`);
const ToolIdRedirect = withRedirect(({id}) => `/tool/${id}/description`);
const ToolVersionRedirect = withRedirect(({id, version}) => `/tool/${id}/info/${version}/scaninfo`);

function HomePageRedirectionComponent ({uiNavigation}) {
  if (!uiNavigation.loaded) {
    return <LoadingView />;
  }
  const url = uiNavigation.home || '/dashboard';
  if (/^http[s]?:\/\//i.test(url)) {
    window.location = url;
    return null;
  }
  return <Navigate to={url} replace />;
}

const HomePageRedirection = inject('uiNavigation')(observer(HomePageRedirectionComponent));

const ClusterNodeGeneralInfoWithContext = withContext(ClusterNodeGeneralInfo);
const ClusterNodePodsWithContext = withContext(ClusterNodePods);
const ClusterNodeMonitorWithContext = withContext(ClusterNodeMonitor);

const PipelineDetailsWithContext = withContext(PipelineDetails);
const PipelineCodeWithContext = withContext(PipelineCode);
const PipelineConfigurationWithContext = withContext(PipelineConfiguration);
const PipelineDocumentsWithContext = withContext(PipelineDocuments);
const PipelineHistoryWithContext = withContext(PipelineHistory);
const PipelineGraphWithContext = withContext(PipelineGraph);
const PipelineStorageRulesWithContext = withContext(PipelineStorageRules);
const LogsRedirectWithContext = withContext(LogsRedirect);

const routeConfig = createRoutesFromElements(
  <Route path="/" element={<App />}>
    <Route path=":pipeline/refs/heads/master" element={<PipelineLatestVersion />} />
    <Route path=":pipeline/refs/heads/master/:section" element={<PipelineLatestVersion />} />
    <Route path=":pipeline/refs/heads/master/:section/:subSection" element={<PipelineLatestVersion />} />
    <Route path="folder/:folder/metadata/:entity/redirect" element={<MetadataClassEntityRedirection />} />
    <Route path="tickets/new" element={<NewTicketPage />} />
    <Route path="tickets/:id" element={<TicketPage />} />
    <Route path="tickets" element={<TicketsBrowser />} />
    <Route path="search/advanced" element={<FacetedSearchPage />} />
    <Route path="search" element={<RunsSearch />} />
    <Route path="settings" element={<SettingsForm />}>
      <Route index element={<Navigate to="cli" replace />} />
      <Route path="cli" element={<CLIForm />} />
      <Route path="cli/:section" element={<CLIForm />} />
      <Route path="events" element={<SystemEvents />} />
      <Route path="user" element={<UserManagementForm />} />
      <Route path="user/:section" element={<UserManagementForm />} />
      <Route path="email" element={<EmailNotificationSettings />} />
      <Route path="email/:section" element={<EmailNotificationSettings />} />
      <Route path="preferences" element={<Preferences />} />
      <Route path="regions" element={<AWSRegionsForm />} />
      <Route path="system" element={<SystemManagement />} />
      <Route path="system/:section" element={<SystemManagement />} />
      <Route path="dictionaries" element={<SystemDictionaries />} />
      <Route path="dictionaries/:currentDictionary" element={<SystemDictionaries />} />
      <Route path="profile" element={<UserProfile />} />
      <Route path="profile/:section" element={<UserProfile />} />
      <Route path="profile/:section/:sub" element={<UserProfile />} />
    </Route>
    <Route path="cluster" element={<ClusterRoot />}>
      <Route index element={<Cluster />} />
      <Route path="core-nodes" element={<CoreNodes />} />
      <Route path="cloud-nodes" element={<CloudNodes />} />
      <Route path="hot" element={<HotCluster />} />
      <Route path="usage" element={<HotClusterUsage />} />
    </Route>
    <Route path="cluster/:nodeName" element={<ClusterNode />}>
      <Route index element={<ClusterNodeRedirect />} />
      <Route path="info" element={<ClusterNodeGeneralInfoWithContext />} />
      <Route path="jobs" element={<ClusterNodePodsWithContext />} />
      <Route path="monitor" element={<ClusterNodeMonitorWithContext />} />
    </Route>
    <Route path="runs/filter" element={<RunsFilter />} />
    <Route path="runs" element={<Navigate to="/runs/active" replace />} />
    <Route path="runs/:status" element={<AllRuns />} />
    <Route path="run/:runId" element={<LogsRedirectWithContext />} />
    <Route path="run/:runId/:mode" element={<LogsRedirectWithContext />} />
    <Route path="run/:runId/:mode/:taskName" element={<LogsRedirectWithContext />} />
    <Route path="run/:runId/:taskName" element={<LogsRedirectWithContext />} />
    <Route path="tool/:id" element={<ToolIdRedirect />} />
    <Route path="tool/:id/:section" element={<Tool />} />
    <Route path="tool/:id/info/:version" element={<ToolVersion />}>
      <Route index element={<ToolVersionRedirect />} />
      <Route path="scaninfo" element={<ToolScanningInfo />} />
      <Route path="settings" element={<ToolSettings />} />
      <Route path="packages" element={<ToolPackages />} />
      <Route path="history" element={<ToolHistory />} />
    </Route>
    <Route path="tools" element={<Tools />} />
    <Route path="tools/:registryId" element={<Tools />} />
    <Route path="tools/:registryId/:groupId" element={<Tools />} />
    <Route path="launch" element={<LaunchPipeline />} />
    <Route path="launch/tool/:image" element={<LaunchPipeline />} />
    <Route path="launch/:runId" element={<LaunchPipeline />} />
    <Route path="launch/:id/:version" element={<LaunchPipeline />} />
    <Route path="launch/:id/:version/:configuration" element={<LaunchPipeline />} />
    <Route path="launch/:id/:version/:configuration/:runId" element={<LaunchPipeline />} />
    <Route path="billing" element={<Billing />}>
      <Route index element={<Navigate to="reports" replace />} />
      <Route path="quotas" element={<BillingQuotas />} />
      <Route path="quotas/:type" element={<BillingQuotas />} />
      <Route path="reports" element={<BillingReports />}>
        <Route index element={<GeneralReport />} />
        <Route path="instance" element={<InstanceReport />} />
        <Route path="instance/:type" element={<InstanceReport />} />
        <Route path="storage" element={<StorageReport />} />
        <Route path="storage/:type" element={<StorageReport />} />
      </Route>
    </Route>
    <Route path="chat" element={<AIChatPage />} />
    <Route path="notifications" element={<NotificationBrowser />} />
    <Route path="miew" element={<MiewPage />} />
    <Route path="wsi" element={<VSIPreviewPage />} />
    <Route path="hcs" element={<HcsImagePage />} />
    <Route path="library" element={<PipelinesLibrary />}>
      <Route index element={<FolderBrowser />} />
    </Route>
    <Route path="pipelines" element={<PipelinesLibrary />}>
      <Route index element={<Browser />} />
    </Route>
    <Route path="storages" element={<PipelinesLibrary />}>
      <Route index element={<Browser />} />
    </Route>
    <Route path="folder" element={<PipelinesLibrary />}>
      <Route path=":id" element={<FolderBrowser />} />
      <Route path=":id/history" element={<ProjectHistory />} />
      <Route path=":id/metadata" element={<MetadataFolderBrowser />} />
      <Route path=":id/metadata/:class" element={<MetadataBrowser />} />
    </Route>
    <Route path="storage" element={<PipelinesLibrary />}>
      <Route path=":id" element={<StorageBrowser />} />
    </Route>
    <Route path="configuration" element={<PipelinesLibrary />}>
      <Route path=":id" element={<DetachedConfiguration />} />
      <Route path=":id/:name" element={<DetachedConfiguration />} />
    </Route>
    <Route path="vs/:id" element={<PipelinesLibrary />}>
      <Route index element={<VersionedStorageBrowser />} />
    </Route>
    <Route path="dashboard" element={<HomePageLoader />} />
    <Route path=":id" element={<PipelinesLibrary />}>
      <Route index element={<PipelineBrowser />} />
      <Route path=":version" element={<PipelineDetailsWithContext />}>
        <Route path="history" element={<PipelineHistoryWithContext />} />
        <Route path="code" element={<PipelineCodeWithContext />} />
        <Route path="configuration" element={<PipelineConfigurationWithContext />} />
        <Route path="configuration/:configuration" element={<PipelineConfigurationWithContext />} />
        <Route path="graph" element={<PipelineGraphWithContext />} />
        <Route path="workflow" element={<PipelineGraphWithContext />} />
        <Route path="documents" element={<PipelineDocumentsWithContext />} />
        <Route path="storage" element={<PipelineStorageRulesWithContext />} />
      </Route>
    </Route>
    <Route index element={<HomePageRedirection />} />
  </Route>
);

export const router = createHashRouter(routeConfig);

function AppRouterComponent ({uiNavigation}) {
  if (!uiNavigation.loaded) {
    return null;
  }
  return <RouterProvider router={router} />;
}

const AppRouter = inject('uiNavigation')(observer(AppRouterComponent));

export default AppRouter;
