# 서명 및 release APK

필요 도구: JDK 17, Android SDK 35, Gradle Wrapper(포함).

## 최초 서명 키

프로젝트 밖의 안전한 폴더에 키스토어를 생성합니다. 다음은 실행 예시이며 경로와 이름을 본인 환경에 맞게 바꿉니다. 비밀번호는 대화형 입력으로 입력하고 공개 문서에 기록하지 않습니다.

```powershell
keytool -genkeypair -keystore "C:/private/motioncoach-release.jks" -alias motioncoach -keyalg RSA -keysize 3072 -validity 10000
```

프로젝트 루트에 Git 추적 제외 파일 `signing.properties`를 작성합니다.

```properties
storeFile=C:/private/motioncoach-release.jks
storePassword=본인_비밀번호
keyAlias=motioncoach
keyPassword=본인_키_비밀번호
```

이 문서의 자리표시자를 그대로 쓰지 마세요. 서명 키와 비밀번호를 별도 백업합니다. 같은 앱 업데이트에는 같은 키가 필요합니다.

## 빌드·검증·복사

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
# Android SDK의 build-tools/35.0.0에 있는 apksigner로 확인
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
New-Item -ItemType Directory -Force apk
Copy-Item app/build/outputs/apk/release/app-release.apk "apk/멀미시러_멀미약_release.apk"
```

`release`는 `debuggable=false`입니다. 디버그 키로 서명한 파일을 제출용 release라고 부르지 않습니다. 서명 설정이 없을 때 생기는 unsigned APK는 설치용 제출물이 아닙니다.

CI는 비밀 키 없이 단위 테스트와 debug 빌드만 수행합니다. 제출용 서명 키는 CI에 넣지 않았습니다.
