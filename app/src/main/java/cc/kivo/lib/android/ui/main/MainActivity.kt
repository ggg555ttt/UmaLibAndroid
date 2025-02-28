package cc.kivo.lib.android.ui.main

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import cc.kivo.lib.android.R
import cc.kivo.lib.android.databinding.ActivityMainBinding
import cc.kivo.lib.android.model.DataBaseHandler
import cc.kivo.lib.android.model.MyBaseActivity
import cc.kivo.lib.android.model.SearchBean
import cc.kivo.lib.android.model.db.ArtCreatorDao
import cc.kivo.lib.android.model.db.DaoSession
import cc.kivo.lib.android.model.db.Tag
import cc.kivo.lib.android.model.db.TagDao
import cc.kivo.lib.android.ui.MainIntroActivity
import cc.kivo.lib.android.ui.main.model.CreatorSuggestionAdapter
import cc.kivo.lib.android.ui.main.model.TagSelectedItem
import cc.kivo.lib.android.ui.main.model.TagSuggestionAdapter
import cc.kivo.lib.android.ui.setting.SettingActivity
import cc.kivo.lib.android.util.FavoriteArticleUtil
import cc.kivo.lib.android.util.SettingUtil
import com.angcyo.dsladapter.DslAdapter
import com.ferfalk.simplesearchview.SimpleSearchView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.liangguo.androidkit.app.ToastUtil
import com.liangguo.androidkit.app.startNewActivity
import kotlinx.coroutines.launch
import org.greenrobot.greendao.query.QueryBuilder
import java.io.FileNotFoundException
import kotlin.system.exitProcess


