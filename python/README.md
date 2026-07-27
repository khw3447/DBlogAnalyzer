# DBLogAnalyzer (Python 버전)

`../` 최상위의 Java/Swing 버전과 기능은 동일합니다. 이 버전이 존재하는 이유는 단 하나, **배포 대상 PC에 JDK를
설치할 수 없고, JRE가 담긴 부속 폴더도 없이 Windows exe 파일 하나만으로 실행해야 하기 때문**입니다.
[PyInstaller](https://pyinstaller.org)의 `--onefile` 모드는 Python 인터프리터 + 모든 의존 라이브러리를
정말로 exe 파일 하나에 담아, 실행 시 임시 폴더에 풀었다가 종료하면서 정리하는 방식으로 동작합니다 - 사용자
입장에서는 별도 설치나 부속 파일 없이 "exe 파일 하나"로 보입니다.

SQL 파싱은 [JSqlParser](https://github.com/JSQLParser/JSqlParser) 대신
[sqlglot](https://github.com/tobymao/sqlglot)(Oracle 방언 지원)을 사용합니다.

## 빌드 및 실행

요구 사항: Python 3.11+ (Tk/Tkinter 포함된 배포판 - Windows 공식 python.org 설치본은 기본 포함).

```
pip install -r requirements.txt
python app.py            # 실행
pip install pytest && pytest tests/    # 테스트 (13건, Java 버전과 동일한 케이스를 이식)
```

## Windows용 단일 exe 만들기

**반드시 실제 Windows 머신에서 실행해야 합니다.** PyInstaller는 jpackage와 마찬가지로 크로스 컴파일을
지원하지 않습니다 - 그 OS에 실제로 설치된 Python의 DLL/네이티브 확장을 그대로 묶어내는 방식이라, 리눅스에서
실행하면 리눅스용 바이너리만 나옵니다. (이 저장소의 개발 환경에서는 리눅스용으로 빌드해 패키징 메커니즘
자체는 검증했지만, 최종 Windows exe는 아래 절차를 실제 Windows PC에서 진행해야 합니다.)

```
pip install -r requirements.txt pyinstaller
pyinstaller --onefile --windowed --collect-all sqlglot --name DBLogAnalyzer app.py
```

`dist\DBLogAnalyzer.exe` 하나가 만들어집니다. 이 파일 하나만 복사하면 폐쇄망 PC에서 바로 실행됩니다
(Python도, JDK도, 별도 폴더도 필요 없습니다). `--windowed`는 실행 시 콘솔 창이 함께 뜨지 않게 합니다.

`--collect-all sqlglot`은 꼭 필요합니다. sqlglot은 `read="oracle"`처럼 문자열로 방언을 지정하면
`importlib.import_module(f"sqlglot.dialects.{key}")`로 해당 모듈을 실행 시점에 동적으로 불러오는데,
PyInstaller는 소스 코드를 정적 분석해서 어떤 모듈을 exe에 담을지 정하기 때문에 이런 동적 임포트는
찾아내지 못합니다. 이 옵션 없이 빌드하면 겉보기엔 정상적으로 빌드/실행되지만 실제 SQL을 분석하려는
순간 `No module named 'sqlglot.dialects.oracle'`로 전부 파싱 실패 처리됩니다(테이블 정보가 하나도
안 잡히는 것으로 나타남) - 실제로 이 증상으로 한 번 걸렸고, 최소 재현으로 원인을 확인한 뒤 고쳤습니다.

빌드 머신에 필요한 것은 Python 3.11+ 설치와 위 두 pip 명령뿐입니다. 빌드가 끝나면 그 Python 자체는 배포
대상 PC에 필요 없습니다 - exe 안에 이미 다 들어있습니다.

`build_windows.bat`을 쓰면 위 과정이 자동화되고, 빌드 머신조차 pypi.org에 못 나가는 환경일 수 있다는 점을
감안해 `wheels/` 폴더(win_amd64용, Python 3.11/3.12/3.13 커버)가 있으면 `pip install --no-index
--find-links wheels ...`로 완전 오프라인 설치를 시도합니다. `pip install` 중 `WinError 10061`(연결 거부)이
난다면 거의 항상 회사 프록시/방화벽이 pypi.org를 막고 있는 경우이니, wheels를 동봉한 배포본을 쓰거나 IT팀에
pip용 프록시 설정을 문의하세요. `python/빌드방법.txt`에 같은 내용을 좀 더 자세히 적어뒀습니다.

## 아키텍처

Java 버전과 패키지 구조가 1:1 대응합니다.

```
dbloganalyzer/
├── logparser.py     로그 텍스트 -> "SQL:" 블록 추출 (Java: log.LogParser)
├── sql_comments.py  주석/힌트 분류 (Java: sql.SqlCommentAnalyzer)
├── sql_analyzer.py  sqlglot 기반 분석 (Java: sql.SqlAnalyzer + TableExtractor + ColumnValueExtractor)
├── model.py         테이블별 집계 (Java: model.*)
└── ui.py            Tkinter 화면 (Java: ui.*)
```

로그 파싱/주석 분류 로직은 Java 버전의 상태추적 스캐너를 그대로 이식했습니다(문자열 리터럴 안의 `--`/`/*`를
주석으로 오인하지 않는 것 포함). sqlglot을 쓰면서 달라진 점 하나: JSqlParser는 서브쿼리의 WHERE절까지
직접 재귀 순회해야 했지만, sqlglot의 `Expression.find_all()`은 트리 전체를 기본으로 순회하므로
`statement.find_all(exp.Where)` 한 줄로 중첩된 서브쿼리의 WHERE절까지 전부 잡힙니다 (`sql_analyzer.py`의
`_extract_column_values` 참고).

`파일 열기`로 로그 파일을 불러올 때는 [`charset_normalizer`](https://github.com/jawah/charset_normalizer)로
인코딩을 감지합니다. 단순히 "UTF-8로 열어보고 실패하면 CP949로 재시도" 방식은 CP949로 인코딩된 한글 바이트가
우연히 유효한(그러나 의미 없는) UTF-8 바이트열을 이루는 경우 예외 없이 조용히 깨진 텍스트를 만들어낼 수
있어서, 후보 인코딩들의 결과 텍스트가 실제로 말이 되는지까지 채점하는 라이브러리로 교체했습니다
(`ui.py`의 `_read_text_auto`).

## 검증

- `tests/`: Java 버전의 JUnit 테스트 13건을 pytest로 그대로 이식, 동일한 `tests/fixtures/sample-log.txt`
  (Java 쪽 `src/test/resources/sample-log.txt`와 동일 파일)로 검증. 전부 통과.
- Xvfb + 스크린샷으로 실제 Tkinter 창을 띄워 붙여넣기 → 분석 → 테이블/타입 필터 → 상세보기(주석·힌트
  하이라이트 + 컬럼-값 표)까지 눈으로 직접 확인.
- PyInstaller `--onefile` 산출물(리눅스 ELF로 빌드됨 - 이 개발 환경이 리눅스라서)을 실제로 실행해 GUI가
  정상적으로 뜨는 것까지 확인. Windows exe도 동일한 메커니즘으로 동작할 것으로 예상되지만, Windows에서의
  최종 실행 확인은 사용자 환경에서 필요합니다.

## 알려진 제한 사항

Java 버전과 동일합니다: sqlglot이 처리 못 하는 드문 Oracle 구문은 파싱 실패로 표시되고 원문만 보여주며,
컬럼-값 추출은 `컬럼 = 리터럴` 형태에 최적화되어 있어 함수 호출/CASE 표현식 내부 비교는 추출되지 않습니다.
