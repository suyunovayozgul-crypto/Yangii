package com.signal.iptv

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Bitta toifa va unga tegishli kanallar ro'yxati — rail sifatida ko'rsatiladi. */
data class CategorySection(val title: String, val channels: List<Channel>)

/**
 * OwnTV/Netflix uslubidagi bosh ekran: har toifa — alohida gorizontal rail
 * bo'lgan qatordagi tik ro'yxat. Har bir rail o'z ichki RecyclerView'iga ega
 * (ChannelCardAdapter), shu bois ular bir-biridan mustaqil gorizontal scroll qiladi.
 */
class CategoryRailAdapter(
    private var sections: List<CategorySection>,
    private val selectedUrlProvider: () -> String,
    private val onChannelClick: (Channel) -> Unit,
    private val recycledViewPool: RecyclerView.RecycledViewPool
) : RecyclerView.Adapter<CategoryRailAdapter.RailViewHolder>() {

    inner class RailViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.railTitle)
        val recycler: RecyclerView = view.findViewById(R.id.railRecycler)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RailViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_rail, parent, false)
        val holder = RailViewHolder(view)
        holder.recycler.setRecycledViewPool(recycledViewPool)
        holder.recycler.layoutManager = LinearLayoutManager(
            parent.context, LinearLayoutManager.HORIZONTAL, false
        )
        return holder
    }

    override fun onBindViewHolder(holder: RailViewHolder, position: Int) {
        val section = sections[position]
        holder.title.text = section.title.uppercase()
        holder.recycler.adapter = ChannelCardAdapter(section.channels, selectedUrlProvider, onChannelClick)
    }

    override fun getItemCount(): Int = sections.size

    fun updateData(newSections: List<CategorySection>) {
        sections = newSections
        notifyDataSetChanged()
    }

    /** Barcha ko'rinib turgan rail'larning EPG/tanlangan holatini yangilaydi. */
    fun refreshVisibleRails(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child) as? RailViewHolder ?: continue
            (holder.recycler.adapter as? ChannelCardAdapter)?.refreshEpgAndSelection()
        }
    }
}
