export type Tag = {
  id: string;
  count: number;
};

export type TagFilters = Record<string, string[]>;

export type ProjectTags = Record<string, Tag[]>;
