@echo off
setlocal EnableDelayedExpansion

echo ================================================
echo  Student Planner - Windows Build Script
echo ================================================
echo.

:: Check mvn
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] mvn not found. Please install Maven and add to PATH.
    pause
    exit /b 1
)

:: Check jpackage
jpackage --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] jpackage not found. Please use JDK 14+ and add to PATH.
    pause
    exit /b 1
)

:: Step 1: Maven build
echo [1/3] Maven clean package...
call mvn clean package -q
if %errorlevel% neq 0 (
    echo [ERROR] Maven build failed.
    pause
    exit /b 1
)
echo       Done: target\student-planner.jar
echo.

:: Step 2: Prepare input folder
echo [2/3] Preparing resources...
if not exist "installer-input" mkdir installer-input
copy /Y "target\student-planner.jar" "installer-input\" >nul
if exist "data" (
    xcopy /E /I /Y "data" "installer-input\data\" >nul
) else (
    mkdir installer-input\data
)
echo       Done
echo.

:: Step 3: jpackage
echo [3/3] Creating installer with jpackage...
jpackage --type exe --name "StudentPlanner" --app-version "1.0.0" --vendor "StudentPlanner" --input installer-input --main-jar student-planner.jar --main-class app.Main --dest installer-output --win-shortcut --win-menu --win-dir-chooser --java-options "-Dfile.encoding=UTF-8"
if %errorlevel% neq 0 (
    echo [ERROR] jpackage failed.
    pause
    exit /b 1
)

echo.
echo ================================================
echo  Build complete! Check installer-output folder.
echo ================================================
pause
