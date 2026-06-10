export type ApplicationInfo = {
  version?: string;
  prettyName?: string;
  build?: string;
  buildDate?: string;
  mode?: string;
};

export type InstanceType = {
  name?: string;
  disk?: number;
  memory?: number;
  cpu?: number;
  gpu?: number;
  sku?: string;
  pricePerHour?: number;
};

export type ClusterNode = {
  name?: string;
  address?: string;
  created?: string;
  labels?: Record<string, string>;
  roles?: string[];
};

export type PermissionGrant = {
  sid?: string;
  principal?: boolean;
  mask?: number;
};

export type PermissionGrantRequest = {
  aclClass?: string;
  id?: number;
  user?: string;
  role?: string;
  mask?: number;
  isPrincipal?: boolean;
};

export type SearchRequest = {
  query?: string;
  aggregate?: boolean;
  searchTypes?: string[];
  paging?: {pageSize?: number; pageNum?: number};
};

export type SearchResultItem = {
  id?: string;
  name?: string;
  description?: string;
  type?: string;
  highlight?: Record<string, string[]>;
};

export type SearchResult = {
  documents?: SearchResultItem[];
  totalHits?: number;
};

export type BillingChartRequest = {
  filters?: Record<string, unknown>;
  grouping?: string;
  period?: string;
};

export type BillingChart = {
  name?: string;
  values?: Array<{label?: string; value?: number}>;
};

export type SystemNotification = {
  id?: number;
  title?: string;
  body?: string;
  severity?: string;
  startDate?: string;
  endDate?: string;
  state?: string;
  createdDate?: string;
};

export type Ontology = {
  id?: number;
  name?: string;
  description?: string;
  parentId?: number;
  externalId?: string;
  childNodes?: Ontology[];
};

export type TemplateDescription = {
  id: string;
  description?: string;
  defaultTemplate?: boolean;
};

export type RunSchedule = {
  id?: number;
  cronExpression?: string;
  timezone?: string;
  owner?: string;
  status?: string;
  createdDate?: string;
};

export type ConfigurationSchedule = RunSchedule & {
  configurationId?: number;
};
