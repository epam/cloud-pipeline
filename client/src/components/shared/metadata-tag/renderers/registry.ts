import type {MetadataValueRenderer} from './types.ts';

const tagRenderers = new Map<string, MetadataValueRenderer>();

export function registerRenderer(tag: string, renderer: MetadataValueRenderer): void {
  tagRenderers.set(tag, renderer);
}

export function unregisterRenderer(tag: string): void {
  tagRenderers.delete(tag);
}

export function getRegisteredRenderer(tag: string): MetadataValueRenderer | undefined {
  return tagRenderers.get(tag);
}

export function getRegisteredRendererTags(): string[] {
  return [...tagRenderers.keys()];
}
