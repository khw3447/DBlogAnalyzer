# DBLogAnalyzer

iBatis/WebLogic/Oracle 배치 로그에서 실행된 SQL을 추출해, 테이블별 사용 현황(SELECT/INSERT/UPDATE/DELETE/MERGE 건수)과
개별 SQL의 전문/사용 컬럼-값/JOIN 테이블을 조회할 수 있는 데스크톱 분석 도구입니다. 폐쇄망 Windows 환경에 `.exe`로
배포하는 것을 목표로 합니다.

이 저장소에는 기능이 동일한 구현이 두 가지 있습니다.

- **이 디렉터리 (Java/Swing + JSqlParser)**: exe 옆에 `jre` 폴더를 두거나, 대상 PC에 JDK가 이미 설치되어
  있어도 되는 경우. 두 방식 모두 "설치"가 아니라 파일/폴더 복사만으로 끝나지만, exe 파일 하나만 달랑
  있어야 하는 요건에는 맞지 않습니다.
- **[`python/`](python/) (Python/Tkinter + sqlglot)**: 대상 PC에 JDK도 설치 못 하고, 부속 폴더/파일 없이
  정말 exe 파일 하나만으로 실행해야 하는 경우. PyInstaller `--onefile`로 Python 런타임까지 exe 하나에
  전부 담습니다. 자세한 내용은 `python/README.md` 참고.

## 빌드 및 실행 (Java 버전)

요구 사항: JDK 21, Maven.

```
mvn test              # 단위 테스트 실행
mvn package            # target/dblog-analyzer.jar (의존성 포함) 생성
java -jar target/dblog-analyzer.jar   # 실행
```

## Windows용 exe 패키징

두 가지 방법이 있습니다. 어느 쪽이든 최종적으로는 `.exe` 하나(+선택적으로 `jre` 폴더)를 폐쇄망 PC에 복사해
넣는 형태입니다.

### 방법 A: `mvn -Pwindows-exe package` (Linux/Mac 빌드 머신에서도 가능, 권장)

```
mvn -Pwindows-exe package
```

