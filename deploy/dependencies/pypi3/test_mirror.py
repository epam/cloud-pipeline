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

# Tests mirror.py: the names and versions it reads from file names, the merge that never loses a link, the check of
# the requirements, and the server of check.sh with its fallback. Run by test.sh: python3 -m unittest test_mirror

import os
import shutil
import socket
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, build_opener, urlopen

import mirror


def _touch(path, content=''):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content)


def _read(path):
    with open(path) as f:
        return f.read()


class FileNamesTest(unittest.TestCase):

    def test_wheel(self):
        self.assertEqual(('flask-httpauth', '4.8.1'), mirror.split_file_name('Flask_HTTPAuth-4.8.1-py3-none-any.whl'))

    def test_source_distribution_with_a_digit_and_a_dash_in_its_name(self):
        self.assertEqual(('pyasn1-modules', '0.4.2'), mirror.split_file_name('pyasn1-modules-0.4.2.tar.gz'))
        self.assertEqual(('pyyaml', '6.0.3'), mirror.split_file_name('PyYAML-6.0.3.tar.gz'))
        self.assertEqual(('lockfile', '0.12.2'), mirror.split_file_name('lockfile-0.12.2.zip'))

    def test_a_file_name_without_a_version_is_an_error(self):
        self.assertRaises(ValueError, mirror.split_file_name, 'noversion.tar.gz')

    def test_versions_compare_as_pip_does(self):
        self.assertTrue(mirror.same_version('6.4', '6.4.0'))
        self.assertTrue(mirror.same_version('1.0.0rc1', '1.0rc1'))
        self.assertFalse(mirror.same_version('6.4', '6.4.1'))
        self.assertFalse(mirror.same_version('1.0', '1.0rc1'))


class IndexTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.files = os.path.join(self.tmp, 'files')
        os.makedirs(self.files)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _published(self, pages):
        published = os.path.join(self.tmp, 'published')
        for page, files in pages.items():
            _touch(os.path.join(published, page, 'index.html'), mirror.page_html(files))
        _touch(os.path.join(published, 'index.html'), mirror.root_html(pages))
        return published

    def test_merge_keeps_every_published_link_and_adds_the_new_files(self):
        published = self._published({'six': {'six-1.15.0-py2.py3-none-any.whl'}, 'idna': {'idna-2.8.tar.gz'}})
        _touch(os.path.join(self.files, 'six-1.16.0-py2.py3-none-any.whl'))
        _touch(os.path.join(self.files, 'PyYAML-6.0.3.tar.gz'))
        _touch(os.path.join(self.files, 'BUILD-INFO.txt'))
        out = os.path.join(self.tmp, 'out')
        mirror.merge(out, self.files, [published])
        self.assertEqual({'six': {'six-1.15.0-py2.py3-none-any.whl', 'six-1.16.0-py2.py3-none-any.whl'},
                          'idna': {'idna-2.8.tar.gz'}, 'pyyaml': {'PyYAML-6.0.3.tar.gz'}}, mirror.read_index(out))
        self.assertEqual('<html><body><a href="idna/">idna</a>\n<a href="pyyaml/">pyyaml</a>\n'
                         '<a href="six/">six</a></body></html>', _read(os.path.join(out, 'index.html')))

    def test_pages_keep_the_layout_of_the_mirror(self):
        # pip follows ../../<file> from simple/<page>/ to the files next to simple/
        out = os.path.join(self.tmp, 'out')
        _touch(os.path.join(self.files, 'idna-3.20-py3-none-any.whl'))
        _touch(os.path.join(self.files, 'idna-2.8.tar.gz'))
        mirror.merge(out, self.files, [])
        self.assertEqual('<html><body><a href="../../idna-2.8.tar.gz">idna-2.8.tar.gz</a>\n'
                         '<a href="../../idna-3.20-py3-none-any.whl">idna-3.20-py3-none-any.whl</a></body></html>',
                         _read(os.path.join(out, 'idna', 'index.html')))

    def test_merge_of_two_indexes_is_their_union(self):
        first = self._published({'six': {'six-1.15.0-py2.py3-none-any.whl'}})
        second = os.path.join(self.tmp, 'second')
        _touch(os.path.join(second, 'six', 'index.html'), mirror.page_html({'six-1.16.0-py2.py3-none-any.whl'}))
        out = os.path.join(self.tmp, 'out')
        mirror.merge(out, self.files, [first, second])
        self.assertEqual({'six': {'six-1.15.0-py2.py3-none-any.whl', 'six-1.16.0-py2.py3-none-any.whl'}},
                         mirror.read_index(out))

    def _requirements(self, *lines):
        path = os.path.join(self.tmp, 'requirements.txt')
        _touch(path, '\n'.join(lines) + '\n')
        return path

    def test_a_link_that_is_not_to_a_file_next_to_simple_stops_the_merge(self):
        # e.g. a link added to the bucket by hand: the merge would drop it from the index
        published = self._published({'six': {'six-1.15.0-py2.py3-none-any.whl'}})
        _touch(os.path.join(published, 'idna', 'index.html'),
               '<html><body><a href="https://files.example.com/idna-2.8.tar.gz">idna-2.8.tar.gz</a></body></html>')
        with self.assertRaises(ValueError) as error:
            mirror.merge(os.path.join(self.tmp, 'out'), self.files, [published])
        self.assertIn('the package page idna links "https://files.example.com/idna-2.8.tar.gz"', str(error.exception))

    def test_require_passes_when_each_pin_has_a_file(self):
        published = self._published({'tornado': {'tornado-6.4-cp38-abi3-manylinux2014_x86_64.whl'},
                                     'pyyaml': {'PyYAML-6.0.3.tar.gz'}})
        mirror.require(self._requirements('# comment', '', 'tornado==6.4.0', 'PyYAML==6.0.3  # inline'), published)

    def test_require_names_the_missing_and_the_unpinned_requirements(self):
        published = self._published({'six': {'six-1.15.0-py2.py3-none-any.whl'}})
        with self.assertRaises(RuntimeError) as error:
            mirror.require(self._requirements('six==1.16.0', 'idna==3.20', 'six'), published)
        self.assertIn('six==1.16.0, idna==3.20, six (not pinned with ==)', str(error.exception))

    def test_require_stops_at_a_requirement_it_cannot_read(self):
        with self.assertRaises(ValueError):
            mirror.require(self._requirements('requests[socks]==2.34.2'), self._published({}))


class ServeAndFetchTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, 'mirror')
        _touch(os.path.join(self.root, 'six-1.16.0-py2.py3-none-any.whl'), 'wheel')
        _touch(os.path.join(self.root, 'simple', 'six', 'index.html'),
               mirror.page_html({'six-1.16.0-py2.py3-none-any.whl', 'six-1.15.0-py2.py3-none-any.whl'}))
        _touch(os.path.join(self.root, 'simple', 'index.html'), mirror.root_html({'six'}))
        with socket.socket() as s:
            s.bind(('127.0.0.1', 0))
            self.port = s.getsockname()[1]
        threading.Thread(target=mirror.serve, args=(self.root, self.port, 'https://bucket.example.com/pypi3'),
                         daemon=True).start()
        self.url = 'http://127.0.0.1:{}/'.format(self.port)
        for _ in range(50):
            try:
                urlopen(self.url + 'simple/', timeout=1)
                break
            except OSError:
                threading.Event().wait(0.1)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _get_without_redirects(self, path):
        class NoRedirect(HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs):
                return None
        try:
            return build_opener(NoRedirect).open(self.url + path, timeout=5)
        except HTTPError as e:
            return e

    def test_serves_the_index_and_the_files_of_the_build(self):
        self.assertIn('six-1.15.0', urlopen(self.url + 'simple/six/', timeout=5).read().decode())
        self.assertEqual(b'wheel', urlopen(self.url + 'six-1.16.0-py2.py3-none-any.whl', timeout=5).read())

    def test_a_file_the_build_lacks_redirects_to_the_bucket(self):
        response = self._get_without_redirects('six-1.15.0-py2.py3-none-any.whl')
        self.assertEqual(302, response.code)
        self.assertEqual('https://bucket.example.com/pypi3/six-1.15.0-py2.py3-none-any.whl',
                         response.headers['Location'])

    def test_a_package_page_the_index_lacks_is_not_found(self):
        self.assertEqual(404, self._get_without_redirects('simple/idna/').code)

    def test_fetch_copies_every_page(self):
        out = os.path.join(self.tmp, 'fetched')
        mirror.fetch(self.url + 'simple', out)
        self.assertEqual(mirror.read_index(os.path.join(self.root, 'simple')), mirror.read_index(out))
        self.assertEqual(_read(os.path.join(self.root, 'simple', 'index.html')), _read(os.path.join(out, 'index.html')))


if __name__ == '__main__':
    unittest.main()
