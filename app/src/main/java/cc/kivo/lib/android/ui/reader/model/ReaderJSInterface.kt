package cc.kivo.lib.android.ui.reader.model

import android.text.TextUtils
import android.util.Log
import cc.kivo.lib.android.R
import cc.kivo.lib.android.model.db.Article
import cc.kivo.lib.android.util.ReaderSettingUtil
import cc.kivo.lib.android.util.SettingUtil
import com.itheima.view.BridgeWebView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class ReaderJSInterface(
    private val webView: BridgeWebView,
    private val article: Article
) {

    companion object {
        val FORMATTER = SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.CHINA
        )

        fun initiativeStyle(webView: BridgeWebView) {
            val callback = "renderAct"

            val json = JSONObject()
            json.put("setting", ReaderSettingUtil.getSetting("default"))

            if (TextUtils.isEmpty(callback)) return
            //调用js方法必须在主线程
            webView.post { webView.loadUrl("javascript:$callback($json)") }
        }
    }

    // 以此格式写方法
    fun getArticle(str: Array<String>) {
        Log.d(this.javaClass.simpleName, str[0])
        //解析js callback方法
        val mJson = JSONObject(str[0])
        val callback = mJson.optString("callback") //解析js回调方法

        val json = JSONObject()
        json.put("name", article.name)
        json.put("source", article.source)
        json.put("note", article.note)
        json.put("translator", article.translator)
        json.put("author", article.author)

        var content = article.content
        val regex = Regex("<p>\\[[^]]+][^<]*</p>\$")
        val elTagRegex = Regex("<[^>]*>")
        val dict = mutableMapOf<String, String>()
        var result: MatchResult?
        do {
            result = regex.find(content)
            if (null != result) {
                content = content.substring(0, result.range.first)
                val annotationArr =
                    result.value.replace(elTagRegex, "").split(']')
                val value = annotationArr[annotationArr.size - 1]
                annotationArr
                    .filterIndexed { i, _ -> i != annotationArr.size - 1 }
                    .forEach { key ->
                        dict[key.substring(1)] = value
                    }
            }
        } while (null != result)
        if (dict.isNotEmpty()) {
            val emptyElRegex = Regex("<p>\\s*<br\\s*/?\\s*>\\s*</p>")
            content = content.replace(emptyElRegex, "")
            dict.forEach { (key, value) ->
                content = content.replace(
                    "[$key]",
                    " <span onclick='if(this.className){this.className=\"\"}" +
                            "else{this.className=\"key\"}'>[<a href='javascript:void(0)'>" +
                            "$key</a>]</span><span class='annotation'>$value</span> "
                )
            }
        }
        json.put("content", content)


        val tagList = JSONArray(article.taggedList.sortedWith { a, b ->
            if (a.tag.type == b.tag.type)
                a.tag.name.compareTo(b.tag.name)
            else
                b.tag.type.compareTo(a.tag.type)
        }.map { tagged ->
            val tagJson = JSONObject()
            tagJson.put("name", tagged.tag.name)
            tagJson.put("type", tagged.tag.type)
            tagJson
        })
        json.put("tags", tagList)
        json.put(
            "time",
            FORMATTER.format(article.uploadTime.toLong() * 1000)
        )
        json.put("setting", ReaderSettingUtil.getSetting("default"))
        Log.d(this.javaClass.simpleName, SettingUtil.getTheme().toString())
        json.getJSONObject("setting").put(
            "theme", when (SettingUtil.getTheme()) {
                R.style.Theme_UmaLibrary_NGA -> "nga"
                R.style.Theme_UmaLibrary_WHITE -> "white"
                R.style.Theme_UmaLibrary_TEAL -> "cyan"
                else -> "purple"
            }
        )
        Log.d(this.javaClass.simpleName, json.toString())

        invokeJavaScript(callback, json.toString())
    }

    /**
     * 统一管理所有android调用js方法
     *
     * @param callback js回调方法名
     * @param json     传递json数据
     */
    private fun invokeJavaScript(callback: String, json: String) {
        Log.d(this.javaClass.simpleName, "callbackName: $callback  data: $json")
        if (TextUtils.isEmpty(callback)) return
        //调用js方法必须在主线程
        webView.post { webView.loadUrl("javascript:$callback($json)") }
    }
}