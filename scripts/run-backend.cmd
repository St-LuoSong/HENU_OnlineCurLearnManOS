@echo off
setlocal

set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1"
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo JDK 21 was not found at "%JAVA_HOME%".
    echo Set JAVA_HOME to a valid JDK 21 path and run this script again.
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
set "PROJECT_ROOT=%~dp0.."

mvn -f "%PROJECT_ROOT%\server\pom.xml" -Dmaven.repo.local="%PROJECT_ROOT%\.m2\repository" -Dmaven.test.skip=true spring-boot:run

endlocal
