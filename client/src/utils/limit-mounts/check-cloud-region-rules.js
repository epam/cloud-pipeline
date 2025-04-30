import cloudRegions from '../../models/cloudRegions/CloudRegions';

/**
 * @typedef {Object} CheckMountRulesOptions
 * @property {string|number} cloudRegionId
 */

/**
 *
 * @param {CheckMountRulesOptions} options
 * @returns {Promise<void>}
 */
export async function checkMountRules (
  options
) {
  const {
    cloudRegionId
  } = options;
  try {
    await cloudRegions.fetchIfNeededOrWait();
    const region = cloudRegions.getRegion(Number(cloudRegionId));
    if (!region) {
      throw new Error(`region #${cloudRegionId} not found`);
    }
    console.log(region);
  } catch (error) {
    console.error('error checking mount rules for storages:', error);
  }
}

const ruleNone = 'NONE';
const ruleSameCloud = 'CLOUD';
const ruleSameRegion = 'REGION';
const ruleAll = 'ALL';

/**
 * @typedef {Object} StorageRegion
 * @property {string} regionId
 * @property {string} provider
 * @property {string} mountRule
 */

/**
 * @param {Object} storage
 * @param {Object} regionsMap
 * @param {Object} nfsMountsMap
 * @returns {StorageRegion|undefined}
 */
function getStorageRegion (storage, regionsMap, nfsMountsMap) {
  const {type, regionId, fileShareMountId} = storage;
  const region = (() => {
    if (/^nfs$/i.test(type)) {
      return nfsMountsMap[fileShareMountId];
    }
    return regionsMap[regionId];
  })();
  return region ? {
    regionId: region.regionId,
    provider: region.provider,
    mountRule: /^nfs$/i.test(type) ? region.mountFileStorageRule : region.mountStorageRule
  } : undefined;
}

function getCacheKey (storages, region, regions) {
  if (!region) {
    return undefined;
  }
  const getRegionKey = (r) => {
    const {id, provider, regionId, fileShareMounts = []} = r;
    return [id, provider, regionId, ...fileShareMounts.map((f) => f.id)].join('/');
  };
  const getStorageKey = (s) => {
    const {id, regionId = '', fileShareMountId = ''} = s;
    return [id, regionId, fileShareMountId].join('/');
  };
  const storagesKey = (storages || []).map(getStorageKey).join(',');
  const regionsKey = (regions || []).map(getRegionKey).join(',');
  const regionKey = getRegionKey(region);
  return [regionKey, regionsKey, storagesKey].join('|');
}

const cached = new Set();

export function getAllowedStoragesForCloudRegion (storages, region, regions) {
  if (!region) {
    return storages;
  }
  const key = getCacheKey(storages, region, regions);
  const verbose = !cached.has(key);
  cached.add(key);
  const {
    provider: currentProvider = '',
    regionId: currentRegionId = ''
  } = region;
  const ensuredStorages = storages || [];
  const ensuredRegions = regions || [];
  if (verbose) {
    console.groupCollapsed(`get allowed storages for cloud region ${currentRegionId} (${currentProvider})`);
  }
  let filtered = [];
  try {
    const regionsMap = ensuredRegions.reduce((acc, r) => ({
      ...acc,
      [r.id]: r
    }), {});
    const nfsMounts = ensuredRegions.reduce((acc, r) => ({
      ...acc,
      ...(r.fileShareMounts || []).reduce((a, m) => ({...a, [m.id]: r}), {})
    }), {});
    if (verbose) {
      console.log('region', currentProvider, currentRegionId);
      console.log('regions', regionsMap);
      console.log('file share mounts:', nfsMounts);
    }

    filtered = ensuredStorages.filter((storage) => {
      const {
        mountRule = ruleAll,
        regionId = '',
        provider = ''
      } = getStorageRegion(storage, regionsMap, nfsMounts) || {};
      const allowed = (() => {
        switch (mountRule) {
          case ruleAll:
            return true;
          case ruleSameCloud:
            return provider.toLowerCase() === currentProvider.toLowerCase();
          case ruleSameRegion:
            return provider.toLowerCase() === currentProvider.toLowerCase() &&
              regionId.toLowerCase() === currentRegionId.toLowerCase();
          case ruleNone:
          default:
            return false;
        }
      })();
      if (!allowed && verbose) {
        console.log(storage.pathMask, `not allowed. storage region ${regionId} (${provider}), rule ${mountRule}`);
      }
      return allowed;
    });
    if (verbose) {
      console.log('total storages count', ensuredStorages.length);
      console.log('allowed count', filtered.length);
      console.log('not allowed count', ensuredStorages.length - filtered.length);
    }
  } catch (error) {
    console.error(`error filtering allowed storages for cloud region ${currentRegionId} (${currentProvider}):`, error);
  } finally {
    if (verbose) {
      console.groupEnd();
    }
  }
  return filtered;
}
