import {createLazyPage} from './create-lazy-page';

export const DashboardPage = createLazyPage(
  () => import('../dashboard/dashboard-page'),
  'DashboardPage',
);

export const LibraryContentsPage = createLazyPage(
  () => import('../library/library-contents-page'),
  'LibraryContentsPage',
);

export const LibraryIndexPage = LibraryContentsPage;

export const PipelinesBrowserPage = createLazyPage(
  () => import('../library/pipelines-browser-page'),
  'PipelinesBrowserPage',
);

export const StoragesBrowserPage = createLazyPage(
  () => import('../library/storages-browser-page'),
  'StoragesBrowserPage',
);

export const FolderPage = LibraryContentsPage;

export const FolderHistoryPage = createLazyPage(
  () => import('../library/project-history-page.tsx'),
  'ProjectHistoryPage',
);

export const MetadataFolderPage = createLazyPage(
  () => import('../library/metadata-folder-page'),
  'MetadataFolderPage',
);

export const MetadataPage = createLazyPage(
  () => import('../library/metadata-page'),
  'MetadataPage',
);

export const StoragePage = createLazyPage(() => import('../library/storage-page'), 'StoragePage');

export const ConfigurationPage = createLazyPage(
  () => import('../library/configuration-page'),
  'ConfigurationPage',
);

export const VersionedStoragePage = createLazyPage(
  () => import('../library/versioned-storage-page'),
  'VersionedStoragePage',
);

export const PipelinePage = createLazyPage(
  () => import('../library/pipeline-page'),
  'PipelinePage',
);

export const PipelineVersionPage = createLazyPage(
  () => import('../library/pipeline-version-page'),
  'PipelineVersionPage',
);

export const PipelineVersionSectionPage = createLazyPage(
  () => import('../library/pipeline-version-section-page'),
  'PipelineVersionSectionPage',
);

export const PipelineGitRefPage = createLazyPage(
  () => import('../library/pipeline-git-ref-page'),
  'PipelineGitRefPage',
);

export const ClusterPage = createLazyPage(() => import('../cluster/cluster-page'), 'ClusterPage');

export const ClusterNodePage = createLazyPage(
  () => import('../cluster/cluster-node-page'),
  'ClusterNodePage',
);

export const ClusterSectionPage = createLazyPage(
  () => import('../cluster/cluster-section-page'),
  'ClusterSectionPage',
);

export const ClusterNodeInfoPage = createLazyPage(
  () => import('../cluster/cluster-node-info-page'),
  'ClusterNodeInfoPage',
);

export const ClusterNodeJobsPage = createLazyPage(
  () => import('../cluster/cluster-node-jobs-page'),
  'ClusterNodeJobsPage',
);

export const ClusterNodeMonitorPage = createLazyPage(
  () => import('../cluster/cluster-node-monitor-page'),
  'ClusterNodeMonitorPage',
);

export const ToolsPage = createLazyPage(() => import('../tools/tools-page'), 'ToolsPage');

export const ToolPage = createLazyPage(() => import('../tools/tool-page'), 'ToolPage');

export const ToolVersionPage = createLazyPage(
  () => import('../tools/tool-version-page'),
  'ToolVersionPage',
);

export const ToolVersionSectionPage = createLazyPage(
  () => import('../tools/tool-version-section-page'),
  'ToolVersionSectionPage',
);

export const RunsPage = createLazyPage(() => import('../runs/runs-page'), 'RunsPage');

export const RunsFilterPage = createLazyPage(
  () => import('../runs/runs-filter-page'),
  'RunsFilterPage',
);

export const RunDetailsPage = createLazyPage(
  () => import('../runs/run-details-page'),
  'RunDetailsPage',
);

export const SettingsSectionPage = createLazyPage(
  () => import('../settings/settings-section-page'),
  'SettingsSectionPage',
);

export const SearchPage = createLazyPage(() => import('../search/search-page'), 'SearchPage');

export const BillingQuotasPage = createLazyPage(
  () => import('../billing/billing-quotas-page'),
  'BillingQuotasPage',
);

export const BillingReportsPage = createLazyPage(
  () => import('../billing/billing-reports-page'),
  'BillingReportsPage',
);

export const BillingReportSectionPage = createLazyPage(
  () => import('../billing/billing-report-section-page'),
  'BillingReportSectionPage',
);

export const NotificationsPage = createLazyPage(
  () => import('../notifications/notifications-page'),
  'NotificationsPage',
);

export const ChatPage = createLazyPage(() => import('../chat/chat-page'), 'ChatPage');

export const LaunchPage = createLazyPage(() => import('../launch/launch-page'), 'LaunchPage');

export const MiewPage = createLazyPage(() => import('../special/miew-page'), 'MiewPage');

export const WsiPage = createLazyPage(() => import('../special/wsi-page'), 'WsiPage');

export const HcsPage = createLazyPage(() => import('../special/hcs-page'), 'HcsPage');

export const NotFoundPage = createLazyPage(
  () => import('../_shared/not-found-page'),
  'NotFoundPage',
);
