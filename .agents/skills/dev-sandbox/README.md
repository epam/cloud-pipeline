# Dev sandbox

A throw-away AWS sandbox for live testing: a Kubernetes 1.15.4 master on EC2, in a shared AWS
account, tagged with the developer it belongs to and the time it expires. `scripts/sandbox.sh`
checks the account, builds the host image the master starts from, creates the master, opens a
tunnel between it and the containers on your workstation, runs the platform's API there against
it, shows it, extends it and removes it.

The expiry is a tag, and nothing in `sandbox.sh` acts on it. The reaper in `lambda/`, which a
person deploys once per account and region, terminates the instances that have expired ("The
reaper", below). Without it, an expired sandbox runs, and costs, until `down` removes it.

## What you need

- **Docker and git.** Every AWS step runs in a tools image (`docker/tools.Dockerfile`: AWS CLI v2,
  the Session Manager plugin, OpenSSH, jq, kubectl 1.15), built on first use and again whenever
  `docker/` changes. Docker must be able to mount your home directory, which holds the checkout and
  `~/.aws`.
- **In the AWS account**, all of it already there. `sandbox.sh check` looks at each item below, and
  `up` and `ami` run it before they launch anything, and launch nothing when an item fails. Of the
  credentials it sees only that they work, not what they may do; of the instance types, only that
  the zone offers them, not that they are x86_64:
  - a VPC, and an available subnet in it with free addresses;
  - a way from that subnet to the internet. The instances get no public IP, and they download
    packages, Docker, the Kubernetes RPMs and the system images from the internet (the Amazon Linux
    repositories and the `cloud-pipeline-oss-builds` S3 bucket), and reach SSM through it too. The
    subnet's route table, its own or else the VPC's main one, needs an active `0.0.0.0/0` route to
    a NAT gateway that is available. A route to an internet gateway is refused; any other
    target, such as a transit gateway, is taken on trust, with a warning. A subnet that gives
    instances a public IP on launch gets a warning too: the sandbox's own instances ask for none,
    but the platform's workers take the subnet's default;
  - one or more security groups in the VPC: at least one of them allows all traffic from itself,
    for the master and the workers, and one allows outbound TCP 443 to `0.0.0.0/0`;
  - an instance profile for the master and the image builder, whose role has
    `AmazonSSMManagedInstanceCore` attached. One for the workers is optional; without that policy
    the workers only cannot be reached through SSM, which `check` warns about;
  - the master and worker instance types offered in the subnet's availability zone;
  - credentials of your own that may make the calls the scripts make:
    - `check`, which only reads: `sts:GetCallerIdentity`, `ec2:DescribeVpcs`,
      `ec2:DescribeSubnets`, `ec2:DescribeRouteTables`, `ec2:DescribeNatGateways`,
      `ec2:DescribeSecurityGroups`, `ec2:DescribeInstanceTypeOfferings`, `iam:GetInstanceProfile`
      and `iam:ListAttachedRolePolicies`. Without the two IAM ones, `check` warns that it could
      not look at the instance profiles, and passes;
    - the other commands: `ec2:RunInstances`, `ec2:CreateTags`, `ec2:DescribeInstances`,
      `ec2:DescribeImages`, `ec2:CreateImage`, `ec2:RebootInstances`, `ec2:TerminateInstances`,
      `ec2:DeleteTags` (for `ami --rebuild`), `iam:PassRole` on the instance profiles' roles,
      `ssm:GetParameter`, `ssm:DescribeInstanceInformation`, `ssm:SendCommand`,
      `ssm:GetCommandInvocation`, and `ssm:StartSession` on the instances and on the
      `AWS-StartSSHSession` document;
    - `services` and `down`, for the key pair the platform's nodes start with:
      `ec2:DescribeKeyPairs`, `ec2:ImportKeyPair`, `ec2:CreateTags` on key pairs, and
      `ec2:DeleteKeyPair`. The API that `services` runs makes its own calls with the same
      credentials: it starts and stops the platform's nodes.

    Prefer a profile with short-lived credentials, such as SSO or a role it assumes, or the keys
    of an IAM user of their own with only these permissions. The API that `services` runs hands
    its credentials to every run's pod, and its log holds them ("A run", below). `check` warns of
    long-term keys: an access key ID that starts with `AKIA`, or no session token.
- **For `services`**, Docker that gives a container `/dev/net/tun` and `NET_ADMIN`, as Docker
  Desktop and Colima do, and room for the images it uses, about 2 GB: PostgreSQL's, the API's,
  and the JDK the jar is built with, besides Gradle's cache. `services --with` needs about 1.3 GB
  more, for Elasticsearch's image, and, on an arm64 workstation, Docker that runs amd64 images:
  `docker run --rm --platform linux/amd64 alpine uname -m` prints `x86_64`.
- **The reaper**, in the account and region, unless someone has deployed it already: it
  terminates what has expired ("The reaper", below).

## Config

`.env.sandbox.local` in the repository root, gitignored. The first command you run copies the
template, `sandbox.env.example`, there and stops; fill it in. A command names the keys still
missing, never their values.

Docker reads the file as `--env-file`: `KEY=value` or `KEY="value"`, no `export`, and no comment
after a value. Credentials are either `AWS_PROFILE`, a profile in `~/.aws` (mounted read-only), or
the key variables.

Working in a git worktree, the scripts use the worktree's own `.env.sandbox.local` when there is
one, and otherwise the main checkout's. `CP_SANDBOX_ENV_FILE` points them at another file.

## Commands

```bash
S=.agents/skills/dev-sandbox/scripts/sandbox.sh

$S check                 # check the config against the account; changes nothing
$S ami                   # find the shared host image, or build it if this version has none
$S ami --rebuild         # list what a new build would replace
$S ami --rebuild --yes   # build a new host image, and take the version's tag off the old one
$S up                    # launch the master and install Kubernetes on it
$S connect               # open the tunnel to the master, for the containers on your workstation
$S connect --stop        # close it
$S services              # run the API from this checkout, here, against the sandbox, and configure it
$S services --with all   # and the billing, search and monitoring services, with an Elasticsearch
$S services --build      # build the jars again first
$S services --stop       # remove the containers services runs
$S services --log        # the API's log, its tokens and credentials blanked out
$S services --log search # the log of another of them: db, elasticsearch, billing, search, monitoring
$S api GET whoami        # one call to the API's REST API, as its admin ("A run", below)
$S status                # local sandboxes, their containers and networks, and your instances
$S extend --hours 24     # move the expiry 24 hours later
$S down                  # list what would be removed
$S down --yes            # terminate the instances, delete the key pair, remove the local state and
                         # the containers, network and volumes

.agents/skills/dev-sandbox/scripts/tools.sh --sandbox <name> kubectl get nodes
```

- `--name <name>` picks a sandbox; without it, a command uses the only local one. A new sandbox is
  named `<owner>-<4 random characters>`; `up --new` starts another one next to it.
- `up` runs `check`, finds the host image (building it first if there is none), launches the
  master from it, and waits until SSM reports it online. It then copies
  `scripts/host/install-master.sh` to the master with Run Command, together with the sandbox's
  kubeadm config and the network plugin `deploy/contents/k8s/kube-system/canal.yaml`, and runs it
  there: Docker 20.10.24, the Kubernetes 1.15.4 RPMs and the system images, `kubeadm init`, and the
  network plugin, until the master node is `Ready`. The system images hold every image the master
  runs, so it pulls none. Of them, `install-master.sh` checks only the network plugin's flannel
  image: before `kubeadm init`, it stops when Docker lacks a flannel image that `canal.yaml` names.
  Last, it copies the master's kubeconfig and join token into the local state over SSH through a
  Session Manager session, so that they never pass through the account's Run Command history, and
  points the kubeconfig at the address the tunnel gives the master's Kubernetes API ("The tunnel",
  below). Allow 10 to 15 minutes, and 20 more when the host image has to be built first.
