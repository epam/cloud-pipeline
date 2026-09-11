# client/AGENTS.md

The main Cloud Pipeline web GUI.

**Setup, env vars, the dev proxy and every `npm` script are documented in `README.md` in this
directory.** Node 14 is the supported version; the `.env` files this app reads are local-only and
agents must not open them.

## Stack

React 15 with MobX 3: **no hooks**, and the decorator API (`@observable`, `@computed`, `@action`,
`@inject`, `@observer`) for anything that needs one. Write a new component as a plain function when
it holds no local state and needs no lifecycle method; reach for a class once it does. Decorators only
apply to classes, so a stateless function that still needs to react to observables or reach a store is
wrapped with the plain-call form instead — `observer(fn)`, `inject(...)(fn)` — never `@observer`/
`@inject` on a function. `src/components/billing/reports/instance-report.js` does exactly this:
`const ResourcesSubData = observer(renderResourcesSubData);` over a plain function, alongside
`inject(...)(...)`/`observer(...)` wrapping the class components in the same file. `antd` is pinned to
2.13.14 and `react-router` to 3.0.2, both of which differ substantially from their current majors —
**check the installed package's behaviour, not the library's current docs.** Versions are in
`package.json`.

**Do not introduce hooks, or a dependency that requires React 16+.** Upgrading React here is a
project, not a side effect of a feature.

## Layout

```
src/
├── models/        API-facing stores — one directory per REST domain
│   └── basic/     Remote.js, RemotePost.js, Authorization.js — the base classes
├── components/    views, one directory per feature area
│   └── main/      Root.js (the store Provider), App.js, AppRouter.js
├── utils/         shared helpers (roleModel, localization, multizone, ...)
├── themes/        the three predefined themes, and the LESS they compile to
├── staticStyles/  global CSS not scoped to a component
└── config.js      SERVER / API_PATH
```

`components/`'s feature areas today: `ai-chat`, `applications`, `billing`, `cluster`, `main`,
`pipelines`, `plugins`, `roleModel`, `runs`, `search`, `settings`, `special`, `tools`,
`versioned-storages`. Add the name here when a new one lands.

`models/` has one directory per REST domain instead (~40 today) and isn't enumerated: it tracks the
API's growth directly and changes several times a year. `ls src/models` is the source of truth for
what exists.

## The model layer — one class per REST call

This is the dominant idiom and new API access should follow it. Each model is a class extending
`Remote` (GET) or `RemotePost` (POST/PUT/DELETE) from `src/models/basic/`, whose only job is to
set `this.url`:

```js
import Remote from '../basic/Remote';

export default class FolderLoad extends Remote {
  constructor (id) {
    super();
    this.url = `/folder/${id}/load`;
  }
}
```

Name the file after the operation, not the entity — `models/pipelines/` contains `PipelineClone`,
`PipelineFileDelete`, `PipelineGenerateReport`, and so on. Put it in the directory matching its
REST domain.

`Remote` gives you observable `pending`, `loaded`, `value`, `error`, `failed`. Useful behavior to
know before working with it:

- **Fetching is lazy and implicit.** Reading `.pending`, `.loaded`, `.value`, or `.response`
  triggers a fetch if one hasn't happened. A component that merely observes `.value` causes the
  request. Set `static auto = true` to fetch in the constructor instead.
- `postprocess(value)` returns `value.payload` by default — override it to reshape a response.
- `update()` treats `status === 'OK'` as success and anything else as failure, and a `401`
  triggers a redirect to `${SERVER}/saml/logout`. Endpoints that don't follow the platform's
  `Result<T>` envelope need `isJson`/`postprocess` overrides.
- `invalidateCache()` marks the model for refetch; `fetchIfNeededOrWait()` avoids a duplicate
  in-flight request. `silentFetch()` refreshes without touching `pending`.

## Stores and components

Global store instances are constructed once and provided in `src/components/main/Root.js` — a
single `<Provider>` of roughly fifty stores. Components reach them with `@inject`:

```js
@inject('preferences', 'pipelines')
@observer
export default class MyView extends React.Component { ... }
```

If you add a global store, register it in `Root.js`. For anything request-scoped, instantiate the
model in the component instead of adding to the global set — that Provider list is already large.

Some stores are injected under a constant rather than a literal name (`HiddenObjects.injectionName`,
`CURRENT_USER_ATTRIBUTES_STORE`). Grep `Root.js` for the injection name you need rather than
guessing it from the store's file name.

## Styling and theming

Three layers, and which one you touch decides whether the result follows the user's theme:

- **`src/themes/styles/*.less`** — the themable layer. It defines ~300 global `cp-*` classes
  (`cp-title`, `cp-primary`, `cp-billing-layout`, …) in terms of LESS variables such as
  `@primary-color` and `@application-background-color`. Nearly 400 component files apply those
  classes.
- **`src/themes/<theme>/index.js`** — `light-theme`, `dark-theme`, `dark-dimmed-theme`: each is a
  map from those variables to concrete values. Themes are compiled and injected into a `<style>`
  element **at runtime** by `src/themes/utilities/inject-theme.js`, scoped by theme identifier.
- **A component's own `.css`** — not themable. A colour hardcoded here stays the same in the dark
  themes.

So: to style something that must follow the theme, add or reuse a `cp-*` class in
`src/themes/styles/`; use component CSS for layout and geometry.

