import threading

class SharedObject(object):

    def __init__(self, obj, lock=None):
        self.lock = lock or threading.RLock()
        self._obj = obj

    def __enter__(self):
        self.lock.acquire()
        return self._obj

    def __exit__(self, exc_type, exc_value, traceback):
        self.lock.release()

    def set(self, obj):
        with self:
            self._obj = obj
