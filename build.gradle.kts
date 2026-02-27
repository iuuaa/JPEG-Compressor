import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.dokka) apply false
}

// Load sensitive publish/signing config from local.properties (not committed to Git)
// Supported keys: mavenCentralUsername / mavenCentralPassword /
// signingInMemoryKey / signingInMemoryKeyPassword / signing.key / signing.password
val extraProps: Map<String, String> = run {
    val file = rootDir.resolve("local.properties")
    if (!file.exists()) {
        emptyMap()
    } else {
        Properties().apply { file.inputStream().use { load(it) } }
            .entries
            .associate { (k, v) -> k.toString() to v.toString() }
    }
}

subprojects {
    extraProps.forEach { (key, value) ->
        // Skip sdk.dir (handled by Android) and already-defined properties
        if (key != "sdk.dir" && findProperty(key) == null && value.isNotBlank()) {
            extensions.extraProperties.set(key, value)
        }
    }
}