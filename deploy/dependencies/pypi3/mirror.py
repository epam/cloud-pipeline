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

"""The simple index (PEP 503) of the pypi3 mirror.

The mirror is a folder of package files with the index in its simple/ subfolder: simple/index.html links
each package page as <name>/, and simple/<name>/index.html links the files of that package as
../../<file>. The standard library only: it runs with the Python of the build host and of the base images.

  mirror.py fetch <simple url> <dir>              copies the index pages under <simple url> into <dir>
  mirror.py merge <out dir> <files dir> [<index dir>...]
                                                  writes into <out dir> the index of every file of
                                                  <files dir> and every link of the <index dir>s
  mirror.py links <index dir>                     prints <page>/<file> for each link of the index
  mirror.py require <requirements> <index dir>    fails when a pinned version has no file in the index
  mirror.py serve <mirror dir> <port> <fallback url>
                                                  serves the mirror; a package file it lacks redirects to
                                                  <fallback url><file>
"""

import functools
import os
import re
import sys

try:
    from http.server import HTTPServer, SimpleHTTPRequestHandler
    from urllib.request import urlopen
except ImportError:
    sys.exit('mirror.py needs Python 3')

PACKAGE_SUFFIXES = ('.whl', '.tar.gz', '.zip')
_HREF = re.compile(r'href="([^"]+)"')


def normalize(name):
    return re.sub(r'[-_.]+', '-', name).lower()


def is_package_file(name):
    return name.endswith(PACKAGE_SUFFIXES)


def split_file_name(file_name):
    """(normalized project name, version) of a wheel or a source distribution file name"""
    if file_name.endswith('.whl'):
        parts = file_name.split('-')
        return normalize(parts[0]), parts[1]
    base = file_name
    for suffix in PACKAGE_SUFFIXES:
        if base.endswith(suffix):
            base = base[:-len(suffix)]
    # the version starts at the first "-" followed by a digit: pyasn1-modules-0.4.2.tar.gz
    match = re.match(r'^(.+?)-(\d.*)$', base)
    if not match:
        raise ValueError('no version in the file name {}'.format(file_name))
    return normalize(match.group(1)), match.group(2)


def same_version(a, b):
    """PEP 440 equality of the release segments: 6.4.0 is 6.4"""
    def key(version):
        release, rest = re.match(r'^v?([0-9.]*)(.*)$', version.lower()).groups()
        numbers = [int(n) for n in release.split('.') if n]
        while numbers and numbers[-1] == 0:
            numbers.pop()
        return numbers, rest
    return key(a) == key(b)


def page_links(html, page='?'):
    """The file names a package page links as ../../<file>. Any other link stops it: a merge that kept only the
    links it reads would drop that one from the index"""
    files = set()
    for href in _HREF.findall(html):
        name = href[len('../../'):]
        if not href.startswith('../../') or not name or '/' in name:
            raise ValueError('the package page {} links "{}", not ../../<file>'.format(page, href))
        files.add(name)
    return files


def read_index(index_dir):
    """{page: {file, ...}} of the package pages in an index folder"""
    pages = {}
    if not os.path.isdir(index_dir):
        return pages
    for page in sorted(os.listdir(index_dir)):
        page_file = os.path.join(index_dir, page, 'index.html')
        if os.path.isfile(page_file):
            with open(page_file) as f:
                pages[page] = page_links(f.read(), page)
    return pages


def page_html(files):
    links = '\n'.join('<a href="../../{0}">{0}</a>'.format(f) for f in sorted(files))
    return '<html><body>{}</body></html>'.format(links)


def root_html(pages):
    links = '\n'.join('<a href="{0}/">{0}</a>'.format(p) for p in sorted(pages))
    return '<html><body>{}</body></html>'.format(links)


