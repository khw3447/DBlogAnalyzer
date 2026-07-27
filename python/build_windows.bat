@echo off
echo [1/3] Python 확인...
python --version
if errorlevel 1 (
  echo Python이 설치되어 있지 않거나 PATH에 없습니다.
  echo https://www.python.org/downloads/ 에서 설치할 때 "Add python.exe to PATH"를 반드시 체크하세요.
  pause
  exit /b 1
)

echo [2/3] 필요한 패키지 설치 중...
if exist wheels (
  echo   동봉된 wheels 폴더에서 오프라인 설치를 시도합니다 ^(인터넷 불필요^)...
  python -m pip install --no-index --find-links wheels -r requirements.txt pyinstaller
) else (
  echo   wheels 폴더가 없어 인터넷으로 설치를 시도합니다...
  python -m pip install -r requirements.txt pyinstaller
)
if errorlevel 1 (
  echo.
  echo 패키지 설치에 실패했습니다. 다음을 확인해 보세요.
  echo   - wheels 폴더로 오프라인 설치를 시도했다면: "python --version"이 3.11/3.12/3.13 중
  echo     하나인지 확인하세요. 동봉된 wheels는 이 세 버전용입니다. 버전이 다르면 인터넷이
  echo     되는 곳에서 아래 명령으로 해당 버전용 wheels를 새로 받아 wheels 폴더에 넣으세요.
  echo       python -m pip download --only-binary=:all: --platform win_amd64 ^
  echo         --python-version 3.13 --implementation cp -d wheels -r requirements.txt pyinstaller
  echo   - 인터넷 설치를 시도했는데 "WinError 10061"^(연결 거부^) 등이 떴다면: 이 PC가 회사
  echo     프록시를 거쳐야 인터넷이 되는 환경일 수 있습니다. IT팀에 pypi.org 접근 방법을
  echo     문의하시거나, wheels 폴더가 있는 배포본을 사용하세요.
  pause
  exit /b 1
)

echo [3/3] exe 빌드 중...
python -m PyInstaller --onefile --windowed --name DBLogAnalyzer app.py
if errorlevel 1 (
  echo 빌드에 실패했습니다.
  pause
  exit /b 1
)

echo.
echo 완료: dist\DBLogAnalyzer.exe
echo 이 파일 하나만 복사해서 폐쇄망 PC로 옮기면 됩니다.
pause