`target/DBLogAnalyzer.exe` 하나가 만들어집니다. [Launch4j](https://launch4j.sourceforge.net/)가 애플리케이션
jar 전체를 exe 안에 그대로 임베드하므로, **이 exe 파일 하나만으로 배포 가능**합니다 (jpackage와 달리 크로스
플랫폼 빌드가 가능해 Windows 머신 없이도 만들 수 있습니다). 다만 이 exe는 "런처"이지 JRE 자체를 담고 있지는
않으므로, 실행하려는 PC에 둘 중 하나가 필요합니다.

- **폐쇄망 완전 오프라인 배포**: JDK 21 Windows용 zip(예: Eclipse Temurin `OpenJDK21U-jre_x64_windows_hotspot_*.zip`,
  **반드시 x64 - 64비트 - 버전**)을 인터넷 되는 PC에서 받아 압축을 풀고, 그 폴더를 `jre`라는 이름으로
  `DBLogAnalyzer.exe`와 같은 폴더에 둡니다.
  ```
  DBLogAnalyzer.exe
  jre/
    bin/
    lib/
    ...
  ```
  이 폴더 전체를 폐쇄망으로 옮기면 대상 PC에 Java를 따로 설치할 필요가 없습니다. (이 리포지토리 빌드
  환경은 조직 네트워크 정책상 JDK 배포처 다운로드가 막혀 있어, `jre` 폴더 자체는 여기서 대신 만들어 드릴 수
  없습니다 - 사용자 환경에서 직접 받아 넣어야 합니다.)
- **PC에 JDK 21이 이미 설치돼 있는 경우**: `jre` 폴더 없이 exe만 복사해도, Launch4j가 레지스트리에서
  JDK 21 이상을 자동으로 찾아 실행합니다 (64비트 JDK를 찾도록 `pom.xml`의 `requires64Bit`를 켜 두었습니다).

**32비트/64비트에 대해**: `DBLogAnalyzer.exe` 파일 속성을 보면 "32비트"로 표시됩니다 - 이건 정상입니다.
Launch4j가 만드는 실행파일은 JVM을 직접 담고 있는 게 아니라 JRE를 찾아서 실행만 해 주는 아주 얇은
런처(스텁)인데, 이 런처 자체는 Launch4j 툴 자체의 한계로 항상 32비트로만 빌드됩니다. 32비트 런처가
64비트 자식 프로세스(javaw.exe)를 실행하는 데는 아무 문제가 없으므로, **위 두 경우 모두 실제 실행은
64비트 JRE로 이뤄집니다** - 다만 `jre` 폴더를 직접 준비하실 때 실수로 32비트용 zip을 받지 않도록 x64
버전인지 꼭 확인해 주세요.

### 방법 B: `jpackage` (Windows 머신 필요, 완전 자동 번들링)

`jpackage`는 크로스 컴파일을 지원하지 않으므로, **Windows 머신에서 JDK 21을 설치한 뒤** 실행해야 합니다.
대신 `jre` 폴더를 직접 구해 넣을 필요 없이 완전 자동으로 런타임까지 묶어 줍니다.

```
mvn package
jpackage ^
  --input target ^
  --main-jar dblog-analyzer.jar ^
  --main-class com.dbloganalyzer.App ^
  --name DBLogAnalyzer ^
  --type app-image ^
  --win-console ^
  --java-options "-Dsun.java2d.d3d=false"
```

`--type app-image`는 JRE가 통째로 포함된 실행 폴더를 만들어 주므로, 이 폴더를 그대로 폐쇄망 PC에 복사하면
별도 Java 설치 없이 `DBLogAnalyzer.exe`를 실행할 수 있습니다. 배포용 설치 파일(msi)이 필요하면
`--type msi` (WiX Toolset 필요)를 사용하세요.

`-Dsun.java2d.d3d=false`는 Java2D가 Direct3D 대신 GDI 렌더링 경로를 쓰도록 강제합니다. 오래된/폐쇄망
산업용 PC에서 그래픽 드라이버가 최신이 아닐 때 Direct3D 파이프라인 초기화 문제로 화면이 깨지는 것을 예방하는
일반적인 방어책이라 기본으로 넣어 두었습니다. 방법 A로 만든 exe에도 같은 옵션을 주려면 pom.xml의
`windows-exe` 프로필에 있는 `launch4j-maven-plugin` 설정에 `<jvmOptions><opt>-Dsun.java2d.d3d=false</opt></jvmOptions>`를
추가하세요.

### 이 저장소에서 검증한 것 / 못한 것

방법 A로 만든 exe는 이 개발 환경(Linux)에서 다음까지 확인했습니다: 올바른 PE(MZ/PE\0\0) 헤더를 가진 정상
Windows 실행파일이라는 것(PE 헤더의 machine type을 직접 파싱해 32비트(I386)로 빌드된다는 것도 확인 -
Launch4j 툴 자체의 한계로, 위에서 설명한 대로 정상입니다), 그 안에 애플리케이션 jar(클래스 전체 +
JSqlParser)가 정확히 임베드되어 있다는 것. 다만 이 환경은 리눅스 컨테이너이고 조직 네트워크 정책상
Windows용 JRE 다운로드가 막혀 있어, **실제 Windows(또는 Wine)에서 더블클릭해 창이 뜨는 것까지는 이
세션에서 직접 실행 검증하지 못했습니다.** 받으신 exe를 대상 환경에서 한 번 실행해 보시고, 문제가 있으면
알려주세요.

### Windows 10 Enterprise 2016 LTSB(1607) 대상일 때 주의할 점

- Oracle의 JDK 21 공식 인증 목록에는 Windows 10이 "더 이상 지원 안 함(No Longer Supported)"으로 표기되어
  있고, Windows 11 및 Windows Server 2016/2019/2022(+2025)만 인증 대상입니다. JDK 17로 낮춰도 동일합니다
  (이 문서가 MS의 Windows 10 지원 종료 시점에 연동되어 갱신되기 때문). 이는 "절대 안 돌아간다"는 뜻이 아니라
  Oracle이 더 이상 테스트/보증하지 않는다는 정책적 의미이며, Swing/AWT는 아주 오래되고 안정적인 Win32 API만
  쓰므로 실제로는 대부분 정상 동작합니다.
- 다만 이 프로젝트는 폐쇄망 배포라 배포 후 패치가 어려우므로, "될 것이다"에 기대지 말고 **실제 대상 빌드와
  동일한(가능하면 동일 패치 레벨) Windows 10 2016 LTSB 머신/VM에서 jpackage로 만든 exe를 배포 전에 반드시
  먼저 실행해 보길 권장**합니다. LTSB는 매월 보안 전용 누적 업데이트만 나오므로, 대상 PC가 2016년 출시 당시
  RTM 상태에 가까운지 최근 누적 패치까지 적용된 상태인지에 따라 결과가 달라질 수 있습니다.
- Windows 10 Enterprise 2016 LTSB는 2026-10-13에 마이크로소프트 지원 자체가 종료됩니다. 지금(2026-07-27)
  기준 약 3개월 밖에 남지 않았습니다. 코드 이슈는 아니지만, 이 도구를 장기간 쓸 계획이라면 조직 차원에서
  OS 자체의 보안 업데이트 종료 시점도 함께 고려하시는 게 좋습니다.
- 이 앱은 순수 Swing이라 WebView2/Electron/.NET 같은 별도 런타임에 의존하지 않으므로, 그런 종류의 구버전
  호환성 문제는 애초에 없습니다.

## 아키텍처

```
com.dbloganalyzer
├── log            로그 텍스트 -> "SQL:" 블록 추출 (LogParser)
├── sql            SQL 주석/힌트 분류 + JSqlParser 기반 분석 (SqlAnalyzer)
├── model          테이블별 집계 (AnalysisResult, TableStat)
└── ui             Swing 화면 (MainFrame, SqlDetailDialog)
```

### 로그 파싱 (`log` 패키지)

로그 한 줄은 `yyyy-MM-dd HH:mm:ss,SSS LEVEL thread logger - message` 형식입니다. `SQL:`로 끝나는 메시지가
나오면, 이후 같은 프리픽스 패턴에 매칭되지 않는 줄들을 전부 하나의 SQL 텍스트로 모읍니다(스택트레이스 같은
멀티라인 로그를 파싱하는 것과 동일한 방식). 이 로그 포맷의 중요한 특징은 **바인드 값이 이미 SQL에 리터럴로
치환되어 찍힌다는 점**입니다 (`Preparing:` + `Parameters:` 분리형이 아님). 따라서 별도의 파라미터-플레이스홀더
매핑 없이 SQL 텍스트 자체를 파싱하는 것만으로 컬럼-값을 얻을 수 있습니다.

### SQL 분석 (`sql` 패키지)

- `SqlCommentAnalyzer` : 문자열 리터럴 내부의 `--`/`/*`는 주석으로 오인하지 않도록 상태 추적 스캐너로 주석을
  찾고, 세 가지로 분류합니다.
  - `HINT` : `/*+ ... */` (`+`로 시작하는 블록 주석) - Oracle 옵티마이저 힌트
  - `STATEMENT_ID` : SQL 키워드 직후에 오는 블록 주석 - iBatis가 로깅 시 자동 삽입하는 매핑 구문 ID
    (`SqlRecord.statementIdTag()`로 조회)
  - `BLOCK` / `LINE` : 그 외 일반 주석 (`--`는 이 코드베이스의 INSERT VALUES 절에서 컬럼명을 표기하는 용도로
    자주 쓰임)
- `SqlAnalyzer` : 주석을 제거한 SQL을 [JSqlParser](https://github.com/JSQLParser/JSqlParser)로 파싱합니다.
  파싱에 실패해도 레코드 자체는 버리지 않고, 키워드 기반으로 추정한 타입과 파싱 실패 사유를 담아 반환합니다
  (SQL 목록에서 "파싱: 실패"로 표시).
- `TableExtractor` : `TablesNamesFinder`로 JOIN·서브쿼리에 등장하는 모든 테이블을 수집합니다. 이 코드베이스의
  SQL은 ANSI `JOIN` 대신 콤마 조인 + WHERE절 조건, 서브쿼리 별칭(`FROM (SELECT ...) M904, TABLE C4C2`)을 쓰는
  구식 Oracle 스타일이 많으므로, 서브쿼리 안쪽 테이블도 놓치지 않도록 검증되어 있습니다.
- `ColumnValueExtractor` : SELECT는 WHERE(및 JOIN ON, 서브쿼리 WHERE까지 재귀적으로), UPDATE/MERGE는
  SET+WHERE, INSERT/MERGE INSERT는 컬럼 목록과 VALUES를 순서대로 매칭해 `column 연산자 value` 목록을
  만듭니다.

### 화면 (`ui` 패키지)

- 상단 : 로그 붙여넣기 또는 파일 열기(EUC-KR로 저장된 레거시 로그 파일도 자동 판별)
- 좌측 : 테이블별 SELECT/INSERT/UPDATE/DELETE/MERGE 건수 - 행 클릭 시 우측 SQL 목록이 해당 테이블 관련
  SQL로 필터링됨
- 우측 : SQL 목록(타입 체크박스 필터) - 더블클릭 시 상세 다이얼로그(전문 + 주석/힌트 하이라이트 + 컬럼-값 표)

## 알려진 제한 사항

- JSqlParser가 처리하지 못하는 드문 Oracle 구문(특이 PL/SQL 블록 등)은 파싱 실패로 표시되고 테이블/컬럼-값
  분석 없이 원문만 보여줍니다.
- 컬럼-값 추출은 `컬럼 = 리터럴` 형태의 비교/할당에 최적화되어 있습니다. 함수 호출이나 CASE 표현식 안에 있는
  비교는 추출 대상에서 제외됩니다.
- 실제 운영 로그에는 iBatis `sqlMapConfig.xml`(DB 접속 정보 포함)이 통째로 찍히는 경우가 있는데, 이 도구는
  `SQL:` 로 시작하는 블록만 인식하므로 그런 설정 덤프는 애초에 화면에 노출되지 않습니다.

## 테스트

`src/test/resources/sample-log.txt`는 실제 운영 로그 샘플(iBatis 배치 잡, 콤마 조인 서브쿼리, INSERT의
`--` 컬럼 주석 등)을 바탕으로 재구성한 픽스처입니다. DELETE/MERGE 예시는 원본 로그에 없어 동일한 도메인
테이블명으로 직접 구성했습니다.
