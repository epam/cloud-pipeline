import {observable, action} from 'mobx';

class LaunchFormStore {
  @observable selectedInstance = 'm5.xlarge(CPU 4, RAM 16)';
  @observable selectedDisk = '250';
  @observable selectedPriceType = 'on-demand';
  @observable inputDocker = 'registry.example.com/tool/image:1.0.0';

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

  @action initializeParameters (parameters) {
    if (parameters && typeof parameters === 'object') {
      this.parameters = {...parameters};
    } else {
      console.error('[MobX Store] Invalid parameters data provided:', parameters);
    }
  }
}

export default LaunchFormStore;
