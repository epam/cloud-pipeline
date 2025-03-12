import { displayDate, type RunTasksData } from '@cloud-pipeline/core';

type BaseAttributes = {
  taskId?: string;
  taskGroup?: string;
  taskTag?: string;
  status?: string;
  started?: string;
  finished?: string;
};

type TaskAttributes = Record<string, string | number | null>;
type ProcessedDataEntry = BaseAttributes & TaskAttributes;

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
