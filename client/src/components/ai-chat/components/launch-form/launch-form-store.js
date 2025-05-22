import {observable, action} from 'mobx';

class LaunchFormStore {
  @observable disk = '';
  @observable selectedPriceType = '';
  @observable dockerImage = '';
  @observable instanceType = '';
  @observable cmd = '';
  @observable is_spot = false;

  @observable parameters = {};

  @action updateField (key, value) {
    if (this.hasOwnProperty(key)) {
      this[key] = value;
    } else {
      console.error(`[LaunchFormStore] Field '${key}' does not exist`);
    }
  }

  @action updateParameter (key, value) {
    if (this.parameters[key]) {
      this.parameters[key].value = value;
    } else {
      console.error(`[LaunchFormStore] Parameter '${key}' not found`);
    }
  }

  @action initializeFields (data) {
    if (data) {
      if (data.disk !== undefined) this.disk = data.disk;
      if (data.selectedPriceType !== undefined) this.selectedPriceType = data.selectedPriceType;
      if (data.dockerImage !== undefined) this.dockerImage = data.dockerImage;
      if (data.instanceType !== undefined) this.instanceType = data.instanceType;
      if (data.cmd !== undefined) this.cmd = data.cmd;
      if (data.is_spot !== undefined) this.is_spot = data.is_spot;
    } else {
      console.error('[LaunchFormStore] Invalid data provided:', data);
    }
  }

  @action initializeParameters (parameters) {
    if (parameters && typeof parameters === 'object') {
      this.parameters = {...parameters};
    } else {
      console.error('[MobX Store] Invalid parameters data provided:', parameters);
    }
  }
}

export default LaunchFormStore;
