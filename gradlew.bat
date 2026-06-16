@rem ╔══════════════════════════════════════════════════════════════════════════╗
@rem ║  YACR Gradle Wrapper Script for Windows                                  ║
@rem ║  Developer : MNM YOUNUS                                                  ║
@rem ╚══════════════════════════════════════════════════════════════════════════╝
@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
set JAVA_EXE=java.exe

%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%"=="0" goto execute
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
goto fail

:execute
set CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar

%JAVA_EXE% %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% neq 0 goto fail
goto end

:fail
exit /b %EXIT_CODE%
:end
if "%ERRORLEVEL%"=="0" endlocal