- `up` is safe to run again: it picks up the running master instead of launching a second one, and
  `install-master.sh` skips what it has done already, and undoes a `kubeadm init` that stopped
  half-way before it runs another. For a master that runs already, `up` skips `check`. It launches
  nothing when `check` fails, when it cannot list the sandbox's instances, or when another owner's
  instances carry the name. A refused `up` leaves no local state behind.
- `--dry-run` on any command prints the AWS calls, and the Docker calls that would change
  something, and changes nothing, locally or in AWS.
- `scripts/tools.sh <command>` runs any command in the tools image with the same mounts and config,
  for example `scripts/tools.sh aws sts get-caller-identity`. With `--sandbox <name>` first, the
  container joins the sandbox's Docker network, with `KUBECONFIG` set to its kubeconfig, so that
  `kubectl` reaches the master through the tunnel.
- On the instances, the scripts log to `/var/log/cp-sandbox/`, which only root can read.
  `kubeadm-init.log` there holds the join token.

## The tunnel

The master has no public address, so nothing on your workstation reaches it directly. `connect`
opens SSH to it through a Session Manager session (`AWS-StartSSHSession`), with the sandbox's
key, and keeps it open in a container: `cp-sandbox-<name>-tunnel`, which runs the tools image
detached on a Docker network of the sandbox's own, `cp-sandbox-<name>`. The session carries three
forwards:

