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

export async function getReservationParametersConfig (instanceType) {
  await preferences.fetchIfNeededOrWait();
  const {launchReservationParameters = {}} = preferences;
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
  const {
    gpu: maxGpu = Infinity,
    memory: maxRam = Infinity,
    vcpu: maxCpu = Infinity
  } = instanceTypeObj || {};
  let {
    cpu_requests_enabled: cpuEnabled = false,
    gpu_requests_enabled: gpuEnabled = false,
    ram_requests_enabled: ramEnabled = false,
    parameters: additionalParameters = {},
    kube_assign_policy: podAssignPolicy
  } = cfg;
  let {
    cpu = 1,
    gpu = 1,
    ram = 1
  } = reservationParameters;
  cpu = Math.min(maxCpu, Math.max(cpu, 1));
  gpu = Math.min(maxGpu, Math.max(gpu, 1));
  ram = Math.min(maxRam, Math.max(ram, 1));
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
    result[CP_CAP_REQUESTS_RAM] = {value: String(ram)};
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
