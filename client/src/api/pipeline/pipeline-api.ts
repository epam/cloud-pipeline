import type {
  CheckRepositoryRequest,
  ConfigurationEntry,
  ConfigurationParameters,
  GitCommitEntry,
  GitCredentials,
  GitRepositoryEntry,
  Pipeline,
  PipelineSourceItemsVO,
  PipelineVO,
  RegisterPipelineVersionVO,
  Revision,
} from '../../@types/pipeline.ts';
import type {PipelineRun} from '../../@types/runs.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function registerPipeline(pipeline: PipelineVO): Promise<Pipeline> {
  return cloudPipelineApi.jsonPost<Pipeline>({uri: 'pipeline/register', body: pipeline});
}

export async function checkPipelineRepository(request: CheckRepositoryRequest): Promise<boolean> {
  return cloudPipelineApi.jsonPost<boolean>({uri: 'pipeline/check', body: request});
}

export async function updatePipeline(pipeline: PipelineVO): Promise<Pipeline> {
  return cloudPipelineApi.jsonPost<Pipeline>({uri: 'pipeline/update', body: pipeline});
}

export async function updatePipelineToken(pipeline: PipelineVO): Promise<Pipeline> {
  return cloudPipelineApi.jsonPost<Pipeline>({uri: 'pipeline/updateToken', body: pipeline});
}

export async function loadAllPipelines(): Promise<Pipeline[]> {
  return cloudPipelineApi.jsonGet<Pipeline[]>({uri: 'pipeline/loadAll'});
}

export async function loadPipeline(id: number): Promise<Pipeline> {
  return cloudPipelineApi.jsonGet<Pipeline>({uri: `pipeline/${id}/load`});
}

export async function findPipeline(identifier: string): Promise<Pipeline> {
  return cloudPipelineApi.jsonGet<Pipeline>({uri: 'pipeline/find', query: {id: identifier}});
}

export async function deletePipeline(id: number): Promise<Pipeline> {
  return cloudPipelineApi.jsonDelete<Pipeline>({uri: `pipeline/${id}/delete`});
}

export async function loadPipelineRuns(id: number): Promise<PipelineRun[]> {
  return cloudPipelineApi.jsonGet<PipelineRun[]>({uri: `pipeline/${id}/runs`});
}

export async function loadPipelineVersions(id: number): Promise<Revision[]> {
  return cloudPipelineApi.jsonGet<Revision[]>({uri: `pipeline/${id}/versions`});
}

export async function loadPipelineVersion(id: number, version: string): Promise<Revision> {
  return cloudPipelineApi.jsonGet<Revision>({uri: `pipeline/${id}/version`, query: {version}});
}

export async function clonePipeline(id: number, version?: string): Promise<Pipeline> {
  return cloudPipelineApi.jsonGet<Pipeline>({uri: `pipeline/${id}/clone`, query: {version}});
}

export async function loadGitCredentials(): Promise<GitCredentials> {
  return cloudPipelineApi.jsonGet<GitCredentials>({uri: 'pipeline/git/credentials'});
}

export async function loadPipelineGraph(id: number, version: string): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: `pipeline/${id}/graph`, query: {version}});
}

export async function loadPipelineSources(id: number, version: string): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: `pipeline/${id}/sources`, query: {version}});
}

export async function movePipelineToFolder(id: number, folderId: number): Promise<Pipeline> {
  return cloudPipelineApi.jsonPost<Pipeline>({
    uri: `pipeline/${id}/folder`,
    query: {folderId},
  });
}

export async function removePipelineFromFolder(id: number): Promise<Pipeline> {
  return cloudPipelineApi.jsonDelete<Pipeline>({uri: `pipeline/${id}/folder`});
}

export async function loadPipelineDocs(
  id: number,
  version: string,
  path?: string,
): Promise<string> {
  return cloudPipelineApi.textGet({
    uri: `pipeline/${id}/docs`,
    query: {version, path},
  });
}

