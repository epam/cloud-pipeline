import type {ReactNode} from 'react';
import highlightText from './highlightText';

export default function highlightTextFuzzy(name: ReactNode, search?: string): ReactNode {
  if (typeof name !== 'string' || !search?.trim()) return name;
  // Try shared substring highlight first
  const result = highlightText(name, search);
  if (result !== name) return result;
  // Fuzzy fallback: highlight individual matching characters in order
  const lowerName = name.toLowerCase();
  const lowerSearch = search.toLowerCase().trim();
  const segments: Array<{text: string; highlight: boolean}> = [];
  let pos = 0;
  let segStart = 0;
  for (const char of lowerSearch) {
    const charIdx = lowerName.indexOf(char, pos);
    if (charIdx === -1) return name;
    if (charIdx > segStart) {
      segments.push({text: name.slice(segStart, charIdx), highlight: false});
    }
    segments.push({text: name[charIdx], highlight: true});
    pos = charIdx + 1;
    segStart = charIdx + 1;
  }
  if (segStart < name.length) {
    segments.push({text: name.slice(segStart), highlight: false});
  }
  return (
    <>
      {segments.map((seg, i) =>
        seg.highlight ? (
          <span key={i} className="cp-search-highlight-text search-highlight">
            {seg.text}
          </span>
        ) : (
          seg.text
        ),
      )}
    </>
  );
}
