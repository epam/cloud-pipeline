# Configure the Cloud Pipeline CLI

A composite GitHub Action that installs the `pipe` CLI and leaves it ready to use, authenticated with
a token exchanged for this workflow run's GitHub OIDC token.

No Cloud Pipeline secret is stored in GitHub, and no CLI version is pinned in the workflow: the
binary comes from the deployment it will talk to.

## Usage

```yaml
permissions:
  id-token: write          # without this no OIDC token is available to the job
  contents: read

steps:
  - uses: epam/cloud-pipeline/deploy/integrations/oidc/github-actions/configure-pipe-cli@develop
    with:
      api-url: https://cloud-pipeline.example.com

  - run: |
      pipe storage ls cp://my-bucket/
      pipe storage cp ./build/ cp://my-bucket/builds/ --recursive
```

## Inputs

| Name | Required | Description |
|---|---|---|
| `api-url` | yes | The Cloud Pipeline URL, which must be `https`. Either the API root — `https://host/pipeline/restapi` — or just the host, in which case `/pipeline/restapi` is appended. |
| `install-dir` | no | Where to put the binary. Defaults to `$HOME/.local/bin`, created if missing and added to `PATH`. |

## Outputs

| Name | Description |
|---|---|
| `pipe-path` | The full path of the installed binary, for a step that would rather not rely on `PATH`. |
| `token` | The Cloud Pipeline API token. Masked in the log, but it is a credential. |
| `api-url` | The API root the token was issued by, after the input was resolved. |
| `expires-in` | How long the token is valid for, in seconds. |

## Where the CLI comes from

From the deployment, not from a release page: the API serves the binary itself, beside the API root
rather than under it — `api-url` of `https://host/pipeline/restapi` means the binary is at
`https://host/pipeline/pipe`. Taking it from there is what keeps the client and the server in step,
and it needs no credential, because that path is on the API's public URL list.

## No token is written to disk

The action installs the binary and stops there. It does **not** run `pipe configure`, because it does
not need to: the CLI reads `API` and `API_TOKEN` from the environment and treats itself as configured
when both are set, without ever reading its config file. The token exchange exports both, so `pipe`
works in every later step of the job.

The gain is that no credential reaches the runner's disk. `pipe configure` would write the token into
`~/.pipe/config.json`, which on a self-hosted runner outlives the job and is inherited by the next
one. If you do want a config file — for a `pipe` run outside the job's environment — call
`pipe configure` yourself and accept that trade.

`~/.pipe` is not left untouched, to be clear about what does persist: the single-file build unpacks
itself into `~/.pipe/tmp` on every run. That directory holds no credential.

A consequence worth knowing: a step that clears the environment, or runs `pipe` as another user via
`sudo`, loses the configuration. Pass the variables through explicitly in that case:

```yaml
  - run: sudo --preserve-env=API,API_TOKEN pipe storage ls cp://my-bucket/
```

## Requirements

- A **Linux x86_64** runner. The binary served at `/pipe` is a single-file x86_64 Linux build, and
  the action checks both the system and the architecture rather than installing something that
  cannot run. A macOS or Windows runner needs a different artifact (`pipe-osx-arm.tar.gz`,
  `pipe.zip`), which this action does not fetch, and the deployment serves no Linux ARM build at
  all — so an `arm64` runner is refused outright.
- `curl` and `jq` on the runner. Both are present on GitHub-hosted runners; the action checks for
  them and says which is missing.
- An `https` `api-url`, because the GitHub OIDC token is sent to it.
- The runner must be able to reach the deployment. A GitHub-hosted runner is on the public internet,
  so a private API needs a self-hosted runner inside the network.

## What the deployment needs

The same setup as [`fetch-api-token`](../fetch-api-token/README.md#what-the-deployment-needs). In full, see
[12.16. OIDC token exchange](../../../../../docs/md/manual/12_Manage_Settings/12.16._OIDC_token_exchange.md).

Whatever the job runs, `pipe` is subject to the acting user's permissions. A `rolesToKeep` that
strips the role granting storage access is the likeliest reason a command that should work does not.

## When it fails

The exchange failures, and what each error code means, are the same as for
[`fetch-api-token`](../fetch-api-token/README.md#when-it-fails). Past the exchange:

| What you see | Likeliest cause |
|---|---|
| `/pipe answered HTTP 404` | `api-url` does not name the host the GUI is served on, or the deployment uses a context path other than `/pipeline` |
| `did not run on this runner` | A very old or very minimal image cannot start the single-file build |
| `this runner is Darwin` | The job is on a macOS runner; this action is Linux-only |
| `this runner is aarch64` | The job is on an ARM runner, such as `ubuntu-24.04-arm`; no Linux ARM build is served |
| `install-dir ... must be an absolute path` | A relative `install-dir` would put a relative entry on `PATH` |
