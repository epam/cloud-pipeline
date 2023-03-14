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
import {IndexRoute, Redirect, Route, Router} from 'react-router';
import {inject, observer} from 'mobx-react';
import HomePageLoader from './home/HomePageLoader';
import PipelinesLibrary from '../pipelines/PipelinesLibrary';
import Browser from '../pipelines/browser/Browser';
import FolderBrowser from '../pipelines/browser/Folder';
import StorageBrowser from '../pipelines/browser/data-storage';
import PipelineBrowser from '../pipelines/browser/Pipeline';
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
import HotCluster from '../cluster/hot-node-pool';
import HotClusterUsage from '../cluster/hot-node-pool/hot-cluster-usage';
import ClusterNode from '../cluster/ClusterNode';
import ClusterNodeGeneralInfo from '../cluster/ClusterNodeGeneralInfo';
import ClusterNodePods from '../cluster/ClusterNodePods';
import ClusterNodeMonitor from '../cluster/ClusterNodeMonitor';
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
import Log from '../runs/logs/Log';
import App from './App';
import ToolVersion from '../tools/tool-version';
import ToolScanningInfo from '../tools/tool-version/scanning-info';
import ToolSettings from '../tools/tool-version/settings';
import ToolPackages from '../tools/tool-version/packages';
import ToolHistory from '../tools/tool-version/history';
import ProjectHistory from '../pipelines/browser/ProjectHistory';
import NotificationBrowser from './notification/NotificationBrowser';

@inject('history', 'themes')
@observer
export default class AppRouter extends React.Component {
  render () {
    const {
      themes
    } = this.props;
    if (!themes.loaded) {
      return null;
    }
    return (
      <Router history={this.props.history}>
        <Route component={App}>
          <Route path="search" component={RunsSearch} />
          <Redirect from="/settings" to="/settings/cli" />
          <Route path="/settings" component={SettingsForm}>
            <Route path="cli(/:section)" component={CLIForm} />
            <Route path="events" component={SystemEvents} />
            <Route path="user(/:section)" component={UserManagementForm} />
            <Route path="email(/:section)" component={EmailNotificationSettings} />
            <Route path="preferences" component={Preferences} />
            <Route path="regions" component={AWSRegionsForm} />
            <Route path="system(/:section)" component={SystemManagement} />
            <Route path="dictionaries(/:currentDictionary)" component={SystemDictionaries} />
            <Route path="profile(/:section(/:sub))" component={UserProfile} />
          </Route>
          <Route path="/cluster" component={ClusterRoot}>
            <IndexRoute component={Cluster} />
            <Route path="hot" component={HotCluster} />
            <Route path="usage" component={HotClusterUsage} />
          </Route>
          <Redirect from="/cluster/:nodeName" to="/cluster/:nodeName/info" />
          <Route path="/cluster/:nodeName" component={ClusterNode}>
            <Route path="info" component={ClusterNodeGeneralInfo} />
            <Route path="jobs" component={ClusterNodePods} />
            <Route path="monitor" component={ClusterNodeMonitor} />
          </Route>
          <Route path="/runs/filter" component={RunsFilter} />
          <Redirect from="/runs" to="runs/active" />
          <Route path="/runs/:status" component={AllRuns} />
          <Redirect from="/run/:runId" to="/run/:runId/plain" />
          <Route path="/run/:runId/:mode(/:taskName)" component={Log} />
          <Redirect from="/tool/:id" to="/tool/:id/description" />
          <Route path="/tool/:id/:section" component={Tool} />
          <Redirect from="/tool/:id/info/:version" to="/tool/:id/info/:version/scaninfo" />
          <Route path="/tool/:id/info/:version" component={ToolVersion}>
            <Route path="scaninfo" component={ToolScanningInfo} tabKey="scaninfo" />
            <Route path="settings" component={ToolSettings} tabKey="settings" />
            <Route path="packages" component={ToolPackages} tabKey="packages" />
            <Route path="history" component={ToolHistory} tabKey="history" />
          </Route>
          <Route path="/tools(/:registryId(/:groupId))" component={Tools} />
          <Route path="/launch" component={LaunchPipeline} />
          <Route path="/launch/tool/:image" component={LaunchPipeline} />
          <Route path="/launch/:runId" component={LaunchPipeline} />
          <Route path="/launch/:id/:version(/:configuration)" component={LaunchPipeline} />
          <Route path="/launch/:id/:version/:configuration(/:runId)" component={LaunchPipeline} />
          <Redirect from="/billing" to="/billing/reports" />
          <Route path="/billing" component={Billing}>
            <Route path="quotas(/:type)" component={BillingQuotas} />
            <Route path="reports" component={BillingReports.default}>
              <IndexRoute component={BillingReports.GeneralReport} />
              <Route path="instance(/:type)" component={BillingReports.InstanceReport} />
              <Route path="storage(/:type)" component={BillingReports.StorageReport} />
            </Route>
          </Route>
          <Route path="/notifications" component={NotificationBrowser} />
          <Route path="/miew" component={MiewPage} />
          <Route path="/library" component={PipelinesLibrary}>
            <IndexRoute component={FolderBrowser} />
          </Route>
          <Route path="/pipelines" component={PipelinesLibrary}>
            <IndexRoute component={Browser} />
          </Route>
          <Route path="/storages" component={PipelinesLibrary}>
            <IndexRoute component={Browser} />
          </Route>
          <Route path="/folder" component={PipelinesLibrary}>
            <Route path=":id" component={FolderBrowser} />
            <Route path=":id/history" component={ProjectHistory} />
          </Route>
          <Route path="/storage" component={PipelinesLibrary}>
            <Route path=":id" component={StorageBrowser} />
          </Route>
          <Route path="/configuration" component={PipelinesLibrary}>
            <Route path=":id(/:name)" component={DetachedConfiguration} />
          </Route>
          <Route path="/metadata" component={PipelinesLibrary}>
            <Route path=":id/:class" component={MetadataBrowser} />
          </Route>
          <Route path="/metadataFolder" component={PipelinesLibrary}>
            <Route path=":id" component={MetadataFolderBrowser} />
          </Route>
          <Route path="/:id" component={PipelinesLibrary}>
            <IndexRoute component={PipelineBrowser} />
            <Redirect from=":version" to=":version/documents" />
            <Route path=":version" component={PipelineDetails}>
              <Route path="history" component={PipelineHistory} />
              <Route path="code" component={PipelineCode} />
              <Route path="configuration(/:configuration)" component={PipelineConfiguration} />
              <Route path="graph" component={PipelineGraph} />
              <Route path="documents" component={PipelineDocuments} />
              <Route path="storage" component={PipelineStorageRules} />
            </Route>
          </Route>
          <Route path="/" component={HomePageLoader} />
        </Route>
      </Router>
    );
  }
};
