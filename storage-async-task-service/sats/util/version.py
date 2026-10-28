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

# The gradle build rewrites this line on every assembly, so the committed value is not what a built
# artefact carries.
__version__ = '0.1.0'


def pep440(version):
    """
    Rewrites the platform's version as one packaging accepts, leaving it readable.

    The root build appends a build number that carries the commit it came from, so an assembled
    artefact reports something like ``0.25.0.42.21e968f1``. That names the build, which is the point,
    but it is not a version packaging can parse: every component of the release segment has to be a
    number. Modern setuptools refuses such a version outright rather than warning, and the refusal
    surfaces as a failed ``pip install`` of this package - inside the image build, where the version
    is nowhere in sight.

    So the leading numbers stay the release, and whatever follows becomes the local segment PEP 440
    keeps for exactly this, after a ``+``. Nothing is dropped, and the result still names the build.

    Only the metadata is rewritten: the stamped version is what the module above keeps, and what the
    source distribution carries inside it, so the service goes on reporting the version the build
    gave it. A version that already carries a local segment is returned untouched, which makes this
    safe to apply twice.
    """
    if '+' in version:
        return version
    release = []
    local = []
    for part in version.split('.'):
        # `isascii` as well: `isdigit` is true of digits packaging does not accept, such as '²'.
        if not local and part.isascii() and part.isdigit():
            release.append(part)
        else:
            local.append(part)
    if not release:
        # Not a version at all. Handing it over unchanged leaves the complaint to packaging, which
        # words it better than a guess here would.
        return version
    if not local:
        return '.'.join(release)
    return '{}+{}'.format('.'.join(release), '.'.join(local))
