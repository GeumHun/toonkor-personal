import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Blacktoon 웹툰"
    pkgName = "ko.blacktoon"
    versionCode = 33
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "Blacktoon 웹툰"
        id = 7080800841003944426L
        lang = "ko"
        baseUrl {
            custom("https://blacktoon423.com")
        }
    }
}
