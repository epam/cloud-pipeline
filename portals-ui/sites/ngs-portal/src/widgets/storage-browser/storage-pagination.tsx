import { PlayIcon } from '@heroicons/react/24/solid';
import { Button } from 'antd';
import type { StoragePaging } from './types';

type Props = {
  pending?: boolean;
  onClickNextPage: () => void;
  onClickPrevPage: () => void;
  paging: StoragePaging;
};

export default function StoragePagination({
  pending,
  paging,
  onClickPrevPage,
  onClickNextPage,
}: Props) {
  return (
    <div className="flex justify-end gap-1">
      <Button
        size="small"
        disabled={!!pending || !paging.canNavigatePrev}
        onClick={onClickPrevPage}>
        <PlayIcon className="w-4 h-4 rotate-180" />
      </Button>
      <div className="min-w-6 h-6 border border-1 border-solid border-slate-300 rounded flex justify-center items-center">
        {paging.currentPage + 1}
      </div>
      <Button
        size="small"
        disabled={!!pending || !paging.canNavigateNext}
        onClick={onClickNextPage}>
        <PlayIcon className="w-4 h-4" />
      </Button>
    </div>
  );
}
