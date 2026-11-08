# 세션 작업 기록

## 2026-11-07
- Kotlin 문자열 `"\s+"` 컴파일 오류 수정(CommandParser, KeyExtractor → raw string)
- Segmenter: `\b`를 한글 lookaround로 교체(JVM과 ICU 동작 차이), "또" 분리 어미에 해/돼 추가
- local.properties sdk.dir 경로 수정(백슬래시 escape로 `C:AndroidSdk`가 되던 문제)
- Gradle wrapper 8.13 생성, 단위 테스트 `app/src/test/java/app/chinae/RulesTest.kt` 10건 통과
- Pleos 에뮬레이터 CLI 설치(AVD pleos21), G1 probe로 TTS·STT·조명 통과, 즐겨찾기는 조건부 (상세 context.md)
- Virtual Console API로 좌석·기어 수신 확인(기어 정상, 좌석 값 반전), demo/vc.sh 추가
- 평소 화면: 설정 카드는 첫 실행과 설정 버튼에서만 열고, 평소에는 맡긴 걱정 수에 비례한 거품 띠가 돎(걱정 1개당 250개, 최대 800개). 에뮬레이터 프레임 중간값 113ms로 끊김 남음, 휴대폰·실차 미측정
- 다음 할 일: VehicleStateHub(좌석 반전 상수 포함)와 TripStateMachine, 내비 집·회사 등록 후 G1-3 재확인

## 2026-11-08
- VehicleStateHub(좌석·기어·내비 → VehicleSnapshot, 좌석 반전 상수), TripStateMachine(3.1 상태 머신) 추가, 단위 테스트 4건 추가(총 14건 통과)
- 에뮬레이터에서 Hub 실측 통과. 좌석 반전은 SDK 쪽 문제로 확인(VHAL 값은 정상)
- remainTime 초 단위 확인(probe에 경로 요청 추가, debug 매니페스트에 NAVI_ROUTE_SEARCH)
- ChinaeService(specialUse 포그라운드) 추가: Hub 스냅샷과 30초 타이머로 상태 머신 구동, 이벤트는 로그만(DialogueEngine 연결 전). PlaceStore는 내비 즐겨찾기 HOME/OFFICE에서 채움. 테스트 15건 통과, 에뮬레이터에서 기어 D/P로 IDLE↔FREE_DRIVE 확인
- GitHub 비공개 저장소 dear-driver 생성, push
- 데이터 계층: Room+SQLCipher, WorryRepository(맡기기 저장, 보존 정리), Settings, 서비스에 맡긴 걱정 수 연결. 테스트 20건 통과, 에뮬레이터에서 암호화 DB 확인
- DialogueEngine, SpeechOut/SpeechIn, 대사 묶음, 서비스 연결(동승자 탑승 시 대화 취소). 테스트 24건, 에뮬레이터에서 맡기기 1회 왕복 통과
- 잠금 의식: LockRitual, LightController, 잠금음 합성(청아한 딱 + 에코), 서비스 연결. 테스트 27건, 에뮬레이터에서 집 도착 P → 조명 전환·잠금음 확인. 조명 복원은 에뮬레이터에서 반영 안 됨(context.md)
- 돌려받기: runReturn(인사 → 한 건씩 읽기 → 분류별 질문 → 명령 반영), 명령 사전, 서비스 연결. 테스트 30건. 에뮬레이터 출근길 확인은 아직
- 설정 화면: 말투, 호칭, 근무일, 집·회사 직접 지정, 모든 기록 지우기, 안내문. 에뮬레이터에서 말투·요일 저장, 시작하기 후 서비스 시작 확인. 화면 전체 1.6배 확대
- 설정 화면 디자인: 남색·크림·산호색 유리 카드. 말투는 물방울처럼 늘어나며 옮겨 가는 선택, 요일은 물방울이 퍼지는 효과. 배율은 폭 1050dp 기준으로 맞춤(차 1.6배), 차 아닌 기기는 가로 고정, 차량 권한이 없어도 화면은 뜸. 휴대폰(S8+) 디버그 빌드 프레임 중간값 8ms(에뮬레이터 61ms)
- 다음 할 일: 주행 중 숨김 확인(Virtual Console), 출근길 S4 확인, 내비 집·회사 등록 후 G1-3 재확인
