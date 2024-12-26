import React from 'react';
import {getParameterNumberValue, getParameterValue} from '../parameter-utilities';
import {
  CP_CAP_SHARE_FS_DEPLOYMENT_TYPE, CP_CAP_SHARE_FS_IOPS,
  CP_CAP_SHARE_FS_SIZE,
  CP_CAP_SHARE_FS_THROUGHPUT,
  CP_CAP_SHARE_FS_TYPE
} from '../parameters';

export const ShareFsType = {
  lfs: 'lfs',
  lustre: 'lustre'
};

export const ShareFsTypeName = {
  [ShareFsType.lfs]: 'LFS',
  [ShareFsType.lustre]: 'LustreFS'
};

export const LustreFSDeploymentTypes = {
  scratch1: 'SCRATCH_1',
  scratch2: 'SCRATCH_2',
  persistent1: 'PERSISTENT_1',
  persistent2: 'PERSISTENT_2'
};

export const CP_CAP_FS_PARAMETERS = [
  CP_CAP_SHARE_FS_TYPE,
  CP_CAP_SHARE_FS_DEPLOYMENT_TYPE,
  CP_CAP_SHARE_FS_SIZE,
  CP_CAP_SHARE_FS_THROUGHPUT,
  CP_CAP_SHARE_FS_IOPS
];

export const CP_CAP_FS_PARAMETERS_HINTS = {
  [CP_CAP_SHARE_FS_TYPE]: (
    <div>
      Defines which file system shall be used as shared for cluster runs
    </div>
  ),
  [CP_CAP_SHARE_FS_DEPLOYMENT_TYPE]: (
    <div>
      Allows to specify shared file deployment type (supported for Lustre FS only)
    </div>
  ),
  [CP_CAP_SHARE_FS_SIZE]: (
    <div>
      Allows to specify shared file system size (supported for Lustre FS only)
    </div>
  ),
  [CP_CAP_SHARE_FS_THROUGHPUT]: (
    <div>
      Allows to specify shared file system throughput (supported for persistent Lustre FS only)
    </div>
  ),
  [CP_CAP_SHARE_FS_IOPS]: (
    <div>
      Allows to specify shared file system IOPS (supported for persistent Lustre FS only).
    </div>
  )
};

export function getShareFsType (parameters) {
  const fsType = getParameterValue(parameters, CP_CAP_SHARE_FS_TYPE, ShareFsType.lfs) || '';
  if (fsType && fsType.toLowerCase() === ShareFsType.lustre.toLowerCase()) {
    return ShareFsType.lustre;
  }
  return ShareFsType.lfs;
}

export function getDeploymentType (parameters) {
  const fsType = getShareFsType(parameters);
  if (fsType === ShareFsType.lustre) {
    const defaultType = getShareFsDeploymentTypeDefault(fsType);
    const deploymentType = getParameterValue(parameters, CP_CAP_SHARE_FS_DEPLOYMENT_TYPE, defaultType) || defaultType;
    for (const o of Object.values(LustreFSDeploymentTypes)) {
      if (o.toLowerCase() === deploymentType.toLowerCase()) {
        return o;
      }
    }
    return undefined;
  }
  return undefined;
}

export function getShareFsVolume (parameters) {
  const fsType = getShareFsType(parameters);
  if (fsType === ShareFsType.lustre) {
    return getParameterNumberValue(parameters, CP_CAP_SHARE_FS_SIZE, 1200);
  }
  return undefined;
}

export function getShareFsThroughput (parameters) {
  const fsType = getShareFsType(parameters);
  if (fsType === ShareFsType.lustre) {
    const defaultValue = getShareFsThroughputDefault(getDeploymentType(parameters));
    return getParameterNumberValue(parameters, CP_CAP_SHARE_FS_THROUGHPUT, defaultValue);
  }
  return undefined;
}

export function getShareFsIOPS (parameters) {
  const fsType = getShareFsType(parameters);
  if (fsType === ShareFsType.lustre) {
    const defaultValue = getShareFsIOPSDefault(getDeploymentType(parameters));
    return getParameterNumberValue(parameters, CP_CAP_SHARE_FS_IOPS, defaultValue);
  }
  return undefined;
}

