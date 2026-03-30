@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.

@if "%DEBUG%"=="" @echo off
@setlocal

set ERROR_CODE=0

:init
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

if "%JAVA_HOME%"=="" (
  echo Error: JAVA_HOME is not set and no 'java' command could be found in your PATH.
  exit /b 1
)

set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if not exist "%JAVA_EXE%" (
  echo Error: JAVA_HOME is not pointing to a valid Java home folder at "%JAVA_HOME%"
  exit /b 1
)

:execute
"%JAVA_EXE%" %MAVEN_OPTS% ^
  "-Dmaven.home=%APP_HOME%" ^
  "-Dmaven.multiModuleProjectDirectory=%APP_HOME%" ^
  "-classpath=%APP_HOME%\.mvn\wrapper\maven-wrapper.jar" ^
  "org.apache.maven.wrapper.MavenWrapperMain" %*

if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable ERRORLEVEL for exit code chaining.
cmd /C exit /B %ERROR_CODE%

:mainEnd
if "%ERRORLEVEL%"=="0" goto end

:end
@endlocal & exit /B %ERRORLEVEL%
