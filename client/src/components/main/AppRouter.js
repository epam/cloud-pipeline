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
import {Redirect, Route, Router, Switch} from 'react-router-dom';
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import HomePageLoader from './home/HomePageLoader';
import PipelinesLibrary from '../pipelines/PipelinesLibrary';
import PipelineLatestVersion from '../pipelines/browser/redirections/PipelineLatestVersion';
import MetadataClassEntityRedirection from '../pipelines/browser/redirections/MetadataClassEntity';
import LaunchPipeline from '../pipelines/launch/LaunchPipeline';
import ClusterRoot from '../cluster';
import Cluster from '../cluster/Cluster';
import HotCluster from '../cluster/hot-node-pool';
import ClusterNode from '../cluster/ClusterNode';
import Tool from '../tools/Tool';
import Tools from '../tools/Tools';
import SettingsForm from '../settings';
import CLIForm from '../settings/CLIForm';
import UserManagementForm from '../settings/UserManagementForm';
import EmailNotificationSettings from '../settings/EmailNotificationSettings';
import Preferences from '../settings/Preferences';
import AWSRegionsForm from '../settings/AWSRegionsForm';
import SystemLogs from '../settings/system-logs';
import SystemEvents from '../settings/SystemEvents';
import SystemDictionaries from '../settings/SystemDictionaries';
import UserProfile from '../user-profile';
import AllRuns from '../runs/AllRuns';
import RunsFilter from '../runs/RunsFilter';
import RunsSearch from '../runs/RunsSearch';
import Billing, {
  // BillingQuotas,
  BillingReports} from '../billing';
import MiewPage from '../applications/miew/MiewPage';
import VSIPreviewPage from '../applications/vsi-preview';
import Log from '../runs/logs/Log';
import App from './App';
import ToolVersion from '../tools/tool-version';
import ToolScanningInfo from '../tools/tool-version/scanning-info';
import ToolSettings from '../tools/tool-version/settings';
import ToolPackages from '../tools/tool-version/packages';
import ToolHistory from '../tools/tool-version/history';
import {FacetedSearchPage} from '../search';

@inject('history', 'preferences', 'uiNavigation')
@observer
export default class AppRouter extends React.Component {
  @computed
  get homeEndpoint () {
    const {
      uiNavigation
    } = this.props;
    return uiNavigation.home;
  }

  render () {
    const {
      uiNavigation
    } = this.props;
    if (!uiNavigation.loaded) {
      return null;
    }
    return (
      <Router history={this.props.history}>
        <App>
          <Switch>
            <Route path="/:pipeline/refs/heads/master/:section?/:subSection?"><PipelineLatestVersion /></Route>
            <Route path="/metadata/redirect/:folder/:entity"><MetadataClassEntityRedirection /></Route>
            <Route path="/search/advanced"><FacetedSearchPage /></Route>
            <Route path="/search"><RunsSearch /></Route>
            <Redirect exact from="/settings" to="/settings/cli" />
            <Route path="/settings/:activeTab">
              <SettingsForm>
                <Switch>
                  <Route path="/settings/cli"><CLIForm /></Route>
                  <Route path="/settings/events"><SystemEvents /></Route>
                  <Route path="/settings/user"><UserManagementForm /></Route>
                  <Route path="/settings/email"><EmailNotificationSettings /></Route>
                  <Route path="/settings/preferences"><Preferences /></Route>
                  <Route path="/settings/regions"><AWSRegionsForm /></Route>
                  <Route path="/settings/logs"><SystemLogs /></Route>
                  <Route path="/settings/dictionaries/:currentDictionary?"><SystemDictionaries /></Route>
                  <Route path="/settings/profile"><UserProfile /></Route>
                </Switch>
              </SettingsForm>
            </Route>
            <Route exact path={['/cluster', '/cluster/hot']}>
              <ClusterRoot>
                <Switch>
                  <Route exact path="/cluster"><Cluster /></Route>
                  <Route path="/cluster/hot"><HotCluster /></Route>
                </Switch>
              </ClusterRoot>
            </Route>
            <Redirect exact from="/cluster/:nodeName" to="/cluster/:nodeName/info" />
            <Route path="/cluster/:nodeName"><ClusterNode /></Route>
            <Route path="/runs/filter"><RunsFilter /></Route>
            <Redirect exact from="/runs" to="runs/active" />
            <Route path="/runs/:status"><AllRuns /></Route>
            <Redirect exact from="/run/:runId" to="/run/:runId/plain" />
            <Route path="/run/:runId/:mode/:taskName?"><Log /></Route>
            <Redirect exact from="/tool/:id" to="/tool/:id/description" />
            <Redirect exact from="/tool/:id/info/:version" to="/tool/:id/info/:version/scaninfo" />
            <Route path="/tool/:id/info/:version/:activeKey">
              <ToolVersion>
                <Switch>
                  <Route path="/tool/:id/info/:version/scaninfo"><ToolScanningInfo /></Route>
                  <Route path="/tool/:id/info/:version/settings"><ToolSettings /></Route>
                  <Route path="/tool/:id/info/:version/packages"><ToolPackages /></Route>
                  <Route path="/tool/:id/info/:version/history"><ToolHistory /></Route>
                </Switch>
              </ToolVersion>
            </Route>
            <Route path="/tool/:id/:section"><Tool /></Route>
            <Route path="/tools/:registryId?/:groupId?"><Tools /></Route>
            <Route path="/launch/tool/:image"><LaunchPipeline /></Route>
            <Route path="/launch/:runId"><LaunchPipeline /></Route>
            <Route path="/launch/:id/:version/:configuration/:runId?"><LaunchPipeline /></Route>
            <Route path="/launch/:id/:version/:configuration?"><LaunchPipeline /></Route>
            <Route path="/launch"><LaunchPipeline /></Route>
            <Redirect exact from="/billing" to="/billing/reports" />
            <Route path="/billing">
              <Billing>
                <Switch>
                  {/* <Route path="/billing/quotas"><BillingQuotas /></Route> */}
                  <Route path="/billing/reports">
                    <BillingReports.default>
                      <Switch>
                        <Route exact path="/billing/reports"><BillingReports.GeneralReport /></Route>
                        <Route path="/billing/reports/instance/:type?"><BillingReports.InstanceReport /></Route>
                        <Route path="/billing/reports/storage/:type?"><BillingReports.StorageReport /></Route>
                      </Switch>
                    </BillingReports.default>
                  </Route>
                </Switch>
              </Billing>
            </Route>
            <Route path="/miew"><MiewPage /></Route>
            <Route path="/wsi"><VSIPreviewPage /></Route>
            <Route path="/dashboard"><HomePageLoader /></Route>
            <Route path={['/library', '/pipelines', '/storages', '/folder', '/storage', '/configuration', '/metadata', '/metadataFolder', '/vs/:id', '/:id']}>
              <PipelinesLibrary />
            </Route>
            <Redirect exact path="/" to={this.homeEndpoint} />
          </Switch>
        </App>
      </Router>
    );
  }
};
