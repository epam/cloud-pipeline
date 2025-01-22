import { useEffect, useRef, useState } from 'react';
import { Button, Checkbox, Input } from 'antd';
import { displayDate, type RunLog } from '@cloud-pipeline/core';
import type { CommonProps } from '@cloud-pipeline/components';
import { ArrowDownCircleIcon } from '@heroicons/react/24/solid';
import { useLogs } from './hooks/logs';
import downloadLog from './utils/download-log';
import type { MappedLog } from './types';
import classNames from 'classnames';

type Props = CommonProps & {
  logs: RunLog[] | undefined;
  maxLinesToDisplay?: number;
};

const MAX_LINES_TO_DISPLAY = 500;

export default function LogsViewer({
  logs: logsProp,
  maxLinesToDisplay,
  className,
  style,
}: Props) {
  const [followLogs, setFollowLogs] = useState(true);
  const [scrolledDown, setScrolledDown] = useState(true);
  const [searchVisible, setSearchVisible] = useState(false);
  const [searchIndex, setSearchIndex] = useState<number>(0);
  const [search, setSearch] = useState('');
  const [maxLines, setMaxLines] = useState(
    maxLinesToDisplay ?? MAX_LINES_TO_DISPLAY,
  );
  const containerRef = useRef<HTMLDivElement | null>(null);
  const { logs, searchResults, logsTruncated } = useLogs(
    logsProp,
    search,
    maxLines,
  );
  useEffect(() => {
    window.addEventListener('keydown', onKeyDown);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
    };
  }, []);
  useEffect(() => {
    if (followLogs && scrolledDown) {
      scrollDown(false);
    }
  }, [followLogs, logs, scrolledDown]);
  const onKeyDown = (event: KeyboardEvent) => {
    if (/^KeyF$/i.test(event.code) && (event.ctrlKey || event.metaKey)) {
      setSearchVisible(true);
      event.preventDefault();
      event.stopPropagation();
      return false;
    }
    if (/^(Escape|esc)$/i.test(event.key)) {
      setSearchVisible(true);
      event.preventDefault();
      event.stopPropagation();
      return false;
    }
  };
  const onScroll = () => {
    if (!containerRef.current) {
      return;
    }
    const { scrollTop, scrollHeight, clientHeight } = containerRef.current;
    const scrolled = scrollTop + clientHeight >= scrollHeight;
    if (scrolledDown !== scrolled) {
      setScrolledDown(scrolled);
    }
  };
  const scrollDown = (animated = false) => {
    if (containerRef.current) {
      const { scrollHeight, clientHeight } = containerRef.current;
      const top = Math.max(0, scrollHeight - clientHeight);
      containerRef.current.scrollTo({
        top,
        behavior: animated ? 'smooth' : 'auto',
      });
    }
  };
  const scrollToLine = (line: number, animated: boolean = false) => {
    const nodes = [...(containerRef?.current?.childNodes ?? [])];
    const lineNode = nodes.find((element) => {
      if (element instanceof HTMLElement) {
        return Number(element.dataset.index) === line;
      }
      return false;
    });
    if (lineNode && lineNode instanceof HTMLElement) {
      lineNode.scrollIntoView({ behavior: animated ? 'smooth' : 'auto' });
    }
  };
  const doSearch = () => {
    setSearchIndex(0);
    scrollToLine(searchResults[searchIndex]?.lineIndex);
  };
  const searchPrev = () => {
    const nextSearchIndex =
      searchIndex === 0
        ? Math.max(0, searchResults.length - 1)
        : searchIndex - 1;
    const line = searchResults[nextSearchIndex];
    setSearchIndex(nextSearchIndex);
    scrollToLine(line?.lineIndex, true);
  };
  const searchNext = () => {
    const nextSearchindex =
      searchIndex >= searchResults.length - 1 ? 0 : searchIndex + 1;
    const line = searchResults[nextSearchindex];
    setSearchIndex(nextSearchindex);
    scrollToLine(line?.lineIndex, true);
  };
  const onExpand = () => {
    setMaxLines(maxLines + (maxLinesToDisplay ?? MAX_LINES_TO_DISPLAY));
  };
  const renderSearch = (log: MappedLog) => {
    const currentLineVariants = searchResults.filter(
      (item) => item.lineIndex === Number(log.index),
    );
    if (currentLineVariants.length) {
      const current = currentLineVariants.find(
        (line) => line.index === searchIndex,
      );
      return current ? current.activeLogHTML : currentLineVariants[0].logHTML;
    }
    return log.html;
  };
  return (
    <div
      style={style}
      className={classNames('relative overflow-hidden flex flex-1', className)}>
      {!searchVisible ? (
        <div className="absolute top-1 right-2 opacity-50 hover:opacity-100">
          <Checkbox
            className="text-white"
            checked={followLogs}
            onChange={(e) => setFollowLogs(e.target.checked)}>
            Follow logs
          </Checkbox>
        </div>
      ) : (
        <div className="bg-white p-1 absolute top-0 flex gap-2 w-full z-10">
          <Input
            size="small"
            style={{ maxWidth: 300 }}
            autoFocus
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              doSearch();
            }}
          />
          <Button size="small" style={{ marginLeft: 5 }} onClick={searchPrev}>
            Prev
          </Button>
          <Button size="small" style={{ marginLeft: 5 }} onClick={searchNext}>
            Next
          </Button>
          {searchResults.length > 0 && (
            <span style={{ marginLeft: 5, marginRight: 5 }}>
              {searchIndex + 1} of {searchResults.length}
            </span>
          )}
          <Button
            size="small"
            onClick={() => setSearchVisible(false)}
            style={{ marginLeft: 'auto' }}>
            Close
          </Button>
          {logs.length > 0 && (
            <Checkbox
              checked={followLogs}
              onChange={(e) => setFollowLogs(e.target.checked)}
              style={{ marginLeft: 15 }}>
              Follow logs
            </Checkbox>
          )}
        </div>
      )}
      <div
        onScroll={onScroll}
        ref={containerRef}
        style={{ scrollPadding: 35 }}
        className="overflow-auto flex-1 bg-zinc-700 text-white">
        {logsTruncated ? (
          <div className="text-center">
            The last <span>{logs.length}</span> lines of the log are shown.{' '}
            <a onClick={onExpand}>Expand more</a> or{' '}
            <a onClick={() => downloadLog(logs)}>download complete log</a>.
          </div>
        ) : null}
        {!logs.length ? <div className="text-center">No data</div> : null}
        {logs.map((log) => (
          <div
            key={log.index}
            data-index={log.index}
            className="flex gap-1 px-2 items-start text-xs font-mono">
            <span className="text-faded select-none">{log.index}</span>
            <span className="text-faded shrink-0 mr-1 select-none">
              [{displayDate(log.date)}]
            </span>
            <span
              className="break-all"
              dangerouslySetInnerHTML={{ __html: renderSearch(log) }}
            />
          </div>
        ))}
      </div>
      {!scrolledDown && (
        <div
          className="opacity-50 hover:opacity-100 cursor-pointer text-white absolute left-[calc(100%-50px)] top-[calc(100%-50px)]"
          onClick={() => scrollDown(true)}>
          <ArrowDownCircleIcon className="w-8 h-8" />
        </div>
      )}
    </div>
  );
}
