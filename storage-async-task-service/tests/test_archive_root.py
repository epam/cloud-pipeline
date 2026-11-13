# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

"""
Tests of how an archive's entries are named: the prefix the selection has in common above the items
it names, and one file's name once that prefix is off it.

Two pure functions, so their cases live here rather than being driven through a worker; that the
worker actually packs under them is `test_archive_preparation_worker.py`.
"""

import pytest

from sats.worker.archive_preparation_worker import _UNUSABLE_NAMES, _archive_name, _archive_root, \
    _is_under


@pytest.mark.parametrize('paths,expected', [
    # One folder, picked deep in a tree: everything above it goes, and it is unpacked under its own
    # name. The case this exists for.
    (('sub1/sub2/folder/',), 'sub1/sub2/'),
    # One file.
    (('sub1/sub2/notes.txt',), 'sub1/sub2/'),
    # Two items of one folder - which is every selection the GUI can make, since it clears the
    # selection when you navigate. Both keep their own name and nothing above it.
    (('sub1/sub2/folder/', 'sub1/sub2/folder2/'), 'sub1/sub2/'),
    (('sub1/sub2/folder/', 'sub1/sub2/notes.txt'), 'sub1/sub2/'),
    # Items in different folders: the archive keeps just enough of the tree to tell them apart, so
    # `sub2/folder` and `sub3/folder2` rather than `folder` and `folder2`.
    (('sub1/sub2/folder/', 'sub1/sub3/folder2/'), 'sub1/'),
    # The same name in two places. Cutting each item back to its own name would give both of them
    # `folder`, and their files would overwrite each other.
    (('sub1/sub2/folder/', 'sub1/sub3/folder/'), 'sub1/'),
    # Nothing in common, so nothing is stripped.
    (('one/data/', 'two/data/'), ''),
    # An item at the storage root has no folder above it, which leaves the whole selection with none.
    (('data/',), ''),
    (('notes.txt',), ''),
    (('sub1/sub2/folder/', 'notes.txt'), ''),
    # A folder is not a prefix of another folder's name: `sub2` and `sub20` share four characters and
    # no folder, which is why this is compared folder by folder.
    (('sub1/sub2/folder/', 'sub1/sub20/folder/'), 'sub1/'),
    # One item inside another, which the GUI cannot produce and a task created through the REST API
    # can. The inner one has the deeper parent, so the outer one's is what they share.
    (('sub1/sub2/', 'sub1/sub2/folder/'), 'sub1/'),
    # A leading slash is not part of a storage-relative path and must not count as a folder of its
    # own: with one, the first case here would strip nothing.
    (('/sub1/sub2/folder/',), 'sub1/sub2/'),
    (('/sub1/sub2/folder/', '/sub1/sub3/folder2/'), 'sub1/'),
    # Spelling that only a task created through the REST API can carry. The folder a path names is
    # what counts, not how it is written, so these answer the same as their plain forms above.
    (('sub1//sub2/folder/',), 'sub1/sub2/'),
    (('sub1/./sub2/folder/',), 'sub1/sub2/'),
    (('sub1/sub2/other/../folder/',), 'sub1/sub2/'),
    # `..` as the item itself: the path names `sub1`, so the selection is that folder and nothing
    # above it is stripped.
    (('sub1/sub2/..',), ''),
    (('sub1/sub2/sub3/..',), 'sub1/'),
    (('sub1/sub2/folder/', 'sub1/sub2/../sub2/folder2/'), 'sub1/sub2/'),
    # A task with no paths at all. It fails before anything is packed, and the answer still has to be
    # a prefix rather than an exception.
    ((), ''),
])
def test_finds_what_the_selection_has_in_common_above_its_items(paths, expected):
    assert _archive_root(paths) == expected