def merge(out_dir, files_dir, index_dirs):
    pages = {}
    for index_dir in index_dirs:
        for page, files in read_index(index_dir).items():
            pages.setdefault(page, set()).update(files)
    for file_name in sorted(os.listdir(files_dir)):
        if is_package_file(file_name) and os.path.isfile(os.path.join(files_dir, file_name)):
            pages.setdefault(split_file_name(file_name)[0], set()).add(file_name)
    for page, files in pages.items():
        os.makedirs(os.path.join(out_dir, page), exist_ok=True)
        with open(os.path.join(out_dir, page, 'index.html'), 'w') as f:
            f.write(page_html(files))
    with open(os.path.join(out_dir, 'index.html'), 'w') as f:
        f.write(root_html(pages))
    # A link the merge dropped would make an installed version uninstallable: never publish such an index
    merged = read_index(out_dir)
    for index_dir in index_dirs:
        for page, files in read_index(index_dir).items():
            lost = files - merged.get(page, set())
            if lost:
                raise RuntimeError('the merged index lost {}/{}'.format(page, sorted(lost)))
    print('Indexed {} files of {} packages in {}'.format(sum(len(f) for f in pages.values()), len(pages), out_dir))


def fetch(simple_url, out_dir):
    simple_url = simple_url.rstrip('/') + '/'
    root = urlopen(simple_url, timeout=60).read().decode()
    pages = [href.rstrip('/') for href in _HREF.findall(root) if href.endswith('/') and '/' not in href[:-1]]
    if not pages:
        raise RuntimeError('{} links no package page'.format(simple_url))
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, 'index.html'), 'w') as f:
        f.write(root)
    for page in pages:
        os.makedirs(os.path.join(out_dir, page), exist_ok=True)
        html = urlopen('{}{}/'.format(simple_url, page), timeout=60).read().decode()
        with open(os.path.join(out_dir, page, 'index.html'), 'w') as f:
            f.write(html)
    print('Fetched {} package pages from {}'.format(len(pages), simple_url))


def read_requirements(path):
    with open(path) as f:
        for line in f:
            line = line.split('#', 1)[0].strip()
            if line:
                yield line


def require(requirements, index_dir):
    pages = read_index(index_dir)
    missing = []
    for requirement in read_requirements(requirements):
        match = re.match(r'^([A-Za-z0-9._-]+)\s*(==)?\s*([^\s;\[]*)$', requirement)
        if not match:
            raise ValueError('{}: "{}" is not name==version'.format(requirements, requirement))
        name, operator, version = match.groups()
        if operator != '==':
            missing.append('{} (not pinned with ==)'.format(requirement))
            continue
        files = pages.get(normalize(name), set())
        if not any(same_version(split_file_name(f)[1], version) for f in files):
            missing.append(requirement)
    if missing:
        raise RuntimeError('no file in {} for: {}'.format(index_dir, ', '.join(missing)))
    print('Every requirement of {} has a file in the index'.format(requirements))


class _MirrorHandler(SimpleHTTPRequestHandler):
    fallback = None

    def do_GET(self):
        path = self.path.split('?', 1)[0]
        local = self.translate_path(path)
        if os.path.isdir(local):
            local = os.path.join(local, 'index.html')
        if not os.path.exists(local) and not path.startswith('/simple/') and is_package_file(path):
            self.send_response(302)
            self.send_header('Location', self.fallback + path.lstrip('/'))
            self.end_headers()
            return
        SimpleHTTPRequestHandler.do_GET(self)

    def log_message(self, *args):
        pass


def serve(mirror_dir, port, fallback):
    handler = type('Handler', (_MirrorHandler,), {'fallback': fallback.rstrip('/') + '/'})
    HTTPServer(('127.0.0.1', int(port)), functools.partial(handler, directory=mirror_dir)).serve_forever()


def main(argv):
    commands = {
        'fetch': (fetch, 2), 'merge': (merge, None), 'links': (None, 1), 'require': (require, 2),
        'serve': (serve, 3),
    }
    if len(argv) < 2 or argv[1] not in commands:
        sys.exit(__doc__)
    command, args = argv[1], argv[2:]
    if command == 'links':
        if len(args) != 1:
            sys.exit(__doc__)
        for page, files in sorted(read_index(args[0]).items()):
            for f in sorted(files):
                print('{}/{}'.format(page, f))
    elif command == 'merge':
        if len(args) < 2:
            sys.exit(__doc__)
        merge(args[0], args[1], args[2:])
    else:
        function, count = commands[command]
        if len(args) != count:
            sys.exit(__doc__)
        function(*args)


if __name__ == '__main__':
    try:
        main(sys.argv)
    except (RuntimeError, ValueError, OSError) as e:
        sys.exit('[ERROR] {}'.format(e))
