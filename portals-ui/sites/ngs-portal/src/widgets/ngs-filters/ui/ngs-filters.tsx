import type { NgsFilterProps } from '../types.ts';
import { NgsFilter } from './ngs-filter.tsx';

export const NgsFilters = ({
  config,
  filters,
  onFiltersChange,
}: NgsFilterProps) => (
  <>
    {config.map((filterConfig) => (
      <NgsFilter
        key={filterConfig.key}
        filters={filters}
        onFiltersChange={onFiltersChange}
        filter={filterConfig}
      />
    ))}
  </>
);
