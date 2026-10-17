@echo off
setlocal
pushd "%~dp0"
set "COMPILER=javac"
set "RUNTIME=java"
if defined JAVA_HOME set "COMPILER=%JAVA_HOME%\bin\javac.exe"
if defined JAVA_HOME set "RUNTIME=%JAVA_HOME%\bin\java.exe"
if not exist out mkdir out
if /I "%~1"=="test" goto tests
"%COMPILER%" --release 11 -d out src\idle\*.java
if errorlevel 1 goto failed
"%RUNTIME%" -cp out idle.IdleGame
if errorlevel 1 goto failed
goto done
:tests
"%COMPILER%" --release 11 -d out src\idle\*.java tests\idle\*.java
if errorlevel 1 goto failed
"%RUNTIME%" -cp out idle.GameStateTest
if errorlevel 1 goto failed
goto done
:failed
echo Could not compile or run. Check that JAVA_HOME or PATH points to JDK 11 or newer.
popd
exit /b 1
:done
popd
exit /b 0
