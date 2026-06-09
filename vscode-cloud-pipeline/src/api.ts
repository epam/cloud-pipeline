import * as http from 'http';
import * as https from 'https';
import { URL } from 'url';

export class ApiAuthError extends Error {
  readonly statusCode: number;

  constructor(statusCode: number, message: string) {
    super(message);
    this.name = 'ApiAuthError';
    this.statusCode = statusCode;
  }
}

export interface ApiResponse<T> {
  status: string;
  message?: string;
  payload?: T;
}

export interface RunInstancePayload {
  nodeType?: string;
  nodeIP?: string;
  nodeId?: string;
  nodeName?: string;
  nodeDisk?: number;
  spot?: boolean;
  cloudRegionId?: number;
  /** e.g. AWS, GCP, AZURE — serialized enum name from API. */
  cloudProvider?: string;
}

/** Mirrors `com.epam.pipeline.entity.cluster.GpuDevice` JSON. */
export interface GpuDevicePayload {
  name?: string;
  manufacturer?: string;
  cores?: number;
}

/** Mirrors `com.epam.pipeline.entity.cluster.InstanceType` JSON (`GET cluster/instance/loadAll`). */
export interface InstanceTypePayload {
  sku?: string;
  name?: string;
  termType?: string;
  operatingSystem?: string;
  /** Jackson may emit `vcpu` or `vCPU` depending on version. */
  vcpu?: number;
  vCPU?: number;
  memory?: number;
  memoryUnit?: string;
  instanceFamily?: string;
  gpu?: number;
  gpuDevice?: GpuDevicePayload | null;
  regionId?: number;
}

export interface RunFilterElement {
  id: number;
  pipelineName?: string;
  dockerImage?: string;
  version?: string;
  status: string;
  owner?: string;
  startDate?: string;
  /** Some API versions expose this at top level; otherwise see `instance.nodeType`. */
  nodeType?: string;
  /** Present when API includes run instance (e.g. `run/filter` payload). */
  instance?: RunInstancePayload;
}

export interface RunFilterPayload {
  elements: RunFilterElement[];
  totalCount: number;
}

export interface TaskPayload {
  name: string;
  status: string;
  started?: string;
}

export interface PipelineRunStarted {
  id: number;
  status?: string;
  dockerImage?: string;
}

/** Tool row from `dockerRegistry/loadTree` (registry groups / tools). */
export interface RegistryToolPayload {
  id: number;
  image: string;
  shortDescription?: string;
  description?: string;
  hasIcon?: boolean;
  iconId?: number;
  registryId?: number;
  instanceType?: string;
  disk?: number;
  defaultCommand?: string;
}

export interface DockerRegistryPayload {
  id: number;
  path: string;
  tools?: RegistryToolPayload[];
  groups?: Array<{ tools?: RegistryToolPayload[] }>;
}

export interface DockerRegistryTreePayload {
  registries: DockerRegistryPayload[];
}

export interface ToolVersionSettingRow {
  version: string;
  settings?: Array<{
    name?: string;
    default?: boolean;
    configuration?: ToolVersionConfigurationPayload;
  }>;
}

export interface ToolVersionConfigurationPayload {
  instance_size?: string;
  instance_disk?: string;
  cmd_template?: string;
  parameters?: Record<string, { type?: string; value?: string; required?: boolean; defaultValue?: string }>;
  is_spot?: boolean;
  node_count?: number;
  /** Serialized as camelCase from API configuration objects. */
  cloudRegionId?: number;
}

export interface ToolInfoPayload {
  toolId: number;
  versions?: Array<{ version?: string }>;
}

export interface InstanceTypeEntry {
  name: string;
  regionId?: number;
}

export interface AllowedInstanceAndPriceTypesPayload {
  'cluster.allowed.instance.types'?: InstanceTypeEntry[];
  'cluster.allowed.instance.types.docker'?: InstanceTypeEntry[];
  'cluster.allowed.price.types'?: string[];
}

export interface CloudRegionPayload {
  id: number;
  default?: boolean;
}

export interface WhoamiPayload {
  id: number;
  userName?: string;
}

export interface MetadataAttributeValue {
  value: string;
  type: string;
}

export interface MetadataEntityPayload {
  entity: { entityId: number; entityClass: string };
  data: Record<string, MetadataAttributeValue>;
}

