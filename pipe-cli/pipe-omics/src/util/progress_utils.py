import math
import shutil
import sys

from omics.transfer import OmicsTransferSubscriber


class ProgressBar:
    OKGREEN = '\033[92m'
    ENDC = '\033[0m'

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
    def __init__(self, total, prefix='', piped_stdout=False,
                 fill='#', print_end="\r", autosize=True, wight_rate=0.3):
        self.total = total
        self.progress = 0
        self.decimals = 1

        self.prefix = prefix
        self.piped_stdout = piped_stdout
        self.fill = fill
        self.printEnd = print_end
        self.autosize = autosize
        self.wight_rate = wight_rate
        self.length = self._resize("0.00", "0/0 B")

    def update(self, shift, prefix=None):
        self.progress += shift
        if not self.prefix and prefix:
            self.prefix = prefix
        self._print()

    def _resize(self, percent_label, human_readable_size):
        styling = '  [%s] %s%% %s %s' % (self.fill, percent_label, self.prefix, human_readable_size)
        tcols, _ = shutil.get_terminal_size(fallback=(100, 1))
        length = int(self.wight_rate * tcols) - len(styling)
        if length < 30:
            length = 30
        return length

    def _print(self):
        percent = ("{0:." + str(self.decimals) + "f}").format(100 * (self.progress / float(self.total)))
        human_readable_size = self._get_human_readable_size_string(self.progress, self.total)
        if self.autosize:
            self.length = self._resize(percent, human_readable_size)
        filled_length = int(self.length * self.progress // self.total)
        bar = self.fill * filled_length + '-' * (self.length - filled_length)
        if self.piped_stdout:
            if self.progress == self.total:
                sys.stdout.write(f'  {ProgressBar.OKGREEN}[{bar}]  {percent}%  {self.prefix}  {human_readable_size}{ProgressBar.ENDC}\n')
            else:
                sys.stdout.write(f'  [{bar}]  {percent}%  {self.prefix}  {human_readable_size}\n')
        else:
            # Print New Line on Complete
            if self.progress == self.total:
                sys.stdout.write(f'\r  {ProgressBar.OKGREEN}[{bar}]  {percent}%  {self.prefix}  {human_readable_size}{ProgressBar.ENDC}\n')
            else:
                sys.stdout.write(f'\r  [{bar}]  {percent}%  {self.prefix}  {human_readable_size}')
        # Doing this because of this issue: https://github.com/pyinstaller/pyinstaller/issues/4908
        # in some cases PyInstaller throws some strange output to the console when pipe executes pipe-omics and
        # reporting its progress back to the pipe stdout.
        # This flash fixes the problem.
        sys.stdout.flush()
        sys.stderr.flush()

    def _get_human_readable_size_string(self, size_bytes, total_size_bytes):
        if total_size_bytes == 0:
            return "0B"
        size_name = ("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
        i = int(math.floor(math.log(total_size_bytes, 1024)))
        p = math.pow(1024, i)
        s = round(size_bytes / p, 2)
        t = round(total_size_bytes / p, 2)
        return "%s/%s %s" % (s, t, size_name[i])


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
