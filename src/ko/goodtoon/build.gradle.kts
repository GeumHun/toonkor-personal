import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goodtoon 웹툰"
    pkgName = "ko.goodtoonwebtoontest"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Goodtoon 웹툰 (Android)"
        id = 760550510744678728L
        lang = "ko"
        baseUrl {
            custom("https://www.goodtoon004.com")
        }
    }
}
