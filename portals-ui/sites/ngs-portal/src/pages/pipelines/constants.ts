import { NgsFilter } from '../../shared/constants/filters';
import type { FilterToDisplay } from '../projects/types';

const allowedFilters: FilterToDisplay[] = [
  { id: 'somekey', label: 'Some' },
  { id: 'SECRET1', label: 'Secret' },
  { id: NgsFilter.OWNER, label: 'Owner' },
];

const deniedFilters: string[] = [];

export const pipelinesFiltersToDisplay = allowedFilters.filter(
  (tag) => !deniedFilters.includes(tag.id),
);
