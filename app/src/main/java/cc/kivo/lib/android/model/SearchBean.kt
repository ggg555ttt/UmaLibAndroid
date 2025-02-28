package cc.kivo.lib.android.model

import cc.kivo.lib.android.model.db.Tag
import java.io.Serializable

data class SearchBean(
    var keyword: String? = "",
    var tags: MutableSet<Tag> = mutableSetOf(),
    var creator: String? = "",
    var exceptedTags: MutableSet<Tag> = mutableSetOf(),
    var isRandom: Boolean = false
) : Serializable
