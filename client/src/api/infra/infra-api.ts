import type {
  ApplicationInfo,
  BillingChart,
  BillingChartRequest,
  ClusterNode,
  ConfigurationSchedule,
  InstanceType,
  Ontology,
  PermissionGrant,
  PermissionGrantRequest,
  RunSchedule,
  SearchRequest,
  SearchResult,
  SystemNotification,
  TemplateDescription,
} from '../../@types/app.ts';
import type {Preference} from '../../@types/preferences.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function loadApplicationInfo(): Promise<ApplicationInfo> {
  return cloudPipelineApi.jsonGet<ApplicationInfo>({uri: 'app/info', cached: true});
}

export async function loadAllInstanceTypes(): Promise<InstanceType[]> {
  return cloudPipelineApi.jsonGet<InstanceType[]>({uri: 'cluster/instance/loadAll', cached: true});
}

export async function loadAllowedInstanceTypes(): Promise<InstanceType[]> {
  return cloudPipelineApi.jsonGet<InstanceType[]>({uri: 'cluster/instance/allowed'});
}

export async function loadClusterNode(
  name: string,
  request?: Record<string, unknown>,
): Promise<ClusterNode> {
  return cloudPipelineApi.jsonPost<ClusterNode>({
    uri: `cluster/node/${name}/load`,
    body: request ?? {},
  });
}

export async function deletePreference(name: string): Promise<boolean> {
  return cloudPipelineApi.jsonDelete<boolean>({uri: `preferences/${name}`});
}

export async function loadPermissionGrants(
  aclClass: string,
  id: number,
): Promise<PermissionGrant[]> {
  return cloudPipelineApi.jsonGet<PermissionGrant[]>({
    uri: 'grant',
    query: {aclClass, id},
  });
}

export async function grantPermission(request: PermissionGrantRequest): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: 'grant', body: request});
}

export async function revokePermission(request: PermissionGrantRequest): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: 'grant', body: request});
}

export async function revokeAllPermissions(aclClass: string, id: number): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: 'grant/all', query: {aclClass, id}});
}

export async function search(request: SearchRequest): Promise<SearchResult> {
  return cloudPipelineApi.jsonPost<SearchResult>({uri: 'search', body: request});
}

export async function searchFacet(request: Record<string, unknown>): Promise<unknown> {
  return cloudPipelineApi.jsonPost({uri: 'search/facet', body: request});
}

export async function loadBillingCharts(request: BillingChartRequest): Promise<BillingChart[]> {
  return cloudPipelineApi.jsonPost<BillingChart[]>({uri: 'billing/charts', body: request});
}

export async function loadBillingChartsPagination(request: BillingChartRequest): Promise<unknown> {
  return cloudPipelineApi.jsonPost({uri: 'billing/charts/pagination', body: request});
}

export async function exportBilling(request: Record<string, unknown>): Promise<unknown> {
  return cloudPipelineApi.jsonPost({uri: 'billing/export', body: request});
}

export async function loadBillingCenters(): Promise<string[]> {
  return cloudPipelineApi.jsonGet<string[]>({uri: 'billing/centers'});
}

export async function createNotification(
  notification: SystemNotification,
): Promise<SystemNotification> {
  return cloudPipelineApi.jsonPost<SystemNotification>({uri: 'notification', body: notification});
}

export async function loadNotifications(): Promise<SystemNotification[]> {
  return cloudPipelineApi.jsonGet<SystemNotification[]>({uri: 'notification/list'});
}

export async function loadActiveNotifications(): Promise<SystemNotification[]> {
  return cloudPipelineApi.jsonGet<SystemNotification[]>({uri: 'notification/active'});
}

export async function filterNotifications(
  filter: Record<string, unknown>,
): Promise<SystemNotification[]> {
  return cloudPipelineApi.jsonPost<SystemNotification[]>({
    uri: 'notification/filter',
    body: filter,
  });
}

export async function loadNotification(id: number): Promise<SystemNotification> {
  return cloudPipelineApi.jsonGet<SystemNotification>({uri: `notification/${id}`});
}

export async function deleteNotification(id: number): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: `notification/${id}`});
}

export async function confirmNotification(ids: number[]): Promise<void> {
  await cloudPipelineApi.jsonPost({uri: 'notification/confirm', body: ids});
}

export async function loadOntologyTree(): Promise<Ontology[]> {
  return cloudPipelineApi.jsonGet<Ontology[]>({uri: 'ontologies/tree'});
}

export async function loadOntology(id: number): Promise<Ontology> {
  return cloudPipelineApi.jsonGet<Ontology>({uri: `ontologies/${id}`});
}

export async function saveOntology(ontology: Ontology): Promise<Ontology> {
  return cloudPipelineApi.jsonPost<Ontology>({uri: 'ontologies', body: ontology});
}

export async function updateOntology(id: number, ontology: Ontology): Promise<Ontology> {
  return cloudPipelineApi.jsonPut<Ontology>({uri: `ontologies/${id}`, body: ontology});
}

export async function deleteOntology(id: number): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: `ontologies/${id}`});
}

export async function loadTemplates(): Promise<TemplateDescription[]> {
  return cloudPipelineApi.jsonGet<TemplateDescription[]>({uri: 'templates/list'});
}

export async function loadFolderTemplates(): Promise<TemplateDescription[]> {
  return cloudPipelineApi.jsonGet<TemplateDescription[]>({uri: 'templates/folder/list'});
}

export async function saveRunSchedule(runId: number, schedule: RunSchedule): Promise<RunSchedule> {
  return cloudPipelineApi.jsonPost<RunSchedule>({uri: `schedule/run/${runId}`, body: schedule});
}

export async function loadRunSchedule(runId: number): Promise<RunSchedule> {
  return cloudPipelineApi.jsonGet<RunSchedule>({uri: `schedule/run/${runId}`});
}

export async function deleteRunSchedule(runId: number): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: `schedule/run/${runId}`});
}

export async function saveConfigurationSchedule(
  configurationId: number,
  schedule: ConfigurationSchedule,
): Promise<ConfigurationSchedule> {
  return cloudPipelineApi.jsonPost<ConfigurationSchedule>({
    uri: `schedule/configuration/${configurationId}`,
    body: schedule,
  });
}

export async function loadConfigurationSchedule(
  configurationId: number,
): Promise<ConfigurationSchedule> {
  return cloudPipelineApi.jsonGet<ConfigurationSchedule>({
    uri: `schedule/configuration/${configurationId}`,
  });
}

export async function deleteConfigurationSchedule(configurationId: number): Promise<void> {
  await cloudPipelineApi.jsonDelete({uri: `schedule/configuration/${configurationId}`});
}

export type {Preference};
