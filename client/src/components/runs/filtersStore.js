import {action, computed, observable} from 'mobx';

class FiltersStore {
  @observable filters = {
    page: 0,
    error: undefined,
    expandedRows: [],
    filters: {},
    additionalFilters: {},
    filtersState: {},
    runs: [],
    total: 0
  };

  @action
  reset () {
    this.filters = {
      page: 0,
      error: undefined,
      expandedRows: [],
      filters: {},
      additionalFilters: {},
      filtersState: {},
      runs: [],
      total: 0
    };
  }
}

export default new FiltersStore();
