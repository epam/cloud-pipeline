export type Tag = {
  id: string;
  count: number;
};

export type FilterToDisplay = { id: string; label: string };

export type TagFilters = Record<string, string[]>;

export type NgsTags = Record<string, Tag[]>;
