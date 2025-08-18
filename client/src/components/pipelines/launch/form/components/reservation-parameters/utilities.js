import {
  getParameterValue,
  isReservationRequestParameter
} from '../../utilities/parameter-utilities';
import {
  CP_CAP_REQUESTS_CPU,
  CP_CAP_REQUESTS_GPU,
  CP_CAP_REQUESTS_RAM
} from '../../utilities/parameters';
import preferences from '../../../../../../models/preferences/PreferencesLoad';
import escapeRegExp from '../../../../../../utils/escape-reg-exp';
import InstanceTypes from '../../../../../../models/utils/InstanceTypes';
import {ClusterNodeResources} from '../../../../../../models/cluster/NodeResources';

const instanceTypesRequest = new InstanceTypes();

export function readReservationParameters (parameters = {}) {
  const cpu = getParameterValue(parameters, CP_CAP_REQUESTS_CPU);
  const ram = getParameterValue(parameters, CP_CAP_REQUESTS_RAM);
  const gpu = getParameterValue(parameters, CP_CAP_REQUESTS_GPU);
  const asNumber = (o) => {
    if (typeof o === 'number') {
      return o;
    }
    if (typeof o === 'string' && !Number.isNaN(Number(o))) {
      return Number(o);
    }
    return 1;
  };
  return {
    cpu: asNumber(cpu),
    gpu: asNumber(gpu),
    ram: asNumber(ram)
  };
}

export function reservationParametersDiffer (a, b) {
  const {
    cpu: aCpu = 1,
    gpu: aGpu = 1,
    ram: aRam = 1
  } = a || {};
  const {
    cpu: bCpu = 1,
    gpu: bGpu = 1,
    ram: bRam = 1
  } = b || {};
  return aCpu !== bCpu || aGpu !== bGpu || aRam !== bRam;
}

export function findReservationParameterConfig (instanceType, prefs = preferences) {
  const {launchReservationParameters = {}} = prefs;
  if (instanceType && launchReservationParameters) {
    for (const [key, cfg] of Object.entries(launchReservationParameters || {})) {
      let l = key.replace(/\*/g, '____STAR____');
      l = escapeRegExp(l);
      l = l.replace(/____STAR____/g, '.*');
      const reg = new RegExp(`^${l}$`, 'i');
      if (reg.test(instanceType)) {
        return cfg;
      }
    }
  }
  return undefined;
}

export async function getReservationParametersConfig (instanceType) {
  await preferences.fetchIfNeededOrWait();
  return findReservationParameterConfig(instanceType, preferences);
}

export async function getInstanceResources (config) {
  const {
    kube_assign_policy: _kubeAssignPolicy = {},
    kubeAssignPolicy = _kubeAssignPolicy
  } = config || {};
  const {
    selector
  } = kubeAssignPolicy || {};
  const {
    label,
    value
  } = selector || {};
  if (!label || !value) {
    return [];
  }
  const req = new ClusterNodeResources();
  await req.send({[label]: value});
  if (req.error) {
    throw new Error(req.error);
  }
  return req.value || [];
}

export async function getInstanceType (instanceType) {
  if (!instanceType) {
    return undefined;
  }
  try {
    await instanceTypesRequest.fetchIfNeededOrWait();
    const values = instanceTypesRequest.value || [];
    return values.find((o) => o.name.toLowerCase() === instanceType.toLowerCase());
  } catch {
    return undefined;
  }
}

export const DEFAULT_RAM_REQUESTS_UNIT = 'GiB';
export const DEFAULT_RAM_REQUESTS_STEP = 1;

