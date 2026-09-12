# Toonkor 개인 Mihon 확장 저장소

Keiyoushi의 Toonkor **1.4.9**를 원본 그대로 분리한 저장소입니다. 다른 확장 및 multisrc 테마는 포함하지 않습니다. GitHub 계정명·저장소명은 미리 정하지 않아도 됩니다. 업로드 후 GitHub Actions가 실제 저장소 주소를 자동으로 설정합니다.

## 지금 사용할 때: 원본 APK 그대로 배포

1. GitHub에서 원하는 이름으로 **Public 저장소**를 만듭니다. 여기서 ‘개인용’은 직접 관리한다는 뜻입니다. 인증이 필요한 Private 저장소의 raw 주소는 이 구성으로 Mihon에 등록할 수 없습니다.
2. 이 폴더의 **내용 전체**를 `main` 브랜치 최상위에 올립니다. 바깥의 `toonkor-personal` 폴더 자체를 한 단계 더 넣지 마세요. 숨김 폴더 `.github` 및 점으로 시작하는 설정 파일도 포함합니다.
3. GitHub의 **Actions → Publish Toonkor**가 실행됩니다. 자동 실행되지 않았다면 **Run workflow → mode: upstream**으로 실행합니다. 이 모드에는 서명 키나 추가 Secrets가 필요 없습니다.
4. 성공하면 `repo` 브랜치가 자동으로 만들어집니다. 결과 화면의 Summary에 Android와 iOS용 저장소 주소가 표시됩니다.
5. 앱의 확장 저장소 설정에 운영체제에 맞는 주소를 입력합니다. 한국어 확장 목록에서 Toonkor를 설치하고, 신뢰 확인이 표시되면 확인합니다. MIXED 분류라 성인 콘텐츠 표시 설정에 따라 목록에서 숨겨질 수 있습니다.

### Android: Mihon

`index.pb` 주소를 사용합니다.

```text
https://raw.githubusercontent.com/GeumHun/toonkor-personal/repo/index.pb
```

### iOS: Tachimanga

`index.min.json` 주소를 사용합니다.

```text
https://raw.githubusercontent.com/GeumHun/toonkor-personal/repo/index.min.json
```

주소에 `main`이나 `outputs`를 넣지 않습니다. `repo` 브랜치 생성 전에는 이 주소가 작동하지 않습니다. GitHub Pages 설정은 필요 없습니다. 조직 정책으로 쓰기가 차단된 경우 Actions의 저장소 쓰기 권한을 허용해야 합니다.

## GitHub에 올라가는 파일

- `src/ko/toonkor/`: 수정 없이 가져온 Toonkor Kotlin 파일 2개, 빌드 설정, 밀도별 아이콘 5개.
- `core/`, `compiler/`, `common/`, `gradle/` 및 루트 Gradle 파일: 원본 공통 코드·KSP 생성기·빌드 도구. Toonkor의 공통 의존성이고 별도의 확장이 아닙니다.
- `prebuilt/`: Keiyoushi가 서명한 원본 Toonkor APK 한 개와 출처·해시 기록.
- `scripts/`, `.github/workflows/`, `requirements.txt`: 단독 빌드, 인덱스 생성, 배포 자동화.
- `UPSTREAM.json`, `AGENTS.md`, `CONTRIBUTING.md`, `LICENSE`, 설정 파일과 이 안내문: 원본 추적 및 유지관리 자료.

자동 생성되는 **`repo` 브랜치에는 다음 파일만** 들어갑니다:

```text
index.pb
index.json
index.min.json
repo.json
apk/tachiyomi-ko.toonkor-v1.4.9.apk
icon/toonkor.png
icon/eu.kanade.tachiyomi.extension.ko.toonkor.png
LICENSE
```

APK 이름의 버전은 향후 빌드 버전에 따라 달라집니다. 인덱스의 APK·아이콘 주소는 모두 **본인 저장소**를 가리킵니다. 다른 확장, 외부 APK 링크, JAR, HTML 목록은 배포하지 않습니다. Android Mihon용 `index.pb`는 gzip 압축 protobuf 형식을 유지합니다. iOS Tachimanga용 `index.min.json`, 호환용 `index.json`, 패키지명 기반 아이콘을 함께 생성합니다. 레거시 JSON의 `apk` 필드는 표준 형식에 따라 파일명만 기록하며 앱이 같은 브랜치의 `apk/` 경로를 붙입니다.

Git으로 올리는 예시(이 폴더에서 실행):

```sh
git init -b main
git add .
git commit -m "Add standalone Toonkor repository"
git remote add origin https://github.com/내계정/내저장소.git
git push -u origin main
```

