from src.utilities.extension.omics import OmicsCopyFileHandler, OmicsListFilesHandler


class ExtensionHandlerRegistry:

    HANDLERS = [
        OmicsCopyFileHandler(),
        OmicsListFilesHandler()
    ]

    @classmethod
    def accept(cls, command_group, command, arguments):
        cls.cleanup_arguments(arguments, ['cls', 'self'])
        for handler in cls.HANDLERS:
            if handler.accept(command_group, command, arguments):
                return True
        return False

    @classmethod
    def cleanup_arguments(cls, arguments, keys):
        for key in keys:
            if key in arguments:
                del arguments[key]
