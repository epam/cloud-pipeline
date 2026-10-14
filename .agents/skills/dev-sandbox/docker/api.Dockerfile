# The image the dev sandbox runs the platform's API in, on the developer's workstation, and its
# optional services: Java 8 for them, Python 3.12 with the packages of the node scripts the API
# runs (scripts/autoscaling's requirements and pipe-common's), and tun2socks, which routes the VPC
# through the tunnel's SOCKS proxy.
#
# Built by scripts/sandbox.sh services, from a context holding this file, the entrypoints,
# scripts/autoscaling/requirements.txt and workflows/pipe-common, and tagged with a hash of them.
# The jars, the node scripts and pipe-common's code come from the checkout at run time.

FROM eclipse-temurin:8-jre-noble

ARG TUN2SOCKS_VERSION=2.7.0
ARG TUN2SOCKS_URL=https://github.com/xjasonlyu/tun2socks/releases/download
ARG TUN2SOCKS_SHA256_AMD64=a612baa287a3b6de6221f74fd02b442a50888508227ecf51e1288a5ccbb77381
ARG TUN2SOCKS_SHA256_ARM64=3931476c9cfa8fa236d23aeaf36767df0eb27cc11ecaab699faba57744450f49

# Set by BuildKit. The legacy builder leaves it empty, and dpkg names the same architectures.
ARG TARGETARCH

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl iproute2 openssl python3 \
      python3-venv unzip \
 && rm -rf /var/lib/apt/lists/*

RUN set -eu; \
    arch="${TARGETARCH:-$(dpkg --print-architecture)}"; \
    case "$arch" in \
      amd64) sha256="$TUN2SOCKS_SHA256_AMD64" ;; \
      arm64) sha256="$TUN2SOCKS_SHA256_ARM64" ;; \
      *) echo "Unsupported architecture: $arch" >&2; exit 1 ;; \
    esac; \
    tmp="$(mktemp -d)"; \
    curl -fsSL -o "$tmp/tun2socks.zip" \
      "$TUN2SOCKS_URL/v$TUN2SOCKS_VERSION/tun2socks-linux-$arch.zip"; \
    echo "$sha256  $tmp/tun2socks.zip" | sha256sum -c -; \
    unzip -p "$tmp/tun2socks.zip" "tun2socks-linux-$arch" >/usr/local/bin/tun2socks; \
    chmod 0755 /usr/local/bin/tun2socks; \
    rm -rf "$tmp"; \
    tun2socks --version

# pipe-common is installed for its dependencies: at run time, PYTHONPATH puts the checkout's copy
# of its code first.
COPY requirements.txt /opt/build/requirements.txt
COPY pipe-common /opt/build/pipe-common
RUN python3 -m venv /opt/venv \
 && /opt/venv/bin/pip install --no-cache-dir -r /opt/build/requirements.txt /opt/build/pipe-common \
 && rm -rf /opt/build

# service-entrypoint.sh starts the platform's optional services, which run in this image too.
COPY tools-entrypoint.sh api-entrypoint.sh service-entrypoint.sh /usr/local/bin/
RUN chmod 0755 /usr/local/bin/tools-entrypoint.sh /usr/local/bin/api-entrypoint.sh \
      /usr/local/bin/service-entrypoint.sh

# CP_PYTHON_PATH is the interpreter the API runs the node scripts with. HOST_AWS_DIR is where
# sandbox.sh mounts the host's ~/.aws, read-only, as for the tools image.
ENV CP_PYTHON_PATH=/opt/venv/bin/python3 \
    HOST_AWS_DIR=/mnt/host-aws \
    HOME=/root \
    AWS_PAGER=""

ENTRYPOINT ["/usr/local/bin/api-entrypoint.sh"]
