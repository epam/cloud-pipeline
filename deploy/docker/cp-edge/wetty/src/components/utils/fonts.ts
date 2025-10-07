import type {FontResource} from "./types.ts";

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

function buildFontResource(fontFamily: string, href: string): FontResource {
  return {
    fontFamily,
    rel: 'stylesheet',
    href: href,
  };
}

const jetBrainsMonoFont = buildFontResource(
  "JetBrains Mono",
  "https://fonts.googleapis.com/css2?family=JetBrains+Mono:ital,wght@0,100..800;1,100..800&display=swap"
);

const firaCodeFont = buildFontResource(
  "Fira Code",
  "https://fonts.googleapis.com/css2?family=Fira+Code:wght@300..700&display=swap"
);

const ibmPlexMonoFont = buildFontResource(
  "IBM Plex Mono",
  "https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:ital,wght@0,100;0,200;0,300;0,400;0,500;0,600;0,700;1,100;1,200;1,300;1,400;1,500;1,600;1,700&display=swap"
);

const ubuntuMonoFont = buildFontResource(
  "Ubuntu Mono",
  "https://fonts.googleapis.com/css2?family=Ubuntu+Mono:ital,wght@0,400;0,700;1,400;1,700&display=swap\" rel=\"stylesheet"
);

const inconsolataFont = buildFontResource(
  "Inconsolata",
  "https://fonts.googleapis.com/css2?family=Inconsolata:wght@200..900&display=swap"
);

const cascadiaMonoFont = buildFontResource(
  "Cascadia Mono",
  "https://fonts.googleapis.com/css2?family=Cascadia+Mono:ital,wght@0,200..700;1,200..700&display=swap"
);

const sourceCodeProFont = buildFontResource(
  "Source Code Pro",
  "https://fonts.googleapis.com/css2?family=Source+Code+Pro:ital,wght@0,200..900;1,200..900&display=swap"
);

const martianMonoFont = buildFontResource(
  "Martian Mono",
  "https://fonts.googleapis.com/css2?family=Martian+Mono:wght@100..800&display=swap"
)

export const fonts = [
  jetBrainsMonoFont,
  sourceCodeProFont,
  ibmPlexMonoFont,
  inconsolataFont,
  ubuntuMonoFont,
  firaCodeFont,
  cascadiaMonoFont,
  martianMonoFont,
];

export const FONT_CHOICES: { label: string; family: string }[] = [
  ...fonts.map((f) => ({ label: f.fontFamily, family: `'${f.fontFamily}'` })),
  { label: 'Menlo', family: "Menlo" },
  { label: 'DejaVu Sans Mono', family: "'DejaVu Sans Mono'"},
  { label: 'Monaco', family: "Monaco" },
  { label: 'Consolas', family: "Consolas" },
  { label: 'Courier New', family: "'Courier New'" },
  { label: 'SF Mono', family: "'SFMono-Regular'" },
  { label: 'System Monospace', family: 'ui-monospace' },
  { label: 'Monospace (generic)', family: 'monospace' },
].filter(({family}) => isFontAvailable(family));