export function getShareFsDeploymentTypeOptions (fsType) {
  switch (fsType) {
    case ShareFsType.lustre:
      return Object.values(LustreFSDeploymentTypes);
    default:
      return [];
  }
}

export function getShareFsDeploymentTypeDefault (fsType) {
  switch (fsType) {
    case ShareFsType.lustre:
      return LustreFSDeploymentTypes.scratch2;
    default:
      return undefined;
  }
}

export function getShareFsThroughputDefault (deploymentType) {
  switch (deploymentType) {
    case LustreFSDeploymentTypes.persistent1:
      return 100;
    case LustreFSDeploymentTypes.persistent2:
      return 500;
    default:
      return undefined;
  }
}

export function getShareFsThroughputOptions (deploymentType) {
  switch (deploymentType) {
    case LustreFSDeploymentTypes.persistent1:
      return [50, 100, 200];
    case LustreFSDeploymentTypes.persistent2:
      return [125, 250, 500, 1000];
    default:
      return [];
  }
}

export function getShareFsIOPSDefault (deploymentType) {
  return getShareFsIOPSOptions(deploymentType)[0];
}

export function getShareFsIOPSOptions (deploymentType) {
  switch (deploymentType) {
    case LustreFSDeploymentTypes.persistent1:
    case LustreFSDeploymentTypes.persistent2:
      return [
        1500,
        3000,
        6000,
        12000,
        24000,
        36000,
        48000,
        60000,
        72000,
        84000,
        96000,
        108000,
        120000,
        132000,
        144000,
        156000,
        168000,
        180000,
        192000
      ];
    default:
      return [];
  }
}

function asNumber(o) {
  if (o === undefined || Number.isNaN(Number(o))) {
    return undefined;
  }
  return Number(o);
}

export function validateFsDeploymentType (config) {
  const {
    fsType = ShareFsType.lfs,
    deploymentType: original,
  } = config || {};
  let deploymentType = original;
  let error;
  if (fsType === ShareFsType.lfs) {
    return {
      corrected: undefined,
      value: original,
      error
    };
  }
  const available = getShareFsDeploymentTypeOptions(fsType);
  if (deploymentType === undefined || !available.includes(deploymentType)) {
    deploymentType = getShareFsDeploymentTypeDefault(fsType);
  }
  return {
    corrected: deploymentType,
    value: original,
    error
  };
}

export function validateFsVolume (config) {
  const {
    fsType = ShareFsType.lfs,
    volume: original,
  } = config || {};
  let volume = asNumber(original);
  let error;
  if (fsType === ShareFsType.lfs) {
    return {
      corrected: volume,
      value: original,
      error
    };
  }
  if (volume === undefined || (volume % 1200 !== 0) || volume < 1200) {
    volume = 1200;
    error = 'Volume must be a multiple of 1200 (e.g., 1200, 2400, 3600, ...)'
  }
  return {
    corrected: volume,
    value: original,
    error
  };
}

export function validateFsThroughput (config) {
  const {
    fsType = ShareFsType.lfs,
    deploymentType,
    throughput: original,
  } = config || {};
  let throughput = asNumber(original);
  let error;
  if (fsType === ShareFsType.lfs) {
    return {
      corrected: throughput,
      value: original,
      error
    };
  }
  const throughputOptions = getShareFsThroughputOptions(deploymentType);
  if (!throughputOptions.includes(throughput)) {
    throughput = getShareFsThroughputDefault(deploymentType);
  }
  return {
    corrected: throughput,
    value: original,
    error
  };
}

export function validateFsIops (config) {
  const {
    fsType = ShareFsType.lfs,
    deploymentType,
    iops: original,
  } = config || {};
  let iops = asNumber(original);
  let error;
  if (fsType === ShareFsType.lfs) {
    return {
      corrected: iops,
      value: original,
      error
    };
  }
  const iopsOptinos = getShareFsIOPSOptions(deploymentType);
  if (!iopsOptinos.includes(iops)) {
    iops = getShareFsIOPSDefault(deploymentType);
  }
  return {
    corrected: iops,
    value: original,
    error
  };
}

