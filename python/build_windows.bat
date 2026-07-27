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

python -c "import struct,sys; b=struct.calcsize('P')*8; print(str(b)+'bit'); sys.exit(0 if b==64 else 1)" > pyarch.tmp 2>&1
set /p PYARCH=<pyarch.tmp
del pyarch.tmp
echo   Python 아키텍처: %PYARCH%
echo   Python architecture: %PYARCH% >> "%LOG%"
if not "%PYARCH%"=="64bit" (
  echo.
  echo [경고] 32비트 Python이 설치되어 있습니다. 실행 환경은 64비트이고, 동봉된 wheels도
  echo         64비트^(win_amd64^) 전용이라 32비트 Python으로는 오프라인 설치가 실패합니다.
  echo         https://www.python.org/downloads/ 에서 "Windows installer ^(64-bit^)"로 다시
  echo         설치한 뒤 다시 실행해 주세요. ^(기존 32비트 Python은 제어판에서 제거 권장^)
  echo WARNING: 32-bit Python detected, wheels are win_amd64-only >> "%LOG%"
  goto :show_log_and_exit
)

echo [2/3] 필요한 패키지 설치 중... (아래에 실시간으로 진행 상황이 표시됩니다. 몇 분 걸릴 수
echo       있으니 화면이 잠시 멈춘 것처럼 보여도 창을 닫지 말고 기다려 주세요)
echo [2/3] Installing packages >> "%LOG%"
if exist wheels (
  echo   wheels 폴더 발견: %CD%\wheels ^(완전 오프라인 설치, 인터넷 접속 안 함^)
  echo   found wheels folder: %CD%\wheels >> "%LOG%"
  python -m pip install --no-index --find-links wheels -r requirements.txt pyinstaller
) else (
  echo   wheels 폴더를 찾지 못했습니다: %CD%\wheels
  echo   wheels folder not found at: %CD%\wheels >> "%LOG%"
  echo   인터넷^(pip, pypi.org^)으로 설치를 시도합니다...
  python -m pip install -r requirements.txt pyinstaller
)
if errorlevel 1 (
  echo [2/3] pip install failed >> "%LOG%"
  echo.
  echo 패키지 설치에 실패했습니다. 위에 표시된 내용을 저에게 보내주세요 ^(build_log.txt에는
  echo 단계 기록만 남습니다. 자세한 오류 문구는 화면에 이미 표시된 것이 전부입니다^).
  goto :show_log_and_exit
)
echo [2/3] pip install ok >> "%LOG%"

echo.
echo [3/3] exe 빌드 중... (마찬가지로 아래에 실시간으로 표시됩니다. 시간이 좀 걸립니다)
echo [3/3] Building exe >> "%LOG%"
python -m PyInstaller --onefile --windowed --collect-all sqlglot --name DBLogAnalyzer app.py
if errorlevel 1 (
  echo [3/3] pyinstaller failed >> "%LOG%"
  echo.
  echo 빌드에 실패했습니다. 위에 표시된 내용을 저에게 보내주세요.
  goto :show_log_and_exit
)
echo [3/3] pyinstaller ok >> "%LOG%"

echo.
echo 완료: dist\DBLogAnalyzer.exe
echo 이 파일 하나만 복사해서 폐쇄망 PC로 옮기면 됩니다.
echo SUCCESS >> "%LOG%"

:show_log_and_exit
echo.
echo ==================== build_log.txt 요약 (단계 진행 기록용, 상세 오류는 위 화면 참고) ====================
type "%LOG%"
echo ===============================================================================================
echo.
echo 문제가 있었다면 위 화면 내용을 캡처하거나 build_log.txt를 같이 보내주세요.
echo (이 창이 바로 닫힌다면: 이 bat 파일을 더블클릭하지 말고, 명령 프롬프트를 먼저 열어서
echo  이 폴더로 이동한 뒤 build_windows.bat 라고 입력해서 실행해 보세요.)
pause
