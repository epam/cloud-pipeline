import { NgsFilter } from '../../shared/constants/filters';
import type { FilterToDisplay } from '../../features/ngs-filters/types';

const allowedFilters: FilterToDisplay[] = [
  { id: 'project-type', label: 'Type' },
  { id: 'ProjectID', label: 'ID' },
  { id: 'ngs-data-location', label: 'Data Location' },
  { id: NgsFilter.OWNER, label: 'Owner' },
];

const deniedFilters: string[] = ['ngs-data-location'];

export const projectFiltersToDisplay = allowedFilters.filter(
  (tag) => !deniedFilters.includes(tag.id),
);
