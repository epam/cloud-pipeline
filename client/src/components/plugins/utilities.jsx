/**
 * @typedef {Object} UIPlugin
 * @property {number} id
 * @property {string} name
 * @property {string} uri
 * @property {string} type
 */

import PipelineRunInfo from '../../models/pipelines/PipelineRunInfo';
import dockerRegistriesTree from '../../models/tools/DockerRegistriesTree';
import {getDockerImage} from '../../utils/get-docker-image';
import FilterPlugins from '../../models/plugins/filter-plugins';
import whoAmI from '../../models/user/WhoAmI';
import UnAssignPlugin from '../../models/plugins/unassign-plugin';
import AssignPlugin from '../../models/plugins/assign-plugin';

/**
 * @typedef {Object} UIPluginLoadResult
 * @property {bool} ok
 * @property {string} error
 * @property {function} [renderLaunchForm]
 * @property {function} [renderLogForm]
 */

const UI_PLUGIN_TYPE_LAUNCH_FORM = 'LaunchForm';
const UI_PLUGIN_TYPE_RUN_LOG = 'RunLog';

export {UI_PLUGIN_TYPE_LAUNCH_FORM, UI_PLUGIN_TYPE_RUN_LOG};

const renderLaunchFormExportName = 'renderLaunchForm';
const renderLogFormExportName = 'renderLogForm';
const renderDefaultExportName = 'render';

const renderExportName = {
  [UI_PLUGIN_TYPE_LAUNCH_FORM]: renderLaunchFormExportName,
  [UI_PLUGIN_TYPE_RUN_LOG]: renderLogFormExportName,
  fallback: renderDefaultExportName,
};

const renderName = {
  [UI_PLUGIN_TYPE_RUN_LOG]: 'run log form',
  [UI_PLUGIN_TYPE_LAUNCH_FORM]: 'launch form',
};

export function getPluginTypeName(pluginType) {
  switch ((pluginType || '').toLowerCase()) {
    case UI_PLUGIN_TYPE_LAUNCH_FORM.toLowerCase():
      return 'Launch Form';
    case UI_PLUGIN_TYPE_RUN_LOG.toLowerCase():
      return 'Run Logs';
    default:
      return pluginType;
  }
}

/**
 * @param {UIPlugin} plugin
 * @param {string} [type]
 * @returns {Promise<function>}
 */
export async function loadPlugin(plugin, type) {
  if (type === undefined) {
    type = plugin.type;
  }
  const {id, path, name: pluginName} = plugin;
  const uri = `${SERVER + API_PATH}/plugins/${id}/content/${path}`;
  console.log(`loading plugin "${pluginName}" (${path})`);
  try {
    const module = await import(/* webpackIgnore: true */ uri);
    let exportName = (type ? renderExportName[type] : undefined) || renderExportName.fallback;
    if (!exportName || !(exportName in module)) {
      exportName = renderExportName.fallback;
    }
    const name = type && type in renderName ? renderName[type] : undefined;
    console.log(`loading plugin "${pluginName}": using export "${exportName}"`);
    if (!exportName || !(exportName in module)) {
      throw new Error(
        name
          ? `Plugin does not provide components for ${name}`
          : 'Plugin does not provide components',
      );
    }
    const render = module[exportName];
    if (typeof render !== 'function') {
      throw new Error(
        name
          ? `Unsupported Plugin component type for ${name}`
          : 'Unsupported Plugin component type',
      );
    }
    console.log(`loading plugin "${pluginName}": loaded`);
    return render;
  } catch (e) {
    console.warn(`error loading plugin "${pluginName}": ${e.message}`);
    throw e;
  }
}

const runsCache = new Map();

export async function fetchRun(runId) {
  if (!runsCache.has(String(runId))) {
    const req = (async () => {
      const r = new PipelineRunInfo(runId);
      await r.fetch();
      if (r.error) {
        throw new Error(r.error);
      }
      return r.value;
    })();
    runsCache.set(runId, req);
  }
  return runsCache.get(String(runId));
}

/**
 * @typedef {Object} FetchPluginOptions
 * @property {string | number} [pipelineId]
 * @property {string | number} [toolId]
 * @property {string} [version]
 */
/**
 * @typedef {Object} GenerateFetchPluginOptions
 * @property {string | number} [pipelineId]
 * @property {string | number} [toolId]
 * @property {string | number} [runId]
 * @property {string} [pipelineVersion]
 * @property {string} [toolVersion]
 */
/**
 * @typedef {GenerateFetchPluginOptions} FetchPluginOptions
 * @property {string} pluginType
 */
/**
 * @param {FetchPluginOptions} opts
 * @returns {Promise<{FetchPluginsOptions}>}
 */
async function generateFetchPluginOptions(opts) {
  const {pipelineId, toolId, runId, pipelineVersion, toolVersion} = opts || {};
  if (pipelineId) {
    return {pipelineId, version: pipelineVersion};
  }
  if (toolId) {
    return {toolId, version: toolVersion};
  }
  if (runId) {
    const run = await fetchRun(runId);
    if (run) {
      const {dockerImage, pipelineId, version} = run;
      if (pipelineId) {
        return {pipelineId, version};
      }
      if (dockerImage) {
        await dockerRegistriesTree.fetchIfNeededOrWait();
        const {tool, version: diVersion} = getDockerImage(dockerImage, dockerRegistriesTree) ?? {};
        if (tool) {
          return {toolId: tool.id, version: diVersion};
        }
      }
      return undefined;
    }
  }
}

