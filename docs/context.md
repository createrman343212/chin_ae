# 설계 결정과 제약

## SDK 실측 결과 (2026-11-07, Pleos Connect Emulator 2.1.0)

G1 확인 도구: `app/src/debug/java/app/chinae/debug/G1ProbeActivity.kt` (debug 빌드 전용)
실행: `adb shell am start -n app.chinae/.debug.G1ProbeActivity`, logcat 태그 `G1`

| 게이트 | 결과 | 비고 |
| --- | --- | --- |
| G1 TTS ON_DEVICE | 통과 | 한국어 재생 onDone까지 확인 |
| G1 STT ON_DEVICE | 통과 | sendAudio로 넣은 한국어 문장이 그대로 최종 결과로 나옴 |
| G1-2 조명 쓰기 | 통과 | capability READ_WRITE, area 1개, setAmbientLightBrightnessColor 성공 |
| G1-3 즐겨찾기 | 조건부 | category HOME/OFFICE로 구분됨. 에뮬레이터 기본값은 이름 빈 값, 좌표 0,0이라 내비에서 실제 등록 후 재확인 필요 |

### TTS
- `addEventListener`는 `initialize` 성공 콜백 안에서 호출. 먼저 부르면 SDK 내부 lateinit 예외
- `speak(text, isFinal)`의 두 번째 인자는 flush가 아니라 스트림 종료 표시. `false`면 서비스가 문장을 쌓아만 두고 읽지 않음. 한 문장씩 말하려면 `true`
- 쌓인 문장은 앱을 재시작해도 서비스 쪽에 남아 다음 `isFinal=true` 때 함께 읽힘

### STT
- 결과는 `onUpdated(text, isFinal)`로 누적 문자열이 옴. 마침표 없이 옴 → Segmenter는 연결어(그리고, 또)로 나눔
- `request()`(마이크)와 `sendAudio`를 같이 쓰면 엔진 오류(`not enough space`)로 앞부분이 버려짐. 녹음 PCM 시연은 `request()` 없이 `sendAudio`만
- PCM 형식: 16kHz, 16bit, mono, 100ms(3200B) 단위 전송 후 `completeAudioSend()`

### 좌석과 기어 (Virtual Console 실측)
- 기어: Pleos `vehicle.getGear().registerGearSelection` 사용, 권한 `pleos.car.permission.CAR_DRIVE`. 콘솔 Drive/Park가 GEAR_DRIVE/GEAR_PARK로 정확히 옴. 리스너 인자는 `VehicleGear?`(nullable), `onFailed`도 구현해야 함
- 좌석: `checkSeatOccupancyCapabilities` → ROW_1_LEFT/RIGHT, ROW_2_LEFT/CENTER/RIGHT 5곳. 콘솔은 driver/passenger/rear_left/rear_right 4곳만 조작 가능(ROW_2_CENTER 조작 불가)
- **SDK 좌석 탑승 Boolean이 실제와 반대**: 4곳 모두 콘솔 Occupied → 앱 false, Empty → 앱 true. 같은 순간 Pleos 차량 서비스 로그는 VHAL `SEAT_OCCUPANCY value=2`(OCCUPIED)를 받으므로 콘솔은 정상이고 SDK 쪽이 반전(2026-11-08 확인). `VehicleStateHub.SEAT_OCCUPANCY_INVERTED = true`로 보정함. SDK가 고쳐지면 이 상수만 바꿈. 제출 전 공모전 문의로 확인
- 콘솔 초기 상태와 앱 초기값이 다름(콘솔에서 한 번 써야 동기화됨). 시연 시작 때 4좌석 값을 모두 한 번 써 줄 것
- 콘솔 HTTP API: 브라우저 heartbeat `POST /browser/session?client=..` 없으면 `emulator_inactive`. 쓰기는 `PUT /vehicle/seat?kind=occupied&area=..&value=..`, `PUT /vehicle/gear?gear=..` → `demo/vc.sh`

- VehicleStateHub 실측(2026-11-08): 기어 P/D, 동승석 탑승/하차 3회 모두 alone 값 정상. 내비 현재 위치는 getCurrentLocationInfo 요청으로 옴(에뮬레이터 37.4127, 127.0947). DestinationInfo.remainTime은 초 단위로 확인(16km 경로에 remainTime=1260, 내비 RouteStartInfo.duration=1260과 일치), remainDistance는 미터. 경로가 없으면 좌표 0,0과 0이 옴
- 경로 요청(`NaviHelper.requestRoute`)은 별도 권한 `pleos.car.permission.NAVI_ROUTE_SEARCH`가 필요. 없으면 예외 없이 조용히 무시됨. 요청하면 내비 앱이 앞으로 나옴. 시연용 경로는 `adb shell am start -n app.chinae/.debug.G1ProbeActivity --es route 37.4979,127.0276`(위도,경도)

### 에뮬레이터 환경
- 첫 부팅 후 첫 음성 요청 때 모델 제공자(ai.pleos.gleoai.model.provider)가 모델을 복사함. 그 전 요청은 ON_DEVICE_ENGINE_ERROR. 복사 뒤에도 TTS는 `adb shell am force-stop ai.pleos.playground.caas`로 음성 서비스를 재시작해야 정상화됨. 모델이 복사된 뒤 재부팅하면 재시작 없이 바로 통과(2026-11-07 확인)
- 앱은 Android user 10으로 실행됨. `/sdcard`에 push한 파일은 앱에서 안 보임 → `run-as app.chinae --user 10`으로 `files/`에 복사
- Git Bash에서 adb 기기 경로를 쓸 때는 `MSYS_NO_PATHCONV=1`
- AVD: `pleos21`, 시스템 이미지 `C:\Android\Sdk\system-images\android-34\android-automotive\x86_64` (CLI로 설치, Studio 업데이트 사이트 대신 XML의 zip 직접 받음)