export interface RunDetailPayload {
  id: number;
  status: string;
  owner?: string;
  podIP?: string;
  sshPassword?: string;
  sensitive?: boolean;
  platform?: string;
  pipelineName?: string;
  dockerImage?: string;
  version?: string;
  commitStatus?: string;
  initialized?: boolean;
  nodeCount?: number;
  parentRunId?: number;
  instance?: RunInstancePayload;
  pipelineRunParameters?: Array<{ name: string; value?: string | number }>;
  tasks?: TaskPayload[];
}

function httpRequest(
  method: string,
  fullUrl: string,
  headers: Record<string, string>,
  body?: string
): Promise<{ statusCode: number; text: string }> {
  return new Promise((resolve, reject) => {
    const u = new URL(fullUrl);
    const isHttps = u.protocol === 'https:';
    const lib = isHttps ? https : http;
    const opts: https.RequestOptions = {
      method,
      hostname: u.hostname,
      port: u.port || (isHttps ? 443 : 80),
      path: u.pathname + u.search,
      headers,
      rejectUnauthorized: false,
    };
    const req = lib.request(opts, (res) => {
      const chunks: Buffer[] = [];
      res.on('data', (c) => chunks.push(c as Buffer));
      res.on('end', () =>
        resolve({ statusCode: res.statusCode ?? 0, text: Buffer.concat(chunks).toString('utf8') })
      );
    });
    req.on('error', reject);
    if (body) {
      req.write(body);
    }
    req.end();
  });
}

/** Unauthenticated GET (same TLS behavior as pipe CLI `verify=False`). */
export function httpGetUnauthenticated(fullUrl: string): Promise<{ statusCode: number; text: string }> {
  return httpRequest('GET', fullUrl, { Accept: 'application/json' });
}

export class CloudPipelineApi {
  constructor(
    private readonly apiBase: string,
    private readonly accessKey: string
  ) {}

  private url(path: string): string {
    const base = this.apiBase.replace(/\/$/, '');
    const p = path.startsWith('/') ? path : `/${path}`;
    return `${base}${p}`;
  }

