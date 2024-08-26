#  Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
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

    def __init__(self, cpu=0, gpu=0, mem=0, exc=0, owner=None):
        """
        Common compute resource.
        """
        self.cpu = cpu
        self.gpu = gpu
        self.mem = mem
        self.exc = exc
        self.owner = owner

    def add(self, other):
        return self.__class__(cpu=self.cpu + other.cpu,
                              gpu=self.gpu + other.gpu,
                              mem=self.mem + other.mem,
                              exc=self.exc + other.exc,
                              owner=self.owner or other.owner)

    def subtract(self, other):
        return (self.__class__(cpu=max(0, self.cpu - other.cpu),
                               gpu=max(0, self.gpu - other.gpu),
                               mem=max(0, self.mem - other.mem),
                               exc=max(0, self.exc - other.exc),
                               owner=self.owner or other.owner),
                other.__class__(cpu=max(0, other.cpu - self.cpu),
                                gpu=max(0, other.gpu - self.gpu),
                                mem=max(0, other.mem - self.mem),
                                exc=max(0, other.exc - self.exc),
                                owner=self.owner or other.owner))

    def sub(self, other):
        return self.subtract(other)[0]

    def mul(self, other):
        if isinstance(other, int):
            return self.__class__(cpu=self.cpu * other,
                                  gpu=self.gpu * other,
                                  mem=self.mem * other,
                                  exc=self.exc * other,
                                  owner=self.owner)
        else:
            raise ArithmeticError('Compute resource can be multiplied to integer values only')

    def gt(self, other):
        return self.cpu > other.cpu or self.gpu > other.gpu or self.mem > other.mem or self.exc > other.exc

    def eq(self, other):
        return self.__dict__ == other.__dict__

    def bool(self):
        return self.cpu + self.gpu + self.mem + self.exc > 0

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
        return ResourceSupply(cpu=instance.cpu, gpu=instance.gpu, mem=instance.mem, exc=1)


class CustomComputeResource:

    def __init__(self, values=None):
        self.values = values or {}

    def add(self, other):
        lvalues, rvalues = dict(self.values), dict(other.values)
        for key in other.values.keys():
            lvalue, rvalue = lvalues.get(key, 0), rvalues.get(key, 0)
            lvalues[key] = lvalue + rvalue
        return self.__class__(values=lvalues)

    def subtract(self, other):
        lvalues, rvalues = dict(self.values), dict(other.values)
        for key in self.values.keys():
            lvalue, rvalue = lvalues.get(key, 0), rvalues.get(key, 0)
            lvalues[key], rvalues[key] = max(0, lvalue - rvalue), max(0, rvalue - lvalue)
        return (self.__class__(values=lvalues),
                other.__class__(values=rvalues))

    def sub(self, other):
        return self.subtract(other)[0]

    def mul(self, other):
        if isinstance(other, int):
            lvalues = dict(self.values)
            for key in self.values.keys():
                lvalues[key] *= other
            return self.__class__(values=lvalues)
        else:
            raise ArithmeticError('Custom compute resource can be multiplied to integer values only')

    def gt(self, other):
        lvalues, rvalues = self.values, other.values
        return any(lvalues.get(key, 0) > rvalues.get(key, 0) for key in self.values.keys())

    def eq(self, other):
        return self.__dict__ == other.__dict__

    def bool(self):
        return sum(self.values.values()) > 0

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


class CustomResourceSupply(CustomComputeResource):
    pass


class CustomResourceDemand(CustomComputeResource):
    pass
