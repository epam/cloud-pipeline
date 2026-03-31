import * as vscode from 'vscode';
import {
  AllowedInstanceAndPriceTypesPayload,
  ApiAuthError,
  CloudPipelineApi,
  DockerRegistryTreePayload,
  RegistryToolPayload,
  ToolVersionConfigurationPayload,
  ToolVersionSettingRow,
} from './api';

const ALLOWED_DOCKER = 'cluster.allowed.instance.types.docker';
const ALLOWED_PIPE = 'cluster.allowed.instance.types';
const ALLOWED_PRICE = 'cluster.allowed.price.types';

export interface FlatRegistryTool extends RegistryToolPayload {
  registryPath: string;
}

function flattenTools(tree: DockerRegistryTreePayload): FlatRegistryTool[] {
  const byId = new Map<number, FlatRegistryTool>();
  for (const reg of tree.registries ?? []) {
    const registryPath = reg.path ?? '';
    const visitTools = (tools: RegistryToolPayload[] | undefined) => {
      for (const t of tools ?? []) {
        if (t?.id == null) {
          continue;
        }
        byId.set(t.id, { ...t, registryPath });
      }
    };
    visitTools(reg.tools);
    for (const g of reg.groups ?? []) {
      visitTools(g.tools);
    }
  }
  return [...byId.values()].sort((a, b) => (a.image || '').localeCompare(b.image || ''));
}

function parameterIsNotEmpty(
  parameter: unknown,
  additional?: (v: unknown) => boolean
): boolean {
  if (parameter === null || parameter === undefined) {
    return false;
  }
  if (`${parameter}`.trim().length === 0) {
    return false;
  }
  return !additional || additional(parameter);
}

function configurationForVersion(
  rows: ToolVersionSettingRow[],
  version: string,
  fallbackVersion?: string
): ToolVersionConfigurationPayload | undefined {
  const pick = (v: string) => rows.find((r) => r.version === v);
  const row = pick(version) ?? (fallbackVersion ? pick(fallbackVersion) : undefined);
  const settings = row?.settings;
  if (!settings?.length) {
    return undefined;
  }
  const entry = settings.find((s) => s.default) ?? settings[0];
  return entry?.configuration ?? undefined;
}

function versionSettingValue(
  rows: ToolVersionSettingRow[],
  version: string,
  fallbackVersion: string | undefined,
  key: keyof ToolVersionConfigurationPayload
): unknown {
  const cfg = configurationForVersion(rows, version, fallbackVersion);
  return cfg?.[key];
}

function chooseDefault<T>(
  versionVal: unknown,
  toolVal: unknown,
  prefVal: unknown,
  extra?: (v: unknown) => boolean
): T | undefined {
  if (parameterIsNotEmpty(versionVal, extra)) {
    return versionVal as T;
  }
  if (parameterIsNotEmpty(toolVal, extra)) {
    return toolVal as T;
  }
  if (parameterIsNotEmpty(prefVal, extra)) {
    return prefVal as T;
  }
  return undefined;
}

function prepareParameters(
  parameters: ToolVersionConfigurationPayload['parameters'] | undefined
): Record<string, { value: string; type: string }> | undefined {
  if (!parameters) {
    return undefined;
  }
  const result: Record<string, { value: string; type: string }> = {};
  for (const key of Object.keys(parameters)) {
    const p = parameters[key];
    result[key] = {
      type: p?.type || 'string',
      value: p?.value ?? p?.defaultValue ?? '',
    };
  }
  return Object.keys(result).length ? result : undefined;
}

