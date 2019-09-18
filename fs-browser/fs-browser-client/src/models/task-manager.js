import {
  computed,
  observable,
} from 'mobx';
import CancelTask from './cancel';
import TaskStatus, {Statuses as TaskStatuses} from './status';
import Download from './download';
import Upload from './upload';
import UploadUrl from './upload-url';
import UploadToBucket from './upload-to-bucket';
import autoDownloadFile from './utilities/auto-download-file';

const LOCAL_STORAGE_TASKS_KEY = `TASKS_${window.location.pathname}`;

const UPDATE_INTERVAL = 5000;

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
    return (this.loaded && isRunning(this.value.status)) || (this.error && this.canReFetch);
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
    if (this.isRunning) {
      this.timer = setTimeout(this.fetch, UPDATE_INTERVAL);
    } else if (this.loaded && onFinished) {
      if (this.item.type === 'download') {
        this.downloadUrl = (this.value.result || {}).url;
      }
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
  constructor(id, path, url, tagValue, file) {
    super(id, url, tagValue, file);
    this.id = id;
    this.item = {path, type: 'upload-to-bucket'};
  }
}

class TaskManager {
  @observable items;

  listener;

  constructor() {
    this.items = JSON
      .parse(localStorage.getItem(LOCAL_STORAGE_TASKS_KEY) || '[]')
      .map(Task.mapper({
        onFinished: this.onTaskFinished,
      }));
  }

  registerListener = (listener) => {
    this.listener = listener;
  };

  onTaskFinished = (task) => {
    if (task.item.type === 'download'
      && task.activeSession
      && task.loaded
      && task.value.result
      && task.value.result.url) {
      const name = task.item.path.split('/').pop();
      autoDownloadFile(name, task.downloadUrl);
      this.removeTask(task);
    }
    if (this.listener) {
      this.listener(task);
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
      localStorage.setItem(
        LOCAL_STORAGE_TASKS_KEY,
        JSON.stringify(this.items.filter(localStorageFilter).map(Task.unmapper)),
      );
    }
    return request;
  };

  upload = async (path, root, file) => {
    const request = new UploadUrl(path);
    await request.fetch();
    if (request.error) {
      return request.error;
    }
    const task = new UploadToBucketTask(
      request.value.task,
      path,
      request.value.url.url,
      request.value.url.tagValue,
      file,
    );
    task.fetch().then(async () => {
      if (task.loaded) {
        const uploadTask = new Upload(request.value.task);
        await uploadTask.fetch();
        if (uploadTask.loaded) {
          const taskStatus = new Task(
            request.value.task,
            {path, root, type: 'upload'},
            {onFinished: this.onTaskFinished},
            true,
          );
          this.removeTask(task);
          this.items.push(taskStatus);
          localStorage.setItem(
            LOCAL_STORAGE_TASKS_KEY,
            JSON.stringify(this.items.filter(localStorageFilter).map(Task.unmapper)),
          );
        } else if (uploadTask.error) {
          this.removeTask(task);
          console.error(uploadTask.error);
        }
      }
    });
    this.items.push(task);
    return null;
  };

  removeTask = (task) => {
    const [item] = this.items.filter(i => i.id === task.id);
    if (item) {
      const index = this.items.indexOf(item);
      this.items.splice(index, 1);
      localStorage.setItem(
        LOCAL_STORAGE_TASKS_KEY,
        JSON.stringify(this.items.filter(localStorageFilter).map(Task.unmapper)),
      );
    }
  };

  cancelTask = async (task) => {
    const request = new CancelTask(task.id);
    await request.fetch();
    if (request.error) {
      console.warn(request.error);
    }
    this.removeTask(task);
    return null;
  }
}

export default new TaskManager();
