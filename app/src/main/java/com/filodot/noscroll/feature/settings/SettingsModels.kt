package com.filodot.noscroll.feature.settings

enum class SystemAccessUiStatus {
    ENABLED,
    NOT_ENABLED,
    SKIPPED,
}

enum class DetectorUiStatus {
    READY,
    INACTIVE,
    UNKNOWN_LAYOUT,
    ERROR,
}

enum class DiagnosticResultCode {
    SHORTS_CONFIRMED,
    NON_SHORTS_CONFIRMED,
    UNKNOWN,
    ACCESS_UNAVAILABLE,
}

data class RedactedDiagnosticsUiState(
    val detectorStatus: DetectorUiStatus,
    val lastRecognitionLabel: String? = null,
    val lastResultCode: DiagnosticResultCode? = null,
    val unknownCount: Int = 0,
    val rulesVersion: Int = 1,
)

data class SettingsUiState(
    val accessibilityStatus: SystemAccessUiStatus,
    val usageAccessStatus: SystemAccessUiStatus,
    val youtubeVersionLabel: String? = null,
    val instagramVersionLabel: String? = null,
    val monitoringHealthLabel: String = "Не подключён",
    val monitoringHealthy: Boolean = false,
    val lastHeartbeatLabel: String? = null,
    val lastInstagramEventLabel: String? = null,
    val recoveryCount: Int = 0,
    val lastFailureCode: String? = null,
    val diagnostics: RedactedDiagnosticsUiState,
    val appVersionLabel: String,
)

sealed interface SettingsAction {
    data object OpenAccessibilitySettings : SettingsAction
    data object OpenUsageAccessSettings : SettingsAction
    data object RefreshSystemAccess : SettingsAction
    data object OpenAppDetailsSettings : SettingsAction
    data object OpenEmergencyHistory : SettingsAction
    data object OpenPrivacyDocument : SettingsAction
    data object OpenLicenses : SettingsAction
}
