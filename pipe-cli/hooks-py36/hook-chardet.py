from PyInstaller.utils.hooks import copy_metadata, collect_all

datas = copy_metadata('chardet')
datas, binaries, hiddenimports = collect_all('chardet')
