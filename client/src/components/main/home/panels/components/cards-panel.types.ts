import type {ComponentType, CSSProperties, ReactNode} from 'react';

export type CardsPanelItem = {
  id?: string | number;
  isGlobal?: boolean;
  [key: string]: unknown;
};

export type CardsPanelAction<TItem extends CardsPanelItem = CardsPanelItem> = {
  title?: string;
  overlay?: ReactNode;
  icon?: ComponentType<{style?: CSSProperties; className?: string}>;
  action?: (source: TItem) => void | Promise<void>;
  disabled?: boolean;
  style?: CSSProperties;
  className?: string;
  target?: string;
  multiZoneUrl?: Record<string, string>;
  runSSH?: boolean;
  runId?: string | number;
};

export type CardsPanelSearch<TItem extends CardsPanelItem = CardsPanelItem> = {
  placeholder?: string;
  searchFn?: (item: TItem, search: string | null) => boolean;
};

export type CardsPanelProps<TItem extends CardsPanelItem = CardsPanelItem> = {
  children?: TItem | TItem[];
  search?: CardsPanelSearch<TItem>;
  panelKey?: string;
  style?: CSSProperties;
  onClick?: (item: TItem) => void;
  childRenderer: (item: TItem, search: string | null) => ReactNode;
  cardClassName?: string | ((item: TItem) => string);
  cardStyle?: CSSProperties | ((item: TItem) => CSSProperties);
  actions?: CardsPanelAction<TItem>[] | ((item: TItem) => CardsPanelAction<TItem>[]);
  emptyMessage?: string | ((search: string | null) => string);
  isFavourite?: (item: TItem) => boolean;
  favouriteEnabled?: boolean | ((item: TItem) => boolean);
  onSetFavourite?: (itemId: string | number | null, isFavourite: boolean) => void;
  displayOnlyFavourites?: boolean;
  itemId?: keyof TItem | ((item: TItem) => string | number | null);
  getFavourites?: () => Array<string | number>;
  setFavourites?: (itemId: string | number | null, isFavourite: boolean) => void;
  hovered?: TItem;
  pageSize?: number;
};
