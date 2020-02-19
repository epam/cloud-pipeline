/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {GetGroupedBillingData} from './get-grouped-billing-data';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';

export class GetGroupedBillingDataPaginated extends GetGroupedBillingData {
  body = {};
  _pageSize;
  _pageNum;
  _totalPages;

  constructor (filters, groupedBy, pageSize = 10, pageNum = 0) {
    super();
    this.url = '/billing/charts/pagination';
    this.filters = filters;
    this.groupedBy = groupedBy;
    this.pageNum = pageNum;
    this.body.pageNum = pageNum;
    this._pageSize = pageSize;
    this.body.pageSize = pageSize;
  }

  get pageNum () {
    return this._pageNum || 0;
  }

  set pageNum (pageNum) {
    this._pageNum = pageNum;
    this.body.pageNum = pageNum;
  }

  get pageSize () {
    return this._pageSize || 0;
  }

  set pageSize (pageSize) {
    this._pageSize = pageSize;
    this.body.pageSize = pageSize;
  }

  get totalPages () {
    return this._totalPages;
  }

  async fetch () {
    return super.fetch();
  }

  async fetchPage (pageNum) {
    this.pageNum = pageNum;
    this.body.pageNum = pageNum;
    return this.fetch();
  }

  postprocess (value) {
    const val = super.postprocess(value);
    const firstKey = Object.keys(val).shift();
    this._totalPages = val && val[firstKey] && val[firstKey].groupingInfo &&
      val[firstKey].groupingInfo.totalPages
      ? +val[firstKey].groupingInfo.totalPages : 0;

    return val;
  }
}

export default class GetGroupedBillingDataWithPreviousRangePaginated extends GetDataWithPrevious {
  constructor (filters, groupedBy, pageSize = 10, pageNum = 0) {
    super(GetGroupedBillingDataPaginated, filters, groupedBy, pageSize, pageNum);
  }

  async fetchPage (pageNum) {
    this.current.pageNum = pageNum;

    return this.fetch();
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
