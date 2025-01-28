import GetRunEngineTasksStats from '../../../../../../models/run-engines/fetch-tasks-stats';
import {NEXTFLOW_ENGINE_TYPE} from '../../../../../../models/run-engines/engines';
import GetRunEngineTasksFilter from "../../../../../../models/run-engines/fetch-tasks";

class NextflowTasksBaseLoader {
  constructor (runId, loader, options = {}) {
    const {
      reload = false,
      interval = 5000
    } = options;
    this.runId = runId;
    this.loader = loader;
    this.token = {};
    this.listeners = [];
    this.pending = true;
    this.error = undefined;
    this.data = undefined;
    this.reload = reload;
    this.interval = interval;
    this.timeout = undefined;
  }

  removeListener = (listener) => {
    this.listeners = this.listeners.filter((l) => l !== listener);
  };

  addListener = (listener) => {
    this.removeListener(listener);
    this.listeners.push(listener);
    this.report();
  }

  destroy = () => {
    this.listeners = [];
    this.abort();
  };

  abort = () => {
    this.token = {};
    if (this.timeout) {
      clearTimeout(this.timeout);
      this.timeout = undefined;
    }
  };

  load = async () => {
    this.abort();
    const token = this.token = {};
    const commit = (fn) => {
      if (token === this.token) {
        fn();
      }
    };
    commit(() => {
      this.pending = true;
      this.error = undefined;
      this.report();
    });
    try {
      const data = await this.loader(this.getLoaderPayload());
      commit(() => {
        this.data = data;
      });
    } catch (error) {
      commit(() => {
        this.error = error.message;
      });
    } finally {
      commit(() => {
        this.pending = false;
        this.report();
        if (this.reload) {
          const interval = this.interval || 5000;
          this.timeout = setTimeout(() => this.load(), interval);
        }
      });
    }
  }

  report = () => {
    const payload = this.getData();
    for (const listener of this.listeners) {
      listener(payload);
    }
  };

  getLoaderPayload = () => ({
    runId: this.runId
  });

  getData = () => ({
    pending: this.pending,
    error: this.error,
    data: this.data
  });
}

async function loadTasks (opts) {
  const {
    runId,
    tasksGroup,
    page = 0,
    pageSize = 20,
    filters,
    sorting
  } = opts;
  const request = new GetRunEngineTasksFilter(
    runId,
    NEXTFLOW_ENGINE_TYPE
  );
  const payload = {
    page: (page + 1),
    pageSize,
    sorts: sorting,
    ...(filters || {})
  };
  if (tasksGroup) {
    payload.taskGroup = tasksGroup;
  }
  await request.send(payload);
  if (request.error) {
    throw new Error(`Error fetching Nextflow tasks: ${request.error}`);
  }
  const {
    elements = [],
    totalCount = 0
  } = request.value || {};
  return {
    elements,
    totalCount
  };
}

async function loadProcesses (opts) {
  const {
    runId
  } = opts;
  const request = new GetRunEngineTasksStats(
    runId,
    NEXTFLOW_ENGINE_TYPE
  );
  await request.fetch();
  if (request.error) {
    throw new Error(`Error fetching Nextflow tasks stats: ${request.error}`);
  }
  const stats = [];
  let total = 0;
  for (const key of Object.keys(request.value || {})) {
    const processStats = {
      key,
      name: key,
      stats: []
    };
    const data = request.value[key] || {};
    for (const [status, count] of Object.entries(data)) {
      processStats.stats.push({status, count});
      total += count;
    }
    stats.push(processStats);
  }
  return {stats, total};
}

class NextflowProcessesLoader extends NextflowTasksBaseLoader {
  constructor (runId, options) {
    super(runId, loadProcesses, options);
    (this.load)();
  }

  getData = () => ({
    pending: this.pending,
    error: this.error,
    processes: this.data ? this.data.stats || [] : [],
    totalTasks: this.data ? this.data.total : 0
  });
}

function parseAttributes (attributes) {
  try {
    const attrs = JSON.parse(attributes);
    if (typeof attrs === 'object') {
      return attrs;
    }
    return {};
  } catch {
    return {};
  }
}

function taskMapper (task) {
  const {
    taskGroup,
    taskName,
    attributes,
    ...rest
  } = task;
  let name = taskName;
  if (taskName && taskGroup) {
    const r = new RegExp(`^\\s*${taskGroup} \\((.+?)\\)\\s*$`, 'i');
    const e = r.exec(taskName);
    if (e) {
      name = e[1];
    }
  }
  return {
    ...rest,
    name,
    taskGroup,
    taskName,
    attributes: parseAttributes(attributes)
  };
}

class NextflowTasksLoader extends NextflowTasksBaseLoader {
  constructor (runId, options) {
    const {
      tasksGroup,
      page,
      pageSize,
      filters,
      sorting
    } = options || {};
    super(runId, loadTasks, options);
    this.tasksGroup = tasksGroup;
    this.page = page;
    this.pageSize = pageSize;
    this.filters = filters;
    this.sorting = sorting;
    (this.load)();
  }

  getLoaderPayload = () => ({
    runId: this.runId,
    tasksGroup: this.tasksGroup,
    page: this.page,
    pageSize: this.pageSize,
    filters: this.filters,
    sorting: this.sorting
  });

  getData = () => ({
    pending: this.pending,
    error: this.error,
    tasks: (this.data ? this.data.elements : []).map(taskMapper),
    totalCount: this.data ? this.data.totalCount : 0
  });
}

export {
  NextflowProcessesLoader,
  NextflowTasksLoader
};
