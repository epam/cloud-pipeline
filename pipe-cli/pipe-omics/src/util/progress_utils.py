import shutil

from omics.transfer import OmicsTransferSubscriber


class ProgressBar:
    """
       Call in a loop to create terminal progress bar
       @params:
           iteration   - Required  : current iteration (Int)
           total       - Required  : total iterations (Int)
           prefix      - Optional  : prefix string (Str)
           suffix      - Optional  : suffix string (Str)
           decimals    - Optional  : positive number of decimals in percent complete (Int)
           length      - Optional  : character length of bar (Int)
           fill        - Optional  : bar fill character (Str)
           printEnd    - Optional  : end character (e.g. "\r", "\r\n") (Str)
    """
    def __init__(self, total, prefix='', suffix='', piped_stdout=False,
                 fill='█', print_end="\r", autosize=True, wight_rate=0.3):
        self.total = total
        self.progress = 0
        self.decimals = 1
        self.suffix = suffix
        self.prefix = prefix
        self.piped_stdout = piped_stdout
        self.fill = fill
        self.printEnd = print_end
        self.autosize = autosize
        self.wight_rate = wight_rate
        self.length = self._resize("0.00")

    def update(self, shift, prefix=None):
        self.progress += shift
        if not self.prefix and prefix:
            self.prefix = prefix
        self._print()

    def _resize(self, percent_label):
        styling = '%s |%s| %s%% %s' % (self.prefix, self.fill, percent_label, self.suffix)
        tcols, _ = shutil.get_terminal_size(fallback=(100, 1))
        length = int(self.wight_rate * tcols) - len(styling)
        if length < 30:
            length = 30
        return length

    def _print(self):
        percent = ("{0:." + str(self.decimals) + "f}").format(100 * (self.progress / float(self.total)))
        if self.autosize:
            self.length = self._resize(percent)
        filled_length = int(self.length * self.progress // self.total)
        bar = self.fill * filled_length + '-' * (self.length - filled_length)
        if self.piped_stdout:
            print(f'{self.prefix} |{bar}| {percent}% {self.suffix}')
        else:
            print(f'\r{self.prefix} |{bar}| {percent}% {self.suffix}', end=self.printEnd)
            # Print New Line on Complete
            if self.progress == self.total:
                print()


class ProgressBarSubscriber(OmicsTransferSubscriber):

    def __init__(self, size, file_name=None, piped_stdout=False):
        self.progress_bar = ProgressBar(size, prefix=file_name, piped_stdout=piped_stdout)

    def on_progress(self, future, bytes_transferred, **kwargs):
        self.progress_bar.update(
            bytes_transferred,
            "{}/{}".format(future.meta.call_args.file_set_id, future.meta.call_args.filename)
        )

    def on_done(self, future, **kwargs):
        print("File {} downloaded!".format(future.meta.call_args.fileobj))


class FinalEventSubscriber(OmicsTransferSubscriber):

    def on_done(self, future, **kwargs):
        print("Omics {} file {}: uploaded!".format(future.meta.call_args.file_type, future.meta.call_args.name))
