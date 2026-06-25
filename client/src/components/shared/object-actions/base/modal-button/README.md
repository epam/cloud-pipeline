# Modal-button pattern for object-actions

`createActionButtonForModal` (`modal-button-action.tsx`) wires a button to a modal so callers never manage `open` state themselves. Use it for every modal under `src/components/shared/object-actions/`.

## How it works

```ts
createActionButtonForModal(ModalComponent, buttonLabel, buttonProps?)
```

Returns a self-contained `<XxxButton>` component. The button owns the `open` boolean and injects `open`, `onClose`, and `disabled` into the modal automatically. Callers only supply the modal's domain props.

```tsx
// src/components/shared/object-actions/pipeline/edit/index.tsx
export const PipelineEditButton = createActionButtonForModal(PipelineEditModal, 'Edit', {
  type: 'link',
});

// Call site — no open/onClose needed
<PipelineEditButton pipelineId={id} />;
```

## Modal component contract

The modal must extend `ActionModalBaseProps`:

```tsx
import type {ActionModalBaseProps} from '../../base/modal-button/modal-button-action.tsx';

export type PipelineEditModalProps = ActionModalBaseProps & {
  // domain-only props — open / onClose / disabled come from ActionModalBaseProps
  pipelineId?: number;
  parentFolderId?: number;
};
```

`ActionModalBaseProps` provides:

- `open: boolean`
- `onClose?: (event: MouseEvent | KeyboardEvent) => void`
- `disabled?: boolean`
- `className?: string` / `style?: CSSProperties`

## Accept ID or object (depends on action type)

### Edit / create modals — ID only

Edit and create modals accept an **ID** (and optionally a `parentFolderId` for new-item flows), then fetch the entity internally with React Query. Do not pass the full entity object — it couples the modal to the caller's loading logic.

```tsx
// ✅ correct — edit modal
export type ConfigurationEditModalProps = ActionModalBaseProps & {
  configurationId?: number;   // absent → create new
  parentFolderId?: number;    // for new-item placement
};

function ConfigurationEditModal({configurationId, parentFolderId, open, onClose}: ConfigurationEditModalProps) {
  const isNew = configurationId === undefined;
  useInvalidateDetailQueryOnOpen(open, configurationKeys.detail, configurationId);
  const {data: configuration, isFetching: loadPending} = useQuery(
    configurationQueryOptions(configurationId, {enabled: !isNew && open}),
  );
  ...
}
```

```tsx
// ❌ wrong for edit modals — forces the caller to load and pass the object
export type ConfigurationEditModalProps = ActionModalBaseProps & {
  configuration?: Configuration;
};
```

### Remove / delete modals — use `createRemoveObjectModal`

For standard "confirm and delete" (and optional "unregister") flows, use `createRemoveObjectModal` from `base/remove-object-modal/`. It handles ID-or-object resolution, conditional fetch, `message.loading` / `message.error`, cache invalidation (`libraryTreeKeys` + parent folder), and the optional `onRemove` callback.

```tsx
// configuration-remove-modal.tsx — delete only
const ConfigurationRemoveModal = createRemoveObjectModal({
  objectProp: 'configuration',
  loadFn: loadConfiguration,
  deleteFn: deleteConfiguration,
  queryKey: configurationKeys.detail,
  title: (configuration) => (
    <span>
      Are you sure you want to remove configuration <b>{configuration.name}</b>?
    </span>
  ),
  canRemove: (user) =>
    user.admin || (user.roles ?? []).some((r) => r.name === 'ROLE_CONFIGURATION_MANAGER'),
});

// pipeline-remove-modal.tsx — unregister + delete
const PipelineRemoveModal = createRemoveObjectModal({
  objectProp: 'pipeline',
  loadFn: loadPipeline,
  deleteFn: deletePipeline,
  unregisterFn: unregisterPipeline,
  queryKey: pipelineKeys.detail,
  title: (pipeline) => (
    <span>
      Do you want to delete pipeline <b>{pipeline.name}</b> with repository or only unregister it?
    </span>
  ),
  unregisterTitle: 'Unregister',
});
```

Wire it to a button with `createActionButtonForModal`:

```tsx
// configuration-remove-button.tsx
const ConfigurationRemoveButton = createActionButtonForModal(
  ConfigurationRemoveModal,
  <DeleteOutlined />,
  {danger: true},
);

// Standalone — pass ID, modal fetches
<ConfigurationRemoveButton configuration={id} />

// In-place inside an edit modal footer — pass object, no fetch
<ConfigurationRemoveButton configuration={configuration} onRemove={() => onDone()} />

// Parent can distinguish unregister vs delete
<PipelineRemoveButton pipeline={pipeline} onRemove={(unregistered) => { ... }} />
```

**Footer layout** (each action is independent — buttons appear only when that action is allowed):

- Delete only (`canRemove`, no `unregisterFn` or `!canUnregister`) → Cancel + Delete
- Unregister only (`canUnregister`, `!canRemove`) → Cancel + Unregister
- Both allowed → Cancel + Unregister + Delete

**`createRemoveObjectModal` options:**

