import PipelineRunFilter from '../../../../models/pipelines/PipelineRunSingleFilter';
import PipelineRunServices from '../../../../models/pipelines/PipelineRunServices';
import {myIssues} from '../../../../mobx-stores/legacy-stores';
import {HOME_PAGE_SIZE} from './constants';
import type {HomeDataSources} from './types';

export function createHomeDataSources(userName: string): HomeDataSources {
  const myRunsSubFilter = userName ? {owners: [userName]} : {};

  return {
    activeRuns: new PipelineRunFilter({
      page: 1,
      pageSize: HOME_PAGE_SIZE,
      userModified: false,
      statuses: ['RUNNING', 'PAUSING', 'PAUSED', 'RESUMING'],
      ...myRunsSubFilter,
    }) as HomeDataSources['activeRuns'],
    completedRuns: new PipelineRunFilter({
      page: 1,
      pageSize: HOME_PAGE_SIZE,
      userModified: false,
      statuses: ['STOPPED', 'FAILURE', 'SUCCESS'],
      ...myRunsSubFilter,
    }) as HomeDataSources['completedRuns'],
    services: new PipelineRunServices({
      page: 1,
      pageSize: HOME_PAGE_SIZE,
      userModified: false,
      statuses: ['RUNNING'],
    }) as HomeDataSources['services'],
    myIssues,
  };
}
