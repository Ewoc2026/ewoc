package com.example.ergometerapp.baseline

/**
 * Applies the accepted FTP-promotion side effects in one place so menu edits
 * and future baseline runtime code do not drift on persistence ordering.
 */
internal interface BaselineFitnessTestActiveFtpStatePort {
    var ftpWatts: Int
    var ftpInputText: String
    var ftpInputError: String?
}

internal class BaselineFitnessTestResultPromotionCoordinator(
    private val statePort: BaselineFitnessTestActiveFtpStatePort,
    private val saveFtpWatts: (Int) -> Unit,
    private val saveLatestResult: (BaselineFitnessTestResult) -> Unit,
    private val onFtpWattsPromoted: () -> Unit,
    private val refreshAfterPromotion: () -> Unit,
) {
    fun recordResult(result: BaselineFitnessTestResult) {
        if (result.status != BaselineFitnessTestStatus.COMPLETED || result.ftpEstimateWatts == null) {
            saveLatestResult(result)
            return
        }

        val promotedFtpWatts = result.ftpEstimateWatts
        if (promotedFtpWatts !in MIN_PROMOTABLE_FTP_WATTS..MAX_PROMOTABLE_FTP_WATTS) {
            saveLatestResult(result)
            return
        }
        statePort.ftpWatts = promotedFtpWatts
        statePort.ftpInputText = promotedFtpWatts.toString()
        statePort.ftpInputError = null
        saveFtpWatts(promotedFtpWatts)
        saveLatestResult(result)
        onFtpWattsPromoted()
        refreshAfterPromotion()
    }

    internal companion object {
        /** Minimum plausible FTP — values below this indicate a protocol anomaly. */
        const val MIN_PROMOTABLE_FTP_WATTS = 20
        /** Maximum plausible FTP — values above this indicate a protocol anomaly. */
        const val MAX_PROMOTABLE_FTP_WATTS = 999
    }
}
