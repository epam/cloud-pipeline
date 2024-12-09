import { SelectFilter } from '../../../shared/ui';
import { ProjectFilter, projectFiltersToDisplay } from '../constants';
import type { ProjectTags, TagFilters } from '../types';

type Props = {
  onFilterValueChange: (tagName: string, selectedTags?: string[]) => void;
  tagsToFilter: TagFilters;
  projectTags: ProjectTags;
  onOwnersFilterFocus: () => void;
};

export const ProjectFilters = ({
  tagsToFilter,
  onFilterValueChange,
  projectTags,
  onOwnersFilterFocus,
}: Props) => {
  const handleFilterChange = (id: string) => (selectedItems?: string[]) => {
    onFilterValueChange(id, selectedItems);
  };

  const handleFocus = (id: string) => {
    if (id === (ProjectFilter.OWNER as string)) {
      onOwnersFilterFocus();
    }
  };

  return (
    <div className="flex flex-wrap gap-2">
      {projectFiltersToDisplay.map(({ id, label }) => {
        const options =
          projectTags[id]?.map((tag) => ({
            id: tag.id,
            name: tag.count !== undefined ? `${tag.id} (${tag.count})` : tag.id,
            disabled: !tag.count && !tagsToFilter[id]?.includes(tag.id),
          })) || [];

        return (
          // div is needed not to let the filter take 100% width
          // re-check if filter is not from uui library
          <div key={id}>
            <SelectFilter
              options={options}
              selectedValues={tagsToFilter[id] ?? []}
              onChange={handleFilterChange(id)}
              label={label}
              onFocus={() => handleFocus(id)}
            />
          </div>
        );
      })}
    </div>
  );
};