function clampPayloadToAllowed(
  payload: Record<string, unknown>,
  allowed: AllowedInstanceAndPriceTypesPayload | undefined
): void {
  if (!allowed) {
    return;
  }
  const listKey = payload.dockerImage ? ALLOWED_DOCKER : ALLOWED_PIPE;
  const raw = allowed[listKey] ?? [];
  const availableInstanceTypes = raw.map((i) => i.name);
  const availablePriceTypes = (allowed[ALLOWED_PRICE] ?? [])
    .map((p) => {
      if (p === 'spot') {
        return true;
      }
      if (p === 'on_demand') {
        return false;
      }
      return undefined;
    })
    .filter((x): x is boolean => x !== undefined);

  function getAvailableValue<T>(value: T, values: T[], chooseDefault: boolean): T | null {
    const hit = values.find((v) => v === value);
    if (hit !== undefined) {
      return hit;
    }
    if (values.length > 0 && chooseDefault) {
      return values[0]!;
    }
    return null;
  }

  const it = getAvailableValue(payload.instanceType as string, availableInstanceTypes, false);
  if (it !== null) {
    payload.instanceType = it;
  }
  const sp = getAvailableValue(`${payload.isSpot}` === 'true', availablePriceTypes, true);
  if (sp !== null) {
    payload.isSpot = sp;
  }
}

/** Mirrors the web UI default tag when opening Run → Run default. */
function defaultVersionTag(versionNames: string[]): string | undefined {
  if (versionNames.includes('latest')) {
    return 'latest';
  }
  if (versionNames.length === 1) {
    return versionNames[0];
  }
  return undefined;
}

