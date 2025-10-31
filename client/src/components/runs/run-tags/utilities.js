import preferences from '../../../models/preferences/PreferencesLoad';
import escapeRegExp from '../../../utils/escape-reg-exp';
import {
  CP_CAP_AUTOSCALE,
  CP_CAP_AUTOSCALE_WORKERS
} from '../../pipelines/launch/form/utilities/parameters';
import whoAmI from '../../../models/user/WhoAmI';
import fetchToolOSCached from '../../pipelines/launch/form/utilities/fetch-tool-os';
import RequiredLaunchTags from '../../special/metadata/special/required-launch-tags';

export async function fillUserTagsWithDefaultValues (
  tags = {},
  tagsTouched = [],
  visibleTags = [],
  userAttributes,
  launchPayload
) {
  const result = tags || {};
  let defaults = {};
  if (!userAttributes.loaded) {
    await userAttributes.refresh();
  }
  if (!userAttributes.hasAttribute(RequiredLaunchTags.metadataKey)) {
    return;
  }
  try {
    defaults = JSON.parse(
      userAttributes.getAttributeValue(RequiredLaunchTags.metadataKey) || '{}'
    ) || {};
  } catch (e) {
    console.error('Error parsing required launch tags defaults.', e);
    return;
  }
  const required = await getRequiredUserTags(launchPayload);
  visibleTags.forEach((tag) => {
    const current = (result || {})[tag];
    const hasValue = current !== undefined && String(current).trim().length > 0;
    const defaultValue = defaults[tag.toLowerCase()];
    const isTagGenerated = hasValue && !tagsTouched.includes(tag);
    if (!required.includes(tag) && result[tag] && isTagGenerated) {
      delete result[tag];
    }
    if (required.includes(tag) && !hasValue && defaultValue) {
      result[tag] = defaultValue;
    }
  });
  return result;
}

export async function getUserTagsValidationResult (tags, opts) {
  await preferences.fetchIfNeededOrWait();
  const {
    launchPayload,
    requiredTags,
    visibleTags
  } = opts || {};
  const required = requiredTags === undefined
    ? await getRequiredUserTags(launchPayload)
    : requiredTags;
  const visible = visibleTags === undefined
    ? await getVisibleUserTags(launchPayload)
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

async function prepareCheckPayload (launchPayload) {
  if (launchPayload) {
    const {
      dockerImage
    } = launchPayload;
    const [os] = await Promise.all([
      fetchToolOSCached(dockerImage),
      whoAmI.fetchIfNeededOrWait(),
      preferences.fetchIfNeededOrWait()
    ]);
    const user = whoAmI.value || undefined;
    return {
      payload: launchPayload,
      os,
      user
    };
  }
  return {};
}

export async function getVisibleUserTags (launchPayload) {
  if (launchPayload) {
    await preferences.fetchIfNeededOrWait();
    const opts = await prepareCheckPayload(launchPayload);
    const config = preferences ? preferences.uiRunsUserTags : [];
    const tagIsVisible = (tag) => userTagIsVisible(tag, opts);
    return config.filter(tagIsVisible).map((c) => c.tag);
  }
  return [];
}

export async function getRequiredUserTags (launchPayload) {
  if (launchPayload) {
    await preferences.fetchIfNeededOrWait();
    const opts = await prepareCheckPayload(launchPayload);
    const config = preferences ? preferences.uiRunsUserTags : [];
    const tagIsRequired = (tag) => userTagIsRequired(tag, opts);
    return config.filter(tagIsRequired).map((c) => c.tag);
  }
  return [];
}

export function filterVisibleTagsSync (tags, visible = []) {
  return Object.entries(tags || {})
    .filter(([tag]) => visible.includes(tag))
    .map(([tag, value]) => ({[tag]: value}))
    .reduce((acc, tag) => ({
      ...acc,
      ...tag
    }), {});
}

function checkTagConfigMatches (config, opts) {
  if (typeof config === 'boolean') {
    return config;
  }
  if (typeof config !== 'object') {
    return Boolean(config);
  }
  const {
    payload,
    os: currentOs,
    user: currentUser
  } = opts || {};
  const {
    userName: currentUserName,
    groups: userGroups = [],
    roles: userRoles = []
  } = currentUser || {};
  const currentUserGroups = new Set(
    (userRoles || [])
      .map((role) => role.name.toLowerCase())
      .concat((userGroups || []).map((group) => group.toLowerCase()))
  );
  const {
    // eslint-disable-next-line camelcase
    docker_image,
    dockerImage: _dockerImage = docker_image,
    // eslint-disable-next-line camelcase
    instance_type,
    instanceType: _instanceType = instance_type,
    // eslint-disable-next-line camelcase
    cluster_size,
    clusterSize: _clusterSize = cluster_size,
    os: _os,
    user,
    users: _users = user
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
  const users = asArray(_users);
  const os = asArray(_os);
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
      return true;
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
      return true;
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
      return true;
    }
    return maxClusterSize >= clusterSize;
  };
  const parseOSMask = (mask) => {
    if (/^all$/i.test(mask)) {
      return /.*/;
    }
    const regExpValue = mask
      .trim()
      .replace(/\./g, '\\.')
      .replace(/\*/g, '.*');
    return new RegExp(`^${regExpValue}$`, 'i');
  };
  const checkOs = () => {
    if (os.length === 0) {
      return true;
    }
    if (!currentOs) {
      return false;
    }
    return os.some((o) => parseOSMask(o).test(currentOs));
  };
  const checkUser = () => {
    if (users.length === 0) {
      return true;
    }
    return users.some((u) => currentUserName.toLowerCase() === u.toLowerCase()) ||
      users.some((u) => currentUserGroups.has(u.toLowerCase()));
  };
  return checkDockerImage() &&
    checkInstanceType() &&
    checkClusterSize() &&
    checkOs() &&
    checkUser();
}

function userTagIsVisible (tag, opts) {
  const {
    visible = true
  } = tag;
  return checkTagConfigMatches(visible, opts);
}

function userTagIsRequired (tag, opts) {
  const {
    required = false
  } = tag;
  return userTagIsVisible(tag, opts) && checkTagConfigMatches(required, opts);
}
