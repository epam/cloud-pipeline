import {computed, observable} from 'mobx';
import preferences from '../../../models/preferences/PreferencesLoad';
import {
  defaultStoragePathTemplate,
  defaultUserStoragePlaceholder,
  findStorage,
  getStoragePathGenerator
} from './utilities';

class LocalFilesConfiguration {
  @observable localFilesConfig = false;
  @observable dataStorage;
  @observable dataStoragePathGenerator;

  constructor () {
    (this.update)();
  }

  @computed
  get loaded () {
    return preferences.loaded;
  }

  @computed
  get enabled () {
    return !!this.localFilesConfig && this.dataStorage && this.dataStoragePathGenerator;
  }

  async update () {
    await preferences.fetchIfNeededOrWait();
    const config = preferences.uiLaunchParameters;
    let {
      // eslint-disable-next-line camelcase
      local_files = {},
      localFiles = local_files
    } = typeof config === 'object' ? config : {};
    if (typeof localFiles === 'boolean' && localFiles) {
      localFiles = {};
    } else if (typeof localFiles !== 'object') {
      localFiles = false;
    }
    this.localFilesConfig = localFiles;
    let dataStorage;
    let dataStoragePathGenerator;
    if (localFiles && typeof localFiles === 'object') {
      let {
        // eslint-disable-next-line camelcase
        upload_storage = defaultUserStoragePlaceholder,
        uploadStorage = upload_storage,
        // eslint-disable-next-line camelcase
        upload_storage_path = defaultStoragePathTemplate,
        uploadStoragePath = upload_storage_path
      } = localFiles;
      dataStorage = await findStorage(uploadStorage);
      if (dataStorage) {
        dataStoragePathGenerator = await getStoragePathGenerator(uploadStoragePath);
      }
    }
    this.dataStorage = dataStorage;
    this.dataStoragePathGenerator = dataStoragePathGenerator;
  }
}

export default LocalFilesConfiguration;