| From | To | For |
|---|---|---|
| `cp-sandbox-<name>-master:6443`, on the network: the tunnel container's alias there | the master's Kubernetes API | the containers on the network: the kubeconfig in the local state names this address, and the API server's certificate carries the name |
| `<private IP>:9999` on the master | `api:9999` on the network | the platform's nodes, which reach the API on the master's private address: a container on the network named, or aliased, `api` serves them |
| a SOCKS proxy at `cp-sandbox-<name>-master:1080`, on the network | whatever address the master connects to | the containers on the network, which reach the VPC through it: the API's container routes the VPC's addresses there ("Local services", below) |

- Start a container on the network to use the tunnel: `docker run --network cp-sandbox-<name>
  ...`, or a Compose service on that network as an external one. `scripts/tools.sh --sandbox
  <name>` runs a tools container there.
- `connect` checks the sandbox before it starts anything: the master named in the local state
  runs, and up has fetched its kubeconfig. Once the container runs, it waits up to three minutes
  for `kubectl get nodes` to answer through the tunnel, checks over SSH that the master listens on
  `<private IP>:9999`, which `install-master.sh` lets sshd do (`GatewayPorts clientspecified`),
  and that the SOCKS proxy reaches the master's Kubernetes API at its private address. It prints
  the nodes. When a check fails, it shows the end of the tunnel's log and removes the container.
- `connect` is safe to run again: it leaves a tunnel container that runs, checks it again, and
  replaces one that has stopped.
- The tunnel opens SSH again when a session ends, as when Session Manager closes one. After five
  sessions in a row that each ended within a minute, it gives up and the container stops; `connect`
  starts it again. It does not come back by itself after Docker restarts.
- Before each session, the tunnel ends every SSH session on the master that still listens on
  `<private IP>:9999`, and its log names them. A session that ends without closing its connection,
  as when `connect --stop` removes the container, stays on the master with its forward
  until Session Manager closes the connection, many minutes later, and until then no new session
  can bind the port. Only a tunnel's session listens there.
- The tunnel keeps the credentials it started with: the config's values, and a copy of `~/.aws` as
  it was then. A session that is open stays open, but once those credentials expire, one that ends
  cannot open again, even after you renew them, and the tunnel gives up. Renew them, then run
  `connect` again.
- The master's `<private IP>:9999` is open to whatever its security groups let in, and those allow
  all traffic between their members: in a shared account, other developers' instances in the same
  groups can reach the API on your workstation through it too. The other way, any container on
  the sandbox's network reaches the VPC through the SOCKS proxy, as the master would.
- The tunnel container holds the config in its environment, as every tools container does. Its
  log is `docker logs cp-sandbox-<name>-tunnel`.
- `connect --stop` removes the tunnel container and leaves the network, for the containers still
  attached to it. `down --yes` removes both, after the instances: every container, network and
  volume labelled `cp-sandbox=<name>`. A network or a volume that a container without that label
  still uses is left, and named.
- `connect` runs the scripts of the checkout you run it from, mounted into the container. Removing
  that checkout, a worktree say, breaks the tunnel the next time it starts.

## Local services

