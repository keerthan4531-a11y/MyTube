package com.github.libretube.ui.adapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.R
import com.github.libretube.api.MediaServiceRepository
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.databinding.ShortsItemBinding
import com.github.libretube.extensions.toID
import com.github.libretube.extensions.formatShort
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.NavigationHelper
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.parcelable.PlayerData
import com.github.libretube.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShortsAdapter(
    private val context: Context,
    private val onVideoClick: (StreamItem) -> Unit
) : RecyclerView.Adapter<ShortsAdapter.ShortsViewHolder>() {

    val items = mutableListOf<StreamItem>()
    var activeViewHolder: ShortsViewHolder? = null

    fun submitList(newItems: List<StreamItem>) {
        val start = items.size
        val unique = newItems.filter { newItem ->
            val id = newItem.url.orEmpty().toID()
            id.isNotEmpty() && items.none { it.url.orEmpty().toID() == id }
        }
        if (unique.isNotEmpty()) {
            items.addAll(unique)
            notifyItemRangeInserted(start, unique.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortsViewHolder {
        val binding = ShortsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortsViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewDetachedFromWindow(holder: ShortsViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.pausePlayer()
    }

    fun releaseAll() {
        activeViewHolder?.releasePlayer()
        activeViewHolder = null
    }

    override fun getItemCount() = items.size

    inner class ShortsViewHolder(val binding: ShortsItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var player: ExoPlayer? = null
        private var streamItem: StreamItem? = null
        private var loadJob: Job? = null

        fun bind(item: StreamItem) {
            streamItem = item
            val videoId = item.url.orEmpty().toID()

            binding.shortTitle.text = item.title
            binding.shortChannelName.text = item.uploaderName ?: "Creator"
            binding.shortLikeCount.text = item.views.formatShort()

            ImageHelper.loadImage(item.thumbnail, binding.shortThumbnail)
            if (item.uploaderAvatar != null) {
                ImageHelper.loadImage(item.uploaderAvatar, binding.shortChannelAvatar, true)
            } else {
                binding.shortChannelAvatar.setImageResource(R.drawable.ic_user_avatar)
            }

            binding.shortChannelAvatar.setOnClickListener {
                item.uploaderUrl?.let { url -> NavigationHelper.navigateChannel(context, url) }
            }
            binding.shortChannelName.setOnClickListener {
                item.uploaderUrl?.let { url -> NavigationHelper.navigateChannel(context, url) }
            }

            binding.shortShareBtn.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://youtu.be/$videoId")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Short"))
            }

            binding.shortMoreBtn.setOnClickListener {
                NavigationHelper.navigateVideo(context, PlayerData(videoId))
            }

            binding.root.setOnClickListener {
                togglePlayPause()
            }

            binding.shortLikeBtn.setOnClickListener {
                binding.shortLikeBtn.isSelected = !binding.shortLikeBtn.isSelected
                binding.shortLikeBtn.setColorFilter(
                    if (binding.shortLikeBtn.isSelected) 0xFFFF0033.toInt() else 0xFFFFFFFF.toInt()
                )
            }

            binding.shortSubscribeGlass.setOnClickListener {
                val text = binding.shortSubscribeText
                if (text.text == "Subscribe") {
                    text.text = "Subscribed"
                    text.setBackgroundColor(0xFF333333.toInt())
                } else {
                    text.text = "Subscribe"
                    text.setBackgroundColor(0xFFFF0000.toInt())
                }
            }
        }

        fun playShort() {
            val item = streamItem ?: return
            val videoId = item.url.orEmpty().toID()
            if (videoId.isEmpty()) return

            activeViewHolder = this
            if (player == null) {
                player = ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = true
                }
                binding.shortPlayerView.player = player
            }

            loadJob?.cancel()
            loadJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val streams = MediaServiceRepository.instance.getStreams(videoId)
                    val dashUri: Uri? = runCatching { PlayerHelper.createDashSource(streams, context) }.getOrNull()

                    withContext(Dispatchers.Main) {
                        if (dashUri != null) {
                            val mediaItem = MediaItem.Builder()
                                .setUri(dashUri)
                                .setMimeType(MimeTypes.APPLICATION_MPD)
                                .build()
                            player?.setMediaItem(mediaItem)
                        } else if (!streams.hls.isNullOrEmpty()) {
                            val mediaItem = MediaItem.Builder()
                                .setUri(streams.hls)
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build()
                            player?.setMediaItem(mediaItem)
                        } else {
                            val progressiveStream = streams.videoStreams.firstOrNull { it.videoOnly != true && !it.url.isNullOrEmpty() }
                                ?: streams.videoStreams.firstOrNull { !it.url.isNullOrEmpty() }
                            progressiveStream?.url?.let {
                                player?.setMediaItem(MediaItem.fromUri(it))
                            }
                        }
                        player?.prepare()
                        player?.play()
                        binding.shortThumbnail.isVisible = false
                    }
                } catch (_: Exception) {
                    // keep showing thumbnail
                }
            }
        }

        private fun togglePlayPause() {
            player?.let { p ->
                if (p.isPlaying) {
                    p.pause()
                    binding.shortPausePlayIcon.setImageResource(R.drawable.ic_pause_filled)
                    showIconFlash()
                } else {
                    p.play()
                    binding.shortPausePlayIcon.setImageResource(R.drawable.ic_play_filled)
                    showIconFlash()
                }
            }
        }

        private fun showIconFlash() {
            binding.shortPausePlayIcon.alpha = 1f
            binding.shortPausePlayIcon.isVisible = true
            binding.shortPausePlayIcon.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction { binding.shortPausePlayIcon.isVisible = false }
                .start()
        }

        fun pausePlayer() {
            loadJob?.cancel()
            player?.pause()
        }

        fun releasePlayer() {
            loadJob?.cancel()
            player?.release()
            player = null
            binding.shortPlayerView.player = null
            binding.shortThumbnail.isVisible = true
        }
    }
}
