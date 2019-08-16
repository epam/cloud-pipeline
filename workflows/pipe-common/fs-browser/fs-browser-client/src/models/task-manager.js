import {
  computed,
  observable,
} from 'mobx';
import TaskStatus, {Statuses as TaskStatuses} from './status';
import Download from './download';
import Upload from './upload';
import UploadUrl from './upload-url';
import UploadToBucket from './upload-to-bucket';
import autoDownloadFile from './utilities/auto-download-file';

const UPDATE_INTERVAL = 500;

const isRunning = status => [
  TaskStatuses.pending,
  TaskStatuses.running,
].indexOf(status) >= 0;

function localStorageFilter(task) {
  return task.type !== 'upload-to-bucket';
}

class Task extends TaskStatus {
  @observable activeSession;

  callbacks;

  @observable downloadUrl;

  id;

  item;

  timer;

  static mapper = callbacks => item => new Task(
    item.id,
    item.item,
    callbacks,
  );

  static unmapper = item => ({
    id: item.id,
    item: item.item,
  });

  @computed
  get isRunning() {
    return !this.loaded || isRunning(this.value.status);
  }

  constructor(id, item, callbacks, activeSession = false) {
    super(id);
    this.id = id;
    this.item = item;
    this.callbacks = callbacks || {};
    this.activeSession = activeSession;
    this.fetch();
  }

  fetch = async () => {
    await super.fetch();
    const {
      onFinished,
      onStatusChanged,
    } = this.callbacks;
    if (onStatusChanged && this.loaded) {
      onStatusChanged(this);
    }
    this.stop();
    if (!this.loaded || isRunning(this.value.status)) {
      this.timer = setTimeout(this.fetch, UPDATE_INTERVAL);
    } else if (onFinished) {
      this.downloadUrl = (this.value.result || {}).url;
      onFinished(this);
    }
  };

  stop = () => {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  };
}

class UploadToBucketTask extends UploadToBucket {
  constructor(id, url, file) {
    super(id, url, file);
    // this.item =
  }
}

class TaskManager {
  @observable items;

  constructor() {
    this.items = JSON
      .parse(localStorage.getItem('TASKS') || '[]')
      .map(Task.mapper({
        onFinished: this.onTaskFinished,
      }));
  }

  onTaskFinished = (task) => {
    if (task.activeSession && task.loaded && task.value.result && task.value.result.url) {
      const name = task.item.path.split('/').pop();
      autoDownloadFile(name, task.downloadUrl);
      this.removeTask(task);
    }
  };

  getTaskById = id => this.items.filter(item => item.id === id).pop();

  getTasksByPath = path => this.items.filter(item => item.item.path === path);

  download = async (path) => {
    const request = new Download(path);
    await request.fetch();
    if (!request.error) {
      const task = new Task(
        request.value.task,
        {path, type: 'download'},
        {onFinished: this.onTaskFinished},
        true,
      );
      this.items.push(task);
      localStorage.setItem('TASKS', JSON.stringify(this.items.filter(localStorageFilter).map(Task.unmapper)));
    }
    return request;
  };

  upload = async (path, file) => {
    const request = new UploadUrl(path);
    await request.fetch();
    if (request.error) {
      return request.error;
    }
    const task = new UploadToBucket(request.value.task, request.value.url.url, file);
    this.items.push(task);
    return null;
  };

  removeTask = (task) => {
    const [item] = this.items.filter(i => i.id === task.id);
    if (item) {
      const index = this.items.indexOf(item);
      this.items.splice(index, 1);
      localStorage.setItem('TASKS', JSON.stringify(this.items.filter(localStorageFilter).map(Task.unmapper)));
    }
  }
}

export default new TaskManager();
