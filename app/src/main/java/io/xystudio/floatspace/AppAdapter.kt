package io.xystudio.floatspace

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onAppClick: (AppEntry) -> Unit,
    private val onFavorite: (AppEntry) -> Boolean
) : RecyclerView.Adapter<AppAdapter.Holder>() {
    private val all = mutableListOf<AppEntry>()
    private val shown = mutableListOf<AppEntry>()
    private var favorites: Set<String> = emptySet()

    fun submit(items: List<AppEntry>, favoriteSet: Set<String>) {
        all.clear(); all.addAll(items); shown.clear(); shown.addAll(items); favorites = favoriteSet; notifyDataSetChanged()
    }
    fun refreshFavorites(set: Set<String>) { favorites = set; notifyDataSetChanged() }
    fun filter(query: String) { shown.clear(); shown.addAll(if(query.isBlank()) all else all.filter{it.label.contains(query,true)||it.packageName.contains(query,true)});notifyDataSetChanged() }
    private fun dp(parent:ViewGroup,v:Int)=(v*parent.resources.displayMetrics.density).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val root=LinearLayout(parent.context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(parent,12),dp(parent,7),dp(parent,8),dp(parent,7));background=parent.context.getDrawable(R.drawable.bg_app_item);layoutParams=ViewGroup.MarginLayoutParams(-1,dp(parent,62)).apply{setMargins(0,0,0,dp(parent,7))}}
        val icon=ImageView(parent.context).apply{setPadding(dp(parent,6),dp(parent,6),dp(parent,6),dp(parent,6));background=GradientDrawable().apply{setColor(Color.rgb(31,31,31));cornerRadius=dp(parent,9).toFloat()};layoutParams=LinearLayout.LayoutParams(dp(parent,43),dp(parent,43))}
        val labels=LinearLayout(parent.context).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(parent,11)}}
        val title=TextView(parent.context).apply{setTextColor(Color.WHITE);textSize=13f}
        val pkg=TextView(parent.context).apply{setTextColor(Color.rgb(145,145,145));textSize=9f;maxLines=1}
        labels.addView(title);labels.addView(pkg)
        val star=Button(parent.context).apply{text="☆";textSize=17f;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.rgb(27,27,27));cornerRadius=dp(parent,8).toFloat();setStroke(dp(parent,1),Color.rgb(65,65,65))};layoutParams=LinearLayout.LayoutParams(dp(parent,43),dp(parent,40))}
        root.addView(icon);root.addView(labels);root.addView(star)
        return Holder(root,icon,title,pkg,star)
    }
    override fun onBindViewHolder(h:Holder,p:Int){val item=shown[p];h.icon.setImageDrawable(item.icon);h.title.text=item.label;h.pkg.text=item.packageName;h.star.text=if(item.component in favorites)"★" else "☆";h.itemView.setOnClickListener{onAppClick(item)};h.star.setOnClickListener{onFavorite(item);notifyItemChanged(p)}}
    override fun getItemCount()=shown.size
    class Holder(root:LinearLayout,val icon:ImageView,val title:TextView,val pkg:TextView,val star:Button):RecyclerView.ViewHolder(root)
}