## 데이터 계층 (2026-11-08)
- Room 2.6.1 + SQLCipher(sqlcipher-android 4.6.1). DB 암호는 무작위 32바이트를 Keystore AES-GCM 키로 감싸 SharedPreferences `secure`에 보관. 에뮬레이터에서 DB 파일 헤더가 암호화돼 있고 재실행 후 다시 열림을 확인
- 엔티티는 Worry, NoteToSelf만. Trip, ReferralCard, Place 테이블은 쓰는 곳이 생길 때(반복 감지 등) 추가
- 설정은 DataStore 대신 SharedPreferences(`Settings`): 말투, 호칭, 근무일, 직접 지정한 집·회사, 온보딩 완료. 화자(speaker)는 TTS 화자 값 형식을 DialogueEngine 때 확인하고 추가
- 장소 우선순위: 설정에서 직접 지정 > 내비 즐겨찾기
- 보존 정리는 WorkManager 없이 상주 서비스가 시작할 때와 24시간마다 실행
- 단위 테스트는 Robolectric + 평문 인메모리 Room(암호화는 JVM에서 불가)
- KeyExtractor: "회의"의 끝 글자 "의"가 조사로 잘리던 문제 수정(사전 단어는 조사 제거 안 함)

## 대화 (2026-11-08)
- DialogueEngine은 Speaker/Listener 인터페이스만 써서 JVM 테스트 가능. SpeechOut(TTS), SpeechIn(STT)이 SDK를 감쌈
- 대사: `app/src/main/assets/dialogue/banmal.json`, `haeyo.json` (같은 키, 테스트로 확인). 지금은 ask_entrust, ack_entrust만
- 맡기기 거절: 짧은 답(없어, 아니, 괜찮아, 됐어, 그만, 나중에 등)만 거절로 봄. 긴 문장 속 "나중에"는 걱정 내용
- 마이크는 앱이 아니라 Pleos 음성 서비스(GAAS ondevice-stt-input-channel)가 엶 → 앱에 RECORD_AUDIO, microphone FGS 유형 불필요(masterplan 16장 미결 사항 해소)
- 에뮬레이터 STT는 sendAudio로 넣은 PCM을 completeAudioSend 뒤에야 인식 결과로 냄 → PCM 경로의 무응답 7초는 다 보낸 뒤부터 셈
- 시연 PCM: `run-as app.chinae --user 10`으로 `files/demo/*.pcm`에 넣으면 듣기 때마다 이름순으로 하나씩 쓰고 지움
- Pleos SDK가 자체 logcat에 STT 결과 전문과 TTS 문장을 남김(`[CAAS][SDK]SttCallback`, `OnDeviceStreamTts`). 앱 로그에는 길이와 분류만 남기지만 SDK 로그는 막을 수 없음
- 에뮬레이터 전체 흐름 확인: 퇴근길 판정 → 5분 뒤 질문 → PCM 인식 → 2건 저장 → "두 개, 잘 맡아 둘게."

## 잠금 의식 (2026-11-08)
- 순서: 조명 따뜻한 색(0xFFFFB46B) → 잠금음 → 10초 정적 → 원래 색 복원. 기어가 P를 벗어나거나 동승자가 타면 취소하고 복원
- 잠금음: Pleos 차량 잠금음 `/product/media/audio/notifications/snd_vehicle_lock.ogg`을 차량에 있는 파일 그대로 재생(현대차그룹 저작물이라 APK에 넣지 않음, 사용자 선택 2026-11-08). 파일이 없거나 못 읽으면 앱 합성음 `res/raw/lock_chime.wav`(청아한 딱 + 에코)로 대신. 실차에 같은 경로가 있는지, 플랫폼 효과음 사용이 규정상 괜찮은지는 공모전 문의 필요. 같은 폴더에 snd_vehicle_lck_01~04, snd_vehicle_bye_01~04, snd_system_end 등 차량 효과음이 더 있음
- 조명 색을 읽으면 알파 없이 0x00RRGGBB로 옴 → 복원 때 0xFF000000을 붙임
- **에뮬레이터 미해결**: 첫 색 변경(따뜻한 색)은 반영되지만, 약 10초 뒤 복원 쓰기는 SDK가 성공 콜백을 주는데도 색이 그대로임(다른 색으로 써도 동일). 앱은 복원을 정상 호출함. 실차·공식 답변으로 확인 필요
- 에뮬레이터 내비는 `adb emu geo fix`를 무시하고 고정 위치(37.4127212, 127.094681)를 씀 → 집 도착 확인은 설정의 집을 현재 위치로 두고, probe로 근처 경로를 걸어 퇴근길로 판정시킴
- 에뮬레이터는 기어를 바꿀 때마다 콘솔의 조명 색(desired)을 다시 내보냄

## 돌려받기 (2026-11-08)

- 들려주는 내용은 원문을 그대로 읽음(요약 없음). 원문이 7일 지나 지워졌으면 주제어만, 그것도 없으면 순서만 말함
- 질문은 분류로 고름: 바꿀 수 없는 일 → 내려놓을래, 나머지(미분류 포함) → 오늘 처리할 거야
- 짧은 긍정(응, 네, 그래 등)은 물어본 쪽 명령으로 처리함. 4.4 사전에 없던 것을 추가한 결정
- 분류 바로잡기는 상태를 바꾸지 않아 다음 출근길에 다시 돌아옴. 인식 실패와 "그만" 뒤 항목도 PENDING 그대로 이월
- 오늘 처리(TODAY)는 pending에서 빠져 다시 돌아오지 않음. 저녁에 다시 묻는 흐름은 없음