export function getInstanceResourcesRestrictions (
  options
) {
  const {
    config,
    instanceType: instanceTypeObj
  } = options || {};
  if (!config) {
    return {
      cpu: [0, 0],
      ram: [0, 0],
      gpu: [0, 0]
    };
  }
  let {
    gpu: maxGpu = Infinity,
    memory: maxRamRaw = Infinity,
    vcpu: maxCpu = Infinity
  } = instanceTypeObj || {};
  let {
    cpu_requests_reserved: cpuReserved = 1,
    gpu_requests_reserved: gpuReserved = 0,
    ram_requests_reserved: ramReservedRaw = 1,
    ram_requests_unit: ramRequestsUnit = DEFAULT_RAM_REQUESTS_UNIT
  } = config;
  const ramReserved = parseRAMRequest(ramReservedRaw, ramRequestsUnit);
  let maxRam = Number.isFinite(maxRamRaw)
    ? parseRAMRequest(maxRamRaw, ramRequestsUnit)
    : maxRamRaw;
  const subReserved = (value, reserved) => Number.isFinite(value) && Number.isFinite(reserved)
    ? Math.max(1, value - reserved)
    : value;
  maxCpu = subReserved(maxCpu, cpuReserved);
  maxGpu = subReserved(maxGpu, gpuReserved);
  maxRam = subReserved(maxRam, ramReserved);
  return {
    cpu: [Math.min(1, maxCpu), maxCpu],
    gpu: [Math.min(1, maxGpu), maxGpu],
    ram: [Math.min(1, maxRam), maxRam]
  };
}

export function correctReservationParameters (
  parameters,
  options
) {
  const {
    config
  } = options || {};
  if (!config) {
    return {
      cpu: 0,
      ram: 0,
      gpu: 0
    };
  }
  let {
    cpu_requests_enabled: cpuEnabled = false,
    gpu_requests_enabled: gpuEnabled = false,
    ram_requests_enabled: ramEnabled = false
  } = config;
  let {
    cpu = 1,
    gpu = 1,
    ram = 1
  } = parameters || {};
  const {
    cpu: cpuRange = [],
    gpu: gpuRange = [],
    ram: ramRange = []
  } = getInstanceResourcesRestrictions(options);
  const [cpuMin, cpuMax] = cpuRange;
  const [gpuMin, gpuMax] = gpuRange;
  const [ramMin, ramMax] = ramRange;
  cpu = Math.min(cpuMax, Math.max(cpuMin, cpu));
  gpu = Math.min(gpuMax, Math.max(gpuMin, gpu));
  ram = Math.min(ramMax, Math.max(ramMin, ram));
  cpuEnabled = cpuEnabled && cpu > 0;
  gpuEnabled = gpuEnabled && gpu > 0;
  ramEnabled = ramEnabled && ram > 0;
  return {
    cpu: cpuEnabled ? cpu : 0,
    ram: ramEnabled ? ram : 0,
    gpu: gpuEnabled ? gpu : 0
  };
}

export async function buildLaunchParametersFromReservationParameters (
  reservationParameters,
  instanceType,
  parameters = {},
  options = {}
) {
  const {
    applyAdditionalParameters = true
  } = options || {};
  const [
    instanceTypeObj = {},
    cfg
  ] = await Promise.all([
    getInstanceType(instanceType),
    getReservationParametersConfig(instanceType)
  ]);
  const result = {...(parameters || {})};
  for (const key of Object.keys(result || {})) {
    try {
      if (
        isReservationRequestParameter(key) ||
        [CP_CAP_REQUESTS_CPU, CP_CAP_REQUESTS_RAM, CP_CAP_REQUESTS_GPU].includes(key)) {
        delete result[key];
      }
    } catch (error) {
      console.warn(`error removing parameter ${key} from payload`, error);
    }
  }
  if (!cfg) {
    return {
      parameters: result
    };
  }
  let {
    cpu_requests_enabled: cpuEnabled = false,
    gpu_requests_enabled: gpuEnabled = false,
    ram_requests_enabled: ramEnabled = false,
    parameters: additionalParameters = {},
    kube_assign_policy: podAssignPolicy
  } = cfg;
  const {
    cpu = 1,
    gpu = 1,
    ram = 1
  } = correctReservationParameters(
    reservationParameters,
    {config: cfg, instanceType: instanceTypeObj}
  );
  cpuEnabled = cpuEnabled && cpu > 0;
  gpuEnabled = gpuEnabled && gpu > 0;
  ramEnabled = ramEnabled && ram > 0;
  if (cpuEnabled) {
    result[CP_CAP_REQUESTS_CPU] = {value: String(cpu)};
  }
  if (gpuEnabled) {
    result[CP_CAP_REQUESTS_GPU] = {value: String(gpu)};
  }
  if (ramEnabled) {
    result[CP_CAP_REQUESTS_RAM] = {
      value: ram
    };
  }
  if (applyAdditionalParameters && (cpuEnabled || gpuEnabled || ramEnabled)) {
    for (const [key, value] of Object.entries(additionalParameters || {})) {
      result[key] = {value};
    }
  }
  return {parameters: result, podAssignPolicy};
}