`services` runs the platform's API on your workstation, built from the checkout you run it from,
against the sandbox, with the tunnel open (`connect`). It runs two containers on the sandbox's
network, both labelled `cp-sandbox=<name>`, and four more with `--with all` ("Billing, search and
monitoring", below):

| Container | On the network as | What |
|---|---|---|
| `cp-sandbox-<name>-db` | `db` | PostgreSQL of the version the platform's installer deploys, `PSG_VERSION` in `deploy/contents/install/install-config`, its data in the volume `cp-sandbox-<name>-db` |
| `cp-sandbox-<name>-api` | `api` | the API, on Java 8, serving HTTPS on port 9999: the tunnel's remote forward leads the master's `<private IP>:9999` there, for the platform's nodes |

In order, `services`:

1. builds the API's jar and jwt-generator's, when either is missing or with `--build`, with
   Gradle in Docker: `./gradlew :api:bootRepackage :jwt-generator:shadowJar`, the lightest build
   that gives a runnable jar. It serves `launch.sh` and `init.sh` from the checkout, and no UI.
   Gradle runs as you, so that what it writes in the checkout stays yours, with its home and cache
   in `.gradle-home` in the local state root;
2. builds the API's image, `docker/api.Dockerfile`, when it has changed: Java 8, Python 3.12 with
   the packages of `scripts/autoscaling/requirements.txt` and pipe-common, for the node scripts
   the API runs, and tun2socks;
3. in the tools image, `services-prepare`:
   - looks up the VPC's address ranges, and refuses a sandbox network that overlaps them;
   - imports the sandbox's SSH key as the EC2 key pair `cp-sandbox-<name>`, tagged as the
     sandbox's, which the platform starts its nodes with;
   - runs `scripts/host/install-services.sh` on the master with Run Command: a Docker registry,
     `registry:2.8.3` on port 5000, with a self-signed certificate for the master's private
     address; the tool `library/centos:7` in it, built from `scripts/host/tool.Dockerfile`, and
     built again when that file changes; and
     the files a run's `launch.sh` downloads (pipe-common, `pipe`, gpustat, fsbrowser), taken
     from a published build of the platform and served at `http://<private IP>:8090/`. The build
     is the newest `cloud-pipeline.<version>.tgz` under
     `https://cloud-pipeline-oss-builds.s3.amazonaws.com/builds/develop/`, unless
     `CP_SANDBOX_DISTRIBUTION_URL` names another; so pipe-common and `pipe` need no local build;
   - fetches the registry's certificate, makes the API's TLS key and its JWT key pair, and picks
     the sandbox's block of run IDs;
   - writes `api.properties`: `api/profiles/dev/application.properties` with the database, the
     node scripts of the checkout (`scripts/autoscaling/aws/`), the master
     (`kube.master.ip`, the join token, the CA cert hash), `kube.cluster.name`
     (`cp-sandbox-<name>`, which only EKS nodes use, but without which `nodeup.py` fails), and
     the API's address on the master for `api.host` and the launch and init script URLs;
4. mints a token for the platform's admin, `pipe_admin`, with jwt-generator, valid 30 days;
5. starts the database, and then the API, which takes three to four minutes to start, Flyway
   creating the schema the first time;
6. moves the sequences of run IDs and pool node IDs (`pipeline.s_pipeline_run`,
   `pipeline.s_free_nodes`) to the sandbox's block: the node scripts find a node's instance by its
   `Name` tag, the run ID, across the whole region, so each sandbox takes 100000 IDs of its own,
   at random, from 100000000 up;
7. through the API's REST API, as the admin, `services-configure`: the region from the config,
   as the default one, with the key pair; the preferences below; the master's registry, with its
   certificate and without the platform's own registry authentication, its tool group `library`
   and the tool. It prints what the API reads back.

| Preference | Value |
|---|---|
| `cluster.networks.config` | the region, the subnet, the security groups, the host image, `scripts/autoscaling/init_multicloud_v1.15.4_al2023.sh`, the worker instance profile, and the sandbox's four tags with `cp-sandbox-role=worker` |
| `cluster.allowed.instance.types`, `.docker`, `cluster.instance.type` | `CP_SANDBOX_WORKER_INSTANCE_TYPE`, the only type the API launches |
| `cluster.max.size` | 2 |
| `cluster.spot` | false: on-demand nodes |
| `cluster.keep.alive.policy`, `cluster.keep.alive.minutes` | `IDLE_MINUTES` and 5: a node goes once it has been idle 5 minutes, where the platform's default policy would keep it until the end of its first hour |
| `base.api.host` | `https://<private IP>:9999/pipeline/restapi/` |
| `base.pipe.distributions.url` | `http://<private IP>:8090/` |

- `services` is safe to run again: the master's steps skip what is done, a database that runs
  is left, an API that runs on the same image, jar and settings is left, and the configuration
  creates only what is missing. It sets the preferences again, so run it after `extend`: the
  worker tag `cp-sandbox-expires` comes from the sandbox's expiry at the time.
- The API reaches the VPC as if it ran there: its container routes the VPC's addresses through
  the tunnel's SOCKS proxy, with tun2socks, for the API and the node scripts it runs. The API
  checks `base.api.host` and `base.pipe.distributions.url` when they are set, and reaches the
  registry, at the master's private address, and a connection to `<private IP>:9999` there comes
  back to the API itself through the tunnel. Anything else, AWS included, goes from your
  workstation, with your credentials.
- tun2socks accepts a TCP connection before the SOCKS proxy has reached the address, so a check
  that only connects passes whatever listens there: `nodeup.py`'s wait for port 8888 on a new
  node passes as soon as the node runs. What `nodeup.py` waits for next, the node joining the
  cluster and turning `Ready`, holds.
- The API's REST API takes the admin token in `admin-token.secret`; there is no identity
  provider, so no sign-in, and no UI. From a container on the network:
  `https://api:9999/pipeline/restapi/`, with the API's certificate, `api-tls.crt`.
- The API's container gets the config as its environment, and `~/.aws` read-only, as the tools
  containers do: never `docker inspect` it. Its log holds secrets once it starts nodes and runs:
  the join token, on the node scripts' command lines, the tokens it issues them, and the spec of
  every run's pod, with your AWS credentials ("A run", below). `sandbox.sh services --log [--tail
  <lines>]` shows it with those blanked out: the values of the variables whose names say they
  are secret, AWS access key IDs, the API's tokens, the join token, long runs of base64, and
  words of 40 base64 characters, as AWS secret keys are, git commit hashes among them. Read it
  that way, not with `docker logs`.
- The API keeps the credentials it started with: the config's values, and a copy of `~/.aws` as it
  was then. `services` starts it again when the config file has changed since; after renewing
  credentials in `~/.aws`, run `services --stop`, then `services`.
- The registry on the master takes pushes from anyone, and the distribution files are served to
  anyone, that the security groups let in: in a shared account, other developers' instances in the
  same groups too.
- `services --stop` removes the containers it runs, the optional services' among them; the
  volumes and the network stay, and `down --yes` removes them, the API's container first, so that
  it starts no node while the instances go. The API's container runs a copy of the jar, so
  building again while it runs leaves it alone; run `services` again to start the new jar.

### Billing, search and monitoring

`services --with billing,search,monitoring`, or `--with all`, runs those of the platform's services
too, each built from its module in the checkout, with an Elasticsearch they share, on the
sandbox's network, labelled `cp-sandbox=<name>`:

| Container | On the network as | What |
|---|---|---|
| `cp-sandbox-<name>-elasticsearch` | `elasticsearch` | Elasticsearch of the version the platform's search runs, the image `deploy/docker/cp-search-elk/Dockerfile` starts `FROM`: one node, its data in the volume `cp-sandbox-<name>-elasticsearch` |
| `cp-sandbox-<name>-billing` | `billing` | `billing-report-agent`: bills the runs, into the `cp-billing-*` indices, once a day, at midnight UTC (below); its storage billing stays off, as its module's settings have it, though its count of the requests to storages runs |
| `cp-sandbox-<name>-search` | `search` | `elasticsearch-agent`: indexes the platform's objects, read from the API's database and its REST API |
| `cp-sandbox-<name>-monitoring` | `monitoring` | `monitoring-service`: the platform's monitors, through the REST API, with the kubeconfig for the cluster |

- A service runs in the API's image, from a copy of its module's jar in the local state, which
  `./gradlew :<module>:bootJar` builds when it is missing, or with `--build`. Its settings,
  `<service>.properties` in the local state, are its module's
  `src/main/resources/application.properties` with the REST API at
  `https://api:9999/pipeline/restapi/`, the Elasticsearch at `elasticsearch:9200`, and, for
  search, the database, with its NFS events synchronizer off, as the platform's search deployment
  has it: on, it fails to start without a bucket of events, which the module's settings leave
  empty.