  private headers(): Record<string, string> {
    return {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${this.accessKey}`,
    };
  }

  async callJson<T>(method: string, path: string, body?: unknown): Promise<T> {
    const bodyStr = body !== undefined ? JSON.stringify(body) : undefined;
    const { statusCode, text } = await httpRequest(method, this.url(path), this.headers(), bodyStr);
    if (statusCode === 401 || statusCode === 403) {
      throw new ApiAuthError(statusCode, text.slice(0, 200) || `HTTP ${statusCode}`);
    }
    if (statusCode !== 200) {
      throw new Error(`HTTP ${statusCode}: ${text.slice(0, 200)}`);
    }
    const data = JSON.parse(text) as ApiResponse<T>;
    if (data.status !== 'OK') {
      throw new Error(data.message || data.status || 'API error');
    }
    if (data.payload === undefined) {
      throw new Error('Empty payload');
    }
    return data.payload;
  }

  /** Active runs: same statuses as web UI / pipe-cli active list. */
  listRunningRunsForOwner(owner: string, pageSize = 100): Promise<RunFilterPayload> {
    return this.callJson<RunFilterPayload>('POST', 'run/filter', {
      page: 1,
      pageSize,
      statuses: ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'],
      owners: [owner],
    });
  }

  getRun(runId: number): Promise<RunDetailPayload> {
    return this.callJson<RunDetailPayload>('GET', `run/${runId}`);
  }

  getRunTasks(runId: number): Promise<TaskPayload[]> {
    return this.callJson<TaskPayload[]>('GET', `run/${runId}/tasks`);
  }

  /** Same as pipe-cli `PipelineRun.get`: load run then attach tasks from `run/{id}/tasks`. */
  async getRunWithTasks(runId: number): Promise<RunDetailPayload> {
    const run = await this.getRun(runId);
    let tasks: TaskPayload[] = [];
    try {
      const raw = await this.getRunTasks(runId);
      tasks = Array.isArray(raw) ? raw : [];
    } catch {
      /* tasks endpoint missing or failed; leave tasks empty */
    }
    return { ...run, tasks };
  }

  getEdgeExternalUrl(region?: string): Promise<string> {
    let path = 'cluster/edge/externalUrl';
    if (region) {
      path += `?region=${encodeURIComponent(region)}`;
    }
    return this.callJson<string>('GET', path);
  }

  async getPreference(name: string): Promise<string | undefined> {
    try {
      const p = await this.callJson<{ name?: string; value?: string }>('GET', `preferences/${name}`);
      return p?.value;
    } catch (e) {
      if (e instanceof ApiAuthError) {
        throw e;
      }
      return undefined;
    }
  }

  async whoamiUserName(): Promise<string | undefined> {
    try {
      const u = await this.callJson<{ userName?: string }>('GET', 'whoami');
      return u?.userName?.split('@')[0];
    } catch (e) {
      if (e instanceof ApiAuthError) {
        throw e;
      }
      return undefined;
    }
  }

  async whoami(): Promise<WhoamiPayload | undefined> {
    try {
      return await this.callJson<WhoamiPayload>('GET', 'whoami');
    } catch (e) {
      if (e instanceof ApiAuthError) {
        throw e;
      }
      return undefined;
    }
  }

  async loadUserMetadata(entityId: number): Promise<MetadataEntityPayload | undefined> {
    try {
      const result = await this.callJson<MetadataEntityPayload[]>(
        'POST',
        'metadata/load',
        [{ entityId, entityClass: 'PIPELINE_USER' }]
      );
      return Array.isArray(result) ? result[0] : undefined;
    } catch (e) {
      if (e instanceof ApiAuthError) {
        throw e;
      }
      return undefined;
    }
  }

  loadDockerRegistryTree(): Promise<DockerRegistryTreePayload> {
    return this.callJson<DockerRegistryTreePayload>('GET', 'dockerRegistry/loadTree');
  }

  loadToolInfo(toolId: number): Promise<ToolInfoPayload> {
    return this.callJson<ToolInfoPayload>('GET', `tool/${toolId}/info`);
  }

  loadToolVersionSettings(toolId: number, version?: string): Promise<ToolVersionSettingRow[]> {
    const q = version ? `?version=${encodeURIComponent(version)}` : '';
    return this.callJson<ToolVersionSettingRow[]>('GET', `tool/${toolId}/settings${q}`);
  }

  loadCloudRegions(): Promise<CloudRegionPayload[]> {
    return this.callJson<CloudRegionPayload[]>('GET', 'cloud/region');
  }

  /**
   * All allowed instance types for a region / price model (`GET cluster/instance/loadAll`).
   * Pass `spot` explicitly so the list matches the run (omitting uses server default preference).
   */
  loadAllInstanceTypes(params: {
    regionId?: number;
    spot: boolean;
    toolInstances: boolean;
  }): Promise<InstanceTypePayload[]> {
    const parts: string[] = [`spot=${params.spot ? 'true' : 'false'}`];
    parts.push(`toolInstances=${params.toolInstances ? 'true' : 'false'}`);
    if (params.regionId !== undefined) {
      parts.push(`regionId=${params.regionId}`);
    }
    return this.callJson<InstanceTypePayload[]>('GET', `cluster/instance/loadAll?${parts.join('&')}`);
  }

  getAllowedInstanceAndPriceTypes(params: {
    toolId?: number;
    regionId?: number;
    spot?: boolean;
  }): Promise<AllowedInstanceAndPriceTypesPayload> {
    const parts: string[] = [];
    if (params.toolId !== undefined) {
      parts.push(`toolId=${params.toolId}`);
    }
    if (params.regionId !== undefined) {
      parts.push(`regionId=${params.regionId}`);
    }
    if (params.spot !== undefined) {
      parts.push(`spot=${params.spot ? 'true' : 'false'}`);
    }
    const q = parts.length ? `?${parts.join('&')}` : '';
    return this.callJson<AllowedInstanceAndPriceTypesPayload>('GET', `cluster/instance/allowed${q}`);
  }

  launchRun(payload: Record<string, unknown>): Promise<PipelineRunStarted> {
    return this.callJson<PipelineRunStarted>('POST', 'run', payload);
  }

  /** Same as web UI Stop / pipe-cli `stop_pipeline`: `POST run/{id}/status` with STOPPED. */
  stopRun(runId: number): Promise<RunDetailPayload> {
    return this.callJson<RunDetailPayload>('POST', `run/${runId}/status`, { status: 'STOPPED' });
  }

  /** `POST run/{id}/pause` (optional `checkSize`, default true on server). */
  pauseRun(runId: number, checkSize = true): Promise<RunDetailPayload> {
    const q = checkSize ? '' : '?checkSize=false';
    return this.callJson<RunDetailPayload>('POST', `run/${runId}/pause${q}`, {});
  }

  resumeRun(runId: number): Promise<RunDetailPayload> {
    return this.callJson<RunDetailPayload>('POST', `run/${runId}/resume`, {});
  }

  /** Terminates a PAUSED run (drops instance); web UI "Terminate" for paused runs. */
  terminateRun(runId: number): Promise<RunDetailPayload> {
    return this.callJson<RunDetailPayload>('POST', `run/${runId}/terminate`, {});
  }
}
