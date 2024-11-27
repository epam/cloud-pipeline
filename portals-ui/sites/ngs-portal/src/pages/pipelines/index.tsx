import { useEffect } from 'react';
import { Spinner } from '@epam/uui';
import { loadPipelines } from '../../state/pipelines/load-pipelines';
import { usePipelinesState } from '../../state/pipelines/hooks';
import { List, ListHeader } from '@cloud-pipeline/components';
import HighlightedText from '../../shared/highlight-text';
import { useSearch } from '../../shared/hooks/use-search.ts';

export default function Pipelines() {
  useEffect(() => {
    loadPipelines()
      .then(() => {})
      .catch(() => {});
  }, []);
  const { pipelines, error, pending } = usePipelinesState();
  const { search, onSearchChange, filtered } = useSearch({
    items: pipelines ?? [],
  });
  if (error) {
    return <div>{error}</div>;
  }
  if (pending) {
    return <Spinner />;
  }
  if (!pipelines) {
    return <div>No data</div>;
  }
  return (
    <div className="flex flex-col overflow-auto">
      <ListHeader
        title="Pipelines"
        className="shrink-0 border"
        search={search}
        onSearch={onSearchChange}
      />
      <List
        className="overflow-auto border-b border-l border-r"
        data={filtered}
        renderItem={(pipeline) => (
          <HighlightedText search={search}>{pipeline.name}</HighlightedText>
        )}
        itemKey="id"
        sliced={20}
      />
    </div>
  );
}
