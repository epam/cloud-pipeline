#!/bin/bash
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

# Runs in quay.io/pypa/manylinux2014_x86_64 (glibc 2.17), started by build.sh. Builds the libraries of
# sources.txt as static -fPIC archives into $DEPS, then CPython into /usr/local/python3.12 with them linked
# in, checks the result and packs /out/python3.12.tar.gz.

set -euo pipefail

RECIPE=/recipe
OUT=/out
SRC=/tmp/src
BUILD=/tmp/build
DEPS=/opt/deps
PREFIX=/usr/local/python3.12
STAGE=/tmp/stage
JOBS="$(nproc)"

mkdir -p "$SRC" "$BUILD" "$DEPS/lib" "$DEPS/include" "$STAGE" "$OUT"
# For the libraries; CPython keeps its own flags (-O3), see below
export CFLAGS="-O2 -fPIC"
export CXXFLAGS="-O2 -fPIC"
export CPPFLAGS="-I$DEPS/include"
export LDFLAGS="-L$DEPS/lib"
# Only the libraries built here: not the .pc files of /usr/local of the build image (its libxcrypt, for one),
# which its PKG_CONFIG_PATH adds
export PKG_CONFIG_LIBDIR="$DEPS/lib/pkgconfig"
export PKG_CONFIG_PATH=""
LICENSES="$STAGE$PREFIX/share/licenses"
mkdir -p "$LICENSES"

log() { echo "==== $(date -u +%H:%M:%S) $*"; }

# The OpenSSL build needs these Perl modules, which CentOS 7 packages apart from Perl
missing_packages=""
for package in perl-IPC-Cmd perl-Time-Piece perl-Digest-SHA; do
    rpm -q "$package" > /dev/null 2>&1 || missing_packages="$missing_packages $package"
done
if [ -n "$missing_packages" ]; then
    # shellcheck disable=SC2086
    yum install -y -q $missing_packages
fi

source_field() {
    awk -v name="$1" -v field="$2" '$1 == name { print $field }' "$RECIPE/sources.txt"
}

# Downloads a source of sources.txt, checks its SHA256, unpacks it into $BUILD and prints the unpacked folder.
# Runs in $(...), where bash turns off -e: each step returns on its own error.
fetch() {
    local name="$1" sha url file top
    sha="$(source_field "$name" 3)"
    url="$(source_field "$name" 4)"
    [ -n "$sha" ] && [ -n "$url" ] || { echo "[ERROR] no source '$name' in sources.txt" >&2; return 1; }
    file="$SRC/$(basename "${url%/download}")"
    curl -fsSL --retry 5 --retry-delay 10 -o "$file" "$url" || { echo "[ERROR] download of $url" >&2; return 1; }
    echo "$sha  $file" | sha256sum -c - >&2 || { echo "[ERROR] SHA256 of $file" >&2; return 1; }
    tar -xf "$file" -C "$BUILD" || { echo "[ERROR] unpacking $file" >&2; return 1; }
    top="$(tar -tf "$file" | sed -n 1p)"
    [ -n "$top" ] || { echo "[ERROR] $file is empty" >&2; return 1; }
    echo "$BUILD/${top%%/*}"
}

# Copies the license files of a source into the distribution
license() {
    local name="$1" dir="$2"
    shift 2
    mkdir -p "$LICENSES/$name"
    for f in "$@"; do
        cp "$dir/$f" "$LICENSES/$name/"
    done
}

log "zlib $(source_field zlib 2)"
dir="$(fetch zlib)"
(cd "$dir" && ./configure --static --prefix="$DEPS" && make -j"$JOBS" && make install)
license zlib "$dir" LICENSE

log "bzip2 $(source_field bzip2 2)"
dir="$(fetch bzip2)"
(cd "$dir" && make -j"$JOBS" libbz2.a CFLAGS="$CFLAGS -D_FILE_OFFSET_BITS=64" \
    && install -m 644 libbz2.a "$DEPS/lib/" && install -m 644 bzlib.h "$DEPS/include/")
