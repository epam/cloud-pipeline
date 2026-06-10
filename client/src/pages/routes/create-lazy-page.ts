import {lazy, type ComponentType} from 'react';

/**
 * Creates a code-split page component from a named export.
 * Vite emits a separate chunk per `import()` call site.
 */
function createLazyPage<
  Module extends Record<string, ComponentType<unknown>>,
  Name extends keyof Module,
>(loader: () => Promise<Module>, exportName: Name) {
  return lazy(() =>
    loader().then((module) => ({default: module[exportName] as ComponentType<unknown>})),
  );
}

export {createLazyPage};
