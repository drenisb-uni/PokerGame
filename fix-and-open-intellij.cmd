@echo off
setlocal

set "LOCAL_DIR=C:\Users\endri\AppData\Local\JetBrains\IntelliJIdea2025.3"
set "CONFIG_DIR=C:\Users\endri\AppData\Roaming\JetBrains\IntelliJIdea2025.3"
set "IDEA_EXE=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\bin\idea64.exe"
set "PROJECT_DIR=C:\Users\endri\IdeaProjects\PokerGame"

echo Cleaning stale IntelliJ startup files...

if exist "%LOCAL_DIR%\.port" (
  fsutil reparsepoint delete "%LOCAL_DIR%\.port" >nul 2>nul
  del /f /q "%LOCAL_DIR%\.port" >nul 2>nul
)

if exist "%CONFIG_DIR%\.lock" (
  del /f /q "%CONFIG_DIR%\.lock" >nul 2>nul
)

echo Opening IntelliJ IDEA...
start "" "%IDEA_EXE%" "%PROJECT_DIR%"

endlocal
