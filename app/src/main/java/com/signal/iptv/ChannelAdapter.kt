package com.signal.iptv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions

class ChannelAdapter(
    private var channels: List<Channel>,
    private val onClick: (Channel, Int) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val idx: TextView = view.findViewById(R.id.chIdx)
        val logo: ImageView = view.findViewById(R.id.chLogo)
        val name: TextView = view.findViewById(R.id.chName)
        val group: TextView = view.findViewById(R.id.chGroup)
        val program: TextView = view.findViewById(R.id.chProgram)
        val progressTrack: View = view.findViewById(R.id.chProgressTrack)
        val progressFill: View = view.findViewById(R.id.chProgressFill)
        val selectedDot: View = view.findViewById(R.id.chSelectedDot)
        val root: View = view
    }

    companion object {
        // Ba'zi logotip serverlari header'siz so'rovlarni 403 bilan rad etadi —
        // brauzerga o'xshash User-Agent/Referer bilan so'raymiz.
        private fun logoHeaders(): LazyHeaders = LazyHeaders.Builder()
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .build()

        // Logotip endi to'rtburchak emas, yumaloq burchakli ko'rsatiladi —
        // qattiq to'g'ri burchakli rasm o'rniga zamonaviy "kartochka" hissi beradi.
        private fun logoOptions(context: android.content.Context): RequestOptions {
            val radiusPx = (8 * context.resources.displayMetrics.density).toInt()
            return RequestOptions.bitmapTransform(CenterCrop().let { RoundedCorners(radiusPx) })
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val ch = channels[position]
        holder.idx.text = (position + 1).toString()
        holder.name.text = ch.name
        holder.group.text = ch.group

        if (ch.logo.isNotEmpty()) {
            Glide.with(holder.logo.context)
                .load(GlideUrl(ch.logo, logoHeaders()))
                .apply(logoOptions(holder.logo.context))
                .placeholder(R.drawable.ic_tv_placeholder)
                .error(R.drawable.ic_tv_placeholder)
                .into(holder.logo)
        } else {
            holder.logo.setImageResource(R.drawable.ic_tv_placeholder)
        }

        // "Hozir: <dastur nomi>" yozuvi ostida shu dasturning necha foizi
        // o'tganini ko'rsatuvchi ingichka chiziq — EPG haqiqatan ham
        // ishlayotganini ko'zga yaqqol ko'rsatib beradi.
        val nowPlaying = EpgRepository.currentProgramme(ch.tvgId)
        if (nowPlaying != null && nowPlaying.title.isNotBlank()) {
            holder.program.visibility = View.VISIBLE
            holder.program.text = holder.program.context.getString(R.string.epg_now_prefix, nowPlaying.title)

            val total = (nowPlaying.stop - nowPlaying.start).coerceAtLeast(1L)
            val elapsed = (System.currentTimeMillis() - nowPlaying.start).coerceIn(0L, total)
            val fraction = elapsed.toFloat() / total.toFloat()
            holder.progressTrack.visibility = View.VISIBLE
            holder.progressTrack.post {
                val trackWidth = holder.progressTrack.width
                val params = holder.progressFill.layoutParams
                params.width = (trackWidth * fraction).toInt().coerceAtLeast(0)
                holder.progressFill.layoutParams = params
            }
        } else {
            holder.program.visibility = View.GONE
            holder.progressTrack.visibility = View.GONE
        }

        holder.root.isSelected = position == selectedPosition
        holder.selectedDot.visibility = if (position == selectedPosition) View.VISIBLE else View.GONE
        holder.root.setOnClickListener {
            val previous = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previous)
            notifyItemChanged(selectedPosition)
            onClick(ch, position)
        }
    }

    override fun getItemCount(): Int = channels.size

    fun updateData(newChannels: List<Channel>) {
        channels = newChannels
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    /** Call after EPG data (re)loads, or periodically, to refresh the "Hozir: ..." rows. */
    fun refreshEpgRows() {
        notifyDataSetChanged()
    }

    /** Highlights the row whose stream URL matches [url] (e.g. the channel currently playing). */
    fun markSelectedByUrl(url: String) {
        val index = channels.indexOfFirst { it.url == url }
        val previous = selectedPosition
        selectedPosition = index
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)
    }

    fun clearSelection() {
        val previous = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
    }
}
