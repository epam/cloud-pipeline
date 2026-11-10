#!/bin/sh
# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Runs in a base image, started by check.sh, with the tarball in /dist and the repository in /repo.
# Unpacks the distribution into /usr/local as a run does, then checks it. POSIX sh: the base images differ.

MIRROR="http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com/tools/python/pypi3/simple"
MIRROR_HOST="${MIRROR#http://}"
MIRROR_HOST="${MIRROR_HOST%%/*}"
PIP_ARGS="-q --disable-pip-version-check --root-user-action=ignore --index-url $MIRROR --trusted-host $MIRROR_HOST"
HOME_DIR=/usr/local/python3.12
P="$HOME_DIR/bin/python3.12"
FAILED=0

pass() { echo "[OK] $*"; }
fail() { echo "[FAIL] $*"; FAILED=1; }
note() { echo "[NOTE] $*"; }

OS="$( (. /etc/os-release && echo "$PRETTY_NAME") 2>/dev/null || cat /etc/system-release)"
echo "==== $OS, glibc $(ldd --version 2>&1 | head -1 | awk '{ print $NF }')"

if command -v tar > /dev/null; then
    tar -xzf /dist/python3.12.tar.gz -C /usr/local
else
    for py in python2 python python3; do
        command -v "$py" > /dev/null \
            && "$py" -c "import tarfile; tarfile.open('/dist/python3.12.tar.gz').extractall('/usr/local')" && break
    done
fi
"$P" --version > /dev/null 2>&1 || { fail "$P does not start"; exit 1; }
pass "$("$P" --version)"

# Libraries: only the system libcrypt.so.1 of _crypt may be missing (RHEL 9 has libcrypt.so.2 only)
for f in "$P" "$HOME_DIR"/lib/python3.12/lib-dynload/*.so; do
    for lib in $(ldd "$f" 2>/dev/null | awk '/not found/ { print $1 }'); do
        case "$(basename "$f"):$lib" in
            _crypt.*:libcrypt.so.1) note "_crypt needs libcrypt.so.1, which this image lacks" ;;
            *) fail "$(basename "$f") needs $lib, which this image lacks" ;;
        esac
    done
done

# Modules: they load and work. TERM=xterm: the terminfo of the image
TERM=xterm "$P" -W ignore - <<'EOF' || FAILED=1
import bz2, ctypes, curses, curses.panel, decimal, hashlib, lzma, readline, sqlite3, ssl, uuid, zlib
failed = []
def check(name, test):
    try:
        test()
        print("[OK] " + name)
    except Exception as e:
        print("[FAIL] %s: %s: %s" % (name, type(e).__name__, e))
        failed.append(name)
check("ctypes", lambda: ctypes.CDLL(None).getpid())
def sqlite():
    db = sqlite3.connect(":memory:")
    db.execute("create virtual table t using fts5(x)")
    db.execute("insert into t values ('cloud pipeline')")
    assert db.execute("select count(*) from t where t match 'pipeline'").fetchone()[0] == 1
check("sqlite3 %s with FTS5" % sqlite3.sqlite_version, sqlite)
check("lzma, bz2, zlib", lambda: [m.decompress(m.compress(b"x" * 100)) for m in (lzma, bz2, zlib)])
check("uuid", lambda: (uuid.uuid1(), uuid.uuid4()))
check("hashlib sha3 and blake2", lambda: (hashlib.sha3_256(b"x").hexdigest(), hashlib.blake2b(b"x").hexdigest()))
check("decimal", lambda: decimal.Decimal("1.1") + decimal.Decimal("2.2"))
check("readline (%s)" % ("libedit" if "libedit" in readline.__doc__ else "GNU readline"),
      lambda: readline.parse_and_bind("tab: complete"))
def terminfo():
    curses.setupterm("xterm", 1)
    assert curses.tigetstr("cup")
check("curses with the image's terminfo", terminfo)
check(ssl.OPENSSL_VERSION, lambda: ssl.create_default_context())
raise SystemExit(1 if failed else 0)
EOF

# crypt, where the image has libcrypt.so.1: a SHA-512 hash through the system library
if ldd "$HOME_DIR"/lib/python3.12/lib-dynload/_crypt.*.so | grep -q 'libcrypt.so.1 => not found'; then
    note "crypt not checked: the image has no libcrypt.so.1"
elif "$P" -W ignore -c 'import crypt; assert crypt.crypt("cloud", "$6$pipeline$").startswith("$6$pipeline$")'; then
    pass "crypt, with the libcrypt.so.1 of the image"
else
    fail "crypt, with the libcrypt.so.1 of the image (exit code $?)"
fi

# HTTPS with the default context. The minimal Debian and Ubuntu images have no CA store: add theirs, so the
# Debian layout (the hashed /etc/ssl/certs) is checked too, as the RHEL one (/etc/pki/tls/cert.pem)
if [ ! -d /etc/ssl/certs ] && command -v apt-get > /dev/null; then
    if apt-get update -qq > /tmp/apt.log 2>&1 \
            && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ca-certificates >> /tmp/apt.log 2>&1; then
        note "installed ca-certificates of the image"
    else
        fail "ca-certificates could not be installed, so the Debian CA layout is not checked: $(tail -1 /tmp/apt.log)"
    fi
fi
CA_STORE=""
for f in /etc/pki/tls/cert.pem /etc/ssl/cert.pem /etc/ssl/certs/ca-certificates.crt; do
    [ -s "$f" ] && CA_STORE="$f" && break
done
if [ -n "$CA_STORE" ]; then
    if "$P" -c "import urllib.request; urllib.request.urlopen('https://pypi.org/simple/pip/', timeout=30)" 2>/tmp/https.log; then
        pass "HTTPS with the default context ($CA_STORE)"
    else
        fail "HTTPS with the default context ($CA_STORE): $(tail -1 /tmp/https.log)"
    fi
else
    note "no CA store in this image: HTTPS with the default context not checked"
fi

# pip3.12, which the runs, the commit scripts and the platform images link as pip3, and the pipeline package
# from the pypi3 mirror, as a run installs it
if "$HOME_DIR/bin/pip3.12" --version > /dev/null 2>&1; then
    pass "bin/pip3.12: $("$HOME_DIR/bin/pip3.12" --version | cut -d' ' -f1-2)"
else
    fail "bin/pip3.12 does not run"
fi
cp -r /repo/workflows/pipe-common /tmp/pipe-common
if "$P" -m pip install $PIP_ARGS /tmp/pipe-common > /tmp/pip.log 2>&1; then
    CHECK="$("$P" -m pip check 2>&1)"
    case "$CHECK" in
        "No broken requirements found.") pass "pipeline package from the pypi3 mirror, pip check clean" ;;
        *) fail "pip check: $CHECK" ;;
    esac
else
    fail "pipeline package from the pypi3 mirror: $(grep -E '^ERROR' /tmp/pip.log | head -2)"
fi

# watchdog, which the NFS events observer of a run uses (through ctypes)
"$P" -W ignore - <<'EOF' && pass "watchdog observer gets file events" || fail "watchdog observer"
import os, tempfile, time
from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer
seen = []
class Handler(FileSystemEventHandler):
    def on_created(self, event):
        seen.append(event.src_path)
folder = tempfile.mkdtemp()
observer = Observer()
observer.schedule(Handler(), folder)
observer.start()
open(os.path.join(folder, "file"), "w").close()
time.sleep(2)
observer.stop()
observer.join()
assert seen, "no event"
EOF

[ "$FAILED" -eq 0 ] && echo "==== passed: $OS" || echo "==== FAILED: $OS"
exit "$FAILED"
