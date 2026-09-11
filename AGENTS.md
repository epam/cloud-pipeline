# AGENTS.md

Repository conventions for AI coding agents. Any directory may carry its own `AGENTS.md` that adds
detail for that subtree and takes precedence there; some are nested well below a module root. Check
for one in every directory you change.

## What this is

Cloud Pipeline wraps AWS/GCP/Azure compute and storage into a single platform for scientific
computing: data processing pipelines, object/file storage management, Docker-based tool
management, and GUI applications (noVNC/NoMachine/web endpoints) launched as containers on
autoscaled cloud nodes.

A **polyglot monorepo** (Java + Spring Boot, React, Python 2/3, shell) built by a single root
Gradle build. Version is `0.17.x` (declared in the root `build.gradle`).

## Repository structure

| Directory | What it is |
|---|---|
| `api` | the REST API server, Spring Boot — controllers, `manager/`, `dao/`, the `acl/` layer and the Flyway schema. The other services are its clients, and `elasticsearch-agent` and `notifier:smtp` also read its database directly |
| `billing-report-agent` | Spring Boot; prices platform usage and writes the billing documents into Elasticsearch, one index per day (`cp-billing-*-YYYY-MM-DD`; only the storage-requests index is monthly) |
| `client` | the GUI — React with MobX, Ant Design and Webpack 4 on Node 14, served under `/pipeline` |
| `cloud-pipeline-common` | `model` — the entities the standalone services share; `event-sourcing-java-client` — a Redis-Streams event bus, used only by `api` today, to invalidate ACL caches across its replicas |
| `cloud-pipeline-webdav-client` | Electron desktop app ("cloud-data") that browses platform storages over WebDAV |
| `core` | `entity/`, `dto/`, `vo/`, `utils/` shared by `api` and `notifier:smtp` — no service logic |
| `data-sharing-service` | a separate Spring Boot API (`api`) and React GUI (`client`) over data storages, deployed as `cp-share-srv`. Package `com.epam.pipeline.external.datastorage` — no WebDAV in it |
| `data-transfer-service` | "DTS" — the service run on-premise, moving data between local filesystems and cloud storages, and reporting to `api` |
| `deploy` | a `cp-*` Dockerfile per platform component — including third-party ones (Clair, GitLab, the IdP) and `deploy/docker/cp-tools/`, the end-user tool images — plus the `pipectl` installer and CI helper scripts (the workflows themselves are in `.github/workflows/`) |
| `docker-comp-scan` | scans a container image with OWASP dependency-check and reports its packages — Python, R, OS and NVIDIA/CUDA are the enabled analyzers |
| `e2e` | `cli` (pytest), `gui` (its own Gradle wrapper) and `load`. Long-running, and never yours |
| `elasticsearch-agent` | Spring Boot; indexes platform entities into Elasticsearch, and `api` serves the searches over that index |
| `elasticsearch-common` | the shared search client, abstracting Elasticsearch V6 from OpenSearch V7 so callers write one query |
| `fs-browser` | python/flask service exposing a compute node's filesystem; needs `pipe` on the node. `fs-browser/fs-browser-client/` is its companion client subproject |
| `git-reader` | python/flask service reading pipeline repositories with GitPython, for what GitLab's own API cannot answer |
| `hcs-image-viewer` | React viewer for high-content-screening images. Its built bundle is committed into `client/public/hcs-image-viewer/` and the sharing-service client, so a change here is not picked up until both copies are rebuilt |
| `monitoring-service` | Spring Boot scheduled monitors — GPU usage, node pools, runs, users and platform usage credits |
| `notifier` | `smtp` — drains the notification queue `api` writes to the database, and delivers it by SMTP, by Microsoft Graph, or as an in-app notification |
| `pipe-cli` | the `pipe` client (Python) — storage transfer, run and cluster management; plus FUSE `mount/` and `pipe-omics/` |
| `scripts` | code that **ships in the distribution**, not developer tooling: `scripts/pipeline-launch/` (the job-container entrypoint, bundled into the API jar and fetched by every run), `scripts/autoscaling/`, the git/NFS role helpers and other service-container scripts |
| `shaded` | `opensearch` — the OpenSearch jar with its packages relocated, so it can coexist with Elasticsearch V6 |
| `storage-lifecycle-service` | Python app that executes the lifecycle rules set on a data storage — archiving (a rule's transitions, with user prolongation and notification) and restoring |
| `vm-monitor` | Spring Boot; reconciles the cloud provider's running VMs against the platform's nodes and runs, and also checks certificate expiry, filesystem space and k8s state, notifying on a mismatch |
| `workflows` | `pipe-common` (the `pipeline` package, in every job container), `gpustat`, `pipe-demo`, `pipe-templates`, `report-templates` |

Everything else is support: `config/` (the checkstyle and pmd rulesets), `docs/` (the mkdocs manual
and release notes), `gradle/` (the wrapper), `jwt-generator/` (a CLI that mints API JWTs),
`vscode-cloud-pipeline/` (the "Cloud Pipeline Remote" VS Code extension), `.github/` (the CI
workflows, and Copilot's instructions) and `.agents/`, `.claude/`, `.cursor/` (agent instructions and
skills).

Names that mislead, or hide a coupling:

- **`api`** — its layering is a security boundary: authorization belongs in
  `api/src/main/java/com/epam/pipeline/acl/`, where 50 of the 54 annotated classes are. Four older
  `*ApiService` and manager classes still carry `@PreAuthorize` outside it; don't add a fifth. The
  Flyway migrations under `api/src/main/resources/db/migration/` are append-only.
- **`core`** vs **`cloud-pipeline-common:model`** — both declare the same packages, and they have
  drifted apart, so the same class name in the two modules is often two different classes. No module
  depends on both, which is why nothing has ever collided.
- **`billing-report-agent`** — produces no reports. `api` queries the documents it writes and builds
  the reports from them.
- **`workflows/pipe-common`** — not a workflow. It is the `pipeline` Python package injected into
  **every** job container, so a break here breaks every run on the platform rather than one feature.
- **`scripts/`** — not developer tooling despite the name: it ships in the distribution, and
  `scripts/pipeline-launch/` runs inside every job container. A script supporting a development
  procedure belongs in that procedure's skill, under `.agents/skills/<name>/scripts/`.
- **`deploy/docker/cp-edge`** — the nginx+Lua (OpenResty) reverse proxy that routes users into
  running job containers. It owns the proxy rules, but the endpoint URLs it serves are built in `api`
  (`EdgeServiceManager`, `PipelineRunServiceUrlManager`), so a routing change often needs both.
- **`data-transfer-service`** and **`data-sharing-service`** are unrelated despite the near-identical
  names.

## Development process

**A task that changes any file here — code, documentation or agent instructions — runs the
`implement-task` skill** (`.agents/skills/implement-task/SKILL.md`). Load it before you start working.
Answering a question and reading code need none of it; reviewing someone else's diff may, to check
what the change owed.

**`git add` a new file as soon as you create it.** Untracked, it is invisible to the checks, to your
own reading of the diff, and to the commit.

**`.agents/localenv/` records how this workstation is set up** — the version manager per language, the
env names, where `JAVA_HOME` lives. It is gitignored and per-developer, so read it rather than assuming
a toolchain, and never commit anything under it. **If the directory does not exist, nobody has
configured this machine yet: run the `configure-dev-environment` skill before any command that needs a
toolchain.** Attended only — unattended, the runner's toolchain belongs to the workflow, so just report
what is missing.

## Files agents must not read

`.env`, `.env.local` and `.env.<environment>.local` files, `secrets/`, keystores and private keys,
and the credential stores under `~` — `.ssh`, `.aws`, `.azure`, `.config/gcloud`, `.config/gh`,
`.kube/config`, `.gnupg`. A `.env.example` is a template, not a secret, and is fine to read.

## Anti-patterns

Things that have gone wrong here before, or that are expensive to undo:

- **Don't weaken or skip a test to make a check pass.** Fix the code, or report the change as
  blocked.
- **Don't start a change without an issue number, and never invent one.** Ask for it, or offer to
  file the issue — the `implement-task` skill → "Issue number".
- **Don't carry on in the branch you were handed.** A task gets its own branch, and whether to cut it
  from a branch that isn't shared — and whether to work in a worktree — are questions to ask, not
  defaults to assume — the `implement-task` skill → "Workspace".
- **Don't skip the test because the code was already there.** Every code change needs one — the
  `implement-task` skill → "Tests".
- **Don't conclude that a change needs no documentation.** An area with no page today is the gap
  itself, not the exemption — the `implement-task` skill → "Docs".
- **Don't edit an applied Flyway migration.** Add a new one.
- **Don't modernize a build file casually.** `compile`/`testCompile` and `bootRepackage` are still in
  use, and a construct that looks obsolete may be load-bearing. Toolchain levels are per-module and
  mid-migration, so read that module's own `build.gradle`.
- **Don't run deployment tooling** (`pipectl`, `docker push`, `deploy/pipectl/build-pipectl.sh`,
  `aws s3 rm|mv`) as part of a code change.
- **Don't document what you did not verify.** Prefer a command that finds the answer over a copied
  value that will drift.
- **Don't claim a command passed if you did not run it.** Say what you actually ran.