/**
 * @param {GenerateFetchPluginOptions} options
 * @returns {Promise<UIPlugin[]>}
 */
export async function fetchAvailablePlugins(options) {
  const {pipelineId, pipelineVersion, toolId, toolVersion, runId} = options || {};
  const opts = await generateFetchPluginOptions({
    pipelineId,
    pipelineVersion,
    toolId,
    toolVersion,
    runId,
  });
  if (opts) {
    const req = new FilterPlugins(opts);
    await req.fetch();
    if (req.error) {
      throw new Error(req.error);
    }
    return req.value || [];
  }
  return [];
}

/**
 * @param {GenerateFetchPluginOptions} options
 * @returns {Promise<UIPlugin>}
 */
export async function fetchPlugin(options) {
  const {pluginType} = options || {};
  const plugins = await fetchAvailablePlugins(options);
  const plugin = await (async () => {
    if (plugins.length > 0) {
      await whoAmI.fetchIfNeededOrWait();
      const {roles = [], groups = [], userName} = whoAmI.value || {};
      const groupsAndRoles = [...new Set([...groups, ...roles.map((o) => o.name)])].map((s) =>
        s.toLowerCase(),
      );
      return plugins.find((plugin) => {
        const {plugin: pluginData = {}, sids = []} = plugin;
        const {type = pluginType} = pluginData;
        return (
          type === pluginType &&
          (sids.length === 0 ||
            sids.some(
              (s) =>
                (s.principal && s.name.toLowerCase() === userName.toLowerCase()) ||
                (!s.principal && groupsAndRoles.includes(s.name.toLowerCase())),
            ))
        );
      });
    }
    return undefined;
  })();
  return plugin ? plugin.plugin : undefined;
}

export async function unAssignPlugins(plugins) {
  await Promise.all(plugins.map(({id}) => new UnAssignPlugin(id).fetch()));
}

function sidsAreEqual(sidA, sidB) {
  if (!sidA && !sidB) {
    return true;
  }
  if (!sidA || !sidB) {
    return false;
  }
  const {name: aName, principal: aPrincipal} = sidA;
  const {name: bName, principal: bPrincipal} = sidB;
  return aName === bName && aPrincipal === bPrincipal;
}

function sidsArraysAreEqual(sidsA = [], sidsB = []) {
  if (sidsA.length !== sidsB.length) {
    return false;
  }
  for (const sidA of sidsA) {
    if (!sidsB.find((sidB) => sidsAreEqual(sidA, sidB))) {
      return false;
    }
  }
  return true;
}

export function pluginAssignmentsAreEqual(plugin1, plugin2) {
  if (!plugin1 && !plugin2) {
    return true;
  }
  if (!plugin1 || !plugin2) {
    return false;
  }
  const {
    plugin: pluginData1 = {},
    sids: sids1 = [],
    pipelineId: pipelineId1,
    toolId: toolId1,
    version: version1,
  } = plugin1;
  const {
    plugin: pluginData2 = {},
    sids: sids2 = [],
    pipelineId: pipelineId2,
    toolId: toolId2,
    version: version2,
  } = plugin2;
  return (
    pluginData1.id === pluginData2.id &&
    sidsArraysAreEqual(sids1, sids2) &&
    pipelineId1 === pipelineId2 &&
    toolId1 === toolId2 &&
    version1 === version2
  );
}

export function pluginAssignmentsArraysAreEqual(assignments1, assignments2) {
  const a1 = assignments1 || [];
  const a2 = assignments2 || [];
  if (a1.length !== a2.length) {
    return false;
  }
  for (let i = 0; i < a1.length; i++) {
    if (!pluginAssignmentsAreEqual(a1[i], a2[i])) {
      return false;
    }
  }
  return true;
}

export async function assignPlugins(assignments, initialAssignments) {
  const perform = async (idx = 0) => {
    if (idx >= assignments.length) {
      return;
    }
    const plugin = assignments[idx];
    const {id, pipelineId, toolId, version, sids = [], plugin: pluginData} = plugin;
    if (pluginData) {
      const data = {
        pipelineId,
        toolId,
        version,
        sids: sids.map((s) => ({
          principal: s.principal,
          name: s.name,
        })),
        plugin: {
          id: pluginData.id,
          name: pluginData.name,
          type: pluginData.type,
          path: pluginData.path,
        },
      };
      if (id <= 0) {
        const req = new AssignPlugin();
        await req.send(data);
        if (req.error) {
          throw new Error(req.error);
        }
      } else {
        const existing = initialAssignments.find((o) => o.id === id);
        if (!existing || !pluginAssignmentsAreEqual(existing, plugin)) {
          const req = new AssignPlugin();
          await req.send({
            id,
            ...data,
          });
          if (req.error) {
            throw new Error(req.error);
          }
        }
      }
    }
    await perform(idx + 1);
  };
  await perform();
}

export async function updatePluginsAssignments(assignments, initialAssignments = []) {
  const toRemove = initialAssignments.filter((i) => !assignments.some((pl) => pl.id === i.id));
  await unAssignPlugins(toRemove);
  await assignPlugins(assignments, initialAssignments);
}