export async function loadPipelineFile(id: number, version: string, path: string): Promise<string> {
  return cloudPipelineApi.textGet({
    uri: `pipeline/${id}/file`,
    query: {version, path},
  });
}

export async function savePipelineFile(
  id: number,
  version: string,
  path: string,
  content: string,
): Promise<void> {
  await cloudPipelineApi.jsonPost({
    uri: `pipeline/${id}/file`,
    query: {version, path},
    body: content,
    contentType: 'text/plain',
  });
}

export async function revertPipelineFile(id: number, version: string, path: string): Promise<void> {
  await cloudPipelineApi.jsonPost({
    uri: `pipeline/${id}/file/revert`,
    query: {version, path},
  });
}

export async function savePipelineFiles(
  id: number,
  version: string,
  items: PipelineSourceItemsVO,
): Promise<void> {
  await cloudPipelineApi.jsonPost({
    uri: `pipeline/${id}/files`,
    query: {version},
    body: items,
  });
}

export async function deletePipelineFile(id: number, version: string, path: string): Promise<void> {
  await cloudPipelineApi.jsonDelete({
    uri: `pipeline/${id}/file`,
    query: {version, path},
  });
}

export async function registerPipelineVersion(
  request: RegisterPipelineVersionVO,
): Promise<Revision> {
  return cloudPipelineApi.jsonPost<Revision>({uri: 'pipeline/version/register', body: request});
}

export async function findPipelineByUrl(url: string): Promise<Pipeline> {
  return cloudPipelineApi.jsonGet<Pipeline>({uri: 'pipeline/findByUrl', query: {url}});
}

export async function loadPipelineRepository(id: number): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: `pipeline/${id}/repository`});
}

export async function loadPipelineLsTree(
  id: number,
  version: string,
  path?: string,
): Promise<GitRepositoryEntry[]> {
  return cloudPipelineApi.jsonGet<GitRepositoryEntry[]>({
    uri: `pipeline/${id}/ls_tree`,
    query: {version, path},
  });
}

export async function loadPipelinePath(
  id: number,
  version: string,
  path: string,
): Promise<GitRepositoryEntry> {
  return cloudPipelineApi.jsonGet<GitRepositoryEntry>({
    uri: `pipeline/${id}/path`,
    query: {version, path},
  });
}

export async function loadPipelineLogsTree(id: number, version: string): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: `pipeline/${id}/logs_tree`, query: {version}});
}

export async function loadPipelineCommits(
  id: number,
  version: string,
  filter?: Record<string, unknown>,
): Promise<GitCommitEntry[]> {
  return cloudPipelineApi.jsonPost<GitCommitEntry[]>({
    uri: `pipeline/${id}/commits`,
    query: {version},
    body: filter ?? {},
  });
}

export async function loadPipelineConfigurations(
  id: number,
  version: string,
): Promise<ConfigurationEntry[]> {
  return cloudPipelineApi.jsonGet<ConfigurationEntry[]>({
    uri: `pipeline/${id}/configurations`,
    query: {version},
  });
}

export async function savePipelineConfigurations(
  id: number,
  version: string,
  configurations: ConfigurationEntry[],
): Promise<ConfigurationEntry[]> {
  return cloudPipelineApi.jsonPost<ConfigurationEntry[]>({
    uri: `pipeline/${id}/configurations`,
    query: {version},
    body: configurations,
  });
}

export async function deletePipelineConfiguration(
  id: number,
  version: string,
  name: string,
): Promise<void> {
  await cloudPipelineApi.jsonDelete({
    uri: `pipeline/${id}/configurations`,
    query: {version, name},
  });
}

export async function loadPipelineParameters(
  id: number,
  version: string,
  configurationName: string,
): Promise<ConfigurationParameters> {
  return cloudPipelineApi.jsonGet<ConfigurationParameters>({
    uri: `pipeline/${id}/parameters`,
    query: {version, configurationName},
  });
}

export async function loadPipelineLanguage(id: number, version: string): Promise<string> {
  return cloudPipelineApi.jsonGet<string>({
    uri: `pipeline/${id}/language`,
    query: {version},
  });
}
