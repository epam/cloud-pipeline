# Configure the Cloud Pipeline docker registry

A composite GitHub Action that logs the runner's docker client into the Cloud Pipeline docker
registry, using a token exchanged for this workflow run's GitHub OIDC token.

No Cloud Pipeline secret is stored in GitHub, and no registry address is hardcoded in the workflow:
the deployment is asked which registry to use.

## Usage

```yaml
permissions:
  id-token: write          # without this no OIDC token is available to the job
  contents: read

steps:
  - uses: epam/cloud-pipeline/deploy/integrations/oidc/github-actions/configure-docker-registry@develop
    id: cp
    with:
      api-url: https://cloud-pipeline.example.com

  - run: |
      docker pull ${{ steps.cp.outputs.registry }}/library/ubuntu:latest
```

`API` and `API_TOKEN` are exported to the rest of the job as well, so `pipe` and plain `curl` work in
later steps without repeating the exchange.

## Inputs

| Name | Required | Description |
|---|---|---|
| `api-url` | yes | The Cloud Pipeline URL, which must be `https`. Either the API root — `https://host/pipeline/restapi` — or just the host, in which case `/pipeline/restapi` is appended. |
| `registry` | no | The registry to log into, as `host:port`. By default the deployment is asked. |

## Outputs

| Name | Description |
|---|---|
| `registry` | The registry that was logged into, as `host:port`. Use it to build image names. |
| `token` | The Cloud Pipeline API token. Masked in the log, but it is a credential. |
| `api-url` | The API root the token was issued by, after the input was resolved. |
| `expires-in` | How long the token is valid for, in seconds. |

## How the registry is found

The registry is a separate service from the API, on its own host and port, so it cannot be derived
from `api-url`. The action asks the API instead — `GET /dockerRegistry/loadCerts` — and takes the
first registry that **uses Cloud Pipeline authentication**, preferring its external address over its
internal one. That endpoint is used rather than `loadTree` because it answers with the registries
and their certificates but without the tool catalogue, which this needs none of and which is most of
a `loadTree` response on a deployment with many tools.

That filter is the point rather than a detail. Only such a registry accepts a platform token as the
docker password; one configured with its own credentials would refuse this login, so picking it would
fail with a confusing error. Where a deployment has several and the first is not the one you want,
name it with the `registry` input and no lookup happens.

The listing is filtered by what the acting user may read, so "no registry this token may use" means
either that the user has no registry permission or that none of them use platform authentication.

A `registry` input that the listing does not mention is used anyway, on the grounds that the caller
may know of a registry their token cannot enumerate.

## The registry's certificate has to be trusted already

**This action installs no CA certificate, and that is a real limitation.** The default Cloud Pipeline
installer gives the registry a *self-signed* certificate and records it on the registry, so on a
stock deployment `docker login` fails here with:

```
x509: certificate signed by unknown authority
```

Trusting that CA is the runner's job, not this action's. Installing it per job would mean writing to
`/etc/docker/certs.d/` — outside the workspace, needing root or passwordless `sudo`, and persisting
on a self-hosted runner after the job ends. Doing it once in the runner image is both cleaner and
cheaper:

- **a self-hosted runner** — add the registry CA to the image, or drop it in
  `/etc/docker/certs.d/<registry>/ca.crt` when building the runner;
- **a GitHub-hosted runner** — the registry needs a publicly trusted certificate, which a
  deployment reachable from the public internet will usually have anyway.

The certificate is available from the API as the registry's `caCert` if you want to automate that
step yourself. The action's failure message names this cause explicitly, so a trust problem does not
get mistaken for a permission one.

## What the deployment needs

The same setup as
[`fetch-api-token`](../fetch-api-token/README.md#what-the-deployment-needs) — a platform user, the
`oidc.token.exchange.enabled` property, and an entry in `system.oidc.token.exchange.issuers`. In
full, see
[12.16. OIDC token exchange](../../../../../docs/md/manual/12_Manage_Settings/12.16._OIDC_token_exchange.md).

Beyond that, the user the exchange acts as needs **read permission on the registry**, and write
permission on the tool group as well if the job pushes. A `rolesToKeep` that strips the roles
granting those permissions is the likeliest reason a login that should work does not.

## Requirements

- `docker`, `curl` and `jq` on the runner. All three are present on GitHub-hosted runners; the action
  checks for them and says which is missing.
- The registry's certificate trusted on the runner, where it is not publicly trusted. The action
  needs no root, and installs nothing outside the workspace.
- An `https` `api-url`, because the GitHub OIDC token is sent to it.
- The runner must reach both the API and the registry. They are different ports, and a private
  deployment needs a self-hosted runner inside the network.

## The credential is left on the runner

`docker login` writes the credential into `~/.docker/config.json`, as it does for any registry. On a
GitHub-hosted runner that disappears with the machine. **On a self-hosted runner it does not**, and
the next job on that runner inherits it. Where that matters, log out at the end of the job:

```yaml
  - if: always()
    run: docker logout ${{ steps.cp.outputs.registry }}
```

The token itself expires on its own — minutes to an hour, depending on the client's
`maxTokenLifetime` — so what is left behind stops working shortly after the job ends either way.

## When it fails

The exchange failures, and what each error code means, are the same as for
[`fetch-api-token`](../fetch-api-token/README.md#when-it-fails). Past the exchange:

| What you see | Likeliest cause |
|---|---|
| `no docker registry this token may use` | The acting user cannot read any registry, or none use Cloud Pipeline authentication |
| `loadTree answered HTTP 401/403` | The token was accepted for the exchange but refused here — a permission problem, not an authentication one |
| `docker login was refused` | The user lacks read permission on that registry, or the registry does not use Cloud Pipeline authentication |
| `x509: certificate signed by unknown authority` | The registry's certificate is not trusted on this runner, and the action installs none — trust the CA in the runner image |
| `registry input contains whitespace` | The `registry` input is not a bare `host:port` |