| Option            | Type                                | Purpose                                                                          |
| ----------------- | ----------------------------------- | -------------------------------------------------------------------------------- |
| `objectProp`      | `string`                            | Domain prop name on the returned component (e.g. `'configuration'`)              |
| `loadFn`          | `(id) => Promise<Object>`           | API loader when an ID is passed                                                  |
| `deleteFn`        | `(id) => Promise<unknown>`          | Full delete API call                                                             |
| `unregisterFn`    | `(id) => Promise<unknown>`          | Optional unregister API call; enables Unregister button                          |
| `queryKey`        | `DetailQueryKeyFactory`             | Stable detail key for fetch + invalidation on open                               |
| `title`           | `ReactNode \| (obj) => ReactNode`   | Modal title (default: "Are you sure you want to delete **{name}**?")             |
| `unregisterTitle` | `ReactNode`                         | Unregister button label (default: `'Unregister'`)                                |
| `children`        | `ReactNode \| (obj) => ReactNode`   | Modal body (default: "This operation cannot be undone.")                         |
| `canRemove`       | `boolean \| (user, obj) => boolean` | Permission gate for Delete only (default: `true`)                                |
| `canUnregister`   | `boolean \| (user, obj) => boolean` | Permission gate for Unregister only (default: `true` when `unregisterFn` is set) |

The entity must satisfy `RemovableObject`: `{id: number; name: string; parent?: LibraryParentRef}`.

**ID-or-object behaviour (built in):**

- ID passed → fetch via `loadFn` when modal opens (`enabled: open && isId`).
- Object passed → use as-is, no fetch (for in-place use inside edit modals).
- `onRemove?: (unregistered: boolean) => void` — optional parent callback after success; `true` when Unregister was chosen, `false` when Delete was chosen.

**When NOT to use `createRemoveObjectModal`:** non-standard delete UI that the factory cannot express — e.g. extra form controls in the body (folder force-delete checkbox), conditionally hiding one of the action buttons per entity state (mirror storage), or more than two distinct action paths. Follow the same ID-or-object and `message.loading` / `message.error` conventions in a custom modal.

### Refresh data when the modal opens

Call `useInvalidateDetailQueryOnOpen(open, detailKey, id)` from `src/components/shared/object-actions/base/hooks.ts` so the modal re-fetches fresh data each time it opens. Keep a **stable query key** (`detailKey(id)`) and invalidate on open — do not embed open state or tokens in the query key.

```tsx
useInvalidateDetailQueryOnOpen(open, pipelineKeys.detail, pipelineId);
const {data: pipeline, isFetching: loadPending} = useQuery(
  pipelineQueryOptions(pipelineId, {enabled: !isNew && open}),
);
```

## File layout

```
object-actions/<domain>/<action>/
  index.tsx            ← re-exports Modal + creates Button via createActionButtonForModal
  <action>-modal.tsx   ← modal component (satisfies ActionModalBaseProps)
  types.ts             ← shared interfaces (if needed)
  <action>.module.css  ← scoped styles (if needed)
```

Remove modals using `createRemoveObjectModal` are typically a single `<domain>-remove-modal.tsx` (factory config only) plus a `<domain>-remove-button.tsx`.

## In-place delete actions

A remove button can be embedded inside another modal's footer (e.g. Delete inside Edit). Pass the entity object the parent already loaded — `createRemoveObjectModal` skips the fetch.

```tsx
// configuration-edit-modal.tsx footer
{
  canDelete && configuration && (
    <ConfigurationRemoveButton
      configuration={configuration}
      onRemove={() => onDone()}
      disabled={pending}
    >
      DELETE
    </ConfigurationRemoveButton>
  );
}
```

The remove modal owns the delete API call and cache invalidation. `onRemove` lets the parent react after success (close itself, refresh a list) without performing the delete.

## Mutation feedback (`message.loading` / `message.error`)

`createRemoveObjectModal` shows `message.loading` during delete/unregister ("Removing…" / "Unregistering…") and `message.error` on failure (keeps the modal open for retry). Edit/save modals follow the same pattern manually:

```tsx
const hide = message.loading(
  <span>
    Updating <b>{name}</b>...
  </span>,
);
try {
  await saveConfiguration(payload);
  onDone?.();
  onClose?.(e);
} catch (error) {
  message.error(<span>Error updating: {getErrorDescription(error)}</span>);
} finally {
  hide();
}
```

Always call `hide()` in `finally`.

## Rules

- **The modal owns no `open` state.** `createActionButtonForModal` owns it.
- **`onClose` is the single dismiss path.** Call `props.onClose(event)` on every dismiss (Cancel, backdrop, Escape). Never `setOpen(false)` inside the modal.
- **Edit modals: ID in, object fetched inside.** Accept `configurationId`, `pipelineId`, etc. Fetch with React Query (`useQuery` + `*QueryOptions`).
- **Remove modals: use `createRemoveObjectModal`.** Thin factory config per domain; accepts ID or object via `objectProp`. Custom delete UIs are the exception.
- **Domain props only on the button's type.** `open`, `onClose`, `disabled` are stripped automatically — do not redeclare them.
- **`onDone` for post-save callbacks.** Edit modals expose `onDone?: () => void` after a successful save.
- **`onRemove` / `onDeleted` for post-delete callbacks.** Remove modals perform delete/unregister themselves; expose `onRemove?: (unregistered: boolean) => void` so the parent can react after success. Separate from `onClose`.

## Examples

- `src/components/shared/object-actions/pipeline/edit/` — `PipelineEditModal` / `PipelineEditButton`
- `src/components/shared/object-actions/configuration/edit/` — `ConfigurationEditModal` / `ConfigurationEditButton`
- `src/components/shared/object-actions/configuration/remove/` — `createRemoveObjectModal` + `ConfigurationRemoveButton`
- `src/components/shared/object-actions/base/remove-object-modal/` — `createRemoveObjectModal` / `RemoveObjectModal` utility