export async function runStartNewRunFlow(
  api: CloudPipelineApi,
  brand: string,
  onListChanged?: () => void
): Promise<void> {
  let tree: DockerRegistryTreePayload;
  try {
    tree = await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: `${brand}: Loading tools…`,
        cancellable: false,
      },
      () => api.loadDockerRegistryTree()
    );
  } catch (e) {
    if (e instanceof ApiAuthError) {
      throw e;
    }
    const msg = e instanceof Error ? e.message : String(e);
    vscode.window.showErrorMessage(`${brand}: Failed to load tools: ${msg}`);
    return;
  }

  const tools = flattenTools(tree);
  if (!tools.length) {
    vscode.window.showInformationMessage(`${brand}: No tools available for your account.`);
    return;
  }

  type ToolPick = vscode.QuickPickItem & { tool: FlatRegistryTool };
  const toolItems: ToolPick[] = tools.map((t) => ({
    label: t.image || `tool ${t.id}`,
    detail: (t.shortDescription || t.description || '').trim() || undefined,
    tool: t,
  }));

  const pickedTool = await vscode.window.showQuickPick(toolItems, {
    placeHolder: 'Search tools by name or description',
    matchOnDetail: true,
  });
  if (!pickedTool) {
    return;
  }
  const t = pickedTool.tool;

  let versionBundle: {
    versionNames: string[];
    settingsRows: ToolVersionSettingRow[];
    regions: { id: number; default?: boolean }[];
  };

  try {
    versionBundle = await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: `${brand}: Loading tool versions…`,
        cancellable: false,
      },
      async () => {
        const [info, settings, regs] = await Promise.all([
          api.loadToolInfo(t.id),
          api.loadToolVersionSettings(t.id),
          api.loadCloudRegions(),
        ]);
        const versionNames = (info.versions ?? [])
          .map((v) => v.version)
          .filter((v): v is string => !!v);
        return {
          versionNames,
          settingsRows: settings,
          regions: regs,
        };
      }
    );
  } catch (e) {
    if (e instanceof ApiAuthError) {
      throw e;
    }
    const msg = e instanceof Error ? e.message : String(e);
    vscode.window.showErrorMessage(`${brand}: Failed to load tool versions: ${msg}`);
    return;
  }

  const { versionNames, settingsRows, regions } = versionBundle;

  if (!versionNames.length) {
    vscode.window.showErrorMessage(`${brand}: No versions found for this tool.`);
    return;
  }

  const fallbackDefault =
    defaultVersionTag(versionNames) ?? (versionNames.length === 1 ? versionNames[0] : undefined);

  type VerPick = vscode.QuickPickItem & { version: string };

  const verItems: VerPick[] = [];
  if (fallbackDefault !== undefined) {
    verItems.push({
      label: `Default (${fallbackDefault})`,
      description: 'Start a run with the default configuration',
      version: fallbackDefault,
    });
  }
  for (const v of [...versionNames].sort((a, b) => a.localeCompare(b))) {
    if (fallbackDefault !== undefined && v === fallbackDefault) {
      continue;
    }
    verItems.push({ label: v, version: v });
  }

  const pickedVer = await vscode.window.showQuickPick(verItems, {
    placeHolder: 'Choose version (search by tag)',
  });
  if (!pickedVer) {
    return;
  }

  const version = pickedVer.version;

  const [
    prefInstanceType,
    prefDisk,
    prefCmd,
    prefSpot,
  ] = await Promise.all([
    api.getPreference('cluster.instance.type'),
    api.getPreference('cluster.instance.hdd'),
    api.getPreference('launch.cmd.template'),
    api.getPreference('cluster.spot'),
  ]);
  const useSpot = `${prefSpot}` === 'true';

  const rawRegion = versionSettingValue(settingsRows, version, fallbackDefault, 'cloudRegionId');
  const cloudRegionIdValue = parameterIsNotEmpty(rawRegion)
    ? Number(rawRegion)
    : regions.find((r) => r.default)?.id;

  const allowed = await api.getAllowedInstanceAndPriceTypes({
    toolId: t.id,
    regionId: cloudRegionIdValue,
    spot: parameterIsNotEmpty(versionSettingValue(settingsRows, version, fallbackDefault, 'is_spot'))
      ? !!versionSettingValue(settingsRows, version, fallbackDefault, 'is_spot')
      : useSpot,
  });

  const dockerBase = `${t.registryPath}/${t.image}`.replace(/\/+/g, '/').replace(/^\/+/, '');
  const dockerImage = `${dockerBase}:${version}`;

  const instanceType = chooseDefault<string>(
    versionSettingValue(settingsRows, version, fallbackDefault, 'instance_size'),
    t.instanceType,
    prefInstanceType
  );
  const diskStr = chooseDefault<string>(
    versionSettingValue(settingsRows, version, fallbackDefault, 'instance_disk'),
    t.disk != null ? String(t.disk) : undefined,
    prefDisk,
    (p: unknown) => +String(p) > 0
  );
  const cmdTemplate = chooseDefault<string>(
    versionSettingValue(settingsRows, version, fallbackDefault, 'cmd_template'),
    t.defaultCommand,
    prefCmd
  );
  const isSpot = parameterIsNotEmpty(versionSettingValue(settingsRows, version, fallbackDefault, 'is_spot'))
    ? !!versionSettingValue(settingsRows, version, fallbackDefault, 'is_spot')
    : useSpot;
  const nodeCountRaw = versionSettingValue(settingsRows, version, fallbackDefault, 'node_count');
  const nodeCount = parameterIsNotEmpty(nodeCountRaw) ? Number(nodeCountRaw) : undefined;

  const params = prepareParameters(
    configurationForVersion(settingsRows, version, fallbackDefault)?.parameters
  );

  const payload: Record<string, unknown> = {
    dockerImage,
    instanceType,
    hddSize: diskStr != null ? +diskStr : undefined,
    timeout: 0,
    cmdTemplate,
    params,
    isSpot,
    nodeCount,
    cloudRegionId: cloudRegionIdValue,
  };

  clampPayloadToAllowed(payload, allowed);

  try {
    const run = await vscode.window.withProgress(
      {
        location: vscode.ProgressLocation.Notification,
        title: `${brand}: Starting run…`,
        cancellable: false,
      },
      () => api.launchRun(payload)
    );
    vscode.window.showInformationMessage(`${brand}: Started run ${run.id}.`);
    onListChanged?.();
  } catch (e) {
    if (e instanceof ApiAuthError) {
      throw e;
    }
    const msg = e instanceof Error ? e.message : String(e);
    vscode.window.showErrorMessage(`${brand}: Failed to start run: ${msg}`);
  }
}