export async function ensureValidReservationParametersForLaunchPayloads (payloads) {
  const reservationRequests = Promise.all(
    payloads.map((p) => buildLaunchParametersFromReservationParameters(
      readReservationParameters(p.params),
      p.instanceType,
      p.params
    ))
  );
  for (let i = 0; i < reservationRequests.length; i++) {
    const {
      parameters: appliedReservationParameters,
      podAssignPolicy
    } = reservationRequests[i];
    payloads[i].params = appliedReservationParameters;
    payloads[i].podAssignPolicy = podAssignPolicy;
  }
}

const binaryUnits = {
  Ki: 1024,
  Mi: 1024 ** 2,
  Gi: 1024 ** 3,
  Ti: 1024 ** 4,
  Pi: 1024 ** 5,
  Ei: 1024 ** 6
};

const binaryUnitsSorted = Object.entries(binaryUnits)
  .sort((a, b) => b[1] - a[1]);

const decimalUnits = {
  K: 1000,
  k: 1000,
  m: 0.001,
  M: 1000 ** 2,
  G: 1000 ** 3,
  T: 1000 ** 4,
  P: 1000 ** 5,
  E: 1000 ** 6,
  '': 1
};

const decimalUnitsSorted = Object.entries(decimalUnits)
  .sort((a, b) => b[1] - a[1]);

/**
 * Transforms k8s RAM request (passed as a string) to bytes, i.e. "1.5Gi" -> 1610612736 bytes, etc.
 * @param {string} request
 * @param {string} [defaultUnit]
 * @returns {number}
 */
function transformK8sRAMRequestStringToBytes (request, defaultUnit = DEFAULT_RAM_REQUESTS_UNIT) {
  if (!request) {
    // undefined, null, '' or 0
    return 0;
  }
  if (typeof request === 'number' || !Number.isNaN(Number(request))) {
    return transformK8sRAMRequestStringToBytes(`${request}${defaultUnit}`);
  }
  const validUnits = Object.keys(binaryUnits)
    .concat(Object.keys(decimalUnits))
    .concat('').join('|');
  const regex = new RegExp(`^(.*\\d)\\s*(${validUnits})B?$`);
  const match = request.trim().match(regex);

  // eslint-disable-next-line max-len
  console.assert(match, `Wrong RAM request input "${request}", check https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#meaning-of-memory`);

  if (!match) {
    return 0;
  }

  const value = parseFloat(match[1]);
  const unit = match[2] || '';

  const multiplier = binaryUnits[unit] || decimalUnits[unit] || 1;

  return Math.max(0, Math.floor(value * multiplier));
}

/**
 * Transforms bytes to K8S RAM request value (string)
 * @param {number} bytes
 * @param {{decimal?: boolean, appendBytesLetter?: boolean, appendSuffix?: boolean; unit?: string}} [options]
 * @returns {string}
 */
export function transformBytesToK8sRAMRequest (bytes, options) {
  const {
    decimal = false,
    appendBytesLetter = false,
    appendSuffix = true,
    unit
  } = options || {};
  if (!bytes || typeof bytes !== 'number' || bytes < 0) {
    return '0';
  }

  const unitCorrected = unit && unit.toLowerCase().endsWith('b') ? unit.slice(0, -1) : unit;

  const decimalUnit = unitCorrected ? decimalUnits[unitCorrected] : undefined;
  const binaryUnit = unitCorrected ? binaryUnits[unitCorrected] : undefined;

  if (decimalUnit || binaryUnit) {
    return transformBytesToK8sRAMUnitRequest(
      bytes,
      {
        unit: appendSuffix ? unit : '',
        factor: decimalUnit || binaryUnit
      }
    );
  }

  const units = decimal ? decimalUnitsSorted : binaryUnitsSorted;

  for (const [suffix, factor] of units) {
    if (bytes >= factor) {
      return transformBytesToK8sRAMUnitRequest(
        bytes,
        {
          unit: appendSuffix ? `${suffix}${appendBytesLetter ? 'B' : ''}` : '',
          factor
        }
      );
    }
  }

  return transformBytesToK8sRAMUnitRequest(
    bytes,
    {
      unit: appendSuffix && appendBytesLetter ? 'B' : ''
    }
  );
}

