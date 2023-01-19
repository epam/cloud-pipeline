@echo off

set all_args=%*
set first_arg=%1

if "%first_arg%" == "update" (
	for /f "delims=" %%i in ('pipe.exe %all_args%') do set bat_file_path=%%i
	bat_file_path
) else (
	pipe.exe %all_args%
)
