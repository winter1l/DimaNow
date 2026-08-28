@echo off
set "JAVA_HOME=%~dp0.tooling\jdk-17.0.20.1+1"
call "%~dp0gradlew.bat" %*
