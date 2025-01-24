export type AsyncState<Data> = {
  pending: boolean;
  error: string | undefined;
  data: Data;
};
