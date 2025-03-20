export type SortingState = {
  column: string;
  order: 'ascend' | 'descend';
};

export type SelectedTask = {
  taskKey: string;
  taskName: string;
};

type BaseAttributes = {
  taskKey?: string;
  taskName?: string;
  taskId?: string;
  taskGroup?: string;
  taskTag?: string;
  status?: string;
  started?: string;
  finished?: string;
};

export type TaskAttributes = Record<string, string | number | null>;
export type ProcessedDataEntry = BaseAttributes & TaskAttributes;
