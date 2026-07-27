@echo off
chcp 949 > nul
cd /d "%~dp0"

set LOG=build_log.txt
echo DBLogAnalyzer build log - %DATE% %TIME% > "%LOG%"
echo Working folder: %CD% >> "%LOG%"

echo [1/3] Python 확인...
echo [1/3] Python check >> "%LOG%"
python --version >> "%LOG%" 2>&1
if errorlevel 1 (
  echo Python이 설치되어 있지 않거나 PATH에 없습니다.
  echo https://www.python.org/downloads/ 에서 설치할 때 "Add python.exe to PATH"를 반드시 체크하세요.
  echo Python not found or not on PATH. >> "%LOG%"
  goto :show_log_and_exit
)

echo [2/3] 필요한 패키지 설치 중...
echo [2/3] Installing packages >> "%LOG%"
if exist wheels (
  echo   wheels 폴더 발견: %CD%\wheels
  echo   found wheels folder: %CD%\wheels >> "%LOG%"
  echo   동봉된 wheels 폴더에서 오프라인 설치를 시도합니다 (인터넷 접속 안 함)...
  python -m pip install --no-index --find-links wheels -r requirements.txt pyinstaller >> "%LOG%" 2>&1
) else (
  echo   wheels 폴더를 찾지 못했습니다: %CD%\wheels
  echo   wheels folder not found at: %CD%\wheels >> "%LOG%"
  echo   wheels 폴더가 없어 인터넷(pip, pypi.org)으로 설치를 시도합니다...
  python -m pip install -r requirements.txt pyinstaller >> "%LOG%" 2>&1
)
if errorlevel 1 (
  echo.
  echo 패키지 설치에 실패했습니다. 아래 로그와 build_log.txt를 확인해 저에게 보내주세요.
  goto :show_log_and_exit
)

echo [3/3] exe 빌드 중...
echo [3/3] Building exe >> "%LOG%"
python -m PyInstaller --onefile --windowed --name DBLogAnalyzer app.py >> "%LOG%" 2>&1
if errorlevel 1 (
  echo.
  echo 빌드에 실패했습니다. 아래 로그와 build_log.txt를 확인해 저에게 보내주세요.
  goto :show_log_and_exit
)

echo.
echo 완료: dist\DBLogAnalyzer.exe
echo 이 파일 하나만 복사해서 폐쇄망 PC로 옮기면 됩니다.
echo SUCCESS >> "%LOG%"

:show_log_and_exit
echo.
echo ==================== build_log.txt 내용 ====================
type "%LOG%"
echo ===============================================================
echo.
echo 문제가 있었다면, 이 폴더에 새로 생긴 build_log.txt 파일을 그대로 보내주세요.
echo (이 창이 바로 닫힌다면: 이 bat 파일을 더블클릭하지 말고, 명령 프롬프트를 먼저 열어서
echo  이 폴더로 이동한 뒤 build_windows.bat 라고 입력해서 실행해 보세요.)
pause
