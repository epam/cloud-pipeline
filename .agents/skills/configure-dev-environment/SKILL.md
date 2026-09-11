---
name: configure-dev-environment
description: Learn which toolchains and environments this workstation has and how to invoke them, or set them up when they are missing. Load this before building, testing or running anything in this repository.
---

# Configure the development environment

Scope-driven and **incremental**. A first run may set up the UI only; a later "install Java please"
adds that scope and leaves the rest alone. Never install outside the scope the user picked.

**Attended only.** Every step here waits on a person — the scope, the version manager, a shell
profile edit. Unattended (`CI` or `GITHUB_ACTIONS` set) there is nobody to ask, so run the probe,
report what is missing, and stop. Never install anything on a CI runner: its toolchain belongs to the
workflow that defines it, not to an agent.

## 1. Read the record first

`.agents/localenv/` (gitignored) holds what was decided on *this* workstation — the version manager
per language, the env names, where `JAVA_HOME` was set. Read it before probing, so a repeat run
reports the delta instead of re-asking. Absent means first run.

## 2. Probe

```bash
bash .agents/skills/configure-dev-environment/scripts/probe-env.sh
```

Reports every tool with its version, the available package/version managers, and the platform. Never
guess from `.agents/localenv/` alone — it records intent and can be stale.

## 3. Ask the scope — one question

**Ask what the user will work on, not what to install.** One multi-select, in the areas of the
platform they think in — never a series of per-tool questions. The toolchain follows from the answer,
so the tool column below is yours to derive, not theirs to choose.

| Offer this | It needs | Because |
|---|---|---|
| **GUI (web)** | **Node 14** | `client`, `data-sharing-service/client` — Webpack 4/2 + React 15, and Node ≥16 breaks the build |
| **API & services (Java)** | **JDK 8** | every Spring Boot module; the Gradle 4.10.2 wrapper does not run on JDK 9+ |
| **Database / migrations** | JDK 8, Docker | Flyway runs from `api`; a local Postgres is easiest as a container |
| **`pipe` CLI & Python services** | 2.7-era and 3.x | `pipe-cli`, `workflows/pipe-common`, `storage-lifecycle-service`, `fs-browser`, `git-reader` |
| **Desktop / viewer apps** | Node 16–18 | `cloud-pipeline-webdav-client` (Electron), `hcs-image-viewer`, `fs-browser/fs-browser-client` |
| **Documentation** | mkdocs | `docs/` |
| **Docker images** | a container runtime | `deploy/docker/*` |
| **GitHub CLI** | `gh`, authenticated | the only supported way to touch issues, PRs and checks from an agent session |
| **Browser-check the GUI** | Playwright's Chromium, via the `playwright` MCP server | `verify-client-live` needs an actual browser to open pages in, not just a running dev server |

Mark each option with what the probe already found — "GUI (Node 14 missing)", "Docker (ready)" — and
say up front which areas are already complete so nobody picks a no-op.

### The question budget

After the scope question, ask **only** where the answer is genuinely the user's and cannot be derived:
the Python manager, the Docker runtime, and permission before editing a shell profile. Everything
else you decide from the probe and state in the summary. Batch what remains into one round rather
than one question per tool, and skip a question outright when the probe already answers it — pyenv
installed and no conda is not a choice worth interrupting for.

Node 14 and Node 18 coexist: that is what nvm is for. Do not "upgrade" `client` to a newer Node.

## 4. Install, per scope

List everything you are about to install — tool, version, manager, and anything it changes outside
the repo — and get one approval for the list. Then install it.

Come back to the user only when something does not match the list: a version that is not available,
an install that fails, a shell profile you need to edit, a port already in use. Do not ask twice
about a line they already said yes to.

Where a choice below is the user's, put it in the list or in the step-3 questions — never pick a
version manager for them.

### Node — nvm, plus a committed `.nvmrc`

```bash
nvm install 14 && nvm use 14        # ui
nvm install 18                      # ui-modern
```

If the probe already lists the version, `nvm use` it rather than installing it again — the active
version says nothing about what is installed.

Install nvm itself only if absent, from its own installer (not brew — brew's nvm needs manual shell
wiring). Write `client/.nvmrc` containing `14` if it does not exist; that file **is** committed —
mention it as a repo change when handing back. Then `cd client && npm install`.

### Java — JDK 8, manager depends on the workstation

Pick by what the probe found, in this order, and say which you chose and why:

1. **SDKMAN present** → `sdk install java 8.0.<latest>-tem` — best when other JDKs are in play.
2. **macOS + Homebrew** → `brew install --cask temurin@8`, then `JAVA_HOME=$(/usr/libexec/java_home -v 1.8)`.
3. **Linux** → the distro's `temurin-8-jdk` / `openjdk-8-jdk`.
4. **Neither** → offer to install SDKMAN, or hand the user the Adoptium download link.

`JAVA_HOME` must be exported in the user's shell profile — ask before editing it, and record which
file you touched. Verify with `java -version` reporting `1.8` **and**:

```bash
./gradlew --version && ./gradlew :core:compileJava
```

The wrapper is the only supported entry point. Do not install a standalone `gradle`; a system Gradle
on `PATH` will not match the 4.10.2 pin and is a known source of confusing failures.

### Database — Postgres, where the repo dictates most of it

The connection is **not** a choice. `api/profiles/dev/application.properties` and
`api/src/test/resources/test-application.properties` hardcode port `5432` and the database names;
only the test profile's user and password are env-overridable (`CP_API_TEST_DB_USER`,
`CP_API_TEST_DB_PASSWORD`), and both profiles default to `pipeline`/`pipeline`. Read both files —
never copy the values from here.

1. **If something already answers 5432, use it** — ask before adding a second server. A container
   publishing `0.0.0.0:5432` starts *successfully* beside a native postmaster bound to `127.0.0.1`
   and then quietly loses; the symptom is `password authentication failed`, which reads like a wrong
   password rather than the wrong server. Check `ps aux | grep -i postgres` before touching
   credentials.
2. Otherwise ask: container or native. Match the version the platform deploys — `PSG_VERSION` in
   `deploy/contents/install/install-config`.
3. Create the role and **two** databases, one per profile — the dev profile's and the test profile's,
   named as those files say.

Flyway runs at test startup; there is no Gradle Flyway task, and the migrations are append-only
(root `AGENTS.md`). Verify with one scoped DAO test, never the full `:api:test` suite:

```bash
./gradlew :api:test --tests "com.epam.pipeline.dao.dts.DtsRegistryDaoTest"
```

### Python — per-subproject isolation, manager is the user's choice

Ask: **pyenv + venv** (interpreters isolated, venv per subproject) or **conda/mamba** (one env per
subproject). Never install into the system Python.

`pipe-cli/requirements.txt` pins a Python-2.7-era set (`click==6.7`, `boto3==1.6.9`). If that
subproject is in scope and no 2.7 interpreter exists, say so plainly. The Python 3 subprojects
(`storage-lifecycle-service`, `fs-browser`, `git-reader`) are unaffected.

On Apple Silicon, conda-forge has no arm64 build of anything below Python 3.8. A 2.7 or 3.6 env must
therefore be `osx-64`, running under Rosetta 2, with `subdir` pinned in that env's own `.condarc` so
later `conda install` calls stay on the same CPU. pyenv often cannot build 2.7 there at all.

Create one env per subproject, named after it, and record the names. A venv inside the repo must be
gitignored — check before creating it.

Check each env works before you make the next one.

The dependency lists in these subprojects are old and were written on other machines —
`git-reader/setup.py` is a Linux `pip freeze`, and some of its pins cannot build on macOS at all. So
you will often end up installing an env with a few packages skipped. Importing the package is the
only way to find out whether the skipped ones mattered.

Run the subproject's tests if it has a `tests/` directory; otherwise just import it:

```bash
cd <subproject> && PYTHONPATH=. python -m pytest
cd <subproject> && PYTHONPATH=. python -c 'import <pkg>'
```

Use `PYTHONPATH=.` and not `pip install -e .` — `*.egg-info` is not in `.gitignore`, so an editable
install drops untracked files into the working tree.

When a package will not install, stop and ask — never skip it quietly. Lay out the options that
actually apply on this platform, with what each costs: dropping the package (say which imports would
break), a different interpreter or arch, relaxing the pin, skipping the subproject. Whichever the user
picks, name every skipped package in `.agents/localenv/`; an env with holes in it is fine, an
*undocumented* one is not.

Same rule when the tests fail. If the cause is the env, ask before working around it. If the code is
already broken, that is a legitimate result: write down which tests failed and why, leave the bug
alone, and move on to the next env.

Skipping a subproject is a normal outcome — the Verify step treats a declared skip as correct. What it
does not accept is a scope reported as ready when it is not.