/**
 * Transforms bytes to K8S RAM request value (string)
 * @param {number} bytes
 * @param {{unit?: string, factor?: number}} [options]
 * @returns {string}
 */
function transformBytesToK8sRAMUnitRequest (bytes, options) {
  const {
    unit = '',
    factor = 1
  } = options || {};
  const value = bytes / factor;
  return `${parseFloat(value.toFixed(2))}${unit}`;
}

// eslint-disable-next-line no-unused-vars
function testTransformK8sRAMRequestStringToBytes () {
  const tests = [
    ['1Ki', 1024],
    ['1Mi', 1024 ** 2],
    ['1Gi', 1024 ** 3],
    ['1Ti', 1024 ** 4],
    ['1Pi', 1024 ** 5],
    ['1Ei', 1024 ** 6],

    ['1K', 1000],
    ['1M', 1000 ** 2],
    ['1G', 1000 ** 3],
    ['1T', 1000 ** 4],
    ['1P', 1000 ** 5],
    ['1E', 1000 ** 6],

    ['1.5Gi', Math.floor(1.5 * 1024 ** 3)],
    ['2.75Mi', Math.floor(2.75 * 1024 ** 2)],
    ['0.5G', Math.floor(0.5 * 1000 ** 3)],
    ['100', 100] // no suffix: treated as bytes
  ];
  const invalidTests = [
    ['400m', 0], // invalid unit — interpreted as 0.4 bytes, but we choose to return 0
    ['1500m', 1],
    ['abc', 0], // invalid string
    ['', 0], // empty string
    [null, 0] // null input
  ];
  console.groupCollapsed('transformK8sRAMRequestStringToBytes test');
  for (const [input, expected] of tests.concat(invalidTests)) {
    const result = transformK8sRAMRequestStringToBytes(input);
    console.log(input, 'expected', expected, 'got', result);
    console.assert(
      result === expected,
      `Failed test transformK8sRAMRequestStringToBytes: ${input} => ${result}, expected ${expected}`
    );
  }
  console.groupEnd();
  const tests2 = [
    ['1Ki', 1024],
    ['1Mi', 1024 ** 2],
    ['1Gi', 1024 ** 3],
    ['1Ti', 1024 ** 4],
    ['1Pi', 1024 ** 5],
    ['1Ei', 1024 ** 6],

    ['1K', 1000],
    ['1M', 1000 ** 2],
    ['1G', 1000 ** 3],
    ['1T', 1000 ** 4],
    ['1P', 1000 ** 5],
    ['1E', 1000 ** 6],

    ['1.5Gi', Math.floor(1.5 * 1024 ** 3)],
    ['2.75Mi', Math.floor(2.75 * 1024 ** 2)],
    ['550M', Math.floor(0.55 * 1000 ** 3)],
    ['100', 100] // no suffix: treated as bytes
  ];
  console.groupCollapsed('transformBytesToK8sRAMRequest test');
  for (const [expected, input] of tests2) {
    const result = transformBytesToK8sRAMRequest(input, {decimal: !expected.includes('i')});
    console.log(input, 'expected', expected, 'got', result);
    console.assert(
      result === expected,
      `Failed test transformBytesToK8sRAMRequest: ${input} => ${result}, expected ${expected}`
    );
  }
  console.groupEnd();
}

/* eslint-disable max-len */
/**
 * Transforms k8s RAM request to bytes,
 * [details](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#meaning-of-memory)
 * @param {number|string} request
 * @param {string} [defaultUnit]
 * @returns {number}
 */
/* eslint-enable max-len */
export function parseRAMRequest (request = 0, defaultUnit = DEFAULT_RAM_REQUESTS_UNIT) {
  if (request === undefined || request === null) {
    return 0;
  }
  if (typeof request === 'number' || !Number.isNaN(Number(request))) {
    return transformK8sRAMRequestStringToBytes(`${request}${defaultUnit}`);
  }
  if (typeof request === 'string') {
    return transformK8sRAMRequestStringToBytes(request);
  }
  return 0;
}

