import { displayDate, type RunTasksData } from '@cloud-pipeline/core';
import type { ProcessedDataEntry, TaskAttributes } from '../types';

export const prepareTaskData = (data: RunTasksData['elements']) => {
  return data.map((item): ProcessedDataEntry => {
    let parsedAttributes: TaskAttributes = {};

    try {
      parsedAttributes = JSON.parse(item.attributes ?? '{}') as TaskAttributes;
    } catch (error) {
      console.error('Failed to parse attributes:', error);
      parsedAttributes = {};
    }

    return {
      taskName: item.taskName,
      taskKey: item.taskKey,
      taskId: item.taskId,
      taskGroup: item.taskGroup,
      taskTag: item.taskTag,
      status: item.status,
      started: displayDate(item.startDateTime) || '',
      finished: displayDate(item.endDateTime) || '',
      ...parsedAttributes,
    } as ProcessedDataEntry;
  });
};
