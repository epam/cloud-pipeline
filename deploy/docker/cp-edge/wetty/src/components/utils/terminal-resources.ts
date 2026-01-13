import type {ThemeResource} from "./types.ts";
import {fonts} from "./fonts.ts";

const resources: ThemeResource[] = [
  {
    rel: 'preconnect',
    href: 'https://fonts.googleapis.com',
  },
  {
    rel: 'preconnect',
    href: 'https://fonts.gstatic.com',
  },
  ...fonts
];

export function enableResources(doc: Document) {
  for (const resource of resources) {
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

enableResources(document);