export function getNodeAvailability (nodeResources) {
  const {
    total = {},
    used = {}
  } = nodeResources;
  const {
    cpu: totalCPU = 0,
    gpu: totalGPU = 0,
    memory: totalRAM = 0
  } = total || {};
  const {
    cpu: usedCPU = 0,
    gpu: usedGPU = 0,
    memory: usedRAM = 0
  } = used || {};
  const availableCPU = Math.max(0, totalCPU - usedCPU);
  const availableGPU = Math.max(0, totalGPU - usedGPU);
  const availableRAM = Math.max(0, totalRAM - usedRAM);
  return {
    ...(nodeResources || {}),
    available: {
      cpu: availableCPU,
      gpu: availableGPU,
      memory: availableRAM
    }
  };
}

export function getInstanceResourcesAvailability (resources, requests, config) {
  const nodesAvailability = (resources || []).map(getNodeAvailability);
  const {
    cpu_requests_enabled: cpuRequestsEnabled = false,
    gpu_requests_enabled: gpuRequestsEnabled = false,
    ram_requests_enabled: ramRequestsEnabled = false
  } = config || {};
  const {
    cpu: cpuRequest = 0,
    gpu: gpuRequest = 0,
    ram: ramRequest = 0
  } = requests || {};
  const getNodeScore = (node) => {
    const {
      available = {},
      total = {}
    } = node || {};
    const {
      cpu: cpuAvailable = 0,
      gpu: gpuAvailable = 0,
      memory: ramAvailable = 0
    } = available || {};
    const {
      cpu: cpuTotal = 0,
      gpu: gpuTotal = 0,
      memory: ramTotal = 0
    } = total || {};
    const cpuFits = !cpuRequestsEnabled || cpuAvailable >= cpuRequest;
    const gpuFits = !gpuRequestsEnabled || gpuAvailable >= gpuRequest;
    const ramFits = !ramRequestsEnabled || ramAvailable >= ramRequest;
    const fits = cpuFits && gpuFits && ramFits;
    const maxRequest = {
      cpu: cpuRequestsEnabled ? Math.max(1, (cpuFits ? cpuRequest : cpuAvailable)) : 0,
      gpu: gpuRequestsEnabled ? Math.max(1, (gpuFits ? gpuRequest : gpuAvailable)) : 0,
      ram: ramRequestsEnabled ? Math.max(1, (ramFits ? ramRequest : ramAvailable)) : 0
    };
    /**
     * @param {{total: number; request: number; available: number; fits: boolean}} opts
     * @returns {number} - score: 0 best fit; 1 - worse fit
     */
    const buildScore = (opts) => {
      const {
        request,
        available,
        total,
        fits
      } = opts;
      return (fits ? 0 : 1) + (total > 0 ? Math.abs(available - request) / total : 1);
    };
    const cpuScore = buildScore({
      request: cpuRequest,
      available: cpuAvailable,
      total: cpuTotal,
      fits: cpuFits
    });
    const gpuScore = buildScore({
      request: gpuRequest,
      available: gpuAvailable,
      total: gpuTotal,
      fits: gpuFits
    });
    const ramScore = buildScore({
      request: ramRequest,
      available: ramAvailable,
      total: ramTotal,
      fits: ramFits
    });
    const score = cpuScore + gpuScore + ramScore;
    return {
      ...node,
      score,
      fits,
      fitsDetails: {
        cpu: cpuFits,
        gpu: gpuFits,
        ram: ramFits
      },
      best: maxRequest,
      request: requests,
      enabled: {
        cpu: cpuRequestsEnabled,
        gpu: gpuRequestsEnabled,
        ram: ramRequestsEnabled
      },
      scores: {
        cpu: cpuScore,
        gpu: gpuScore,
        ram: ramScore
      }
    };
  };
  const details = nodesAvailability.map(getNodeScore).sort((a, b) => a.score - b.score);
  const fits = details.filter((o) => o.fits);
  const bestFit = fits.length > 0 ? fits[0] : undefined;
  return {
    nodes: details,
    best: bestFit
  };
}
