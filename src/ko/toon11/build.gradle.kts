import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "11toon"
    pkgName = "ko.toon11"
    versionCode = 29
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "11toon 만화"
        id = 8796296375202334266L
        lang = "ko"
        baseUrl {
            custom("https://11toon.com")
        }
    }
}
