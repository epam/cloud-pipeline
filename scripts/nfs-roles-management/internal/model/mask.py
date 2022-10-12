# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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


class AbstractMask:

    @classmethod
    def is_set(cls, mask, permission_mask):
        return mask & permission_mask == permission_mask

    @classmethod
    def is_not_set(cls, mask, permission_mask):
        return mask & permission_mask != permission_mask

    @classmethod
    def trim(cls, mask, trimming_mask):
        return mask & trimming_mask

    @classmethod
    def is_equal(cls, left, right, trimming_mask=None):
        if trimming_mask:
            left = Mask.trim(left, trimming_mask=trimming_mask)
            right = Mask.trim(right, trimming_mask=trimming_mask)
        return left == right

    @classmethod
    def is_not_equal(cls, left, right, trimming_mask=None):
        return not AbstractMask.is_equal(left, right, trimming_mask=trimming_mask)


class Mask(AbstractMask):
    """
    Mask of read|write|execute permissions.
    """

    READ = 0b1
    WRITE = 0b10
    EXECUTE = 0b100
    NOTHING = 0
    ALL = READ | WRITE | EXECUTE
    READ_PERMISSION = 'READ'
    WRITE_PERMISSION = 'WRITE'
    EXECUTE_PERMISSION = 'EXECUTE'

    @classmethod
    def get_permissions(cls, mask):
        permissions = []
        if Mask.is_set(mask, Mask.READ):
            permissions.append(Mask.READ_PERMISSION)
        if Mask.is_set(mask, Mask.WRITE):
            permissions.append(Mask.WRITE_PERMISSION)
        if Mask.is_set(mask, Mask.EXECUTE):
            permissions.append(Mask.EXECUTE_PERMISSION)
        return permissions

    @classmethod
    def as_string(cls, mask):
        return '|'.join(cls.get_permissions(mask))

    @classmethod
    def from_full(cls, full_mask):
        if not full_mask:
            return Mask.NOTHING
        full_mask = str(full_mask)
        if not full_mask.isdigit():
            return Mask.NOTHING
        full_mask = int(full_mask)
        mask = Mask.NOTHING
        if full_mask & FullMask.READ == FullMask.READ:
            mask |= Mask.READ
        if full_mask & FullMask.WRITE == FullMask.WRITE:
            mask |= Mask.WRITE
        return mask


class FullMask(AbstractMask):
    """
    Mask of read|noread|write|nowrite|execute|noexecute permissions.
    """

    READ = 0b1
    NO_READ = 0b10
    WRITE = 0b100
    NO_WRITE = 0b1000
    EXECUTE = 0b10000
    NO_EXECUTE = 0b100000
    NOTHING = 0
    ALL = READ | WRITE | EXECUTE
    READ_PERMISSION = 'READ'
    NO_READ_PERMISSION = 'NO_READ'
    WRITE_PERMISSION = 'WRITE'
    NO_WRITE_PERMISSION = 'NO_WRITE'
    EXECUTE_PERMISSION = 'EXECUTE'
    NO_EXECUTE_PERMISSION = 'NO_EXECUTE'

    @classmethod
    def get_permissions(cls, mask):
        permissions = []
        if FullMask.is_set(mask, FullMask.READ):
            permissions.append(FullMask.READ_PERMISSION)
        if FullMask.is_set(mask, FullMask.NO_READ):
            permissions.append(FullMask.NO_READ_PERMISSION)
        if FullMask.is_set(mask, FullMask.WRITE):
            permissions.append(FullMask.WRITE_PERMISSION)
        if FullMask.is_set(mask, FullMask.NO_WRITE):
            permissions.append(FullMask.NO_WRITE_PERMISSION)
        if FullMask.is_set(mask, FullMask.EXECUTE):
            permissions.append(FullMask.EXECUTE_PERMISSION)
        if FullMask.is_set(mask, FullMask.NO_EXECUTE):
            permissions.append(FullMask.NO_EXECUTE_PERMISSION)
        return permissions

    @classmethod
    def as_string(cls, mask):
        return '|'.join(cls.get_permissions(mask))