license bzip2 "$dir" LICENSE

log "xz $(source_field xz 2)"
dir="$(fetch xz)"
(cd "$dir" && ./configure --prefix="$DEPS" --disable-shared --enable-static --with-pic \
        --disable-xz --disable-xzdec --disable-lzmadec --disable-lzmainfo --disable-lzma-links \
        --disable-scripts --disable-doc --disable-nls \
    && make -j"$JOBS" && make install)
license xz "$dir" COPYING COPYING.0BSD

log "libffi $(source_field libffi 2)"
dir="$(fetch libffi)"
(cd "$dir" && ./configure --prefix="$DEPS" --disable-shared --enable-static --with-pic \
        --disable-docs --disable-multi-os-directory \
    && make -j"$JOBS" && make install)
license libffi "$dir" LICENSE

log "sqlite $(source_field sqlite 2)"
dir="$(fetch sqlite)"
(cd "$dir" && CFLAGS="$CFLAGS -DSQLITE_ENABLE_FTS3 -DSQLITE_ENABLE_FTS3_PARENTHESIS -DSQLITE_ENABLE_FTS4 \
            -DSQLITE_ENABLE_FTS5 -DSQLITE_ENABLE_RTREE -DSQLITE_ENABLE_DBSTAT_VTAB -DSQLITE_ENABLE_MATH_FUNCTIONS" \
        ./configure --prefix="$DEPS" --disable-shared --enable-static \
    && make -j"$JOBS" && make install)
mkdir -p "$LICENSES/sqlite"
echo "SQLite is in the public domain: https://www.sqlite.org/copyright.html" > "$LICENSES/sqlite/NOTICE"

# The terminfo databases are not shipped: ncurses reads them where RHEL and Debian keep them
log "ncurses $(source_field ncurses 2)"
dir="$(fetch ncurses)"
(cd "$dir" && ./configure --prefix="$DEPS" --without-shared --with-normal --enable-widec --with-pic \
        --without-debug --without-cxx --without-cxx-binding --without-ada --without-tests \
        --without-manpages --without-progs --disable-db-install \
        --enable-pc-files --with-pkg-config-libdir="$DEPS/lib/pkgconfig" \
        --with-terminfo-dirs=/etc/terminfo:/lib/terminfo:/usr/share/terminfo \
        --with-default-terminfo-dir=/usr/share/terminfo \
    && make -j"$JOBS" && make install)
license ncurses "$dir" COPYING
# libedit looks for -lncurses or -ltinfo: both are the wide-character library here
ln -sf libncursesw.a "$DEPS/lib/libncurses.a"
ln -sf libncursesw.a "$DEPS/lib/libtinfo.a"

# libedit in place of GNU readline, which is GPL
log "libedit $(source_field libedit 2)"
dir="$(fetch libedit)"
(cd "$dir" && CPPFLAGS="$CPPFLAGS -I$DEPS/include/ncursesw" ./configure --prefix="$DEPS" \
        --disable-shared --enable-static --with-pic \
    && make -j"$JOBS" && make install)
license libedit "$dir" COPYING

log "libuuid $(source_field libuuid 2)"
dir="$(fetch libuuid)"
(cd "$dir" && ./configure --prefix="$DEPS" --disable-shared --enable-static --with-pic \
    && make -j"$JOBS" && make install)
license libuuid "$dir" COPYING

# /etc/ssl is the OpenSSL directory of Debian; the ssl patch adds the CA file of the RHEL family
log "openssl $(source_field openssl 2)"
dir="$(fetch openssl)"
(cd "$dir" && ./Configure linux-x86_64 --prefix="$DEPS" --libdir=lib --openssldir=/etc/ssl \
        no-shared no-tests no-docs -fPIC \
    && make -j"$JOBS" && make install_sw)
license openssl "$dir" LICENSE.txt

