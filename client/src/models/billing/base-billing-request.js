import {action, isObservableArray} from 'mobx';
import RemotePost from '../basic/RemotePost';
import TimedOutCache from '../basic/timed-out-cache';
import {bytesToGbs, costMapper} from './utils';
import defer from '../../utils/defer';
import extendFiltersWithFilterBy from './filter-by-payload';

export const KEYS = {
  cost: 'cost',
  oldVersionCost: 'oldVersionCost',
  accumulativeCost: 'accumulativeCost',
  accumulativeOldVersionCost: 'accumulativeOldVersionCost',
  size: 'size',
  avgSize: 'avgSize',
  oldVersionSize: 'oldVersionSize',
  oldVersionAvgSize: 'oldVersionAvgSize'
};

/**
 * @typedef {Object} BaseBillingRequestPagination
 * @property {number} pageNum
 * @property {number} pageSize
 */

/**
 * @typedef {Object} BaseBillingRequestOptions
 * @property {Object} [filters]
 * @property {BaseBillingRequestPagination|boolean} [pagination]
 * @property {boolean} [loadDetails=false]
 * @property {boolean} [loadCostDetails=false]
 */

const cache = new TimedOutCache();

/**
 * @param {BaseBillingRequest} billingRequest
 * @returns {Promise<void>}
 */
export async function preFetchBillingRequest (billingRequest) {
  if (!billingRequest.loadBillingDataFromCache()) {
    await billingRequest.fetch();
  }
}

export default class BaseBillingRequest extends RemotePost {
  body = {};

  loadDetails;

  loadCostDetails;

  paginated;

  pageNum;

  pageSize;

  grouping;

  /**
   * @param {BaseBillingRequestOptions} [options]
   */
  constructor (options = {}) {
    super();
    const {
      filters,
      loadDetails = false,
      pagination: paginationOptions,
      loadCostDetails = false
    } = options;

    let pagination;
    if (
      !!paginationOptions &&
      `${paginationOptions}`.toLowerCase() === 'true'
    ) {
      pagination = {};
      pagination.pageSize = 10;
      pagination.pageNum = 0;
    } else if (
      !!paginationOptions &&
      typeof paginationOptions === 'object' &&
      paginationOptions.pageSize
    ) {
      pagination = {...paginationOptions};
    }

    if (pagination && pagination.pageSize) {
      this.paginated = true;
      this.url = '/billing/charts/pagination';
      this.pageNum = pagination.pageNum;
      this.pageSize = pagination.pageSize;
    } else {
      this.paginated = false;
      this.url = '/billing/charts';
    }
    this.filters = filters;
    this.pagination = pagination;
    this.loadDetails = loadDetails;
    this.loadCostDetails = loadCostDetails;
  }

  get totalPages () {
    if (!this._value) {
      return 0;
    }
    const firstKey = Object.keys(this._value).shift();
    return this._value && this._value[firstKey] && this._value[firstKey].groupingInfo &&
      this._value[firstKey].groupingInfo.totalPages
      ? +this._value[firstKey].groupingInfo.totalPages : 0;
  }

  prepareBody () {
    const asArray = (value) => Array.isArray(value) || isObservableArray(value) ? value : [value];
    this.body.from = this.filters && this.filters.start
      ? this.filters.start.toISOString() : undefined;
    this.body.to = this.filters && this.filters.end
      ? this.filters.end.toISOString() : undefined;
    this.body.filters = extendFiltersWithFilterBy({}, this.filters ? this.filters.filterBy : {});
    if (this.filters && this.filters.user) {
      this.body.filters.owner = asArray(this.filters.user);
    }
    if (this.filters && this.filters.group) {
      this.body.filters.billing_center = asArray(this.filters.group);
    }
    if (this.filters && this.filters.cloudRegionId) {
      this.body.filters.cloudRegionId = this.filters.cloudRegionId.slice();
    }
    if (this.filters && this.filters.order) {
      this.body.order = {...this.filters.order};
    }
    if (this.filters && this.filters.storageIds) {
      this.body.filters.storage_id = asArray(this.filters.storageIds);
    }
    if (this.filters && this.filters.storageTypes) {
      this.body.filters.storage_type = asArray(this.filters.storageTypes);
    }
    if (this.filters && this.filters.tools) {
      this.body.filters.tool = asArray(this.filters.tools);
    }
    if (this.filters && this.filters.instances) {
      this.body.filters.instance_type = asArray(this.filters.instances);
    }
    if (this.filters && this.filters.pipelines) {
      this.body.filters.pipeline = asArray(this.filters.pipelines);
    }
    if (this.loadDetails) {
      this.body.loadDetails = true;
    }
    if (this.loadCostDetails) {
      this.body.loadCostDetails = true;
    }
    if (this.paginated) {
      this.body.pageNum = this.pageNum;
      this.body.pageSize = this.pageSize;
    }
    if (this.grouping) {
      this.body.grouping = this.grouping;
    }
  }

