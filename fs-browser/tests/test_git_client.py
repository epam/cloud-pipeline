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

# Covers the versioned storage operations of the FS Browser (GitClient) with the pygit2 version of requirements.txt,
# against a local bare repository: clone, status, diff, staging, commit, push, pull, revert and checkout.
# Run with `python3 -m unittest discover -s tests` from inside fs-browser, with the packages of requirements.txt.

import os
import shutil
import sys
import tempfile
import unittest

import pygit2

FS_BROWSER_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, FS_BROWSER_DIR)

from fsbrowser.src.git.git_client import GitClient  # noqa: E402

AUTHOR = pygit2.Signature('Seed', 'seed@example.com')


class _Logger(object):

    def __init__(self):
        self.messages = []

    def log(self, message):
        self.messages.append(message)


def _write(path, content):
    with open(path, 'w') as f:
        f.write(content)


def _read(path):
    with open(path) as f:
        return f.read()


class GitClientTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.remote = os.path.join(self.tmp, 'remote.git')
        pygit2.init_repository(self.remote, bare=True)
        self.first_commit = self._push_from_seed({'README.md': 'line 1\nline 2\n', 'data.txt': 'data\n'})
        self.client = GitClient('token', 'user', 'user@example.com', _Logger())

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def _push_from_seed(self, files, name='seed'):
        seed_path = os.path.join(self.tmp, name)
        if os.path.isdir(seed_path):
            seed = pygit2.Repository(seed_path)
            parents = [seed.head.target]
        else:
            seed = pygit2.init_repository(seed_path, initial_head='master')
            seed.remotes.create('origin', self.remote)
            parents = []
        for file_name, content in files.items():
            _write(os.path.join(seed_path, file_name), content)
            seed.index.add(file_name)
        seed.index.write()
        commit = seed.create_commit('refs/heads/master', AUTHOR, AUTHOR, 'seed commit', seed.index.write_tree(),
                                    parents)
        seed.remotes['origin'].push(['refs/heads/master:refs/heads/master'])
        return str(commit)

    def _clone(self, name='clone'):
        return self.client.clone(os.path.join(self.tmp, name), self.remote)

    def _states(self, repo_path):
        return dict((git_file.path, git_file.state) for git_file in self.client.status(repo_path))

    def _remote_head(self):
        return str(pygit2.Repository(self.remote).lookup_reference('refs/heads/master').target)

    def test_clones_the_latest_revision(self):
        repo_path = self._clone()
        self.assertEqual({'detached': False, 'revision': self.first_commit}, self.client.get_repo(repo_path))
        self.assertEqual('data\n', _read(os.path.join(repo_path, 'data.txt')))

    def test_status_and_diff_of_a_modified_and_a_new_file(self):
        repo_path = self._clone()
        _write(os.path.join(repo_path, 'README.md'), 'line 1\nline 2 changed\n')
        _write(os.path.join(repo_path, 'new.txt'), 'new\n')
        self.assertEqual({'README.md': 'modified', 'new.txt': 'created'}, self._states(repo_path))

        modified = self.client.diff(repo_path, 'README.md', False, context_lines=3).to_json()
        self.assertEqual(('README.md', 'README.md', False), (modified['new_name'], modified['old_name'],
                                                             modified['binary']))
        self.assertEqual((1, 1), (modified['insertions'], modified['deletions']))
        self.assertEqual(['line 1\n', 'line 2\n', 'line 2 changed\n'], [line['content'] for line in modified['lines']])
        self.assertEqual([' ', '-', '+'], [line['origin'] for line in modified['lines']])

        created = self.client.diff(repo_path, 'new.txt', True, context_lines=3).to_json()
        self.assertEqual(('new.txt', None), (created['new_name'], created['old_name']))
        self.assertIn('+new', created['raw_output'])

        statuses = dict((git_file.path, git_file) for git_file in self.client.status(repo_path))
        status_diff = self.client.diff_status(repo_path, statuses['README.md'])
        self.assertEqual((False, len('line 1\nline 2 changed\n'), len('line 1\nline 2\n')),
                         (status_diff.binary, status_diff.new_size, status_diff.old_size))

    def test_commits_the_selected_files_and_pushes(self):
        repo_path = self._clone()
        _write(os.path.join(repo_path, 'README.md'), 'line 1\nline 2 changed\n')
        _write(os.path.join(repo_path, 'data.txt'), 'data changed\n')
        # Both are staged first, then only README.md is selected: data.txt goes back out of the index
        self.client.prepare_index(repo_path, None)
        self.client.prepare_index(repo_path, ['README.md'])
        commit = self.client.commit(repo_path, 'change the readme')
        self.assertEqual((1, 0), self.client.ahead_behind(repo_path))
        self.assertEqual({'data.txt': 'modified'}, self._states(repo_path))

        self.client.push(repo_path)
        self.assertEqual(str(commit), self._remote_head())
        self.assertEqual((0, 0), self.client.ahead_behind(repo_path))
        self.assertEqual(str(commit), self.client.get_head_id(repo_path))
        self.assertEqual(str(commit), self.client.get_last_pushed_commit_id(repo_path))

    def test_pulls_a_newer_revision(self):
        repo_path = self._clone()
        newer_commit = self._push_from_seed({'data.txt': 'newer data\n'})
        self.assertEqual([], self.client.pull(repo_path))
        self.assertEqual(newer_commit, self.client.get_repo(repo_path)['revision'])
        self.assertEqual('newer data\n', _read(os.path.join(repo_path, 'data.txt')))

    def test_reverts_the_local_changes(self):
        repo_path = self._clone()
        _write(os.path.join(repo_path, 'data.txt'), 'data changed\n')
        self.client.revert(repo_path)
        self.assertEqual({}, self._states(repo_path))
        self.assertEqual('data\n', _read(os.path.join(repo_path, 'data.txt')))

    def test_checks_out_an_older_revision(self):
        newer_commit = self._push_from_seed({'data.txt': 'newer data\n'})
        repo_path = self._clone()
        self.assertEqual(newer_commit, self.client.get_repo(repo_path)['revision'])
        self.client.checkout(repo_path, self.first_commit)
        self.assertEqual({'detached': True, 'revision': self.first_commit}, self.client.get_repo(repo_path))
        self.assertEqual('data\n', _read(os.path.join(repo_path, 'data.txt')))


if __name__ == '__main__':
    unittest.main()
