export type PagedRequest = {
  page: number;
  pageSize?: number;
};

export type PagedResponse = PagedRequest & {
  total: number;
};