### Docs — mkdocs via pipx

`pipx install mkdocs`, then the theme and plugins `docs/mkdocs.yml` actually declares — read the file
rather than assuming. Install pipx first if absent. Verify with `mkdocs build` from `docs/`;
`docs/site` is already gitignored. Never run the Gradle `buildDoc` task.

### Docker — ask which runtime

Docker Desktop, colima, or Rancher Desktop — the user's call, then install via brew. If a working
daemon already answers `docker info`, change nothing regardless of which runtime it is.

### GitHub CLI — `gh`, then an interactive auth step

Install `gh` itself like any package — `brew install gh` on macOS, the distro's `gh` package on
Linux (Debian/Ubuntu needs GitHub's own apt repo added first; follow `cli.github.com`'s install
instructions rather than guessing a package name).

Authenticate with the device-code flow, and run it in the background — it blocks until the user
finishes in the browser:

```bash
gh auth login --hostname github.com --git-protocol https --web
```

Use these exact flags — without them `gh` prompts for protocol/editor choices with no TTY to answer.
Read its output for the one-time code and URL (they may take a moment to appear) and put both
directly in your reply to the user. Wait for it to finish, verify with `gh auth status`, and report
success or failure. Device codes expire in ~15 minutes; if that lapses, kill it and re-run.

Never pass `--with-token` or ask the user for a personal access token in chat. Record only the
account `gh auth status` reports — never the token, and never read `gh`'s own credential store
(`~/.config/gh/hosts.yml`).

### Browser verification — Playwright's own Chromium

The MCP server needs Node **≥18** on `PATH` (its own `engines` field) — separate from `client`'s
pinned Node 14, and not read from a `client`-scoped `nvm use`. Check the ambient default
(`node --version`, outside `client/`) first; nothing to install if it's already ≥18.

Ask headed (visible) or headless as the default, and write it — together with a fixed `outputDir`,
so snapshots and screenshots land under the gitignored `.agents/.temp/` rather than the repo
root — to `.agents/localenv/playwright-mcp-config.json`:

```json
{
  "browser": {"launchOptions": {"headless": false}},
  "outputDir": "<repo root>/.agents/.temp/.playwright-mcp"
}
```

Register the server at user/global scope (every project, not just this repo) with whichever client
is running this skill, pointing every vendor's registration at that same file — one setting, not one
copy per vendor. Confirm before running, real downloads either way:

```bash
# Claude Code
claude mcp add playwright --scope user -- npx @playwright/mcp@latest --config <absolute path to the .agents/localenv/playwright-mcp-config.json above>
```

```json
// Cursor — add to ~/.cursor/mcp.json
{"mcpServers": {"playwright": {"command": "npx", "args": ["@playwright/mcp@latest", "--config", "<same absolute path>"]}}}
```

VS Code Copilot: command palette → **MCP: Open User Configuration** → add the same `command`/`args`
under `servers`.

Then, regardless of client, install the browser binary itself:

```bash
npx -y playwright install chromium
```

**Changing any of this later** ("switch Playwright to headless", "run the browser check headed")
means editing the same file — the MCP server only reads it at its own startup, so nothing here ever
takes effect in the session making the change. Say so plainly and tell the user to exit and relaunch
before it applies; don't imply the new setting is already active.

## 5. Record what was decided

Write one file per scope under `.agents/localenv/` — `node.md`, `java.md`, `python.md`,
`database.md`, `gh.md`, … Each opens with the same four things, and whatever else that scope needs
below them:

- **Scope** — which subprojects it serves
- **Manager and versions** — exactly what is installed
- **Outside the repo** — the shell profile, service or port it touches, or none
- **Verified** — the date, and the command that proved it

Update a file in place on a repeat run; do not append a second history section.

`README.md` is the index, written on first run: that the directory is per-workstation, gitignored and
written by this skill, plus a row for **every** file in it — not only the `.md` ones. A tool config
dropped here without a row is invisible to the next run. The probe prints the directory listing;
compare it against the table.

Never record a token, a superuser password, or any credential the repo does not already hardcode.

The directory is already in `.gitignore`. Verify that, and never commit anything under it.

## 6. Hand back

What was installed, what was **already** present and skipped, every choice the user made (so the
record can be checked against it), and what remains uninstalled for the scopes not picked. If a tool
could not be installed, say which and why — an absent toolchain is a legitimate outcome here, and
the Verify step treats a declared skip as correct.
