
const mainFb = 'Courier New';
const genericFb = 'monospace';
const mainTerminalDefault = "'DejaVu Sans Mono'";
const terminalDefault = `${mainTerminalDefault}, "Noto Sans Mono", "Everson Mono", FreeMono, Menlo, Terminal, monospace`;

export function addFallbacks (family: string) {
  if (terminalDefault.startsWith(family)) {
    return terminalDefault;
  }
  const families = family.split(',').map(f => f.trim().replace(/^['"]|['"]$/g, ''));
  if (!families.includes(mainFb)) {
    families.push(mainFb);
  }
  if (!families.includes(genericFb)) {
    families.push(genericFb);
  }
  return families.map(f => (f.includes(' ') ? `'${f}'` : f)).join(', ');
}

export function removeFallbacks (family: string) {
  const families = family.split(',').map(f => f.trim().replace(/^['"]|['"]$/g, ''));
  const filtered = families
    .filter(f => f !== mainFb && f !== genericFb)
    .map(f => (f.includes(' ') ? `'${f}'` : f));
  return filtered[0];
}

export function isFontAvailable(family: string): boolean {
  const first = removeFallbacks(family);
  if (family === mainFb || family === genericFb) {
    return true;
  }
  if (!first) return false;
  try {
    if ('fonts' in document && typeof document.fonts?.check === 'function') {
      return Boolean(document.fonts.check(`12px "${first}"`));
    }
  } catch {
    // noop
  }
  return false;
}

export const FONT_CHOICES: { label: string; family: string }[] = [
  { label: 'Fira Code', family: "'Fira Code'" },
  { label: 'JetBrains Mono', family: "'JetBrains Mono'" },
  { label: 'Source Code Pro', family: "'Source Code Pro'" },
  { label: 'IBM Plex Mono', family: "'IBM Plex Mono'" },
  { label: 'Inconsolata', family: "Inconsolata" },
  { label: 'Ubuntu Mono', family: "'Ubuntu Mono'" },
  { label: 'Menlo', family: "Menlo" },
  { label: 'DejaVu Sans Mono', family: "'DejaVu Sans Mono'"},
  { label: 'Monaco', family: "Monaco" },
  { label: 'Consolas', family: "Consolas" },
  { label: 'Liberation Mono', family: "'Liberation Mono'" },
  { label: 'Courier New', family: "'Courier New'" },
  { label: 'SF Mono', family: "'SFMono-Regular'" },
  { label: 'System Monospace', family: 'ui-monospace' },
  { label: 'Monospace (generic)', family: 'monospace' },
].filter(({family}) => isFontAvailable(family));

