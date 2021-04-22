package de.rki.coronawarnapp.ui.submission.warnothers

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.asLiveData
import androidx.navigation.NavDirections
import com.google.android.gms.nearby.exposurenotification.TemporaryExposureKey
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import de.rki.coronawarnapp.coronatest.type.CoronaTest
import de.rki.coronawarnapp.datadonation.analytics.modules.keysubmission.AnalyticsKeySubmissionCollector
import de.rki.coronawarnapp.datadonation.analytics.modules.keysubmission.Screen
import de.rki.coronawarnapp.exception.ExceptionCategory
import de.rki.coronawarnapp.exception.reporting.report
import de.rki.coronawarnapp.nearby.ENFClient
import de.rki.coronawarnapp.presencetracing.checkins.CheckInRepository
import de.rki.coronawarnapp.presencetracing.checkins.common.completedCheckIns
import de.rki.coronawarnapp.storage.interoperability.InteroperabilityRepository
import de.rki.coronawarnapp.submission.SubmissionRepository
import de.rki.coronawarnapp.submission.auto.AutoSubmission
import de.rki.coronawarnapp.submission.data.tekhistory.TEKHistoryUpdater
import de.rki.coronawarnapp.util.coroutine.DispatcherProvider
import de.rki.coronawarnapp.util.ui.SingleLiveEvent
import de.rki.coronawarnapp.util.viewmodel.CWAViewModel
import de.rki.coronawarnapp.util.viewmodel.CWAViewModelFactory
import kotlinx.coroutines.flow.first
import timber.log.Timber

class SubmissionResultPositiveOtherWarningNoConsentViewModel @AssistedInject constructor(
    dispatcherProvider: DispatcherProvider,
    private val enfClient: ENFClient,
    private val autoSubmission: AutoSubmission,
    tekHistoryUpdaterFactory: TEKHistoryUpdater.Factory,
    interoperabilityRepository: InteroperabilityRepository,
    private val submissionRepository: SubmissionRepository,
    private val checkInRepository: CheckInRepository,
    private val analyticsKeySubmissionCollector: AnalyticsKeySubmissionCollector
) : CWAViewModel(dispatcherProvider = dispatcherProvider) {
    // TODO Use navargs to supply this
    private val coronaTestType: CoronaTest.Type = CoronaTest.Type.PCR

    init {
        Timber.v("init() coronaTestType=%s", coronaTestType)
    }

    val routeToScreen = SingleLiveEvent<NavDirections>()

    val showKeysRetrievalProgress = SingleLiveEvent<Boolean>()

    val showPermissionRequest = SingleLiveEvent<(Activity) -> Unit>()

    val showEnableTracingEvent = SingleLiveEvent<Unit>()

    val countryList = interoperabilityRepository.countryList
        .asLiveData(context = dispatcherProvider.Default)

    val showTracingConsentDialog = SingleLiveEvent<(Boolean) -> Unit>()

    private val tekHistoryUpdater = tekHistoryUpdaterFactory.create(
        object : TEKHistoryUpdater.Callback {
            override fun onTEKAvailable(teks: List<TemporaryExposureKey>) = launch {
                Timber.tag(TAG).d("onTEKAvailable(tek.size=%d)", teks.size)
                showKeysRetrievalProgress.postValue(false)

                val completedCheckInsExist = checkInRepository.completedCheckIns.first().isNotEmpty()
                val navDirections = if (completedCheckInsExist) {
                    Timber.tag(TAG).d("Navigate to CheckInsConsentFragment")
                    SubmissionResultPositiveOtherWarningNoConsentFragmentDirections
                        .actionSubmissionResultPositiveOtherWarningNoConsentFragmentToCheckInsConsentFragment()
                } else {
                    autoSubmission.updateMode(AutoSubmission.Mode.MONITOR)
                    Timber.tag(TAG).d("Navigate to SubmissionResultReadyFragment")
                    SubmissionResultPositiveOtherWarningNoConsentFragmentDirections
                        .actionSubmissionResultPositiveOtherWarningNoConsentFragmentToSubmissionResultReadyFragment()
                }
                routeToScreen.postValue(navDirections)
            }

            override fun onTEKPermissionDeclined() {
                Timber.tag(TAG).d("onTEKPermissionDeclined")
                showKeysRetrievalProgress.postValue(false)
                // stay on screen
            }

            override fun onTracingConsentRequired(onConsentResult: (given: Boolean) -> Unit) {
                Timber.tag(TAG).d("onTracingConsentRequired")
                showKeysRetrievalProgress.postValue(false)
                showTracingConsentDialog.postValue(onConsentResult)
            }

            override fun onPermissionRequired(permissionRequest: (Activity) -> Unit) {
                Timber.tag(TAG).d("onPermissionRequired")
                showKeysRetrievalProgress.postValue(false)
                showPermissionRequest.postValue(permissionRequest)
            }

            override fun onError(error: Throwable) {
                Timber.tag(TAG).e(error, "Couldn't access temporary exposure key history.")
                showKeysRetrievalProgress.postValue(false)
                error.report(ExceptionCategory.EXPOSURENOTIFICATION, "Failed to obtain TEKs.")
            }
        }
    )

    fun onBackPressed() {
        routeToScreen.postValue(
            SubmissionResultPositiveOtherWarningNoConsentFragmentDirections
                .actionSubmissionResultPositiveOtherWarningNoConsentFragmentToMainFragment()
        )
    }

    fun onConsentButtonClicked() = launch {
        showKeysRetrievalProgress.postValue(true)
        submissionRepository.giveConsentToSubmission(type = coronaTestType)
        launch {
            if (enfClient.isTracingEnabled.first()) {
                Timber.tag(TAG).d("tekHistoryUpdater.updateTEKHistoryOrRequestPermission()")
                tekHistoryUpdater.updateTEKHistoryOrRequestPermission()
            } else {
                Timber.tag(TAG).d("showEnableTracingEvent:Unit")
                showKeysRetrievalProgress.postValue(false)
                showEnableTracingEvent.postValue(Unit)
            }
        }
    }

    fun onDataPrivacyClick() {
        Timber.tag(TAG).d("onDataPrivacyClick")
        routeToScreen.postValue(
            SubmissionResultPositiveOtherWarningNoConsentFragmentDirections
                .actionSubmissionResultPositiveOtherWarningNoConsentFragmentToInformationPrivacyFragment()
        )
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Timber.tag(TAG).d("handleActivityResult($resultCode)")
        showKeysRetrievalProgress.value = true
        tekHistoryUpdater.handleActivityResult(requestCode, resultCode, data)
    }

    fun onResume() {
        analyticsKeySubmissionCollector.reportLastSubmissionFlowScreenPcr(Screen.WARN_OTHERS)
    }

    @AssistedFactory
    interface Factory : CWAViewModelFactory<SubmissionResultPositiveOtherWarningNoConsentViewModel> {
        fun create(): SubmissionResultPositiveOtherWarningNoConsentViewModel
    }

    companion object {
        private const val TAG = "WarnNoConsentViewModel"
    }
}
