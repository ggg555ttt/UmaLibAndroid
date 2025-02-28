package cc.kivo.lib.android.ui

import android.os.Bundle
import android.view.MenuItem
import br.tiagohm.markdownview.css.styles.Github
import cc.kivo.lib.android.databinding.ActivityUpdateLogBinding
import cc.kivo.lib.android.model.MyBaseActivity

class UpdateLogActivity : MyBaseActivity() {

    private lateinit var binding: ActivityUpdateLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUpdateLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        binding.markdownView.addStyleSheet(Github())
            .loadMarkdownFromUrl("https://umalib.github.io/UmaLibAndroid/update-log.md")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}