import { displayDate, type RunTasksData } from '@cloud-pipeline/core';

type BaseAttributes = {
  taskId?: string;
  taskGroup?: string;
  taskTag?: string;
  status?: string;
  started?: string;
  finished?: string;
};

const KEYS = [
  'cpus',
  'memory',
  'disk',
  'time',
  'duration',
  'realtime',
  '%cpu',
  '%mem',
  'vmem',
  'rss',
  'peak_vmem',
  'peak_rss',
  'read_bytes',
  'write_bytes',
];

type TaskAttributes = Record<string, string | number | null>;
type ProcessedDataEntry = BaseAttributes & TaskAttributes;

export const prepareTaskData = (data: RunTasksData['elements']) => {
  const dynamicKeys = new Set<string>();

  const processedData = data.map((item): ProcessedDataEntry => {
    let parsedAttributes: TaskAttributes = {};

    try {
      parsedAttributes = JSON.parse(item.attributes ?? '{}') as TaskAttributes;
    } catch (error) {
      console.error('Failed to parse attributes:', error);
      parsedAttributes = {};
    }

    Object.keys(parsedAttributes).forEach((key) => {
      if (KEYS.includes(key)) {
        dynamicKeys.add(key);
      }
    });

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

  const includedDynamicKeys = Array.from(dynamicKeys).filter((key) => processedData.some((item) => item[key] !== null));

  return { processedData, dynamicKeys: includedDynamicKeys };
};
