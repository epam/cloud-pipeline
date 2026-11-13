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
Tests of rewriting the stamped version as one packaging accepts.

Why this is worth a test of its own: the version is stamped by the gradle build and read by
`setup.py`, so nothing inside the service ever looks at it, and the only place it is validated is
`pip install` during the image build - where the failure names setuptools internals and not the
version. The image build is also the slowest thing in the pipeline to find it in.
"""

import pytest
from packaging.version import InvalidVersion, Version

from sats.util.version import __version__, pep440

# What the pipeline stamps: a build number and the commit it was built from
# (deploy/github_actions/gha_pack_dist.sh), which is where this started.
RELEASE_BUILD = '0.25.0.42.21e968f10b23f8a793dd88e01ad18abb3da6749f'
# What a build by hand stamps, where the build number is not a number at all.
NAMED_BUILD = '0.25.0.local1.21e968f10b23f8a793dd88e01ad18abb3da6749f'


@pytest.mark.parametrize('stamped,expected', [
    (RELEASE_BUILD, '0.25.0.42+21e968f10b23f8a793dd88e01ad18abb3da6749f'),
    (NAMED_BUILD, '0.25.0+local1.21e968f10b23f8a793dd88e01ad18abb3da6749f'),
    # No build number at all, which is what a bare `./gradlew` run stamps.
    ('0.25.0', '0.25.0'),
    # A build number that is only digits needs no moving: a fourth component is a release component.
    ('0.25.0.42', '0.25.0.42'),
    # Applying it twice changes nothing. Nothing does today - the source distribution carries the
    # stamped version inside it, not this output - but the property is what makes that safe to stop
    # relying on.
    ('0.25.0.42+21e968f1', '0.25.0.42+21e968f1'),
])
def test_rewrites_the_stamped_version(stamped, expected):
    assert pep440(stamped) == expected


@pytest.mark.parametrize('stamped', [RELEASE_BUILD, NAMED_BUILD, '0.25.0', '0.25.0.42', __version__])
def test_result_is_a_version_packaging_can_parse(stamped):
    # The assertion that matters, made by the library setuptools itself validates with: the shape of
    # the string is incidental, being accepted is the point.
    assert Version(pep440(stamped))


def test_the_committed_version_is_left_alone():
    # Read by `pip install -e .`, which is how a developer sets the service up.
    assert pep440(__version__) == __version__


def test_keeps_the_build_the_version_names():
    # Nothing is dropped on the way: the commit is still there to be read off a running service.
    assert '21e968f10b23f8a793dd88e01ad18abb3da6749f' in pep440(RELEASE_BUILD)
    assert 'local1' in pep440(NAMED_BUILD)


def test_leaves_something_that_is_not_a_version_to_packaging():
    # Guessing at a repair here would hide the mistake; packaging words the complaint better.
    assert pep440('not-a-version') == 'not-a-version'
    with pytest.raises(InvalidVersion):
        Version(pep440('not-a-version'))
