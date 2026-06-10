import {computed, observable, makeObservable} from 'mobx';
import preferences from '../../models/preferences/PreferencesLoad';
import LocalFilesConfiguration from './local-files';

class UILaunchParametersConfiguration {
  localFiles = new LocalFilesConfiguration();

  constructor() {
    makeObservable(this, {
      localFiles: observable,
      loaded: computed,
    });
    this.update();
  }

  get loaded() {
    return preferences.loaded;
  }

  async update() {
    await Promise.all([preferences.fetchIfNeededOrWait(), this.localFiles.update()]);
  }
}

const uiLaunchParametersConfiguration = new UILaunchParametersConfiguration();

export default uiLaunchParametersConfiguration;