- The services take the admin's token from `admin-token.secret` (`docker/service-entrypoint.sh`),
  and get no AWS credentials: neither the config nor `~/.aws`.
- With billing or search, `services` points the API's search at the Elasticsearch: the
  `search.elastic.*` preferences that the platform's installer sets (`api_register_search` in
  `deploy/contents/install/app/configure-utils.sh`), read from the checkout, with the
  Elasticsearch's address in place of the platform's. The API's searches and billing reports read
  them.
- The Elasticsearch image is built for amd64 only, so on an arm64 workstation Docker runs it
  emulated. `services` starts it with the database, and waits for it, up to 10 minutes, once the
  API is configured. Then it starts each service, and waits up to 5 minutes for its log to say that
  it has started. It stops at the first that does not, and shows the end of its log.
  `services --log <billing|search|monitoring|elasticsearch>` shows a log, blanked out as the
  API's is.
- A run of `services` leaves a service that runs on the same jar and settings and has started,
  and starts one again otherwise, a service that failed to start but runs on among them. A run
  without `--with` leaves the services that run as they are.
- While the platform has no data storage, as the sandbox's has none at first, the API answers
  the list of storages with no payload, and on every cycle the billing service's count of storage
  requests and the search service's file synchronizers log a `NullPointerException` where they
  read it. Their other synchronizers go on: search indexes the runs, the tools and the registry.