log "cpython $(source_field cpython 2)"
dir="$(fetch cpython)"
cd "$dir"
patch -p1 < "$RECIPE/patches/cpython-3.12-ssl-rhel-ca-file.patch"
# The extension modules are built into the interpreter (MODULE_BUILDTYPE=static), so each library is linked
# once: curses and curses.panel share one ncurses. CPython's own test modules are not built: built in, they
# break the link of its build tools, and the test suite they serve is not shipped. _crypt stays a loadable module: it needs the system's
# libcrypt.so.1, which RHEL 9 lacks, and that must not keep the interpreter from starting.
printf '*shared*\n_crypt _cryptmodule.c\n' > Modules/Setup.local
# The symbols of the libraries stay private to the interpreter, so they cannot clash with the libraries
# that user code loads; the Python API (libpython3.12.a) is exported as usual
EXCLUDE_LIBS="libssl.a:libcrypto.a:libffi.a:libsqlite3.a:libncursesw.a:libncurses.a:libtinfo.a:libpanelw.a:libedit.a"
EXCLUDE_LIBS="$EXCLUDE_LIBS:liblzma.a:libbz2.a:libz.a:libuuid.a:libmpdec.a:libHacl_Hash_SHA2.a:libexpat.a"
# _crypt needs libcrypt.so.1, which the glibc systems have. The libcrypt.so.1 of manylinux2014 (libxcrypt, in
# /usr/local) only runs old binaries: its symbols have no default version, so nothing links against it. _crypt
# links against a stub of that name instead, and binds at run time to the libcrypt.so.1 of the system. It calls
# crypt(), not crypt_r(): the struct crypt_data of crypt_r() is larger in glibc than in libxcrypt, so a module
# built for one overruns its stack with the other. CPython takes crypt_r() when pkg-config finds libxcrypt, or
# when -lcrypt has it (libxcrypt's libcrypt.so, in /usr/local/lib): PKG_CONFIG_LIBDIR and the answer
# ac_cv_search_crypt_r=no keep it from doing so.
mkdir -p /opt/crypt-stub
printf '%s\n' 'char *crypt(const char *key, const char *salt) { return 0; }' > /opt/crypt-stub/stub.c
gcc -shared -fPIC -Wl,-soname,libcrypt.so.1 -o /opt/crypt-stub/libcrypt.so.1 /opt/crypt-stub/stub.c
unset CFLAGS CXXFLAGS
./configure --prefix="$PREFIX" \
    --with-openssl="$DEPS" --with-openssl-rpath=no \
    --with-readline=editline \
    --enable-loadable-sqlite-extensions \
    --enable-optimizations --with-lto \
    --with-ensurepip=install \
    --disable-test-modules \
    MODULE_BUILDTYPE=static \
    PKG_CONFIG="pkg-config --static" \
    CPPFLAGS="$CPPFLAGS -I$DEPS/include/ncursesw" \
    LDFLAGS="$LDFLAGS -Wl,--exclude-libs,$EXCLUDE_LIBS" \
    LIBCRYPT_LIBS="-L/opt/crypt-stub -l:libcrypt.so.1" ac_cv_search_crypt_r=no \
    py_cv_module__gdbm=n/a py_cv_module__dbm=n/a py_cv_module_nis=n/a py_cv_module__tkinter=n/a
# Stop before the long optimized build, if configure did not find a library, or took crypt_r()
if grep -q '^#define HAVE_CRYPT_R' pyconfig.h; then
    echo "[ERROR] configure: _crypt would call crypt_r()"
    exit 1
fi
for module in _ctypes _sqlite3 readline _curses _curses_panel _lzma _bz2 zlib binascii _uuid _ssl _hashlib _decimal _crypt; do
    variable="MODULE_$(echo "$module" | tr '[:lower:]' '[:upper:]')_STATE"
    state="$(sed -n "s/^$variable=//p" Makefile)"
    if [ "$state" != yes ]; then
        echo "[ERROR] configure: module $module is '$state'. Modules not built:"
        grep -E '^MODULE_[A-Z0-9_]+_STATE=' Makefile | grep -vE '=(yes|n/a|disabled)$' | sort -u || true
        exit 1
    fi
