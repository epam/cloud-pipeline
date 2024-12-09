export enum ProjectFilter {
  OWNER = 'owner',
}

const allowedFilters: { id: string; label: string }[] = [
  { id: 'project-type', label: 'Type' },
  { id: 'ProjectID', label: 'ID' },
  { id: 'ngs-data-location', label: 'Data Location' },
  { id: ProjectFilter.OWNER, label: 'Owner' },
];

const deniedFilters: string[] = ['ngs-data-location'];

export const projectFiltersToDisplay = allowedFilters.filter(
  (tag) => !deniedFilters.includes(tag.id),
);
