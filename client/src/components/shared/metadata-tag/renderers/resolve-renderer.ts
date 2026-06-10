import type {MetadataValueContext, MetadataValueRenderer} from './types.ts';
import {getRegisteredRenderer} from './registry.ts';
import {DefaultRenderer} from './default-renderer.tsx';
import {JsonRenderer} from './json-renderer.tsx';
import {SecretRenderer} from './secret-renderer.tsx';
import {isJsonString, isJsonType} from './utilities.ts';

export function resolveRenderer(context: MetadataValueContext): MetadataValueRenderer {
  const registered = getRegisteredRenderer(context.tag);
  if (registered) {
    return registered;
  }
  if (context.secret) {
    return SecretRenderer;
  }
  if (isJsonType(context.type) || isJsonString(context.value)) {
    return JsonRenderer;
  }
  return DefaultRenderer;
}