export function getDefaultConfig () {
  return {
    fsType: ShareFsType.lfs,
    deploymentType: undefined,
    volume: undefined,
    throughput: undefined,
    iops: undefined,
  };
}

export function normalizeFsConfig (config) {
  const {
    fsType = ShareFsType.lfs,
  } = config || {};
  if (fsType === ShareFsType.lfs) {
    return {
      fsType,
      deploymentType: undefined,
      volume: undefined,
      throughput: undefined,
      iops: undefined,
    };
  }
  const { corrected: deploymentType } = validateFsDeploymentType(config);
  const { corrected: volume } = validateFsVolume(config);
  const { corrected: throughput} = validateFsThroughput(config);
  const { corrected: iops} = validateFsIops(config);
  return {
    fsType,
    deploymentType,
    volume,
    throughput,
    iops,
  };
}

export function getFsConfigFromParameters (parameters) {
  const fsType = getShareFsType(parameters);
  const volume = getShareFsVolume(parameters);
  const deploymentType = getDeploymentType(parameters);
  const throughput = getShareFsThroughput(parameters);
  const iops = getShareFsIOPS(parameters);
  return normalizeFsConfig({
    fsType,
    deploymentType,
    volume,
    throughput,
    iops,
  });
}

export function getParametersFromFsConfig (fsConfig, parameters) {
  const result = parameters || {};
  for (const param of CP_CAP_FS_PARAMETERS) {
    if (result.hasOwnProperty(param)) {
      delete result[param];
    }
  }
  if (fsConfig) {
    const {
      fsType,
      deploymentType,
      volume,
      throughput,
      iops
    } = normalizeFsConfig(fsConfig);
    const addParameter = (param, value) => {
      if (value !== undefined) {
        result[param] = {
          type: 'string',
          value: value.toString()
        };
      }
    }
    addParameter(CP_CAP_SHARE_FS_TYPE, fsType);
    addParameter(CP_CAP_SHARE_FS_DEPLOYMENT_TYPE, deploymentType);
    addParameter(CP_CAP_SHARE_FS_SIZE, volume);
    addParameter(CP_CAP_SHARE_FS_THROUGHPUT, throughput);
    addParameter(CP_CAP_SHARE_FS_IOPS, iops);
  }
  return result;
}

export function getTooltParametersFromFsConfig (fsConfig, parameters) {
  const result = (parameters || []).filter((p) => !CP_CAP_FS_PARAMETERS.includes(p.name));
  if (fsConfig) {
    const {
      fsType,
      deploymentType,
      volume,
      throughput,
      iops
    } = normalizeFsConfig(fsConfig);
    const addParameter = (param, value) => {
      if (value !== undefined) {
        result.push({
          name: param,
          type: 'string',
          value: value.toString()
        });
      }
    }
    addParameter(CP_CAP_SHARE_FS_TYPE, fsType);
    addParameter(CP_CAP_SHARE_FS_DEPLOYMENT_TYPE, deploymentType);
    addParameter(CP_CAP_SHARE_FS_SIZE, volume);
    addParameter(CP_CAP_SHARE_FS_THROUGHPUT, throughput);
    addParameter(CP_CAP_SHARE_FS_IOPS, iops);
  }
  return result;
}

export function fsConfigsAreEqual(config1, config2) {
  const {
    fsType: fsType1 = ShareFsType.lfs,
    deploymentType: deploymentType1,
    volume: volume1,
    throughput: throughput1,
    iops: iops1
  } = config1 || {};
  const {
    fsType: fsType2 = ShareFsType.lfs,
    deploymentType: deploymentType2,
    volume: volume2,
    throughput: throughput2,
    iops: iops2
  } = config2 || {};
  return fsType1 === fsType2 &&
    deploymentType1 === deploymentType2 &&
    volume1 === volume2 &&
    throughput1 === throughput2 &&
    iops1 === iops2;
}
