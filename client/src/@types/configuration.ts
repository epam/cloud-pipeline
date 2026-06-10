import type {Configuration, ConfigurationEntry} from './library.ts';

export type RunConfigurationVO = {
  id?: number;
  parentId?: number;
  name?: string;
  description?: string;
  entries?: ConfigurationEntry[];
};

export type {Configuration as RunConfiguration};
