import {
  createHashRouter,
  createRoutesFromElements,
  Navigate,
  Route,
  useParams,
} from 'react-router-dom';
import {HomeRedirect} from './home-redirect.tsx';
import {MainLayout} from '../layouts/main-layout';
import {RootLayout} from '../layouts/root-layout';
import {FullscreenLayout} from '../layouts/fullscreen-layout';
import {LibraryLayout} from '../layouts/library-layout';
import {SettingsLayout} from '../layouts/settings-layout';
import {ClusterLayout} from '../layouts/cluster-layout';
import {BillingLayout} from '../layouts/billing-layout';
import * as pages from './lazy-pages';

function ToolRootRedirect() {
  const {id} = useParams<{id: string}>();
  return <Navigate replace to={`/tool/${id}/description`} />;
}

function ToolVersionRootRedirect() {
  const {id, version} = useParams<{id: string; version: string}>();
  return <Navigate replace to={`/tool/${id}/info/${version}/scaninfo`} />;
}

function ClusterNodeRootRedirect() {
  const {nodeName} = useParams<{nodeName: string}>();
  return <Navigate replace to={`/cluster/${nodeName}/info`} />;
}

export const router = createHashRouter(
  createRoutesFromElements(
    <Route element={<RootLayout />}>
      <Route element={<FullscreenLayout />}>
        <Route element={<pages.MiewPage />} path="miew" />
        <Route element={<pages.WsiPage />} path="wsi" />
        <Route element={<pages.HcsPage />} path="hcs" />
      </Route>

      <Route element={<MainLayout />}>
        <Route element={<HomeRedirect />} index />

        <Route element={<pages.DashboardPage />} path="dashboard" />

        <Route element={<LibraryLayout />} path="library">
          <Route element={<pages.LibraryIndexPage />} index />
        </Route>
        <Route element={<LibraryLayout />} path="pipelines">
          <Route element={<pages.PipelinesBrowserPage />} index />
        </Route>
        <Route element={<LibraryLayout />} path="storages">
          <Route element={<pages.StoragesBrowserPage />} index />
        </Route>
        <Route element={<LibraryLayout />} path="folder">
          <Route element={<pages.FolderPage />} path=":id" />
          <Route element={<pages.FolderHistoryPage />} path=":id/history" />
          <Route element={<pages.MetadataFolderPage />} path=":id/metadata" />
          <Route element={<pages.MetadataPage />} path=":id/metadata/:class" />
        </Route>
        <Route element={<LibraryLayout />} path="storage">
          <Route element={<pages.StoragePage />} path=":id" />
        </Route>
        <Route element={<LibraryLayout />} path="configuration">
          <Route element={<pages.ConfigurationPage />} path=":id" />
          <Route element={<pages.ConfigurationPage />} path=":id/:name" />
        </Route>
        <Route element={<LibraryLayout />} path="vs/:id">
          <Route element={<pages.VersionedStoragePage />} index />
        </Route>

        <Route element={<ClusterLayout />} path="cluster">
          <Route element={<pages.ClusterPage />} index />
          <Route element={<pages.ClusterSectionPage />} path="core-nodes" />
          <Route element={<pages.ClusterSectionPage />} path="cloud-nodes" />
          <Route element={<pages.ClusterSectionPage />} path="hot" />
          <Route element={<pages.ClusterSectionPage />} path="usage" />
        </Route>
        <Route element={<pages.ClusterNodePage />} path="cluster/:nodeName">
          <Route element={<ClusterNodeRootRedirect />} index />
          <Route element={<pages.ClusterNodeInfoPage />} path="info" />
          <Route element={<pages.ClusterNodeJobsPage />} path="jobs" />
          <Route element={<pages.ClusterNodeMonitorPage />} path="monitor" />
        </Route>

        <Route element={<pages.ToolsPage />} path="tools" />
        <Route element={<pages.ToolsPage />} path="tools/:registryId" />
        <Route element={<pages.ToolsPage />} path="tools/:registryId/:groupId" />
        <Route element={<ToolRootRedirect />} path="tool/:id" />
        <Route element={<pages.ToolPage />} path="tool/:id/:section" />
        <Route element={<pages.ToolVersionPage />} path="tool/:id/info/:version">
          <Route element={<ToolVersionRootRedirect />} index />
          <Route element={<pages.ToolVersionSectionPage />} path="scaninfo" />
          <Route element={<pages.ToolVersionSectionPage />} path="settings" />
          <Route element={<pages.ToolVersionSectionPage />} path="packages" />
          <Route element={<pages.ToolVersionSectionPage />} path="history" />
        </Route>

        <Route element={<Navigate replace to="/runs/active" />} path="runs" />
        <Route element={<pages.RunsFilterPage />} path="runs/filter" />
        <Route element={<pages.RunsPage />} path="runs/:status" />
        <Route element={<pages.RunDetailsPage />} path="run/:runId" />
        <Route element={<pages.RunDetailsPage />} path="run/:runId/:mode" />
        <Route element={<pages.RunDetailsPage />} path="run/:runId/:mode/:taskName" />
        <Route element={<pages.RunDetailsPage />} path="run/:runId/:taskName" />

        <Route element={<SettingsLayout />} path="settings">
          <Route element={<Navigate replace to="cli" />} index />
          <Route element={<pages.SettingsSectionPage />} path="cli" />
          <Route element={<pages.SettingsSectionPage />} path="cli/:section" />
          <Route element={<pages.SettingsSectionPage />} path="events" />
          <Route element={<pages.SettingsSectionPage />} path="user" />
          <Route element={<pages.SettingsSectionPage />} path="user/:section" />
          <Route element={<pages.SettingsSectionPage />} path="email" />
          <Route element={<pages.SettingsSectionPage />} path="email/:section" />
          <Route element={<pages.SettingsSectionPage />} path="preferences" />
          <Route element={<pages.SettingsSectionPage />} path="regions" />
          <Route element={<pages.SettingsSectionPage />} path="system" />
          <Route element={<pages.SettingsSectionPage />} path="system/:section" />
          <Route element={<pages.SettingsSectionPage />} path="dictionaries" />
          <Route element={<pages.SettingsSectionPage />} path="dictionaries/:currentDictionary" />
          <Route element={<pages.SettingsSectionPage />} path="profile" />
          <Route element={<pages.SettingsSectionPage />} path="profile/:section" />
          <Route element={<pages.SettingsSectionPage />} path="profile/:section/:sub" />
        </Route>

        <Route element={<Navigate replace to="/search/advanced" />} path="search" />
        <Route element={<pages.SearchPage />} path="search/advanced" />

        <Route element={<BillingLayout />} path="billing">
          <Route element={<Navigate replace to="reports" />} index />
          <Route element={<pages.BillingQuotasPage />} path="quotas" />
          <Route element={<pages.BillingQuotasPage />} path="quotas/:type" />
          <Route element={<pages.BillingReportsPage />} path="reports">
            <Route element={<pages.BillingReportSectionPage />} index />
            <Route element={<pages.BillingReportSectionPage />} path="instance" />
            <Route element={<pages.BillingReportSectionPage />} path="instance/:type" />
            <Route element={<pages.BillingReportSectionPage />} path="storage" />
            <Route element={<pages.BillingReportSectionPage />} path="storage/:type" />
          </Route>
        </Route>

        <Route element={<pages.NotificationsPage />} path="notifications" />
        <Route element={<pages.ChatPage />} path="chat" />

        <Route element={<pages.LaunchPage />} path="launch" />
        <Route element={<pages.LaunchPage />} path="launch/tool/:image" />
        <Route element={<pages.LaunchPage />} path="launch/:runId" />
        <Route element={<pages.LaunchPage />} path="launch/:id/:version" />
        <Route element={<pages.LaunchPage />} path="launch/:id/:version/:configuration" />
        <Route element={<pages.LaunchPage />} path="launch/:id/:version/:configuration/:runId" />

        <Route element={<pages.PipelineGitRefPage />} path=":pipeline/refs/heads/master" />
        <Route element={<pages.PipelineGitRefPage />} path=":pipeline/refs/heads/master/:section" />
        <Route
          element={<pages.PipelineGitRefPage />}
          path=":pipeline/refs/heads/master/:section/:subSection"
        />

        <Route element={<LibraryLayout />} path=":id">
          <Route element={<pages.PipelinePage />} index />
          <Route element={<pages.PipelineVersionPage />} path=":version">
            <Route element={<pages.PipelineVersionSectionPage />} path="history" />
            <Route element={<pages.PipelineVersionSectionPage />} path="code" />
            <Route element={<pages.PipelineVersionSectionPage />} path="configuration" />
            <Route
              element={<pages.PipelineVersionSectionPage />}
              path="configuration/:configuration"
            />
            <Route element={<pages.PipelineVersionSectionPage />} path="graph" />
            <Route element={<pages.PipelineVersionSectionPage />} path="workflow" />
            <Route element={<pages.PipelineVersionSectionPage />} path="documents" />
            <Route element={<pages.PipelineVersionSectionPage />} path="storage" />
          </Route>
        </Route>

        <Route element={<pages.NotFoundPage />} path="*" />
      </Route>
    </Route>,
  ),
);
