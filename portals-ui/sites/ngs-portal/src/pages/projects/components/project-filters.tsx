import type { Project } from '@cloud-pipeline/core';
import { SelectFilter } from './select-filter';
import { ProjectFilter, projectTagsToDisplay } from '../constants';
import { useOwnersFilter, useProjectTags } from '../hooks';

type Tags = {
  [key: string]: string[];
};

type Props = {
  projects: Project[];
  onFilterValueChange: (tagName: string, selectedTags?: string[]) => void;
  tagsToFilter: Tags;
};

export const ProjectFilters = ({
  projects,
  tagsToFilter,
  onFilterValueChange,
}: Props) => {
  const projectTags = useProjectTags(projects);
  const { handleFocus, users = [] } = useOwnersFilter();

  return (
    <div className="flex flex-wrap gap-2">
      {projectTagsToDisplay.map(({ id, label }) => (
        <div>
          <SelectFilter
            key={id}
            options={projectTags[id]?.map((type) => ({
              id: type,
              name: type,
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