**A `.less` edit does nothing until the template is regenerated.**
`src/themes/utilities/theme.less.template.js` is generated from `src/themes/styles/` by
`npm run gui-themes-prepare` (`scripts/gui-themes/generate-theme-template.js` writes it), and it is
committed. Neither `npm start` nor `npm run build` regenerates it — so a themed change means
editing the LESS, running that script, and committing the regenerated template as well. The same
command also regenerates each theme's `index.js` from its `_dev_/*-variables.less` source, so
editing a theme's variables file needs this step too, not just editing a shared style.

## Permissions in the UI

`src/utils/roleModel.js` decodes the permission bitmask the API returns: `readAllowed`,
`writeAllowed`, `executeAllowed`, the corresponding `*Denied`, `isOwner`, `userIs`/`hasRole`,
`ROLES`, and mask-building helpers. Use these — don't hand-roll bit arithmetic on `mask`.
`src/components/roleModel/` is the permissions *editor* (`PermissionsForm`), a different thing.

UI permission checks are **presentation only**; hiding a button is not a security control. The real
boundary is `api/src/main/java/com/epam/pipeline/acl/`.

## Platform preferences

`src/models/preferences/PreferencesLoad.js` is a singleton — `preferences` in `Root.js`'s Provider —
not a per-use `Remote` subclass. `getPreferenceValue(key)` is called both through this file's named
`@computed` getters and directly at the call site (`preferences.getPreferenceValue('some.key')`) — both
exist in this codebase already, but a new preference gets a getter here, not a direct call. Check this
file first: it might already have one.

## Checks

Run from this directory. `npm start` and `npm run build` are covered in `README.md`.

```bash
npm test              # jest, jsdom, no network — src/**/*.test.{js,jsx} and scripts/**/*.test.js
npm run lint          # eslint over src/, test/ and scripts/ — .js and .jsx — against the baseline
npm run stylelint     # the same, for src/**/*.css and src/**/*.less
npm run lint:fix
```

`test/AGENTS.md` documents the test harness itself: the module map, the rules a test file follows,
and the split between `npm test` (hermetic, jsdom) and `npm run test:live` (opt-in, against a real
deployment, configured only by an untracked `.env.test.local`). Read it before adding a test.

Lint is the other real check for this directory. The rules are in `.eslintrc.js`: `standard` +
`standard-react`, `max-len` 100, `semi` always, `object-curly-spacing` never, double quotes in JSX.
`react/prop-types` and `react/no-unused-prop-types` are **off**, so missing propTypes are never
reported. `.eslintignore` excludes the generated files — the two jison parsers under
`src/utils/filter/` and the generated `src/themes/utilities/theme.less.template.js`.

The config is JavaScript rather than JSON for one reason, and it is worth knowing before you touch
it: **JSX indentation belongs to `react/jsx-indent`, not to core `indent`.** The two configs
disagree about how a JSX element passed as a call argument is indented, and where they disagree no
indentation satisfies both — `eslint --fix` used to oscillate between them and give up. So `indent`
carries an `ignoredNodes` list of the JSX node types, built by spreading `eslint-config-standard`'s
own options so they cannot drift apart on an upgrade.

### The lint baseline

`src/` carries a large amount of pre-existing lint debt, and rather than soften a rule or stop
checking the files that carry it, the counts are recorded in `.eslintbaseline.json` and
`.stylelintbaseline.json`. `npm run lint` and `npm run stylelint` go through `scripts/lint.js` and
`scripts/stylelint.js`, which fail **only on problems the baseline does not already record** — so
every rule keeps its full severity for new code, including new code added to an old file. A parse
error is never baselined: it means the file was not really checked.

```bash
npm run lint:all         # every problem, baselined debt included; exits 1 while any remains
npm run lint:baseline    # re-record the baseline
```

**The baseline may only shrink.** Growing it is how a real problem gets buried, so if `npm run lint`
reports something, fix it — don't re-record. To pay down debt in a file:

1. `node_modules/.bin/eslint <file>` — see what it actually has
2. fix it, `--fix` first if the rules are formatting ones
3. `npm run lint:baseline`, and commit the shrunk baseline together with the fix

When a file improves without the baseline being re-recorded, the run says so and still passes:
cleaning lint up must never be the thing that breaks someone's build.

## Pitfalls

- **ESLint 5 defaults to `--ext .js`.** `scripts/lint.js` names `.jsx` explicitly, so `npm run lint`
  covers it — but any bare `eslint <directory>` you run by hand will silently skip every `.jsx` file
  under it. Pass `--ext .js,.jsx`, or name the file.
- **A raw `stylelint` call takes one parser.** That is why `scripts/stylelint.js` runs two passes,
  `src/**/*.css` and then `src/**/*.less --syntax less`. Checking the LESS by hand means passing
  `--syntax less` yourself; without it, stylelint reports parse noise instead of findings.
- **`:client:buildUI` wipes `api/src/main/resources/static/`** except an explicit exclude list in
  `client/build.gradle`. Adding a newly served artifact means editing that exclude list *and*
  `api.security.public.urls` in `api/profiles/*/application.properties`. That task also builds with
  `PUBLIC_URL=/pipeline`, which is why asset paths must go through the bundler rather than being
  written absolute.
- Styling is mixed: 345 component CSS files, `staticStyles/`, LESS under `themes/`, and one `.scss`
  file. Match the file you're editing rather than introducing a new approach.
