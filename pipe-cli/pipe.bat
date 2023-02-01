:: Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
::
:: Licensed under the Apache License, Version 2.0 (the "License");
:: you may not use this file except in compliance with the License.
:: You may obtain a copy of the License at
::
::    http://www.apache.org/licenses/LICENSE-2.0
::
:: Unless required by applicable law or agreed to in writing, software
:: distributed under the License is distributed on an "AS IS" BASIS,
:: WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
:: See the License for the specific language governing permissions and
:: limitations under the License.

@echo off
SETLOCAL

set all_args=%*
set first_arg=%1
set "sys_executable=%~dp0"
set update_bat="%sys_executable%pipe-cli-update.bat"
set pipe_exe="%sys_executable%pipe-cli.exe"

if "%first_arg%" == "update" (
    set "CP_CLI_UPDATE_WRAPPER=true"
	%pipe_exe% %all_args%
    if exist %update_bat% (
	    %update_bat%
	)
) else (
	%pipe_exe% %all_args%
)
