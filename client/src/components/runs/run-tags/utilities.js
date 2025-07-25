import preferences from '../../../models/preferences/PreferencesLoad';
import escapeRegExp from '../../../utils/escape-reg-exp';
import {
  CP_CAP_AUTOSCALE,
  CP_CAP_AUTOSCALE_WORKERS
} from '../../pipelines/launch/form/utilities/parameters';

export async function getUserTagsValidationResult (tags, opts) {
  await preferences.fetchIfNeededOrWait();
  return getUserTagsValidationResultSync(tags, opts);
}

export async function getVisibleUserTags (launchPayload) {
  await preferences.fetchIfNeededOrWait();
  return getVisibleUserTagsSync(launchPayload);
}

export async function getRequiredUserTags (launchPayload) {
  await preferences.fetchIfNeededOrWait();
  return getRequiredUserTagsSync(launchPayload);
}

export async function filterVisibleTags (tags, opts) {
  await preferences.fetchIfNeededOrWait();
  return filterVisibleTagsSync(tags, opts);
}

export function filterVisibleTagsSync (tags, opts) {
  const {
    launchPayload,
    visibleTags
  } = opts || {};
  const visible = visibleTags === undefined
    ? getVisibleUserTagsSync(launchPayload)
    : visibleTags;
  return Object.entries(tags || {})
    .filter(([tag]) => visible.includes(tag))
    .map(([tag, value]) => ({[tag]: value}))
    .reduce((acc, tag) => ({
      ...acc,
      ...tag
    }), {});
}

export function getUserTagsValidationResultSync (tags, opts) {
  const {
    launchPayload,
    requiredTags,
    visibleTags
  } = opts || {};
  const required = requiredTags === undefined
    ? getRequiredUserTagsSync(launchPayload)
    : requiredTags;
  const visible = visibleTags === undefined
    ? getVisibleUserTagsSync(launchPayload)
    : visibleTags;
  const result = [];
  const runUserTags = preferences.uiRunsUserTags || [];
  for (const requiredTag of required) {
    if (!visible.includes(requiredTag)) {
      continue;
    }
    const value = (tags || {})[requiredTag];
    if (value === undefined || value.trim().length === 0) {
      const tag = runUserTags.find((t) => t.tag === requiredTag);
      const displayName = tag ? (tag.display || tag.tag || requiredTag) : requiredTag;
      result.push({
        tag: requiredTag,
        error: `${displayName} is required`
      });
    }
  }
  return result;
}

function checkTagConfigMatches (config, payload) {
  if (typeof config === 'boolean') {
    return config;
  }
  if (typeof config !== 'object') {
    return Boolean(config);
  }
  const {
    // eslint-disable-next-line camelcase
    docker_image,
    dockerImage: _dockerImage = docker_image,
    // eslint-disable-next-line camelcase
    instance_type,
    instanceType: _instanceType = instance_type,
    // eslint-disable-next-line camelcase
    cluster_size,
    clusterSize: _clusterSize = cluster_size
  } = config || {};
  const asArray = (o) => {
    if (o === undefined) {
      return [];
    }
    if (typeof o === 'string') {
      return o.split(',').map((oo) => oo.trim()).filter((oo) => oo.length > 0);
    }
    return [];
  };
  const dockerImage = asArray(_dockerImage);
  const instanceType = asArray(_instanceType);
  const clusterSize = _clusterSize && !Number.isNaN(Number(_clusterSize))
    ? Number(_clusterSize)
    : Infinity;
  const {
    dockerImage: pDockerImage,
    instanceType: pInstanceType,
    nodeCount: pNodeCount,
    params = {}
  } = payload;
  const getParameterValue = (parameterName) => {
    return (params[parameterName] || {}).value;
  };
  const parameterIsEnabled = (parameterName) => {
    const v = getParameterValue(parameterName);
    return v !== undefined && v !== null && String(v).toLowerCase().trim() === 'true';
  };
  const isAutoScaled = parameterIsEnabled(CP_CAP_AUTOSCALE);
  const maxAutoScaleNodesCountRaw = getParameterValue(CP_CAP_AUTOSCALE_WORKERS);
  const maxAutoScaleNodesCount = isAutoScaled &&
  maxAutoScaleNodesCountRaw &&
  !Number.isNaN(Number(maxAutoScaleNodesCountRaw))
    ? Number(maxAutoScaleNodesCountRaw)
    : undefined;
  const match = (criteria, s) => {
    const o = escapeRegExp(criteria.replace(/\*/g, '___STAR___'))
      .replace(/(___STAR___)/g, '.*');
    const r = new RegExp(`^${o}$`, 'i');
    return r.test(s);
  };
  const checkDockerImage = () => {
    if (dockerImage.length === 0 || pDockerImage === undefined) {
      return false;
    }
    const checkSingleDockerImage = (di) => {
      const [i, v = '*'] = di.split(':');
      const criteria = `${i}:${v}`;
      const [r, g, ii] = pDockerImage.split('/');
      const [image, version = 'latest'] = ii.split(':');
      const candidates = [
        image, // i.e., "ubuntu"
        `${image}:${version}`, // i.e., "ubuntu:20.04"
        `${g}/${image}`, // i.e., "library/ubuntu"
        `${g}/${image}:${version}`, // i.e., "library/ubuntu:20.04"
        `${r}/${g}/${image}`, // i.e., "registry/library/ubuntu"
        `${r}/${g}/${image}:${version}` // i.e., "registry/library/ubuntu:20.04"
      ];
      return candidates.some((candidate) => match(criteria, candidate));
    };
    return dockerImage.some(checkSingleDockerImage);
  };
  const checkInstanceType = () => {
    if (instanceType.length === 0) {
      return false;
    }
    const checkSingleInstanceType = (iType) => {
      return match(iType, pInstanceType);
    };
    return instanceType.some(checkSingleInstanceType);
  };
  const maxClusterSize = Math.max(
    pNodeCount || 0,
    (pNodeCount || 0) + (maxAutoScaleNodesCount || 0)
  );
  const checkClusterSize = () => {
    if (!maxClusterSize || !Number.isFinite(clusterSize)) {
      return false;
    }
    console.log('check cluster size:', maxClusterSize, 'max allowed', clusterSize);
    return maxClusterSize >= clusterSize;
  };
  return checkDockerImage() || checkInstanceType() || checkClusterSize();
}

export function userTagIsVisible (tag, launchPayload) {
  const {
    visible = true
  } = tag;
  return checkTagConfigMatches(visible, launchPayload);
}

export function userTagIsRequired (tag, launchPayload) {
  const {
    required = false
  } = tag;
  return userTagIsVisible(tag, launchPayload) && checkTagConfigMatches(required, launchPayload);
}

export function getVisibleUserTagsSync (launchPayload) {
  if (launchPayload) {
    const config = preferences ? preferences.uiRunsUserTags : [];
    const tagIsVisible = (tag) => userTagIsVisible(tag, launchPayload);
    return config.filter(tagIsVisible).map((c) => c.tag);
  }
  return [];
}

export function getRequiredUserTagsSync (launchPayload) {
  if (launchPayload) {
    const config = preferences ? preferences.uiRunsUserTags : [];
    const tagIsRequired = (tag) => userTagIsRequired(tag, launchPayload);
    return config.filter(tagIsRequired).map((c) => c.tag);
  }
  return [];
}