@SuppressLint("InflateParams")
class MainActivity : MyBaseActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var _mViewModel: MainViewModel? = null
    val mViewModel get() = _mViewModel!!
    private var daoSession: DaoSession? = null

    // 是否可以返回
    private var canPressBack = false

    //是否退出的flag
    private var isExit = false
    private val mHandler = Handler(Looper.getMainLooper()) {
        isExit = false
        false
    }

    private var creatorList = mutableSetOf<String>()
    private var tagList = mutableSetOf<Tag>()

    /**
     * 搜索选项dialog
     */
    private var searchFilterView: View? = null
    private var tagTextView: MaterialAutoCompleteTextView? = null
    private var tagExceptTextView: MaterialAutoCompleteTextView? = null
    private var creatorTextView: MaterialAutoCompleteTextView? = null
    private val searchFilterDialog by lazy {
        val tags = tagList.toList()
        val tagAdapter = TagSuggestionAdapter(tags)
        val tagExceptAdapter = TagSuggestionAdapter(tags)
        val creatorAdapter = CreatorSuggestionAdapter(creatorList.toList())

        searchFilterView = LayoutInflater.from(this).inflate(R.layout.dialog_search_filter, null)

        tagTextView = searchFilterView!!.findViewById(R.id.tag_textView)
        tagTextView?.setAdapter(tagAdapter)
        tagTextView?.setOnItemClickListener { adaptorView, _, i, _ ->
            tagTextView?.setText("")
            val item = adaptorView.adapter.getItem(i) as Tag
            with(mViewModel) {
                viewModelScope.launch {
                    searchParams.value?.tags?.add(item)
                    // 为使flow数据得到相应，这里必须使用一个新的对象
                    val tmp = mutableSetOf<Tag>()
                    searchParams.value?.tags?.forEach {
                        tmp.add(it)
                    }
                    selectedTags.emit(tmp)
                }
            }
        }
        tagExceptTextView = searchFilterView!!.findViewById(R.id.tag_except_textView)
        tagExceptTextView?.setAdapter(tagExceptAdapter)
        tagExceptTextView?.setOnItemClickListener { adaptorView, _, i, _ ->
            tagExceptTextView?.setText("")
            val item = adaptorView.adapter.getItem(i) as Tag
            with(mViewModel) {
                viewModelScope.launch {
                    searchParams.value?.exceptedTags?.add(item)
                    // 为使flow数据得到相应，这里必须使用一个新的对象
                    val tmp = mutableSetOf<Tag>()
                    searchParams.value?.exceptedTags?.forEach {
                        tmp.add(it)
                    }
                    selectedExceptTags.emit(tmp)
                }
            }
        }

        creatorTextView =
            searchFilterView!!.findViewById(R.id.creator_textView)
        creatorTextView?.setAdapter(creatorAdapter)

        // 初始化已输入的搜索信息
        with(mViewModel) {
            val tagSelectedList = mutableListOf<TagSelectedItem>()
            selectedTags.value.forEach { tag ->
                tagSelectedList.add(TagSelectedItem(tag, mViewModel, true))
            }
            val selectedTagRecyclerView =
                searchFilterView!!.findViewById<RecyclerView>(R.id.selected_tags_recycler_view)
            selectedTagRecyclerView.adapter = DslAdapter(tagSelectedList)

            val tagExceptSelectedList = mutableListOf<TagSelectedItem>()
            selectedExceptTags.value.forEach { tag ->
                tagExceptSelectedList.add(TagSelectedItem(tag, mViewModel, false))
            }
            val selectedTagExceptRecyclerView =
                searchFilterView!!.findViewById<RecyclerView>(R.id.selected_except_tags_recycler_view)
            selectedTagExceptRecyclerView.adapter = DslAdapter(tagExceptSelectedList)

            creatorTextView?.setText(searchParams.value?.creator)
        }

        MaterialAlertDialogBuilder(
            this
        )
            .setTitle(R.string.search_settings)
            .setView(searchFilterView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                mViewModel.searchParams.value?.creator = creatorTextView?.text.toString()
                search()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }


    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _mViewModel =
            ViewModelProvider(this)[MainViewModel::class.java]

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_recommend,
                R.id.nav_favorites,
                R.id.nav_history,
                R.id.nav_thanks,
                R.id.nav_setting
            ), drawerLayout
        )

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.appBarMain.toolbarLayout.title = destination.label
            binding.appBarMain.refresh.isVisible =
                !(null != destination.label && (
                        destination.label!! == getString(R.string.thanks)
                                // 暂时隐藏推荐页面按钮
                        || destination.label!! == getString(R.string.recommend
                )))

            // 为了方便回退继续查看评价，当从推荐页面跳转至文章页面时，可以直接返回
            val pre = navController.previousBackStackEntry
            if (pre?.destination?.label == getString(R.string.recommend)) {
                this.canPressBack = true
            }

            // 当前页面是推荐页面时，刷新按钮显示为用语解释
            if (destination.label == getString(R.string.recommend)) {
                binding.appBarMain.refresh.setImageResource(R.drawable.baseline_help_outline_24)
                binding.appBarMain.refresh.tooltipText = getString(R.string.recommend_dict)
            } else {
                binding.appBarMain.refresh.setImageResource(R.drawable.ic_baseline_refresh_24)
                binding.appBarMain.refresh.tooltipText = getString(R.string.refresh)
            }
        }

        with(binding) {
            // 搜索框输入监听
            appBarMain.searchView.setKeepQuery(true)
            appBarMain.searchView.setOnQueryTextListener(object :
                SimpleSearchView.OnQueryTextListener {
                override fun onQueryTextChange(newText: String): Boolean {
                    return false
                }

                override fun onQueryTextCleared(): Boolean {
                    return false
                }

                override fun onQueryTextSubmit(query: String): Boolean {
                    mViewModel.searchParams.value?.keyword = query
                    search()
                    return false
                }
            })
            //重置搜索选项
            appBarMain.refresh.setOnClickListener {
                // 搜索按钮根据不同的页面有不同的功能
                when (navController.currentDestination?.label) {
                    getString(R.string.home) -> {
                        with(mViewModel) {
                            viewModelScope.launch {
                                searchParams.value = SearchBean()
                                selectedTags.emit(mutableSetOf())
                                selectedExceptTags.emit(mutableSetOf())
                            }
                        }
                        tagTextView?.setText("")
                        tagExceptTextView?.setText("")
                        creatorTextView?.setText("")
                        search()
                    }
                    getString(R.string.my_favorites) -> {
                        navController.navigate(R.id.nav_favorites)
                    }
                    getString(R.string.history) -> {
                        navController.navigate(R.id.nav_history)
                    }
                    getString(R.string.recommend) -> {
                        // TODO 用语解释页面逻辑
                    }
                }
            }
            appBarMain.indexBg.apply {
                try {
                    val uri = SettingUtil.getImageBackground(SettingUtil.APP_BAR_BG)
                    if (null != uri) setImageDrawable(Drawable.createFromPath(uri.path))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        with(mViewModel) {
            viewModelScope.launch {
                selectedTags.collect {
                    val tagSelectedList = mutableListOf<TagSelectedItem>()
                    it.forEach { tag ->
                        tagSelectedList.add(TagSelectedItem(tag, mViewModel, true))
                    }
                    if (null != searchFilterView) {
                        val selectedTagRecyclerView =
                            searchFilterView!!.findViewById<RecyclerView>(R.id.selected_tags_recycler_view)
                        selectedTagRecyclerView.adapter = DslAdapter(tagSelectedList)
                    }
                }
            }
            viewModelScope.launch {
                selectedExceptTags.collect {
                    val tagSelectedList = mutableListOf<TagSelectedItem>()
                    it.forEach { tag ->
                        tagSelectedList.add(TagSelectedItem(tag, mViewModel, false))
                    }
                    if (null != searchFilterView) {
                        val selectedExceptTagRecyclerView =
                            searchFilterView!!.findViewById<RecyclerView>(R.id.selected_except_tags_recycler_view)
                        selectedExceptTagRecyclerView.adapter = DslAdapter(tagSelectedList)
                    }
                }
            }
        }

        // 新建一个守护线程，每个数据库操作任务自动进入队列排队处理
        val dataBaseThread = DatabaseCopyThread()
        dataBaseThread.isDaemon = true
        dataBaseThread.start()

        baseViewModel.getUpdate(this, false)

        loadSearchOptions()

        val sharedPreferences = getSharedPreferences("introduction", 0)
        if (null == sharedPreferences.getString("done", null)) {
            MainIntroActivity::class.startNewActivity()
            sharedPreferences.edit().putString("done", "right").apply()
        }

        FavoriteArticleUtil.refactorFavorites(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 若可以返回则返回上一个fragment
            if (this.canPressBack) {
                navController.navigateUp()
                this.canPressBack = false
                return false
            }
            exit()
            return false
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun exit() {
        if (!isExit) {
            isExit = true
            ToastUtil.info("再按一次返回键退出程序")
            //利用handler延迟发送更改状态信息
            mHandler.sendEmptyMessageDelayed(0, 2000)
        } else {
            finish()
            exitProcess(0)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        val item = menu.findItem(R.id.action_search)
        binding.appBarMain.searchView.setMenuItem(item)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // 搜索过滤项
            R.id.action_search_settings -> searchFilterDialog.show()
            R.id.app_intro -> MainIntroActivity::class.startNewActivity()
            R.id.setting -> SettingActivity::class.startNewActivity()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    /**
     * 自定义搜索
     */
    fun searchByOption(searchBean: SearchBean) {
        val handler = DataBaseHandler(this@MainActivity) {
            daoSession = it.obj as DaoSession

            // 获取名字为空的tag
            val emptyTags = searchBean.tags.filter { tag ->
                tag.name.isNullOrEmpty()
            }

            if (null != daoSession) {
                val tagDao: TagDao = daoSession!!.tagDao

                val query: QueryBuilder<Tag> = tagDao.queryBuilder()
                query.where(TagDao.Properties.Id.`in`(emptyTags.map { tag -> tag.id }))

                val list = query.build().listLazy()
                val tagList = mutableListOf<Tag>()

                list.forEach { tag ->
                    tagList.add(tag)
                }

                searchBean.tags.removeAll(emptyTags.toSet())
                searchBean.tags.addAll(tagList)

                list.close()
            }

            with(mViewModel) {
                viewModelScope.launch {
                    searchParams.value = searchBean
                    Log.d("searchParams", "searchParams: ${searchBean.tags.map { tag -> tag.name}}")
                    selectedTags.emit(searchBean.tags)
                    selectedExceptTags.emit(searchBean.exceptedTags)
                }
            }
            tagTextView?.setText("")
            tagExceptTextView?.setText("")
            creatorTextView?.setText(searchBean.creator)
            search()
        }
        DatabaseCopyThread.addHandler(handler)
    }

    /**
     * 执行搜索
     */
    @SuppressLint("RestrictedApi")
    fun search() {
        shapeLoadingDialog?.show()
        val bundle = Bundle()
        bundle.putSerializable("searchParams", mViewModel.searchParams.value)
//        while (navController.backStack.size >= 2) {
//            navController.popBackStack()
//        }
        navController.navigate(R.id.nav_home, bundle)
    }

    /**
     * 加载可用的搜索选项
     */
    private fun loadSearchOptions() {
        DatabaseCopyThread.addHandler(DataBaseHandler(this) { it ->
            daoSession = it.obj as DaoSession
            if (null != daoSession) {
                with(daoSession!!) {
                    val artCreatorDao: ArtCreatorDao = artCreatorDao
                    val tagDao: TagDao = tagDao
                    // 获取创作者列表
                    artCreatorDao.queryBuilder().listLazy()
                        .forEach {
                            if (it.author.isNotBlank()) creatorList.add(it.author)
                            if (it.translator.isNotBlank()) creatorList.add(it.translator)
                        }
                    creatorList = creatorList.toSortedSet()
                    // 获取tag
                    tagDao.queryBuilder().orderDesc(TagDao.Properties.Name).listLazy().forEach {
                        tagList.add(it)
                    }
                }
            }
        })
    }

    companion object {
        @Throws(FileNotFoundException::class)
        fun getBitmapFromUri(uri: Uri, activity: MainActivity): Bitmap {
            val parcelFileDescriptor = activity.contentResolver.openFileDescriptor(uri, "r")
            val ret = BitmapFactory.decodeFileDescriptor(parcelFileDescriptor?.fileDescriptor)
            parcelFileDescriptor?.close()
            return ret
        }
    }
}