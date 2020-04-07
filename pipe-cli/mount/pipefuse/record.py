import io
import logging


def _merge_arguments(args, kwargs):
    args_string = _args_string(args)
    kwargs_string = _kwargs_string(kwargs)
    if args_string and kwargs_string:
        complete_args_string = args_string + ', ' + kwargs_string
    elif args_string:
        complete_args_string = args_string
    else:
        complete_args_string = kwargs_string
    return complete_args_string


def _args_string(args):
    return ', '.join(_trimmed(v) for v in args)


def _kwargs_string(kwargs):
    return ', '.join(str(k) + '=' + _trimmed(v) for k, v in kwargs.items())


def _trimmed(value):
    if isinstance(value, io.BytesIO):
        return 'BYTES'
    elif isinstance(value, (bytearray, bytes, str)):
        return 'BYTES#' + str(len(value))
    else:
        return str(value)


class RecordingFS:

    def __init__(self, inner):
        """
        Recording File System.

        It records any call to the inner file system.

        :param inner: Recording file system.
        """
        self._inner = inner
        self._tag = type(inner).__name__ + ' Recorder'

    def __getattr__(self, name):
        if hasattr(self._inner, name):
            attr = getattr(self._inner, name)
            if callable(attr):
                def _wrapped_attr(method_name, *args, **kwargs):
                    complete_args_string = _merge_arguments(args, kwargs)
                    if method_name in ['read', 'write', 'getxattr']:
                        logging.debug('[%s] %s (%s)' % (self._tag, method_name, complete_args_string))
                    else:
                        logging.info('[%s] %s (%s)' % (self._tag, method_name, complete_args_string))
                    return attr(method_name, *args, **kwargs)
                return _wrapped_attr
            else:
                return attr
        else:
            return getattr(self._inner, name)


class RecordingFileSystemClient:

    def __init__(self, inner):
        """
        Recording File System Client.

        It records any call to the inner file system client.

        :param inner: Recording file system client.
        """
        self._inner = inner
        self._tag = type(inner).__name__ + ' Recorder'

    def __getattr__(self, name):
        if hasattr(self._inner, name):
            attr = getattr(self._inner, name)
            if callable(attr):
                def _wrapped_attr(*args, **kwargs):
                    complete_args_string = _merge_arguments(args, kwargs)
                    logging.info('[%s] %s (%s)' % (self._tag, name, complete_args_string))
                    return attr(*args, **kwargs)
                return _wrapped_attr
            else:
                return attr
        else:
            return getattr(self._inner, name)
