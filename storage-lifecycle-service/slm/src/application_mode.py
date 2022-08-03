import schedule


class ApplicationModeRunner:

    def run(self):
        pass

    @staticmethod
    def get_application_runner(slm, mode="single"):
        if mode == "daemon":
            return DaemonApplicationModeRunner(slm)
        else:
            return SingleApplicationModeRunner(slm)


class SingleApplicationModeRunner(ApplicationModeRunner):

    def __init__(self, storage_lifecycle_manager):
        self.storage_lifecycle_manager = storage_lifecycle_manager

    def run(self):
        self.storage_lifecycle_manager.sync()


class DaemonApplicationModeRunner(ApplicationModeRunner):

    def __init__(self, storage_lifecycle_manager):
        self.storage_lifecycle_manager = storage_lifecycle_manager

    def run(self):
        schedule.every().day.at('00:01').do(self.storage_lifecycle_manager.sync)