  postprocess (value) {
    const items = super.postprocess(value);
    const costKeys = [
      KEYS.cost,
      KEYS.oldVersionCost,
      KEYS.accumulativeCost,
      KEYS.accumulativeOldVersionCost
    ];
    const sizeKeys = [
      KEYS.size,
      KEYS.avgSize,
      KEYS.oldVersionSize,
      KEYS.oldVersionAvgSize
    ];
    const processTier = (tier) => {
      const result = {
        ...tier
      };
      const processKeys = (keys, mapper) => {
        keys.forEach((aKey) => {
          const value = result[aKey] || 0;
          if (!Number.isNaN(Number(value))) {
            result[aKey] = mapper(value);
          }
        });
      };
      processKeys(costKeys, costMapper);
      processKeys(sizeKeys, bytesToGbs);
      return result;
    };
    const processCost = (value) => value !== undefined && !Number.isNaN(Number(value))
      ? costMapper(Number(value))
      : undefined;
    return (items || []).map((item) => {
      const {
        costDetails = {},
        ...rest
      } = item;
      const {
        tiers = [],
        computeCost,
        diskCost,
        accumulatedComputeCost,
        accumulatedDiskCost,
        ...costDetailsRest
      } = costDetails;
      const processedTiers = tiers.map(processTier);
      if (processedTiers.length > 0) {
        const total = {
          storageClass: 'TOTAL'
        };
        tiers.forEach((aTier) => {
          const processKeys = (keys) => {
            keys.forEach((aKey) => {
              const value = aTier[aKey] || 0;
              if (!Number.isNaN(Number(value))) {
                total[aKey] = (total[aKey] || 0) + value;
              }
            });
          };
          processKeys([...costKeys, ...sizeKeys]);
        });
        processedTiers.push(processTier(total));
      }
      return {
        ...rest,
        costDetails: {
          ...costDetailsRest,
          computeCost: processCost(computeCost),
          diskCost: processCost(diskCost),
          accumulatedComputeCost: processCost(accumulatedComputeCost),
          accumulatedDiskCost: processCost(accumulatedDiskCost),
          tiers: processedTiers
            .reduce((result, tier) => ({
              ...result,
              [tier.storageClass]: tier
            }), {})
        }
      };
    });
  }

  getRequestKey () {
    const {
      from = '',
      to = '',
      filters = {},
      order = {},
      loadCostDetails = false,
      loadDetails = false,
      interval = '',
      grouping = '',
      pageNum = '',
      pageSize = ''
    } = this.body || {};
    const {
      resource_type: resourceType = [],
      storage_type: storageType = [],
      compute_type: computeType = [],
      owner = [],
      billing_center: billingCenter = [],
      cloudRegionId = []
    } = filters;
    const {
      aggregate = '',
      metric = '',
      desc = false
    } = order;
    const asArrayKey = (value) => {
      if (value && (Array.isArray(value) || isObservableArray(value))) {
        return value.join(',');
      }
      return [value].filter((o) => o !== undefined).join(',');
    };
    return [
      from,
      to,
      loadDetails,
      loadCostDetails,
      interval,
      grouping,
      pageNum,
      pageSize,
      asArrayKey(computeType),
      asArrayKey(resourceType),
      asArrayKey(storageType),
      asArrayKey(owner),
      asArrayKey(billingCenter),
      asArrayKey(cloudRegionId),
      aggregate,
      metric,
      desc
    ].join('|');
  }

  @action
  loadBillingDataFromCache () {
    this.prepareBody();
    const key = this.getRequestKey();
    if (cache.has(key)) {
      const response = cache.get(key);
      this.update(response);
      this._pending = false;
      this._postIsExecuting = false;
      this._fetchIsExecuting = false;
      return true;
    }
    return false;
  }

  @action
  async fetch () {
    this.prepareBody();
    this._pending = true;
    await defer();
    if (!this.loadBillingDataFromCache()) {
      await super.send(this.body);
      if (this._value && !this.error) {
        const key = this.getRequestKey();
        cache.set(key, {...(this._response || {})});
      }
    }
  }

  @action
  async fetchPage (pageNum) {
    if (this.paginated) {
      this.pageNum = pageNum;
      await this.fetch();
    }
  }
}
