import { useEffect } from 'react';
import { Spinner } from '@epam/uui';
import { loadProjects } from '../../state/projects/load-projects';
import { useProjectsState } from '../../state/projects/hooks';
import { List, ListHeader } from '@cloud-pipeline/components';
import { useSearch } from '../../shared/hooks/use-search.ts';
import HighlightedText from '../../shared/highlight-text';

export function ProjectsPage() {
  useEffect(() => {
    loadProjects()
      .then(() => {})
      .catch(() => {});
  }, []);
  const { projects, error, pending } = useProjectsState();
  const { search, onSearchChange, filtered } = useSearch({
    items: projects ?? [],
  });
  if (error) {
    return <div>{error}</div>;
  }
  if (pending) {
    return <Spinner />;
  }
  if (!projects) {
    return <div>No data</div>;
  }
  return (
    <div className="flex flex-col overflow-auto">
      <ListHeader
        title="Projects"
        className="shrink-0 border"
        search={search}
        onSearch={onSearchChange}
      />
      <List
        className="overflow-auto border-b border-l border-r"
        data={filtered}
        renderItem={(project) => (
          <HighlightedText search={search}>{project.name}</HighlightedText>
        )}
        itemKey="id"
        sliced={20}
      />
    </div>
  );
}
