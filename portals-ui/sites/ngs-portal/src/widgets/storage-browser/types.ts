export type PageMarker = {
  currentPage: number;
  markers: Array<string | undefined>;
};

export type PageMarkers = Record<string, PageMarker>;

export type StoragePaging = {
  marker: string | undefined;
  currentPage: number;
  canNavigateNext: boolean;
  canNavigatePrev: boolean;
};