@pytest.mark.parametrize('paths,path,expected', [
    # What the worker does with the prefix, end to end: the storage path minus it.
    (('R/x86_64-pc-linux-gnu-library/ggplot2/',),
     'R/x86_64-pc-linux-gnu-library/ggplot2/DESCRIPTION', 'ggplot2/DESCRIPTION'),
    (('R/x86_64-pc-linux-gnu-library/ggplot2/',),
     'R/x86_64-pc-linux-gnu-library/ggplot2/R/plot.R', 'ggplot2/R/plot.R'),
    (('sub1/sub2/notes.txt',), 'sub1/sub2/notes.txt', 'notes.txt'),
    (('sub1/sub2/folder/', 'sub1/sub3/folder2/'), 'sub1/sub2/folder/a.txt', 'sub2/folder/a.txt'),
    (('data/',), 'data/nested/b.txt', 'data/nested/b.txt'),
    # A listing that spells the same folder another way - `NfsSource` keeps a `..` of the selected
    # path in the paths it reports under it. The name is what the file is, not how it was written.
    (('sub1/sub2/..',), 'sub1/sub2/../other/x.txt', 'sub1/other/x.txt'),
    (('sub1/sub2/folder/', 'sub1/sub2/../sub2/folder2/'), 'sub1/sub2/../sub2/folder2/x.txt',
     'folder2/x.txt'),
    (('sub1//sub2/folder/',), 'sub1/sub2/folder/a.txt', 'folder/a.txt'),
    # A leading slash on the file's own path is not a folder either, and an entry name may not be
    # absolute: `zipfile` would store it, and an extractor may then write to it.
    (('sub1/sub2/folder/',), '/sub1/sub2/folder/a.txt', 'folder/a.txt'),
])
def test_names_a_file_relative_to_that_prefix(paths, path, expected):
    assert _archive_name(path, _archive_root(paths)) == expected


@pytest.mark.parametrize('root,path', [
    # An S3 key may hold `..` literally, and such a key is listed under the storage's own prefix,
    # so this needs no REST-built task at all.
    ('sub1/sub2/', 'sub1/sub2/../../../etc/passwd'),
    ('', '../../etc/passwd'),
    ('sub1/', '../sub1/x.txt'),
    ('', '/etc/passwd'),
])
def test_never_names_a_file_outside_the_archive(root, path):
    # An entry called `../x` is written outside the folder the user unpacks into by every extractor
    # that does not sanitise its input. `zipfile` sanitises a name of its own accord *except* when
    # it already starts with `..`, which is exactly what taking a prefix off a path can leave.
    name = _archive_name(path, root)

    assert not name.startswith('../')
    assert not name.startswith('/')


@pytest.mark.parametrize('root,path,expected', [
    # Normalising can leave a path with no name in it at all. Only a literal S3 key of this spelling
    # produces one, and the worker refuses to pack it rather than writing an entry called `.` or
    # `..`: `ZipFile.extractall` raises on such an entry and abandons the rest of the archive.
    ('', 'a/..', '.'),
    ('', 'a/../..', '..'),
    ('', '.', '.'),
    # The path names the storage root, which is not under the folder the selection has in common,
    # so nothing is stripped off it - and `.` is all it was in the first place.
    ('sub1/', 'sub1/..', '.'),
])
def test_answers_an_unusable_name_where_normalising_leaves_none(root, path, expected):
    name = _archive_name(path, root)

    assert name == expected
    # The caller checks exactly this, so the two cannot drift apart.
    assert name in _UNUSABLE_NAMES


def test_packs_a_file_the_prefix_does_not_cover_under_its_whole_path():
    # The two paths disagree about their own spelling, which neither source can do today. Stripping
    # nothing keeps the file in the archive and whole, rather than naming it by a half-match.
    assert _archive_name('other/place/a.txt', 'sub1/sub2/') == 'other/place/a.txt'


@pytest.mark.parametrize('path,root,expected', [
    ('sub1/sub2/folder/a.txt', 'sub1/sub2/', True),
    # Spelt another way, and still the same folder.
    ('sub1//sub2/folder/a.txt', 'sub1/sub2/', True),
    ('/sub1/sub2/folder/a.txt', 'sub1/sub2/', True),
    ('other/a.txt', 'sub1/sub2/', False),
    # A folder is not half of another folder's name.
    ('sub1/sub20/a.txt', 'sub1/sub2/', False),
    # Nothing is stripped, so every path is under it.
    ('other/a.txt', '', True),
])
def test_tells_whether_a_path_is_under_the_common_folder(path, root, expected):
    assert _is_under(path, root) is expected