`.gitignore`가 `dist/`, 빌드 캐시, 서명 키·Base64 파일을 제외합니다. 웹 업로드를 이용한다면 `dist/`는 직접 제외하세요. 로컬 빌드 도구와 임시 파일은 이 패키지에 포함하지 않았습니다.

## 나중에 직접 빌드·수정할 때

원본 동작을 유지하므로 현재의 `HttpSource`와 `libVersion = "1.4"`를 그대로 보존했습니다. 최신 지침의 새 확장용 `KeiSource` 전환을 임의로 적용하지 않았습니다.

주로 수정할 파일:

```text
src/ko/toonkor/src/eu/kanade/tachiyomi/extension/ko/toonkor/Toonkor.kt
src/ko/toonkor/src/eu/kanade/tachiyomi/extension/ko/toonkor/Filters.kt
src/ko/toonkor/build.gradle.kts
```

사이트 파싱은 `Toonkor.kt`, 필터는 `Filters.kt`, 기본 도메인은 `build.gradle.kts`의 `baseUrl`에서 관리합니다. **변경 후 `versionCode = 9`를 10, 11처럼 증가**시켜야 Mihon이 업데이트로 인식합니다. 패키지명·소스 이름·언어·소스 ID는 특별한 이유 없이 바꾸지 마세요.

### 개인 서명 키: 최초 한 번

JDK의 `keytool`로 이 저장소 루트에서 실행합니다. 비밀번호는 대화형으로 입력하며 안전하게 보관합니다.

```sh
keytool -genkeypair -keystore signingkey.jks -storetype JKS -alias toonkor -keyalg RSA -keysize 4096 -validity 10000
```

다음 명령으로 GitHub Secret에 넣을 Base64 파일을 만듭니다(Python 3):

```sh
python -c "import base64,pathlib; pathlib.Path('signingkey.b64').write_text(base64.b64encode(pathlib.Path('signingkey.jks').read_bytes()).decode())"
```

GitHub **Settings → Secrets and variables → Actions → Secrets**에 등록합니다:

- `SIGNING_KEY`: `signingkey.b64`의 전체 내용.
- `ALIAS`: `toonkor`.
- `KEY_STORE_PASSWORD`: 키 저장소 비밀번호.
- `KEY_PASSWORD`: 해당 키의 비밀번호.

같은 화면의 **Variables**에 `RELEASE_MODE`를 만들고 값을 **`built`**로 설정합니다. 그 후 **Actions → Publish Toonkor → Run workflow → mode: built**로 실행합니다. Keiyoushi 서명에서 개인 서명으로 처음 바꿀 때만 **allow_signer_change**를 켭니다. 이후 `main`에 코드를 올릴 때마다 개인 빌드와 배포가 자동 실행됩니다.

개인 키는 매번 새로 만들지 마세요. 같은 키를 유지해야 후속 APK를 업데이트로 설치할 수 있습니다. 키와 비밀번호를 별도로 백업하고 GitHub 일반 파일에 올리지 마세요. `signingkey.b64`도 비밀 키와 동일합니다.

### 기존 Keiyoushi 설치에서 개인 서명으로 전환

초기 `upstream` 모드는 원본 서명을 그대로 사용합니다. 개인 키로 빌드하면 같은 소스라도 APK 서명이 달라지므로 기존 Keiyoushi APK 위에 덮어 설치할 수 없습니다.

Mihon에서 백업한 뒤 **Toonkor 확장만 제거**하고 개인 저장소의 APK를 다시 설치합니다. Mihon 앱 자체를 삭제하는 작업이 아닙니다. 소스 ID `6596496791271983268`을 유지했으므로 라이브러리 연결을 유지하도록 구성되어 있습니다. 확장 설정은 다시 지정해야 할 수 있습니다. 저장소의 서명 정보가 갱신되지 않으면 개인 저장소를 제거 후 같은 주소로 다시 추가하고 신뢰를 확인합니다.

Keiyoushi 저장소와 개인 저장소에는 동일한 Toonkor 패키지가 있으므로, 이후에는 개인 저장소에서 배포한 APK를 선택해 업데이트하세요. `allow_signer_change`는 Android의 서명 검사를 우회하는 옵션이 아니라 배포 스크립트의 전환 확인입니다.

### 로컬 빌드

원본과 동일하게 JDK 17, Android SDK Platform 37.0, Build Tools 37.0.0, Gradle Wrapper 9.7.1을 사용합니다. AGP 9.4.0 및 Kotlin 2.4.10은 원본 버전 카탈로그에 고정되어 있습니다. Python 3.10 이상도 필요합니다. 처음에는 Maven/Google/JitPack 및 Gradle 다운로드에 인터넷 연결이 필요합니다.

`JAVA_HOME`은 JDK 17, `ANDROID_HOME`은 Android SDK 위치로 설정하고 필요한 SDK 패키지를 설치합니다:

