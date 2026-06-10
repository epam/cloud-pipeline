/**
 * Central route path definitions aligned with navigation items.
 * Use these constants instead of hard-coded path strings.
 */
export const routeingPaths = {
  root: '/',

  dashboard: '/dashboard',

  library: '/library',
  pipelines: '/pipelines',
  storages: '/storages',
  folder: (id: string | number) => `/folder/${id}`,
  folderHistory: (id: string | number) => `/folder/${id}/history`,
  folderMetadata: (id: string | number) => `/folder/${id}/metadata`,
  folderMetadataClass: (id: string | number, className: string) =>
    `/folder/${id}/metadata/${className}`,
  storage: (id: string | number) => `/storage/${id}`,
  configuration: (id: string | number, name?: string) =>
    name ? `/configuration/${id}/${name}` : `/configuration/${id}`,
  versionedStorage: (id: string | number) => `/vs/${id}`,
  pipeline: (id: string | number) => `/${id}`,
  pipelineVersion: (id: string | number, version: string | number) => `/${id}/${version}`,
  pipelineVersionSection: (id: string | number, version: string | number, section: string) =>
    `/${id}/${version}/${section}`,
  pipelineGitRef: (pipeline: string, section?: string, subSection?: string) => {
    const base = `/${pipeline}/refs/heads/master`;
    if (!section) return base;
    if (!subSection) return `${base}/${section}`;
    return `${base}/${section}/${subSection}`;
  },

  cluster: '/cluster',
  clusterCoreNodes: '/cluster/core-nodes',
  clusterCloudNodes: '/cluster/cloud-nodes',
  clusterHot: '/cluster/hot',
  clusterUsage: '/cluster/usage',
  clusterNode: (nodeName: string) => `/cluster/${nodeName}`,
  clusterNodeInfo: (nodeName: string) => `/cluster/${nodeName}/info`,
  clusterNodeJobs: (nodeName: string) => `/cluster/${nodeName}/jobs`,
  clusterNodeMonitor: (nodeName: string) => `/cluster/${nodeName}/monitor`,

  tools: '/tools',
  toolsRegistry: (registryId: string | number) => `/tools/${registryId}`,
  toolsGroup: (registryId: string | number, groupId: string | number) =>
    `/tools/${registryId}/${groupId}`,
  tool: (id: string | number, section = 'description') => `/tool/${id}/${section}`,
  toolVersion: (id: string | number, version: string) => `/tool/${id}/info/${version}`,
  toolVersionSection: (id: string | number, version: string, section: string) =>
    `/tool/${id}/info/${version}/${section}`,

  runs: '/runs',
  runsFilter: '/runs/filter',
  runsByStatus: (status: string) => `/runs/${status}`,
  run: (runId: string | number) => `/run/${runId}`,
  runWithMode: (runId: string | number, mode: string) => `/run/${runId}/${mode}`,
  runWithTask: (runId: string | number, taskName: string) => `/run/${runId}/${taskName}`,
  runWithModeAndTask: (runId: string | number, mode: string, taskName: string) =>
    `/run/${runId}/${mode}/${taskName}`,

  settings: '/settings',
  settingsSection: (section: string) => `/settings/${section}`,

  search: '/search',
  searchAdvanced: '/search/advanced',

  billing: '/billing',
  billingQuotas: '/billing/quotas',
  billingReports: '/billing/reports',

  notifications: '/notifications',
  chat: '/chat',

  launch: '/launch',
  launchTool: (image: string) => `/launch/tool/${image}`,
  launchRun: (runId: string | number) => `/launch/${runId}`,
  launchPipeline: (id: string | number, version?: string) =>
    version ? `/launch/${id}/${version}` : `/launch/${id}`,
  launchPipelineConfig: (id: string | number, version: string, configuration: string) =>
    `/launch/${id}/${version}/${configuration}`,

  miew: '/miew',
  wsi: '/wsi',
  hcs: '/hcs',
} as const;

/** Routes that render without the main navigation shell. */
export const fullscreenPaths = [routeingPaths.miew, routeingPaths.wsi, routeingPaths.hcs] as const;

export type AppPath = (typeof routeingPaths)[keyof typeof routeingPaths];

export const navigationPages = {
  dashboard: 'dashboard',
  library: 'library',
  cluster: 'cluster',
  tools: 'tools',
  runs: 'runs',
  settings: 'settings',
  search: 'search',
  billing: 'billing',
  notifications: 'notifications',
  chat: 'chat',
  run: 'run',
  miew: 'miew',
  wsi: 'wsi',
  hcs: 'hcs',
  launch: 'launch',
} as const;

export type NavigationPage = (typeof navigationPages)[keyof typeof navigationPages];
