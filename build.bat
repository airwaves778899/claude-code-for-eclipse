@echo off
setlocal EnableDelayedExpansion

echo ============================================================
echo  Claude Code for Eclipse - Build Script
echo ============================================================

:: ==============================================================
:: CONFIGURATION - Edit these variables as needed
:: ==============================================================
set "ECLIPSE_HOME=C:\eclipse\eclipse-jee-2024-06-R-win32-x86_64"
set "BUILD_DIR=%~dp0build"
set "PLUGIN_VERSION=1.0.0"
:: ==============================================================

set "PROJECT_DIR=%~dp0"
set "SRC_DIR=%PROJECT_DIR%src"
set "OUT_DIR=%BUILD_DIR%\classes"
set "JAR_OUT=%BUILD_DIR%\io.github.airwaves778899.claudecode_%PLUGIN_VERSION%.jar"
set "PLUGINS=%ECLIPSE_HOME%\plugins"

echo.
echo [1/5] Checking Eclipse at: %ECLIPSE_HOME%
if not exist "%PLUGINS%" (
    echo ERROR: plugins folder not found.
    goto :error
)
echo Eclipse OK.

echo.
echo [2/5] Finding Java compiler...
set "JAVAC_CMD=javac"
set "JDK_FOUND="

for /d %%D in ("%PLUGINS%\org.eclipse.justj.openjdk*") do (
    if not defined JDK_FOUND (
        if exist "%%D\jre\bin\javac.exe" (
            set "JAVAC_CMD=%%D\jre\bin\javac.exe"
            set "JDK_FOUND=1"
        )
    )
)

if not defined JDK_FOUND (
    if exist "%ECLIPSE_HOME%\jre\bin\javac.exe" (
        set "JAVAC_CMD=%ECLIPSE_HOME%\jre\bin\javac.exe"
        set "JDK_FOUND=1"
    )
)

echo Using: %JAVAC_CMD%
"%JAVAC_CMD%" -version 2>&1

echo.
echo [3/5] Collecting Eclipse JARs...

:: Use wildcard to include ALL jars in plugins folder at once (Java classpath wildcard)
set "CP=%PLUGINS%\*"
echo CP set to: %PLUGINS%\*
echo JARs OK.

echo.
echo [4/5] Compiling Java sources...
if exist "%OUT_DIR%" rd /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

set "SOURCES=%TEMP%\claude_sources.txt"
if exist "%SOURCES%" del "%SOURCES%"
for /r "%SRC_DIR%" %%F in (*.java) do echo %%F>> "%SOURCES%"

"%JAVAC_CMD%" -encoding UTF-8 --release 21 -cp "!CP!" -d "%OUT_DIR%" @"%SOURCES%"
if errorlevel 1 (
    echo ERROR: Compilation failed.
    goto :error
)
echo Compilation OK.

echo.
echo [5/5] Packaging JAR...
if exist "%PROJECT_DIR%icons"      xcopy /E /Q /Y "%PROJECT_DIR%icons"      "%OUT_DIR%\icons\" >nul 2>&1
if exist "%PROJECT_DIR%plugin.xml" copy  /Y        "%PROJECT_DIR%plugin.xml" "%OUT_DIR%\"       >nul 2>&1

jar cfm "%JAR_OUT%" "%PROJECT_DIR%META-INF\MANIFEST.MF" -C "%OUT_DIR%" .
if errorlevel 1 (
    echo ERROR: JAR packaging failed.
    goto :error
)

echo.
echo ============================================================
echo  BUILD SUCCESS
echo  Output: %JAR_OUT%
echo ============================================================
echo.
echo Dropins folder: %ECLIPSE_HOME%\dropins\
echo.
set /p COPY_NOW=Auto-copy to dropins now? (y/n):
if /i "!COPY_NOW!"=="y" (
    if not exist "%ECLIPSE_HOME%\dropins" mkdir "%ECLIPSE_HOME%\dropins"
    copy /Y "%JAR_OUT%" "%ECLIPSE_HOME%\dropins" >nul
    echo Copied! Please restart Eclipse (Help - Restart).
)
goto :end

:error
echo.
echo Build FAILED.
pause
exit /b 1

:end
pause
endlocal
