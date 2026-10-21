package com.github.libretube.ui.fragments

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.R
import com.github.libretube.api.MediaServiceRepository
import com.github.libretube.api.TrendingCategory
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.databinding.FragmentShortsBinding
import com.github.libretube.extensions.toID
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.adapters.ShortsAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShortsFragment : Fragment(R.layout.fragment_shorts) {
    private var _binding: FragmentShortsBinding? = null
    private val binding get() = _binding!!

    private lateinit var shortsAdapter: ShortsAdapter
    private var isFetchingMore = false
    private val searchQueries = listOf(
        "#shorts",
        "trending shorts",
        "viral shorts",
        "shorts funny",
        "shorts comedy",
        "shorts entertainment",
        "shorts music"
    )
    private var queryIndex = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentShortsBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)

        binding.shortsTopBarGlass.enableDynamicBackground = true

        shortsAdapter = ShortsAdapter(requireContext()) { streamItem -> }
        binding.shortsViewPager.adapter = shortsAdapter
        binding.shortsViewPager.orientation = ViewPager2.ORIENTATION_VERTICAL

        binding.shortsViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                playShortAt(position)

                // When user reaches near the end of loaded shorts, automatically load the next batch!
                if (position >= shortsAdapter.itemCount - 3 && !isFetchingMore) {
                    loadMoreShorts()
                }
            }
        })

        loadInitialShorts()
    }

    private fun playShortAt(position: Int) {
        val recyclerView = binding.shortsViewPager.getChildAt(0) as? RecyclerView ?: return
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position) as? ShortsAdapter.ShortsViewHolder
        shortsAdapter.activeViewHolder?.pausePlayer()
        viewHolder?.playShort()
    }

    private fun loadInitialShorts() {
        binding.shortsProgressBar.isVisible = true

        lifecycleScope.launch(Dispatchers.IO) {
            val shortsList = mutableListOf<StreamItem>()

            // 1. Fetch viral shorts from #shorts
            try {
                val searchResult = MediaServiceRepository.instance.getSearchResults("#shorts", "videos")
                val searchStreams = searchResult.items.map { it.toStreamItem() }
                    .filter { (it.duration ?: 0L) in 1..90 || it.isShort }
                shortsList.addAll(searchStreams)
            } catch (_: Exception) {}

            // 2. Fetch trending shorts from trending shorts query
            try {
                val searchResult = MediaServiceRepository.instance.getSearchResults("trending shorts", "videos")
                val searchStreams = searchResult.items.map { it.toStreamItem() }
                    .filter { (it.duration ?: 0L) in 1..90 || it.isShort }
                shortsList.addAll(searchStreams)
            } catch (_: Exception) {}

            // 3. Complement with trending short clips
            try {
                val region = PreferenceHelper.getTrendingRegion(requireContext())
                val trendingStreams = MediaServiceRepository.instance.getTrending(region, TrendingCategory.MUSIC)
                val shortVideos = trendingStreams.filter { (it.duration ?: 0L) in 1..90 || it.isShort }
                shortsList.addAll(shortVideos)
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                _binding?.shortsProgressBar?.isVisible = false
                val uniqueShorts = shortsList.distinctBy { it.url.orEmpty().toID() }
                if (uniqueShorts.isNotEmpty()) {
                    shortsAdapter.submitList(uniqueShorts)
                    binding.shortsViewPager.post {
                        playShortAt(0)
                    }
                }
            }
        }
    }

    private fun loadMoreShorts() {
        if (isFetchingMore) return
        isFetchingMore = true

        lifecycleScope.launch(Dispatchers.IO) {
            val query = searchQueries[queryIndex % searchQueries.size]
            queryIndex++

            val newShorts = mutableListOf<StreamItem>()
            try {
                val searchResult = MediaServiceRepository.instance.getSearchResults(query, "videos")
                val searchStreams = searchResult.items.map { it.toStreamItem() }
                    .filter { (it.duration ?: 0L) in 1..90 || it.isShort }
                newShorts.addAll(searchStreams)
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                isFetchingMore = false
                val existingIds = shortsAdapter.items.map { it.url.orEmpty().toID() }.toSet()
                val freshShorts = newShorts.filter { it.url.orEmpty().toID() !in existingIds }
                if (freshShorts.isNotEmpty()) {
                    shortsAdapter.submitList(freshShorts)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        shortsAdapter.activeViewHolder?.playShort()
    }

    override fun onPause() {
        super.onPause()
        shortsAdapter.activeViewHolder?.pausePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shortsAdapter.releaseAll()
        _binding = null
    }
}