- Billing runs on the schedule of the platform's billing image, `sync.billing.schedule` in
  `deploy/docker/cp-billing-srv/config/application.properties`, which `services` reads from the
  checkout: once a day, at midnight UTC, the containers' time zone. A sync bills the days that
  have ended, of the runs active since the sync before it, so a run shows in the `cp-billing-*`
  indices after the midnight that follows it. The module's own schedule, every 3 minutes, would
  never bill the last day of a run, nor any run that starts and ends on one day.
- Nothing of them is published on the workstation. Reach them from a container on the network:
  `tools.sh --sandbox <name> curl -s http://elasticsearch:9200/_cat/indices`.

## A run

`sandbox.sh api <method> <path> [<json body>]` makes one call to the API's REST API, as its
admin, from a tools container on the sandbox's network, at `<path>` under
`https://api:9999/pipeline/restapi/`. It writes the payload, with the string values of the keys
that name a secret blanked out (a run's `sshPassword`, the `API_TOKEN` among its `envVars`), and
of the `{name, value}` pairs whose name does; `--jq <filter>` picks from it, with an empty
environment. Then what it writes, and its errors, go through what `services --log` blanks out, a
line at a time, so a git commit hash in the answer is blanked out too. The token never leaves its
file.

To launch the sandbox's tool on a node of its own, and follow it:

```bash
S=.agents/skills/dev-sandbox/scripts/sandbox.sh
$S api --jq .id POST run '{"dockerImage": "<private IP>:5000/library/centos:7",
  "instanceType": "<CP_SANDBOX_WORKER_INSTANCE_TYPE>", "hddSize": 20, "isSpot": false,
  "cloudRegionId": <the region ID services prints>, "timeout": 0,
  "cmdTemplate": "echo hello from $(hostname) && sleep 30"}'
$S api --jq '{status, endDate, node: .instance.nodeId}' GET run/<id>
$S api --jq '.[] | "\(.date) \(.taskName) \(.logText)"' GET run/<id>/logs
.agents/skills/dev-sandbox/scripts/tools.sh --sandbox <name> kubectl get nodes,pods -o wide
```

The API starts a node for the run with `nodeup.py`: an instance from the host image, tagged
`Name=<run ID>` and with the sandbox's four tags, `cp-sandbox-role=worker`, which joins the master.
Its pod pulls the tool from the master's registry, and the tool's `launch.sh`, downloaded from the
API through the tunnel, reaches the API there too. On a `t3.medium`, the node was
`Ready` 3 minutes after the launch, and the run `SUCCESS` 6 minutes after it. Once the node has
been idle 5 minutes, the API runs `nodedown.py`, which terminates the instance tagged with the
run's ID.

The API hands every run's container the AWS credentials it runs with, as the platform's
deployments hand it those of their instance role: here they are yours, from the config, as
`CP_ACCOUNT_ID_<region>`, `CP_ACCOUNT_KEY_<region>` and, for temporary ones,
`CP_ACCOUNT_TOKEN_<region>`. They are in the pod's spec, which the API logs, which Kubernetes
keeps in etcd on the master, and which the node holds; and in the container's environment. Never
print a run pod's spec or environment: no `kubectl get pod -o yaml` or `-o json`, no `kubectl
describe pod`, no `env` in the container. `down` terminates the instances, and their volumes go
with them.

## The host image

Masters and the platform's workers start from one image, shared by everyone in the account: stock
Amazon Linux 2023 as `scripts/host/prepare-host.sh` leaves it (the packages, the legacy iptables,
btrfs, Python 2, jq, the kernel settings, SELinux permissive and cgroup v1), rebooted into cgroup v1.
Its version is the start of the SHA-256 of `prepare-host.sh`, so a change to that script makes a new
version, which the next `ami` or `up` builds.

A build launches a builder instance tagged `cp-sandbox=ami-<version>`, runs `prepare-host.sh` on it
with Run Command, reboots it, checks that it runs on cgroup v1, creates the image and terminates the
builder, about 20 minutes in all. The builder is terminated when the build fails, too. Before the
image is taken, `prepare-host.sh seal` empties the machine ID and removes the SSH host keys, which
every instance started from the image then makes for itself.

A build refuses to start while an instance that has not expired carries the builder's name: wait
for the other build to end. A builder past its 3-hour expiry is taken for one left over from a build
that stopped, and left alone; the reaper terminates it, where one is deployed, and otherwise, when
it is yours, remove it with `down --name ami-<version>`.

The scripts never deregister an image. `ami --rebuild --yes` builds a new one, then takes the
`cp-sandbox-ami` tag off the old one and tags it `cp-sandbox-ami-replaced=<version>`; deregister it
by hand once nothing uses it.

## Tags

Every instance, volume and network interface the scripts create carries, and so does the key pair
`services` imports; `cluster.networks.config` gives the same to the nodes the platform starts:

| Tag | Value |
|---|---|
| `cp-sandbox` | the sandbox name, or `ami-<version>` for an image builder |
| `cp-sandbox-owner` | `CP_SANDBOX_OWNER` |
| `cp-sandbox-role` | `master`, `worker` (the platform's nodes, and the key pair they start with), or `ami-builder` |
| `cp-sandbox-expires` | UTC, ISO-8601: creation plus `CP_SANDBOX_TTL_HOURS` (48 by default), or plus 3 hours for a builder |

A host image and its snapshot carry `cp-sandbox-ami=<version>`, and `cp-sandbox-ami-built-by` with
the owner who built it.

`extend` and `down` act only on instances that carry both the sandbox's own `cp-sandbox` value and
your `cp-sandbox-owner`, and `down` deletes only a key pair that carries both; it lists any others
with the same name as left alone. `extend` moves the expiry tag on the instances only, not on
their volumes and network interfaces, nor on the key pair.

## The reaper

`lambda/reaper.py`, a Lambda function that `lambda/template.yaml` runs every hour. It acts on the
instances of its own region, whoever owns them, and terminates:

- every instance whose `cp-sandbox-expires` has passed: a master, a worker or an image builder,
  pending, running, stopping or stopped;
- every worker (`cp-sandbox-role=worker`) whose sandbox has no master that is pending or running:
  no instance with the same `cp-sandbox` and `cp-sandbox-owner` tags and
  `cp-sandbox-role=master`. A master the same run terminates for its expiry counts as gone.

It leaves alone every instance without a `cp-sandbox` tag, and an instance whose
`cp-sandbox-expires` is missing or is not a time, unless it is a worker without a master. It
touches no image, and none of what `down` removes besides the instances: the key pair, the local
state and the containers stay, and `down --yes` removes them. Its role may describe instances,
terminate only those with a `cp-sandbox` tag, and write its own log.

So, where it runs:

- `extend` a sandbox before it expires, or the reaper takes it within the hour after.
- A worker that the API starts carries the expiry that `services` last put into
  `cluster.networks.config`. Run `services` after `extend`, or a worker started since goes when
  the old expiry passes.
- The log, `/aws/lambda/<stack name>`, kept 30 days, has one line for each instance on each run:
  `terminate`, `would terminate`, `could not terminate` or `keep`, with the instance's ID, its
  four sandbox tags, its state and the reason; and `skip` for an instance without a
  `cp-sandbox` tag that EC2 returned all the same. A run that could not terminate an instance
  ends in an error, after trying the others.

### Deploying it

A person deploys it, once per account and region the sandboxes run in, from the repository root,
with the config's credentials. It needs:

- an S3 bucket in that region that you may write to and read from, for its code;
- the rights to create, with CloudFormation, an IAM role and pass it to Lambda
  (`iam:PassRole`), a Lambda function and the permission that lets EventBridge invoke it
  (`lambda:AddPermission`), an EventBridge rule and a CloudWatch log group. The function runs
  outside the account's VPCs, and needs none of what "What you need" lists.

```bash
.agents/skills/dev-sandbox/scripts/tools.sh bash -c 'cd .agents/skills/dev-sandbox/lambda &&
  aws cloudformation package --template-file template.yaml --s3-bucket "$0" \
    --s3-prefix cp-sandbox-reaper --output-template-file /tmp/packaged.yaml &&
  aws cloudformation deploy --template-file /tmp/packaged.yaml --stack-name cp-sandbox-reaper \
    --capabilities CAPABILITY_IAM --no-fail-on-empty-changeset \
    --parameter-overrides DryRun=true' <bucket>
```

- `package` zips `reaper.py` and uploads it to the bucket, named by a hash of its content, and
  writes the template with the object in place of the file. Run it in `lambda/`, as above:
  started elsewhere, it uploads the bare file, which Lambda cannot run.
- The function is named after the stack. With `DryRun=true`, also the template's default, it logs
  what it would terminate and terminates nothing. Once its log shows what you expect, run the
  command again with `DryRun=false`. To update the function to the `reaper.py` of your checkout
  later, run it with the value the stack has.
- `aws cloudformation delete-stack --stack-name cp-sandbox-reaper` removes it; the code stays in
  the bucket.

## Local state

`.agents/sandboxes.local/<name>/` in the main checkout, gitignored, so it outlives any worktree.
`CP_SANDBOX_STATE_DIR` points the scripts at another directory. Beside the sandboxes, `.gradle-home`
holds Gradle's home and cache for the builds of `services`.

| File | Holds |
|---|---|
| `state.json` | the instance, region, AMI, private IP, created, expires; the Kubernetes API port, the hash of the cluster CA's public key, and the name the API server's certificate carries for the tunnel; while the tunnel works, its container and network, the Kubernetes API's address on the network, the API port's address on the master and where it leads, and the SOCKS proxy: `connect` writes these once its checks pass, and they go when a check fails, when the tunnel gives up, and on `connect --stop`; once `services` has run, the VPC's ranges, the subnet's zone, the key pair, the registry's path, the distribution's address and source, the API's address and the block of run IDs |
| `ssh-key.pem`, `ssh-key.pem.pub` | the sandbox's SSH key; the public half reaches the master through its user data |
| `known_hosts` | the master's SSH host key, recorded the first time |
| `kubeconfig.secret` | the master's `admin.conf`: cluster-admin, for `https://cp-sandbox-<name>-master:6443`, through the tunnel |
| `kube-join-token.secret` | the token the platform's nodes join the cluster with |
| `api.properties`, `api.env` | the API's settings and its container's environment, which `services` writes again on every run; no secret: the API's container reads the keys and the join token from their files |
| `api-tls-key.pem`, `api-tls.crt` | the API's TLS key and certificate, for `api` and the master's private address |
| `jwt-private-key.pem`, `jwt-public-key.pem` | the key pair the API signs and checks its tokens with |
| `admin-token.secret` | a token for the platform's admin, `pipe_admin` |
| `registry-ca.crt` | the certificate of the registry on the master |
| `api.jar` | the copy of the jar the API's container runs |
| `<service>.properties`, `<service>.jar` | for `services --with`: the settings of the billing, search or monitoring service, which `services` writes again on every run that names the service in `--with`, and the copy of the jar its container runs; no secret |

`state.json` holds no secret. Every file that does ends in `.pem` or `.secret`, readable by you
only, and those are among the files agents must not read.

## Checks

From the repository root, both in Docker, neither touching AWS:

```bash
.agents/skills/dev-sandbox/scripts/test/sandbox-test.sh

docker run --rm -v "$PWD/.agents/skills/dev-sandbox:/mnt:ro" -w /mnt koalaman/shellcheck:stable \
    -x $(cd .agents/skills/dev-sandbox && find . -name '*.sh')
```

`sandbox-test.sh` runs every command under `--dry-run` against a made-up config, and the Docker
steps of `connect`, `services` and `down` with `docker` stubbed. Then, in the tools image, it runs
`in-tools-test.sh`, `check-test.sh`, `ami-test.sh`, `connect-test.sh` and `services-test.sh`: the
libraries and the commands, with AWS, Run Command, SSH, kubectl and the API's REST API replaced
by the stubs in `stubs.sh` and the tests; `services-test.sh` reads the search preferences from the
checkout's own installer, and the billing schedule from its billing image's settings.
`api-entrypoint-test.sh` runs the entrypoints of the API's image, with
what they call stubbed. Last, `host-test.sh` runs the host scripts twice each,
as root in a container of their own, with every command that would change the host or reach the
network replaced by a stub, and runs `install-master.sh` on `deploy/`'s `canal.yaml` too, to
check which flannel image it looks for; and `services-host-test.sh` does the same for
`install-services.sh`, in `python:3.12`, since it needs Python 3, as the master has. Then it runs
the reaper's tests, `lambda/test/`, with pytest and EC2 replaced by botocore's `Stubber`, and
`cfn-lint` over `lambda/template.yaml`, both in an image built from
`docker/lambda-test.Dockerfile`, again when that file changes.
