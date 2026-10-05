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

# Covers the Python 2/3 compatibility of this package (issue #4531, the cp-dav side): no runner
# existed here before this. Run with `python -m unittest test_nfs_roles_management` (or pytest)
# under both Python 2.7 and Python 3.12 - this package is copied as-is into the cp-dav image
# (deploy/docker/build-dockers.sh) and run under whichever interpreter CP_PYTHON_PATH resolves to.

import glob
import os
import py_compile
import sys
import tempfile
import unittest

try:
    from unittest import mock
except ImportError:
    import mock

THIS_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, THIS_DIR)

from internal.synchronization.synchronization import Synchronization  # noqa: E402


class ModulesCompileUnderCurrentInterpreterTest(unittest.TestCase):

    def test_every_module_compiles(self):
        pattern = os.path.join(THIS_DIR, '**', '*.py')
        modules = glob.glob(pattern, recursive=True)
        self.assertTrue(modules, 'no .py files found under %s - did the layout change?' % THIS_DIR)
        for module_path in modules:
            with tempfile.NamedTemporaryFile(suffix='.pyc') as cfile:
                py_compile.compile(module_path, cfile=cfile.name, doraise=True)


class ResolveMountsBytesCompatTest(unittest.TestCase):
    """
    Regression test for issue #4531: subprocess.check_output() returns bytes under Python 3 and
    str under Python 2. resolve_mounts() used to split() the raw check_output() result directly,
    which raises TypeError under Python 3 ("a bytes-like object is required, not 'str'").
    resolve_mounts() uses no instance state, so it is called unbound rather than constructing a
    real Synchronization (whose __init__ makes live API calls).
    """

    SAMPLE_OUTPUT = (
        "/dev/sda1 on / type ext4 (rw,relatime)\n"
        "nfsserver:/export on /dav-mount/nfsserver type nfs (ro,relatime)\n"
        "tmpfs on /dav-mount/other type tmpfs (rw,relatime)\n"
    )

    def _resolve_mounts_under_root(self, root_dir, mount_output):
        # Synchronization.resolve_mounts(None, root_dir) would call an unbound method with a
        # non-instance first argument - Python 2 new-style classes reject that with a TypeError
        # ("must be called with Synchronization instance as first argument"); Python 3 has no such
        # check and would silently let it through. __new__ bypasses __init__ (which makes live API
        # calls) while still producing a real instance, so this works under both.
        instance = Synchronization.__new__(Synchronization)
        with mock.patch('internal.synchronization.synchronization.subprocess.check_output',
                         return_value=mount_output):
            return instance.resolve_mounts(root_dir)

    def test_bytes_output_does_not_raise(self):
        mounts = self._resolve_mounts_under_root('/dav-mount', self.SAMPLE_OUTPUT.encode('utf-8'))
        self.assertIn('/dav-mount/nfsserver', mounts)
        self.assertIn('/dav-mount/other', mounts)

    def test_str_output_does_not_raise(self):
        # Covers Python 2, where check_output() already returns str - must keep working unchanged.
        mounts = self._resolve_mounts_under_root('/dav-mount', self.SAMPLE_OUTPUT)
        self.assertIn('/dav-mount/nfsserver', mounts)
        self.assertIn('/dav-mount/other', mounts)

    def test_read_only_vs_read_write_mask(self):
        from internal.model.mask import Mask
        mounts = self._resolve_mounts_under_root('/dav-mount', self.SAMPLE_OUTPUT.encode('utf-8'))
        self.assertEqual(Mask.READ, mounts['/dav-mount/nfsserver'])
        self.assertEqual(Mask.READ | Mask.WRITE, mounts['/dav-mount/other'])

    def test_unrelated_mounts_are_ignored(self):
        mounts = self._resolve_mounts_under_root('/dav-mount', self.SAMPLE_OUTPUT.encode('utf-8'))
        self.assertNotIn('/', mounts)
        self.assertEqual(2, len(mounts))


if __name__ == '__main__':
    unittest.main()
