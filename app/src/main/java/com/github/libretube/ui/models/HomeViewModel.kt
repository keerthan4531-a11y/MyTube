package com.github.libretube.ui.models

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.libretube.api.MediaServiceRepository
import com.github.libretube.api.PlaylistsHelper
import com.github.libretube.api.SubscriptionHelper
import com.github.libretube.api.TrendingCategory
import com.github.libretube.api.obj.Playlists
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.db.DatabaseHelper
import com.github.libretube.db.obj.PlaylistBookmark
import com.github.libretube.enums.WatchHistoryStatus
import com.github.libretube.extensions.runSafely
import com.github.libretube.extensions.toID
import com.github.libretube.extensions.updateIfChanged
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.repo.FeedRefresh
import com.github.libretube.repo.UserDataRepositoryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {
    private val hideWatched
        get() = PreferenceHelper.getBoolean(
            PreferenceKeys.HIDE_WATCHED_FROM_FEED,
            false
        )
    private val showUpcoming
        get() = PreferenceHelper.getBoolean(
            PreferenceKeys.SHOW_UPCOMING_IN_FEED,
            true
        )

    val trending: MutableLiveData<Pair<TrendingCategory, TrendsViewModel.TrendingStreams>> =
        MutableLiveData(null)
    val feed: MutableLiveData<List<StreamItem>> = MutableLiveData(null)
    val bookmarks: MutableLiveData<List<PlaylistBookmark>> = MutableLiveData(null)
    val playlists: MutableLiveData<List<Playlists>> = MutableLiveData(null)
    val continueWatching: MutableLiveData<List<StreamItem>> = MutableLiveData(null)
    val isLoading: MutableLiveData<Boolean> = MutableLiveData(true)
    val loadedSuccessfully: MutableLiveData<Boolean> = MutableLiveData(false)

    private val sections get() = listOf(trending, feed, bookmarks, playlists, continueWatching)

    private var loadHomeJob: Job? = null

    fun loadHomeFeed(
        context: Context,
        subscriptionsViewModel: SubscriptionsViewModel,
        visibleItems: Set<String>,
        onUnusualLoadTime: () -> Unit
    ) {
        isLoading.value = true

        loadHomeJob?.cancel()
        loadHomeJob = viewModelScope.launch {
            val result = async {
                awaitAll(
                    async { if (visibleItems.contains(TRENDING)) loadTrending(context) },
                    async { if (visibleItems.contains(FEATURED)) loadFeed(subscriptionsViewModel) },
                    async { if (visibleItems.contains(BOOKMARKS)) loadBookmarks() },
                    async { if (visibleItems.contains(PLAYLISTS)) loadPlaylists() },
                    async { if (visibleItems.contains(WATCHING)) loadVideosToContinueWatching() }
                )
                loadedSuccessfully.value = sections.any { it.value != null }
                isLoading.value = false
            }

            withContext(Dispatchers.IO) {
                delay(UNUSUAL_LOAD_TIME_MS)
                if (result.isActive) {
                    onUnusualLoadTime.invoke()
                }
            }
        }
    }

    private suspend fun loadTrending(context: Context) {
        val region = PreferenceHelper.getTrendingRegion(context)

        runSafely(
            onSuccess = { videos ->
                trending.updateIfChanged(
                    Pair(
                        TrendingCategory.MUSIC,
                        TrendsViewModel.TrendingStreams(region, videos)
                    )
                )
            },
            ioBlock = {
                generatePersonalizedFeed(region)
            }
        )
    }

    private suspend fun generatePersonalizedFeed(region: String): List<StreamItem> {
        val feedPool = mutableListOf<StreamItem>()

        // 1. Check user watch history for personalized recommendations
        val watchedVideos = runCatching {
            UserDataRepositoryHelper.userDataRepository.getWatchHistory(
                40, null, WatchHistoryStatus.ALL
            ).first.map { it.video }
        }.getOrDefault(emptyList())

        val watchedIds = watchedVideos.mapNotNull { it.url?.toID() }.filter { it.isNotEmpty() }.toSet()

        if (watchedVideos.isNotEmpty()) {
            // Extract at most 2 favorite channels to avoid filling feed with only one creator
            val favoriteChannels = watchedVideos
                .mapNotNull { it.uploaderName }
                .filter { it.isNotBlank() }
                .distinct()
                .take(2)

            for (channel in favoriteChannels) {
                try {
                    val searchResult = MediaServiceRepository.instance.getSearchResults(channel, "videos")
                    val items = searchResult.items.map { it.toStreamItem() }
                        .filter { it.url?.toID() !in watchedIds && !it.isLive }
                        .take(2)
                    feedPool.addAll(items)
                } catch (_: Exception) {}
            }

            // Subscriptions feed if available
            try {
                val subFeed = SubscriptionHelper.getFeed(FeedRefresh.Automatically)
                val subItems = subFeed.filter { it.url?.toID() !in watchedIds && !it.isLive }.take(5)
                feedPool.addAll(subItems)
            } catch (_: Exception) {}
        }

        // 2. Fetch diverse trending categories across Music, Trailers, Gaming, and Podcasts
        val categories = listOf(
            TrendingCategory.MUSIC,
            TrendingCategory.TRAILERS,
            TrendingCategory.GAMING,
            TrendingCategory.PODCASTS
        )
        for (cat in categories) {
            try {
                val catStreams = MediaServiceRepository.instance.getTrending(region, cat)
                feedPool.addAll(catStreams.take(10))
            } catch (_: Exception) {}
        }

        // 3. Strict Deduplication, Title Uniqueness, and Channel Frequency Capping (max 2 per channel)
        val result = mutableListOf<StreamItem>()
        val seenIds = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()
        val uploaderCounts = mutableMapOf<String, Int>()
        var liveCount = 0

        for (item in feedPool) {
            val id = item.url.orEmpty().toID()
            val titleKey = item.title.orEmpty().trim().lowercase().take(30)
            val uploader = item.uploaderName.orEmpty().trim().lowercase()
            val isLive = item.isLive

            if (id.isNotEmpty() && seenIds.contains(id)) continue
            if (titleKey.isNotEmpty() && seenTitles.contains(titleKey)) continue
            if (isLive && liveCount >= 1) continue // max 1 live stream on Home feed

            val currentCount = uploaderCounts.getOrDefault(uploader, 0)
            if (uploader.isNotEmpty() && currentCount >= 2) continue // max 2 videos per creator

            if (id.isNotEmpty()) seenIds.add(id)
            if (titleKey.isNotEmpty()) seenTitles.add(titleKey)
            if (uploader.isNotEmpty()) uploaderCounts[uploader] = currentCount + 1
            if (isLive) liveCount++

            result.add(item)
        }

        return result
    }

    private suspend fun loadFeed(subscriptionsViewModel: SubscriptionsViewModel) {
        runSafely(
            onSuccess = { videos -> feed.updateIfChanged(videos) },
            ioBlock = { tryLoadFeed(subscriptionsViewModel) }
        )
    }

    private suspend fun loadBookmarks() {
        runSafely(
            onSuccess = { newBookmarks -> bookmarks.updateIfChanged(newBookmarks) },
            ioBlock = { UserDataRepositoryHelper.userDataRepository.getPlaylistBookmarks() }
        )
    }

    private suspend fun loadPlaylists() {
        runSafely(
            onSuccess = { newPlaylists -> playlists.updateIfChanged(newPlaylists) },
            ioBlock = { PlaylistsHelper.getPlaylists() }
        )
    }

    private suspend fun loadVideosToContinueWatching() {
        // Disabled per user request: "mela continue watching kaata kudathu"
        continueWatching.updateIfChanged(emptyList())
    }

    private suspend fun tryLoadFeed(subscriptionsViewModel: SubscriptionsViewModel): List<StreamItem> {
        // use cached feed if available, otherwise load feed from API/database
        val feed = subscriptionsViewModel.videoFeed.value ?: run {
            SubscriptionHelper.getFeed(FeedRefresh.Automatically).also {
                subscriptionsViewModel.videoFeed.postValue(it)
            }
        }

        return DatabaseHelper.filterByStreamTypeAndWatchPosition(feed, hideWatched, showUpcoming)
    }

    companion object {
        private const val UNUSUAL_LOAD_TIME_MS = 10000L
        private const val FEATURED = "featured"
        private const val WATCHING = "watching"
        private const val TRENDING = "trending"
        private const val BOOKMARKS = "bookmarks"
        private const val PLAYLISTS = "playlists"
    }
}
