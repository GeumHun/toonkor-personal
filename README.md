# 개인 Mihon 확장 저장소

이 저장소는 Android Mihon용 개인 확장 네 개만 배포합니다.

- Toonkor — `eu.kanade.tachiyomi.extension.ko.toonkor`, Source ID `6596496791271983268`, 버전 `1.4.17`
- Goodtoon 웹툰 (Android) — `eu.kanade.tachiyomi.extension.ko.goodtoonwebtoontest`, Source ID `760550510744678728`, 버전 `1.4.9`
- 11toon 만화 — `eu.kanade.tachiyomi.extension.ko.toon11`, Source ID `8796296375202334266`, 버전 `1.4.29`
- Blacktoon 웹툰 — `eu.kanade.tachiyomi.extension.ko.blacktoon`, Source ID `7080800841003944426`, 버전 `1.4.33`

Mihon 등록 주소:

```text
https://raw.githubusercontent.com/GeumHun/toonkor-personal/repo/index.pb
```

`main`에는 네 확장의 소스와 빌드·배포 설정이 있습니다. `repo` 브랜치는 GitHub Actions가 생성하며, 네 APK·아이콘·`index.pb`·`index.json`만 배포합니다. 개인 서명키는 GitHub Actions Secrets(`SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`)로만 사용하며 저장소에 커밋하지 않습니다.

## 로컬 빌드

JDK 17, Android SDK, Python 3.10 이상을 준비한 뒤 필요한 모듈을 빌드합니다.

```sh
./gradlew :src:ko:goodtoon:spotlessApply
./gradlew :src:ko:blacktoon:spotlessApply
./gradlew :src:ko:goodtoon:lintRelease :src:ko:goodtoon:assembleRelease
./gradlew :src:ko:blacktoon:lintRelease :src:ko:blacktoon:assembleRelease
```

모든 확장의 package 및 Source ID는 라이브러리 연결을 위해 변경하지 않습니다.
