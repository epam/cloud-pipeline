import { escapeRegExp } from '@cloud-pipeline/core';
import classNames from 'classnames';

function markSearch(
  source: string,
  search: string,
  options: {
    lastIndex?: number;
    currentIndex?: number;
  },
) {
  const { lastIndex = 0, currentIndex } = options ?? {};
  const tagRegExp = new RegExp(`(${escapeRegExp(search)})`, 'ig');
  let result = '';
  let e = tagRegExp.exec(source);
  let position = 0;
  const positions = [];
  while (e) {
    const classes = classNames('text-black', {
      // inactive
      'bg-amber-400 opacity-50':
        currentIndex !== undefined &&
        lastIndex + positions.length !== currentIndex,
      // active
      'bg-amber-200':
        currentIndex !== undefined &&
        lastIndex + positions.length === currentIndex,
    });
    positions.push(e.index);
    result = result
      .concat(source.slice(position, e.index))
      .concat(`<span class="${classes}">`)
      .concat(e[0])
      .concat('</span>');
    position = e.index + e[0].length;
    e = tagRegExp.exec(source);
  }
  result = result.concat(source.slice(position));
  return {
    result,
    positions,
  };
}

export function findText(
  html: string,
  text: string,
  options: {
    lastIndex?: number;
    currentIndex?: number;
  },
) {
  const { lastIndex = 0, currentIndex } = options ?? {};
  const tagRegExp = /<[^>]+>/g;
  let result = '';
  let e = tagRegExp.exec(html);
  let position = 0;
  const positions = [];
  let index = lastIndex;
  while (e) {
    const { result: marked, positions: partPositions = [] } = markSearch(
      html.slice(position, e.index),
      text,
      { lastIndex: index, currentIndex },
    );
    index += partPositions.length;
    positions.push(...partPositions.map((n) => n + position));
    result = result.concat(marked).concat(e[0]);
    position = e.index + e[0].length;
    e = tagRegExp.exec(html);
  }
  const { result: other, positions: otherPositions = [] } = markSearch(
    html.slice(position),
    text,
    { lastIndex: index, currentIndex },
  );
  positions.push(...otherPositions.map((n) => n + position));
  result = result.concat(other);
  return {
    result,
    positions,
    lastIndex: index,
  };
}