done
make -j"$JOBS"
make install DESTDIR="$STAGE"
license cpython "$dir" LICENSE Doc/license.rst
cd /

log "checks"
P="$STAGE$PREFIX"
failed=0
# No shared library outside glibc, but the system libcrypt.so.1 for _crypt
GLIBC_LIBS='^(libc\.so\.6|libm\.so\.6|libpthread\.so\.0|libdl\.so\.2|libutil\.so\.1|librt\.so\.1|ld-linux-x86-64\.so\.2)$'
for f in "$P/bin/python3.12" $(find "$P/lib/python3.12/lib-dynload" -name '*.so'); do
    for lib in $(readelf -d "$f" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p'); do
        if [[ "$lib" =~ $GLIBC_LIBS ]] || { [ "$lib" = libcrypt.so.1 ] && [[ "$f" == */_crypt.* ]]; }; then
            continue
        fi
        echo "[ERROR] ${f#$P/} needs $lib"
        failed=1
    done
done
# _crypt calls crypt(), not crypt_r()
if nm -D --undefined-only "$P"/lib/python3.12/lib-dynload/_crypt.*.so | grep -qw crypt_r; then
    echo "[ERROR] _crypt calls crypt_r()"
    failed=1
fi
# The interpreter exports the Python API and the usual ELF symbols (glibc's data, copied into the executable,
# keeps its GLIBC version), none of the libraries linked into it
unexpected="$(nm -D --defined-only "$P/bin/python3.12" | awk '{ print $3 }' | grep -vE '^_?Py|@@GLIBC_[0-9.]+$' \
    | grep -vxE '_IO_stdin_used|__data_start|data_start|_edata|_end|__bss_start|_init|_fini|_start|main|__libc_csu_(init|fini)|__dso_handle' \
    || true)"
if [ -n "$unexpected" ]; then
    echo "[ERROR] the interpreter exports $(echo "$unexpected" | wc -l) symbols outside the Python API, e.g.:"
    echo "$unexpected" | sed -n 1,40p
    failed=1
fi
# The modules load. Not crypt: the build image has no libcrypt.so.1 that it can bind to (check.sh checks it)
if ! "$P/bin/python3.12" -W ignore -c '
import sys, ctypes, sqlite3, readline, curses, curses.panel, lzma, bz2, zlib, uuid, ssl, hashlib, decimal
print(sys.version.split()[0], ssl.OPENSSL_VERSION, "sqlite", sqlite3.sqlite_version)
'; then
    echo "[ERROR] a module does not load"
    failed=1
fi
[ "$failed" -eq 0 ] || exit 1

log "pack"
# Smaller download at every run start: no test suite, no static libpython for embedding, no debug symbols
rm -rf "$P/lib/python3.12/test" "$P/lib/python3.12/idlelib/idle_test" "$P/lib/python3.12/tkinter/test"
rm -f "$P/lib/libpython3.12.a" "$P"/lib/python3.12/config-3.12-*/libpython3.12.a
strip "$P/bin/python3.12"
find "$P/lib/python3.12/lib-dynload" -name '*.so' -exec strip --strip-unneeded {} +
{
    echo "Python 3.12 distribution for /usr/local/python3.12"
    echo "commit: $GIT_COMMIT"
    echo "build image: $BUILD_IMAGE"
    echo "built: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "sources:"
    grep -vE '^(#|$)' "$RECIPE/sources.txt" | awk '{ print "  " $1 " " $2 " " $3 }'
} > "$P/BUILD-INFO.txt"
cp "$P/BUILD-INFO.txt" "$OUT/BUILD-INFO.txt"
tar -C "$STAGE/usr/local" -czf "$OUT/python3.12.tar.gz" python3.12
(cd "$OUT" && sha256sum python3.12.tar.gz > python3.12.tar.gz.sha256)
log "done: $(du -h "$OUT/python3.12.tar.gz" | cut -f1)"
