import { useMemo } from 'react';
import { displayDate, type RunLog } from '@cloud-pipeline/core';
import { AnsiUp } from 'ansi_up';
import { findText } from '../utils/search-logs';

const ansi_up = new AnsiUp();

type MappedLog = RunLog & {
  html: string;
  index: number;
};

export function useParsedLogs(logs: RunLog[] | undefined, maxLines: number) {
  return useMemo(() => {
    if (logs?.length) {
      const parsed = logs
        .filter((log) => log.logText?.length)
        .map((log) =>
          log.logText.split('\n').map((line) => ({
            date: displayDate(log.date),
            logText: log.logText,
            html: ansi_up.ansi_to_html(line),
          })),
        )
        .reduce((r, c) => [...r, ...c], [])
        .map((log, index) => ({
          ...log,
          index,
        }));
      const sliceFrom = Math.max(0, parsed.length - maxLines);
      return {
        logs: parsed.slice(sliceFrom),
        logsTruncated: parsed.length >= maxLines,
      };
    }
    return { logs: [], logsTruncated: false };
  }, [logs, maxLines]) as { logs: MappedLog[]; logsTruncated: boolean };
}

export function useLogs(
  logs: RunLog[] | undefined,
  search: string,
  maxLines: number,
) {
  const { logs: parsedLogs, logsTruncated } = useParsedLogs(logs, maxLines);
  const searchResults = useMemo(() => {
    if (!search || !logs) {
      return [];
    }
    const searchResults = parsedLogs
      .map((log) => {
        if (!search || search.length < 2) {
          return [];
        }
        const { result, positions } = findText(log.html, search, {
          currentIndex: -1,
        });
        return positions.map((position, idx) => ({
          lineIndex: log.index,
          position,
          logHTML: result,
          activeLogHTML: findText(log.html, search, { currentIndex: idx })
            .result,
        }));
      })
      .reduce((r, c) => [...r, ...c], [])
      .map((result, index) => ({
        ...result,
        index,
      }));
    return searchResults;
  }, [logs, parsedLogs, search]);
  return useMemo(
    () => ({
      searchResults,
      logs: parsedLogs,
      logsTruncated,
    }),
    [logsTruncated, parsedLogs, searchResults],
  );
}
