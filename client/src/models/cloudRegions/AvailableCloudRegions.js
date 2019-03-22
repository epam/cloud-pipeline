import Remote from '../basic/Remote';
import AWSRegionIds from './AWSRegionIds';

class AvailableCloudRegions extends Remote {

  /* eslint-disable */
  static getCache (cache, id, model) {
    if (!cache.has(id)) {
      cache.set(id, new model(id));
    }

    return cache.get(id);
  }

  /* eslint-disable */
  static invalidateCache (cache, id) {
    if (cache.has(id)) {
      if (cache.get(id).invalidateCache) {
        cache.get(id).invalidateCache();
      } else {
        cache.delete(id);
      }
    }
  }

  _availableRegionIdsCache = new Map();

  load(provider) {
    return AvailableCloudRegions.getCache(this._availableRegionIdsCache, provider, AWSRegionIds);
  }

  invalidateCache(provider) {
    return AvailableCloudRegions.invalidateCache(this._availableRegionIdsCache, provider);
  }

}

export default new AvailableCloudRegions();
