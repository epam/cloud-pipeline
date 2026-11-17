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
The one path guard, in `util` because two different layers need the same answer.

A reader of a share uses it on the paths a task names, a writer on the paths an archive names, and
an extraction on the paths it is about to create locally. One implementation, so that a fix reaches
all three.
"""

from __future__ import annotations

from pathlib import Path


def is_inside(candidate: Path, root: Path) -> bool:
    """
    Whether a path stays within a root once `..` and symlinks are resolved.

    **Resolve first, compare after.** `is_relative_to` compares text, so
    `/mnt/share/../../etc/cron.d` is "inside" `/mnt/share` as a string and is `/etc/cron.d` on disk.
    A symlink already in the root is the same problem by another route, and `resolve` follows those
    too.

    `strict=False` so a path that does not exist yet - which is every path an extraction is about to
    write - gets an answer rather than an exception.
    """
    return candidate.resolve(strict=False).is_relative_to(root.resolve(strict=False))
