import { Button, Tag } from 'antd';
import classNames from 'classnames';
import type { CommonProps } from '@cloud-pipeline/components';
import { displayDate, type Pipeline, type Project } from '@cloud-pipeline/core';
import { NgsUserCard } from '../../../../widgets/cards';
import { NgsTag } from '../../../../widgets/ngs-tag';
import { TrashIcon } from '@heroicons/react/24/outline';
import HighlightedText from '../../../../shared/highlight-text';
import { Link } from 'react-router-dom';
import {
  generateLaunchRoutePath,
  generatePipelineRoutePath,
} from '../../../../shared/constants/routes.ts';
import { omitClonedPipelinePrefix } from '../../../../shared/helpers';
import { usePipelineTags } from '../../../../shared/tags/use-pipeline-tags.ts';
import { useCallback } from 'react';

type Props = CommonProps & {
  project: Project | undefined;
  onDelete: (id: number) => void;
  pipeline: Pipeline;
  search?: string;
};

export const ProjectPipelineCard = (props: Props) => {
  const { className, style, project, pipeline, search, onDelete } = props;
  const tags = usePipelineTags(pipeline?.data);

  const handleDelete = useCallback(() => {
    onDelete(pipeline.id);
  }, [onDelete, pipeline.id]);

  return (
    <div
      className={classNames(
        className,
        'flex flex-nowrap gap-3 px-3 py-2 bg-white space-y-1 items-center',
      )}
      style={style}>
      <div className="flex flex-col gap-1 grow">
        <Link
          className="font-semibold"
          to={generatePipelineRoutePath(pipeline.id)}>
          <HighlightedText search={search}>
            {omitClonedPipelinePrefix(pipeline, project)}
          </HighlightedText>
        </Link>
        {pipeline.description ? <span>{pipeline.description}</span> : null}
        <div className="flex gap-3 items-center text-xs">
          <span>
            Language: <b>PYTHON</b>
          </span>
          <span className="flex gap-3 items-center">
            Last modified: {displayDate(pipeline.createdDate)}
            <Tag className="text-xs">
              <NgsUserCard userName={pipeline.owner} />
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
        <Button style={{ padding: 0 }} type="primary" size="small">
          <Link
            className="flex w-full h-full px-2"
            to={generateLaunchRoutePath(pipeline.id)}>
            Run
          </Link>
        </Button>
        <Button type="primary" danger size="small" onClick={handleDelete}>
          <TrashIcon className="w-4 h-4" />
        </Button>
      </div>
    </div>
  );
};
