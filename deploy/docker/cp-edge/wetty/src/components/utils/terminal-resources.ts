import type {HtermTerminal, ThemeResource} from './types.ts';

const fonts: ThemeResource[] = [
  {
    rel: 'preconnect',
    href: 'https://fonts.googleapis.com',
  },
  {
    rel: 'preconnect',
    href: 'https://fonts.gstatic.com',
  },
  {
    rel: 'stylesheet',
    href: 'https://fonts.googleapis.com/css2?family=JetBrains+Mono:ital,wght@0,100..800;1,100..800&display=swap'
  },
];

export function enableResources(term: HtermTerminal) {
  const doc = term.document_;
  for (const resource of fonts) {
    const fontLink = document.createElement("link");
    fontLink.rel = resource.rel;
    if (typeof resource.href === 'string') {
      fontLink.href = resource.href;
    } else {
      fontLink.href = resource.href.href;
    }
    fontLink.crossOrigin = 'anonymous';
    doc.head.appendChild(fontLink);
  }
}
