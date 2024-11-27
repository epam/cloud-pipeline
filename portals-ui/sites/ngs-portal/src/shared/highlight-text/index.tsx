import { useMemo } from 'react';
import type { CSSProperties } from 'react';
import classNames from 'classnames';
import type { CommonProps } from '@cloud-pipeline/components';
import './style.css';

export type HighlightedTextProps = CommonProps & {
  search?: string | undefined;
  children: string;
  highlightClassName?: string;
  highlightStyle?: CSSProperties;
};

type HighlightPart = {
  part: string;
  highlight: boolean;
};

const escapeRegExpCharacters = [
  '.',
  '-',
  '+',
  '*',
  '?',
  '^',
  '$',
  '(',
  ')',
  '[',
  ']',
  '{',
  '}',
];

function escapeRegExp(
  string: string,
  characters = escapeRegExpCharacters,
): string {
  let result = string;
  characters.forEach((character) => {
    result = result.replace(
      new RegExp('\\' + character, 'g'),
      `\\${character}`,
    );
  });
  return result;
}

export default function HighlightedText(props: HighlightedTextProps) {
  const {
    children: text,
    search = '',
    className,
    style,
    highlightClassName,
    highlightStyle,
  } = props;
  const parts = useMemo<HighlightPart[]>(() => {
    if (!search?.length) {
      return [
        {
          part: text,
          highlight: false,
        },
      ];
    }
    const regExp = new RegExp(`${escapeRegExp(search)}`, 'ig');
    let e = regExp.exec(text);
    let idx = 0;
    const result: HighlightPart[] = [];
    while (e) {
      result.push({
        part: text.slice(idx, e.index),
        highlight: false,
      });
      result.push({
        part: e[0],
        highlight: true,
      });
      idx = e.index + e[0].length;
      e = regExp.exec(text);
    }
    if (idx < text.length) {
      result.push({
        part: text.slice(idx),
        highlight: false,
      });
    }
    return result;
  }, [text, search]);
  return (
    <span className={classNames(className, 'highlighted-text')} style={style}>
      {parts.map((part, idx) => (
        <span
          key={`part-${idx}`}
          className={classNames(
            'm-0',
            'highlighted-text-part',
            highlightClassName,
            {
              highlighted: part.highlight,
            },
          )}
          style={part.highlight ? highlightStyle : undefined}>
          {part.part}
        </span>
      ))}
    </span>
  );
}
