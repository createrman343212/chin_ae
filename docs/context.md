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
- 경로 요청(`NaviHelper.requestRoute`)은 별도 권한 `pleos.car.permission.NAVI_ROUTE_SEARCH`가 필요. 없으면 예외 없이 조용히 무시됨. 요청하면 내비 앱이 앞으로 나옴. 시연용 경로는 `adb shell am start -n app.chinae/.debug.G1ProbeActivity --ez route true`(강남역)

### 에뮬레이터 환경
- 첫 부팅 후 첫 음성 요청 때 모델 제공자(ai.pleos.gleoai.model.provider)가 모델을 복사함. 그 전 요청은 ON_DEVICE_ENGINE_ERROR. 복사 뒤에도 TTS는 `adb shell am force-stop ai.pleos.playground.caas`로 음성 서비스를 재시작해야 정상화됨. 모델이 복사된 뒤 재부팅하면 재시작 없이 바로 통과(2026-11-07 확인)
- 앱은 Android user 10으로 실행됨. `/sdcard`에 push한 파일은 앱에서 안 보임 → `run-as app.chinae --user 10`으로 `files/`에 복사
- Git Bash에서 adb 기기 경로를 쓸 때는 `MSYS_NO_PATHCONV=1`
- AVD: `pleos21`, 시스템 이미지 `C:\Android\Sdk\system-images\android-34\android-automotive\x86_64` (CLI로 설치, Studio 업데이트 사이트 대신 XML의 zip 직접 받음)
