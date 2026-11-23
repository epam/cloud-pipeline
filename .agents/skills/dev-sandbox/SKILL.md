---
name: dev-sandbox
description: Create, check, connect to, extend or remove a throw-away AWS sandbox for live testing (a Kubernetes 1.15 master on EC2, tagged to one developer, driven by scripts/sandbox.sh, and reached from local containers through a tunnel), run the platform's API, its GUI, or its billing, search or monitoring service, locally against it, or check an AWS account and build the shared host image it starts from. Use when asked to test a change against a live cluster or live AWS, to run the API, the GUI or another of the platform's services on this workstation against real Kubernetes and AWS, or to create, inspect or remove a sandbox.
---

# Dev sandbox

`scripts/sandbox.sh <check|ami|up|connect|services|api|ui|status|extend|down>`; `sandbox.sh help`
lists the options. Every command takes `--dry-run`, which prints the AWS calls instead of making
them.
`README.md` has the setup.

- **Create a sandbox only when asked**, after the person confirms the instance type, the region and
  the TTL. `sandbox.sh up --dry-run` shows all three (`--instance-type`, `--region`, the
  `cp-sandbox-expires` tag); the person changes them in `.env.sandbox.local`.
- **Run `check` before `up` or `ami`.** It changes nothing. When an item fails, show the person
  the failed items and stop. Pass its warnings on too: one says the config holds long-term AWS
  keys, which every run's pod and the API's log then carry.
- **`up` and `ami` run for 10 to 35 minutes.** Run them in the background rather than under a
  command timeout: stopped half-way, a host image build terminates its builder and is lost.
- **`connect` opens the tunnel local containers reach the master through**, in the container
  `cp-sandbox-<name>-tunnel` on the Docker network `cp-sandbox-<name>`; `connect --stop` closes
  it. `scripts/tools.sh --sandbox <name> kubectl ...` runs kubectl through it.
- **`services` runs the platform's API from this checkout, here, against the sandbox**, once the
  tunnel is open, and configures it: region, preferences, the master's registry and a tool.
  `--with billing,search,monitoring`, or `--with all`, runs those services too, with an
  Elasticsearch; billing bills a run only after the midnight, UTC, that follows it (`README.md` →
  "Billing, search and monitoring"). `--pipe-common-from-checkout` serves the runs this checkout's
  `workflows/pipe-common` in place of the published build's; run `services` without it to serve
  the build's again. `services` runs for 5 to 15 minutes, longer when it builds jars; run it in the
  background. `--build` builds the jars again first; `services --stop` removes its containers.
  The master's registry takes a login, deletes, and notifies the API of pushes, as a deployment's
  does (`README.md` → "The registry").
  The admin token is `admin-token.secret` in the local state, for scripts to read; the REST API is
  `https://api:9999/pipeline/restapi/` on the sandbox's network. **Call it with `sandbox.sh api
  <method> <path> [<json body>] [--jq <filter>]`**, which blanks out the secrets in the answer,
  never with curl and the token. `README.md` → "A run" launches the tool, follows it, and commits,
  pauses and resumes it. **Read a log with `services --log [<container>]`** — the API's, or `db`,
  `elasticsearch`, `billing`, `search`, `monitoring` — which blanks out the tokens and credentials
  it holds, never with `docker logs`.
- **`ui` runs this checkout's GUI against the API `services` runs**, at `http://localhost:3100/`,
  and waits for its first compile, several minutes; run it in the background. Every request it
  makes goes as the platform's admin, so its ports are on `127.0.0.1` only: run `ui --stop` once
  the check is over. Its log holds no secret: `docker logs cp-sandbox-<name>-ui` is safe.
- **A run's pod holds the developer's AWS credentials**, in its spec and its environment: never
  print either (`kubectl get pod -o yaml` or `-o json`, `kubectl describe pod`, `env` in it).
  Follow a run through `sandbox.sh api` and `services --log`. A committed image keeps the run's
  API token in `/etc/cp_env.sh`, for anyone with the master's registry's login: commit only when
  asked.
- **`down` and `ami --rebuild` list what they would remove and stop.** Pass `--yes` only after the
  person has confirmed that list.
- **At every hand-back, name the sandboxes still running, with their tunnels, services and GUI**,
  as you would a worktree left behind — `sandbox.sh status`.
- **Never deploy, invoke, update or remove the reaper in `lambda/`**, which terminates expired
  instances of every owner: a person does that, as `README.md` → "The reaper" says.
- **Never open `.env.sandbox.local`, `~/.aws`, or the `*.pem` and `*.secret` files in the local
  state**; the scripts hand them to Docker, SSH and kubectl. The config becomes the environment of
  every tools container, credentials included, the tunnel and API containers among them: never
  run a command through `scripts/tools.sh` that prints its environment, and never `docker
  inspect` one of those containers. `docker logs` of the tunnel is safe.

A change to these scripts or to `lambda/` owes `scripts/test/sandbox-test.sh`, which runs the
reaper's tests and `cfn-lint` too, and `shellcheck` over every `*.sh` here, both in Docker —
`README.md` → "Checks".
