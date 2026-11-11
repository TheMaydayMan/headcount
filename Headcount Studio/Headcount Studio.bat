@echo off
rem Windows: double-click to open Headcount Studio in your browser. It stops by itself a couple of minutes
rem after you close its browser tab (or use the power button in Studio).
cd /d "%~dp0.."
where pythonw >nul 2>nul && (start "" pythonw "Headcount\tools\studio\server.py" --exit-when-idle & exit /b)
where py >nul 2>nul && (start "" py -3 "Headcount\tools\studio\server.py" --exit-when-idle & exit /b)
where python >nul 2>nul && (python "Headcount\tools\studio\server.py" --exit-when-idle & exit /b)
echo Headcount Studio needs Python 3: https://www.python.org/downloads/
pause
