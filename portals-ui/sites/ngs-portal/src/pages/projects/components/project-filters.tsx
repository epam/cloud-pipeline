import type { Project } from '@cloud-pipeline/core';
import { SelectFilter } from './select-filter';
import { ProjectFilter, projectTagsToDisplay } from '../constants';
import { useOwnersFilter } from '../hooks';
import type { Tag, TagFilters } from '../types';

type Props = {
  filteredProjects: Project[];
  onFilterValueChange: (tagName: string, selectedTags?: string[]) => void;
  tagsToFilter: TagFilters;
  projectTags: Record<string, Tag[]>;
};

export const ProjectFilters = ({
  filteredProjects,
  tagsToFilter,
  onFilterValueChange,
  projectTags,
}: Props) => {
  const { handleFocus, users = [] } = useOwnersFilter(filteredProjects);

  return (
    <div className="flex flex-wrap gap-2">
      {projectTagsToDisplay.map(({ id, label }) => (
        <div>
          <SelectFilter
            key={id}
            options={projectTags[id]?.map((tag) => ({
              id: tag.id,
              name: tag.count ? `${tag.id} (${tag.count})` : tag.id,
              disabled: false,
            }))}
            selectedValues={tagsToFilter[id] ?? []}
            onChange={(selectedItems) => {
              onFilterValueChange(id, selectedItems);
            }}
            label={label}
          />
        </div>
      ))}

      <div>
        <SelectFilter
          selectedValues={tagsToFilter[ProjectFilter.OWNER]}
          onChange={(selectedItems) => {
            onFilterValueChange(ProjectFilter.OWNER, selectedItems);
          }}
          label="Owner"
          options={users}
          onFocus={handleFocus}
        />
      </div>
    </div>
  );
};
