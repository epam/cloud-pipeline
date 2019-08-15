import {
  computed,
  observable,
} from 'mobx';
import TaskStatus, {Statuses as TaskStatuses} from './status';
import Download from './download';

const UPDATE_INTERVAL = 500;

const isRunning = status => [
  TaskStatuses.pending,
  TaskStatuses.running,
].indexOf(status) >= 0;

class Task extends TaskStatus {
  id;

  item;

  callbacks;

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

  constructor(id, item, callbacks) {
    super(id);
    this.id = id;
    this.item = item;
    this.callbacks = callbacks || {};
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
    console.log('task finished', task);
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
      );
      this.items.push(task);
      localStorage.setItem('TASKS', JSON.stringify(this.items.map(Task.unmapper)));
    }
    return request;
  }
}

export default new TaskManager();
