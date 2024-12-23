import { useCallback } from 'react';
import { Button, Tag } from 'antd';
import classNames from 'classnames';
import type { CommonProps } from '@cloud-pipeline/components';
import { displayDate, type Pipeline, type Project } from '@cloud-pipeline/core';
import { NgsUserCard } from '../../../widgets/cards';
import { NgsTag } from '../../../widgets/ngs-tag';
import { extractTags } from '../../../shared/tags';
import { ItemsPanel } from '../../../widgets/items-panel/items-panel';
import { useNgsFilters } from '../../../features/ngs-filters';
import { TrashIcon } from '@heroicons/react/24/outline';
import HighlightedText from '../../../shared/highlight-text';
import { Link } from 'react-router-dom';

type Props = CommonProps & {
  project: Project | undefined;
};

export const ProjectPipelines = (props: Props) => {
  const { project } = props;
  const { filteredItems, onSearchChange, search } = useNgsFilters({
    items: project?.pipelines ?? [],
    withFilters: false,
    filtersToDisplay: [],
  });
  const renderItem = useCallback(
    (item: Pipeline, search: string, i: number) => {
      const tags = extractTags(project?.data);
      return (
        <div
          className={classNames(
            {
              ['border-t']: i !== 0,
              ['border-b']: i === filteredItems.length - 1,
            },
            'flex flex-nowrap gap-3 px-3 py-2 bg-white space-y-1 items-center',
          )}>
          <div className="flex flex-col gap-1 grow">
            <Link className="font-semibold" to="#">
              <HighlightedText search={search}>{item.name}</HighlightedText>
            </Link>
            {item.description ? <span>{item.description}</span> : null}
            <div className="flex gap-3 items-center text-xs">
              <span>
                Language: <b>PYTHON</b>
              </span>
              <span className="flex gap-3 items-center">
                Last modified: {displayDate(item.createdDate)}
                <Tag className="text-xs">
                  <NgsUserCard userName={item.owner} />
                </Tag>
              </span>
            </div>
            <div className="flex gap-1">
              {tags.map((tag) => (
                <NgsTag
                  key={tag.key}
                  tag={tag.key}
                  value={tag.value}
                  className="shrink-0 m-0"
                />
              ))}
            </div>
          </div>
          <div className="flex flex-nowrap items-center gap-1">
            <Button type="primary" size="small">
              Run
            </Button>
            <Button type="primary" danger size="small">
              <TrashIcon className="w-4 h-4" />
            </Button>
          </div>
        </div>
      );
    },
    [filteredItems, project?.data],
  );
  return (
    <div className="overflow-hidden h-full w-full flex">
      <ItemsPanel
        className="max-h-full grow list-container overflow-auto"
        items={filteredItems}
        render={renderItem}
        sliced
        virtualized
        search={search}
        onSearchChange={onSearchChange}
        itemKey="id"
      />
    </div>
  );
};
