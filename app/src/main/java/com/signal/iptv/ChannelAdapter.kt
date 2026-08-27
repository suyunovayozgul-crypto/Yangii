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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val ch = channels[position]
        holder.idx.text = String.format("%03d", position + 1)
        holder.name.text = ch.name
        holder.group.text = ch.group

        if (ch.logo.isNotEmpty()) {
            Glide.with(holder.logo.context)
                .load(GlideUrl(ch.logo, logoHeaders()))
                .placeholder(R.drawable.ic_tv_placeholder)
                .error(R.drawable.ic_tv_placeholder)
                .into(holder.logo)
        } else {
            holder.logo.setImageResource(R.drawable.ic_tv_placeholder)
        }

        val nowPlaying = EpgRepository.currentProgramme(ch.tvgId)
        if (nowPlaying != null && nowPlaying.title.isNotBlank()) {
            holder.program.visibility = View.VISIBLE
            holder.program.text = holder.program.context.getString(R.string.epg_now_prefix, nowPlaying.title)
        } else {
            holder.program.visibility = View.GONE
        }

        holder.root.isSelected = position == selectedPosition
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

    fun clearSelection() {
        val previous = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
    }
}