```sh
sdkmanager "platforms;android-37.0" "build-tools;37.0.0"
python -m pip install -r requirements.txt
```

서명 키를 루트에 두고 환경 변수 `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`를 설정한 뒤:

```sh
python scripts/build.py
```

이 스크립트는 Toonkor만 clean/assembleRelease/lintRelease하며, 원본의 Spotless 검사를 실행합니다. 자동 포맷으로 소스를 바꾸지 않습니다. 원본 Gradle이 부수적으로 만드는 JAR는 배포하지 않습니다.

Windows 인덱스 생성 예시(`SDK경로` 및 저장소 이름 교체):

```sh
python scripts/make_repo.py --mode built --repository 내계정/내저장소 --apksigner "SDK경로/build-tools/37.0.0/apksigner.bat" --aapt "SDK경로/build-tools/37.0.0/aapt.exe"
```

Linux/macOS에서는 `.bat`, `.exe`를 제외합니다. 실제 APK 서명을 검증하고 APK 패키지·버전과 Gradle 생성 메타데이터가 일치할 때만 인덱스를 생성합니다. 디버그 서명 APK 배포는 거부합니다. 생성된 `dist/` 내용이 배포 파일이며, 자동 배포는 Actions가 처리합니다.

원본 APK용 인덱스만 로컬에서 다시 만들 때:

```sh
python scripts/make_repo.py --mode upstream --repository 내계정/내저장소
```

동봉된 `dist/`는 주소 형식을 확인하기 위한 **예시**입니다. 내부 `YOUR-USERNAME/toonkor-extension` 주소를 그대로 등록하지 마세요. Actions 또는 위 명령이 실제 주소로 다시 생성합니다.

## 원본 기준과 검증 범위

- 소스 기준 커밋: `a1a1d8ccfb31f3fa9c8cf2fab3e4f1cd67071b1f`.
- 배포 인덱스 기준 커밋: `a7dd81d7705e18f9474b35283e30a10e8959fc8c`.
- 원본 APK: `tachiyomi-ko.toonkor-v1.4.9.apk`, Android versionCode `104009`.
- 원본 APK SHA-256: `a68627e7b1fd868dd1c1a73365ded42160d306f9db2fa1e53d2be06945864b1f`.
- 원본 서명 SHA-256: `9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2`.

최신 AGENTS.md와 CONTRIBUTING.md를 읽고 해당 커밋의 Toonkor 디렉터리를 바이트 그대로 가져왔습니다. 공통 소스도 유지했으며, 개인 저장소용 변경은 모듈 선택 설정과 관리·배포 파일에 한정됩니다. 원본 비교는 `python scripts/verify_upstream.py`로 반복할 수 있습니다. 나중에 의도적으로 소스를 수정하면 이 비교가 실패하는 것이 정상입니다. `built` 모드는 원본 동일성 검사를 강제하지 않습니다.

초기 구성 시 원본 파일 102개의 해시, APK 실제 서명, protobuf 생성·해독을 확인했습니다. 로컬 시험용 Git 저장소에서 최초 배포·동일 파일 재배포·서명 변경 차단도 확인했습니다. **이 PC의 실행 환경에서 Gradle JAR에 대한 AccessDeniedException이 발생해 재컴파일과 release lint는 완료하지 못했습니다.** GitHub Actions 빌드 실행 및 Android/Mihon 실기기 설치·사이트 동작도 아직 검증하지 않았습니다. 동봉 APK는 직접 빌드했다고 주장하는 파일이 아니라 Keiyoushi의 실제 배포 APK입니다. 사이트 동작을 고치거나 보장하는 작업은 포함하지 않았습니다.

업스트림을 자동으로 따라가지는 않습니다. 현재 스냅샷이 독립적으로 유지되며, 앞으로는 이 저장소 소스를 직접 수정해 관리할 수 있습니다. 업스트림을 다시 반영하려면 그 시점의 지침·공통 의존성과 버전도 함께 검토하세요.

원본 출처:

- [Toonkor 소스](https://github.com/keiyoushi/extensions-source/tree/a1a1d8ccfb31f3fa9c8cf2fab3e4f1cd67071b1f/src/ko/toonkor)
- [원본 기여 지침](https://github.com/keiyoushi/extensions-source/blob/a1a1d8ccfb31f3fa9c8cf2fab3e4f1cd67071b1f/CONTRIBUTING.md)
- [원본 배포 인덱스](https://github.com/keiyoushi/extensions/blob/a7dd81d7705e18f9474b35283e30a10e8959fc8c/index.json)

Apache-2.0 라이선스를 유지합니다. 원저작권 및 기여자 권리는 Keiyoushi/Tachiyomi 원본에 있습니다. Keiyoushi 또는 Mihon의 공식 저장소가 아닙니다.
