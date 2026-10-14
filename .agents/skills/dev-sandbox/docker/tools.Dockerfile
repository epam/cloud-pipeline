# The tools the dev sandbox scripts run in: AWS CLI v2, the Session Manager plugin, OpenSSH, jq,
# and a kubectl that matches the sandbox's Kubernetes master.
#
# Built by scripts/tools.sh, which tags the image with a hash of this directory, so an edit here
# is picked up on the next run.

FROM debian:trixie-slim

ARG AWS_CLI_VERSION=2.37.13
ARG SESSION_MANAGER_PLUGIN_VERSION=1.2.896.0
ARG KUBECTL_VERSION=1.15.4

ARG AWS_CLI_URL=https://awscli.amazonaws.com
ARG SESSION_MANAGER_PLUGIN_URL=https://s3.amazonaws.com/session-manager-downloads/plugin
ARG KUBECTL_URL=https://dl.k8s.io/release

# Set by BuildKit. The legacy builder leaves it empty, and dpkg names the same architectures.
ARG TARGETARCH

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl jq openssh-client unzip \
 && rm -rf /var/lib/apt/lists/*

RUN set -eu; \
    arch="${TARGETARCH:-$(dpkg --print-architecture)}"; \
    case "$arch" in \
      amd64) aws_arch=x86_64;  plugin_arch=ubuntu_64bit ;; \
      arm64) aws_arch=aarch64; plugin_arch=ubuntu_arm64 ;; \
      *) echo "Unsupported architecture: $arch" >&2; exit 1 ;; \
    esac; \
    tmp="$(mktemp -d)"; \
    curl -fsSL -o "$tmp/awscli.zip" \
      "$AWS_CLI_URL/awscli-exe-linux-$aws_arch-$AWS_CLI_VERSION.zip"; \
    unzip -q "$tmp/awscli.zip" -d "$tmp"; \
    "$tmp/aws/install"; \
    curl -fsSL -o "$tmp/session-manager-plugin.deb" \
      "$SESSION_MANAGER_PLUGIN_URL/$SESSION_MANAGER_PLUGIN_VERSION/$plugin_arch/session-manager-plugin.deb"; \
    dpkg -i "$tmp/session-manager-plugin.deb"; \
    curl -fsSL -o /usr/local/bin/kubectl \
      "$KUBECTL_URL/v$KUBECTL_VERSION/bin/linux/$arch/kubectl"; \
    echo "$(curl -fsSL "$KUBECTL_URL/v$KUBECTL_VERSION/bin/linux/$arch/kubectl.sha256")  /usr/local/bin/kubectl" \
      | sha256sum -c -; \
    chmod 0755 /usr/local/bin/kubectl; \
    rm -rf "$tmp"; \
    aws --version; \
    session-manager-plugin --version; \
    kubectl version --client --short

# The scripts run as the calling user, so that files they write on the host stay the user's.
# HOME is shared by whatever uid that is; /etc/passwd is writable for the entrypoint, because
# OpenSSH refuses to run for a uid it cannot look up.
RUN mkdir -p /home/sandbox \
 && chmod 1777 /home/sandbox \
 && chmod 0666 /etc/passwd

COPY tools-entrypoint.sh /usr/local/bin/tools-entrypoint.sh
RUN chmod 0755 /usr/local/bin/tools-entrypoint.sh

# HOST_AWS_DIR is where scripts/tools.sh mounts the host's ~/.aws, read-only.
ENV HOME=/home/sandbox \
    HOST_AWS_DIR=/mnt/host-aws \
    AWS_PAGER=""

ENTRYPOINT ["/usr/local/bin/tools-entrypoint.sh"]
CMD ["bash"]
