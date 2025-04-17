import {computed, observable} from 'mobx';
import preferences from '../../models/preferences/PreferencesLoad';
import LocalFilesConfiguration from './local-files';

class UILaunchParametersConfiguration {
  @observable localFiles = new LocalFilesConfiguration();
  constructor () {
    (this.update)();
  }

  @computed
  get loaded () {
    return preferences.loaded;
  }

  async update () {
    await Promise.all([
      preferences.fetchIfNeededOrWait(),
      this.localFiles.update()
    ]);
  }
}

const uiLaunchParametersConfiguration = new UILaunchParametersConfiguration();

export default uiLaunchParametersConfiguration;
