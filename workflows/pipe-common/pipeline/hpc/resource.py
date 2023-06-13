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

def _not(func):
    def _func(*args, **kwargs):
        return not func(*args, **kwargs)

    return _func


def _or(left_func, right_func):
    def _func(*args, **kwargs):
        return left_func(*args, **kwargs) or right_func(*args, **kwargs)

    return _func


def _and(left_func, right_func):
    def _func(*args, **kwargs):
        return left_func(*args, **kwargs) and right_func(*args, **kwargs)

    return _func


class ComputeResource:

    def __init__(self, cpu=0, gpu=0, mem=0, owner=None):
        """
        Common compute resource.
        """
        self.cpu = cpu
        self.gpu = gpu
        self.mem = mem
        self.owner = owner

    def add(self, other):
        return self.__class__(cpu=self.cpu + other.cpu,
                              gpu=self.gpu + other.gpu,
                              mem=self.mem + other.mem,
                              owner=self.owner or other.owner)

    def subtract(self, other):
        return (self.__class__(cpu=max(0, self.cpu - other.cpu),
                               gpu=max(0, self.gpu - other.gpu),
                               mem=max(0, self.mem - other.mem),
                               owner=self.owner or other.owner),
                other.__class__(cpu=max(0, other.cpu - self.cpu),
                                gpu=max(0, other.gpu - self.gpu),
                                mem=max(0, other.mem - self.mem),
                                owner=self.owner or other.owner))

    def sub(self, other):
        return self.subtract(other)[0]

    def mul(self, other):
        if isinstance(other, int):
            return self.__class__(cpu=self.cpu * other,
                                  gpu=self.gpu * other,
                                  mem=self.mem * other,
                                  owner=self.owner)
        else:
            raise ArithmeticError('Compute resource can be multiplied to integer values only')

    def gt(self, other):
        return self.cpu > other.cpu or self.gpu > other.gpu or self.mem > other.mem

    def eq(self, other):
        return self.__dict__ == other.__dict__

    def bool(self):
        return self.cpu + self.gpu + self.mem > 0

    def __repr__(self):
        return str(self.__dict__)

    __add__ = add
    __sub__ = sub
    __mul__ = mul
    __cmp__ = gt
    __eq__ = eq
    __ne__ = _not(__eq__)
    __lt__ = _and(_not(gt), _not(eq))
    __gt__ = gt
    __le__ = _or(__lt__, __eq__)
    __ge__ = _or(__gt__, __eq__)
    __bool__ = bool
    __nonzero__ = bool


class FractionalDemand(ComputeResource):
    """
    Fractional resource demand which can be fulfilled using multiple resource supplies.

    Example of a fractional demand is mpi grid engine job requirements.
    """
    pass


class IntegralDemand(ComputeResource):
    """
    Integral resource demand which can be fulfilled using only a single resource supply.

    Example of an integral demand is non mpi grid engine job requirements.
    """
    pass


class ResourceSupply(ComputeResource):
    """
    Resource supply which can be used to fulfill resource demands.
    """
    pass

    @classmethod
    def of(cls, instance):
        return ResourceSupply(cpu=instance.cpu, gpu=instance.gpu, mem=instance.mem)
