@echo off
rem Runs Maven with the JDK and Maven bundled with IntelliJ IDEA on this machine.
rem Usage:  build.bat test          (run all tests)
rem         build.bat package       (build target\sevasync.jar)
set "JAVA_HOME=C:\Users\Windows 10\.jdks\temurin-17.0.20.1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" %*
