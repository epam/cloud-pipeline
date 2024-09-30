from PyInstaller.utils.hooks import copy_metadata, collect_all

datas = copy_metadata('urllib3')
datas, binaries, hiddenimports = collect_all('urllib3')
