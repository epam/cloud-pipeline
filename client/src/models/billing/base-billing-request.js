import {isObservableArray} from 'mobx';
import RemotePost from '../basic/RemotePost';

export default class BaseBillingRequest extends RemotePost {
  body = {};

  loadDetails;

  paginated;

  pageNum;

  pageSize;

  grouping;

  constructor (filters, loadDetails = false, pagination = null) {
    super();
    if (`${pagination}`.toLowerCase() === 'true') {
      pagination = {};
      pagination.pageSize = 10;
      pagination.pageNum = 0;
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

  async prepareBody () {
    this.body.from = this.filters && this.filters.start
      ? this.filters.start.toISOString() : undefined;
    this.body.to = this.filters && this.filters.end
      ? this.filters.end.toISOString() : undefined;
    this.body.filters = {};
    if (this.filters && this.filters.user) {
      this.body.filters.owner = Array.isArray(this.filters.user) ||
      isObservableArray(this.filters.user)
        ? this.filters.user
        : [this.filters.user];
    }
    if (this.filters && this.filters.group) {
      this.body.filters.billing_center = Array.isArray(this.filters.group) ||
      isObservableArray(this.filters.group)
        ? this.filters.group
        : [this.filters.group];
    }
    if (this.filters && this.filters.cloudRegionId) {
      this.body.filters.cloudRegionId = this.filters.cloudRegionId.slice();
    }
    if (this.loadDetails) {
      this.body.loadDetails = true;
    }
    if (this.paginated) {
      this.body.pageNum = this.pageNum;
      this.body.pageSize = this.pageSize;
    }
    if (this.grouping) {
      this.body.grouping = this.grouping;
    }
  }

  async fetch () {
    await this.prepareBody();

    return super.send(this.body);
  }

  async fetchPage (pageNum) {
    if (this.paginated) {
      this.pageNum = pageNum;

      return this.fetch();
    }
  }
}
