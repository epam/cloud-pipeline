import type {
  DockerRegistry,
  DockerRegistryList,
  DockerRegistryVO,
  Tool,
  ToolDescription,
  ToolGroup,
  ToolSymlinkRequest,
  ToolVersion,
} from '../../@types/tools.ts';
import cloudPipelineApi from '../cloud-pipeline-api.ts';

export async function loadDockerRegistryTree(): Promise<DockerRegistryList> {
  return cloudPipelineApi.jsonGet<DockerRegistryList>({
    uri: 'dockerRegistry/loadTree',
    cached: true,
  });
}

export async function loadDockerRegistry(id: number): Promise<DockerRegistry> {
  return cloudPipelineApi.jsonGet<DockerRegistry>({uri: `dockerRegistry/${id}/load`});
}

export async function registerDockerRegistry(registry: DockerRegistryVO): Promise<DockerRegistry> {
  return cloudPipelineApi.jsonPost<DockerRegistry>({
    uri: 'dockerRegistry/register',
    body: registry,
  });
}

export async function updateDockerRegistry(registry: DockerRegistryVO): Promise<DockerRegistry> {
  return cloudPipelineApi.jsonPost<DockerRegistry>({uri: 'dockerRegistry/update', body: registry});
}

export async function updateDockerRegistryCredentials(
  registry: DockerRegistryVO,
): Promise<DockerRegistry> {
  return cloudPipelineApi.jsonPost<DockerRegistry>({
    uri: 'dockerRegistry/updateCredentials',
    body: registry,
  });
}

export async function deleteDockerRegistry(id: number): Promise<DockerRegistry> {
  return cloudPipelineApi.jsonDelete<DockerRegistry>({uri: `dockerRegistry/${id}/delete`});
}

export async function loadTool(id: number): Promise<Tool> {
  return cloudPipelineApi.jsonGet<Tool>({uri: 'tool/load', query: {id}});
}

export async function registerTool(tool: Tool): Promise<Tool> {
  return cloudPipelineApi.jsonPost<Tool>({uri: 'tool/register', body: tool});
}

export async function updateTool(tool: Tool): Promise<Tool> {
  return cloudPipelineApi.jsonPost<Tool>({uri: 'tool/update', body: tool});
}

export async function deleteTool(id: number): Promise<Tool> {
  return cloudPipelineApi.jsonDelete<Tool>({uri: 'tool/delete', query: {id}});
}

export async function loadToolTags(id: number): Promise<ToolVersion[]> {
  return cloudPipelineApi.jsonGet<ToolVersion[]>({uri: `tool/${id}/tags`});
}

export async function loadToolDescription(id: number, version?: string): Promise<ToolDescription> {
  return cloudPipelineApi.jsonGet<ToolDescription>({
    uri: `tool/${id}/description`,
    query: {version},
  });
}

export async function loadToolDockerfile(id: number, version?: string): Promise<string> {
  return cloudPipelineApi.textGet({uri: `tool/${id}/dockerfile`, query: {version}});
}

export async function loadToolDefaultCmd(id: number, version?: string): Promise<string> {
  return cloudPipelineApi.jsonGet<string>({uri: `tool/${id}/defaultCmd`, query: {version}});
}

export async function symlinkTool(request: ToolSymlinkRequest): Promise<Tool> {
  return cloudPipelineApi.jsonPost<Tool>({uri: 'tool/symlink', body: request});
}

export async function loadToolGroups(registryId?: number): Promise<ToolGroup[]> {
  return cloudPipelineApi.jsonGet<ToolGroup[]>({
    uri: 'toolGroup/list',
    query: registryId ? {registryId} : undefined,
  });
}

export async function loadToolGroup(id: number): Promise<ToolGroup> {
  return cloudPipelineApi.jsonGet<ToolGroup>({uri: `toolGroup/${id}`});
}

export async function createToolGroup(group: ToolGroup): Promise<ToolGroup> {
  return cloudPipelineApi.jsonPost<ToolGroup>({uri: 'toolGroup', body: group});
}

export async function updateToolGroup(group: ToolGroup): Promise<ToolGroup> {
  return cloudPipelineApi.jsonPut<ToolGroup>({uri: 'toolGroup', body: group});
}

export async function deleteToolGroup(id: number): Promise<ToolGroup> {
  return cloudPipelineApi.jsonDelete<ToolGroup>({uri: `toolGroup/${id}`});
}

export async function scanTool(request: Record<string, unknown>): Promise<unknown> {
  return cloudPipelineApi.jsonPost({uri: 'tool/scan', body: request});
}

export async function loadToolScanStatus(scanId: string): Promise<unknown> {
  return cloudPipelineApi.jsonGet({uri: 'tool/scan', query: {scanId}});
}

export async function isToolScanEnabled(): Promise<boolean> {
  return cloudPipelineApi.jsonGet<boolean>({uri: 'tool/scan/enabled'});
}
