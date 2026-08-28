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

/**
 * Bitta rail (toifa qatori) ichidagi gorizontal kanal kartalari.
 * ChannelAdapter'dan farqli o'laroq — bu tik ro'yxat emas, kichik kvadrat
 * kartalar, OwnTV/Netflix uslubidagi ko'rinish uchun.
 */
class ChannelCardAdapter(
    private var channels: List<Channel>,
    private val selectedUrlProvider: () -> String,
    private val onClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelCardAdapter.CardViewHolder>() {

    inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.cardLogo)
        val name: TextView = view.findViewById(R.id.cardName)
        val program: TextView = view.findViewById(R.id.cardProgram)
        val ring: View = view.findViewById(R.id.cardSelectedRing)
        val root: View = view
    }

    companion object {
        private fun logoHeaders(): LazyHeaders = LazyHeaders.Builder()
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .build()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val ch = channels[position]
        holder.name.text = ch.name

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
            holder.program.text = nowPlaying.title
        } else {
            holder.program.visibility = View.GONE
        }

        holder.ring.visibility = if (ch.url == selectedUrlProvider()) View.VISIBLE else View.GONE
        holder.root.setOnClickListener { onClick(ch) }
    }

    override fun getItemCount(): Int = channels.size

    fun updateData(newChannels: List<Channel>) {
        channels = newChannels
        notifyDataSetChanged()
    }

    fun refreshEpgAndSelection() {
        notifyDataSetChanged()
    }
}
