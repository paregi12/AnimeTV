package com.paregi.animetv

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.paregi.animetv.domain.Anime
import com.paregi.animetv.domain.HomeFeed
import com.paregi.animetv.domain.isAllowedForParentalRating
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Refreshes Android TV preview channels hourly, matching the original launcher feed cadence. */
class TvChannelRefreshJobService : JobService() {
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runningJob = scope.launch {
            var completed = false
            try {
                val networkStack = AndroidNetworkStack(applicationContext)
                try {
                    val profileNamespace = ProfileNamespace()
                    val profileDirectory = AndroidProfileRepository(applicationContext, profileNamespace).loadProfiles()
                    val defaultProfile = profileDirectory.profiles.firstOrNull {
                        it.prefix == profileDirectory.defaultPrefix
                    } ?: profileDirectory.profiles.firstOrNull()
                    defaultProfile?.let { profileNamespace.activate(it.prefix) }
                    val settings = AndroidSettingsRepository(
                        applicationContext,
                        networkStack,
                        profileNamespace
                    ).readSettings()
                    val streamProxy = AndroidStreamProxy(networkStack)
                    try {
                        val translationRepository = AndroidTranslationRepository(applicationContext, networkStack)
                        val animeRepository = AndroidAnimeRepository(streamProxy, networkStack, translationRepository)
                        animeRepository.selectSource(settings.selectedSource)
                        val feed = animeRepository.loadAndroidTvHome()
                            .filteredForProfileRating(defaultProfile?.parentalRating ?: 6)
                        AndroidTvIntegrationRepository(applicationContext).updateHomeChannels(feed)
                        completed = true
                    } finally {
                        streamProxy.close()
                    }
                } finally {
                    networkStack.close()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not refresh Android TV channels", error)
            } finally {
                if (currentCoroutineContext().isActive) {
                    Handler(Looper.getMainLooper()).post {
                        jobFinished(params, !completed)
                        runningJob = null
                        scope.cancel()
                        if (completed) schedule(applicationContext)
                    }
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = null
        return true
    }

    companion object {
        private const val JOB_ID = 66_607
        private const val REFRESH_DELAY_MS = 60L * 60L * 1_000L
        private const val REFRESH_DEADLINE_MS = 63L * 60L * 1_000L
        private const val INITIAL_REFRESH_DEADLINE_MS = 60L * 1_000L
        private const val TAG = "ParegiTvChannels"

        fun schedule(context: Context) {
            schedule(context, REFRESH_DELAY_MS, REFRESH_DEADLINE_MS)
        }

        fun refreshNow(context: Context) {
            schedule(context, minimumLatencyMs = 0L, overrideDeadlineMs = INITIAL_REFRESH_DEADLINE_MS)
        }

        private fun schedule(context: Context, minimumLatencyMs: Long, overrideDeadlineMs: Long) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                !context.packageManager.hasSystemFeature("android.software.leanback")
            ) return
            val component = ComponentName(context, TvChannelRefreshJobService::class.java)
            val job = JobInfo.Builder(JOB_ID, component)
                .setMinimumLatency(minimumLatencyMs)
                .setOverrideDeadline(overrideDeadlineMs)
                .setPersisted(true)
                .build()
            runCatching {
                (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler)
                    ?.schedule(job)
            }
        }
    }
}

private fun Anime.allowedForProfileRating(rating: Int): Boolean = isAllowedForParentalRating(rating)

private fun HomeFeed.filteredForProfileRating(rating: Int): HomeFeed = copy(
    featured = featured.filter { it.allowedForProfileRating(rating) },
    continueWatching = continueWatching.filter { it.allowedForProfileRating(rating) },
    sections = sections.map { section ->
        section.copy(items = section.items.filter { it.allowedForProfileRating(rating) })
    }
)
