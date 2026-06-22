package com.subscheck.apk

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.subscheck.apk.databinding.ActivityMainBinding
import com.subscheck.apk.model.AppConfig
import com.subscheck.apk.pipeline.PipelineProgress
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var config = AppConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        config = AppConfig.load(this)
        restoreConfig()

        setupClickListeners()
        observeViewModel()
    }

    private fun restoreConfig() {
        binding.editTextUrl.setText(config.subscriptionUrls.joinToString("\n"))
    }

    private fun setupClickListeners() {
        binding.buttonCheck.setOnClickListener {
            startCheck()
        }

        binding.buttonSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.buttonCancel.setOnClickListener {
            viewModel.cancelCheck()
        }

        binding.buttonSave.setOnClickListener {
            viewModel.saveToFile(this)
        }
    }

    private fun startCheck() {
        val urlsText = binding.editTextUrl.text?.toString()?.trim()
        if (urlsText.isNullOrEmpty()) {
            showError(getString(R.string.enter_url))
            return
        }

        val urls = urlsText.split("\n").map { it.trim() }.filter { it.isNotBlank() && it.startsWith("http") }
        if (urls.isEmpty()) {
            showError("请输入有效的订阅链接（以 http 开头）")
            return
        }

        config = config.copy(subscriptionUrls = urls)
        AppConfig.save(this, config)

        resetUI()
        showProgress(true)

        viewModel.runCheck(this, urls, config)
    }

    private fun resetUI() {
        binding.layoutEmpty.visibility = View.GONE
        binding.cardStats.visibility = View.GONE
        binding.cardLog.visibility = View.GONE
        binding.buttonSave.visibility = View.GONE
        binding.textLog.text = ""
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.progressFlow.collectLatest { progress ->
                when (progress) {
                    is PipelineProgress.Started -> {
                        binding.textTotal.text = progress.total.toString()
                        binding.layoutProgress.visibility = View.VISIBLE
                    }
                    is PipelineProgress.Phase -> {
                        binding.textPhase.text = "阶段: ${progress.name}"
                        binding.textProgress.text = "总计: ${progress.total}, 已处理: ${progress.processed}"
                        if (progress.total > 0) {
                            binding.progressBar.visibility = View.VISIBLE
                            binding.progressBar.max = progress.total
                            binding.progressBar.progress = progress.processed
                        }
                    }
                    is PipelineProgress.ResultsReady -> {
                        binding.textAlive.text = progress.count.toString()
                    }
                    is PipelineProgress.Completed -> {
                        showProgress(false)
                        binding.textFinal.text = progress.results.size.toString()
                        binding.cardStats.visibility = View.VISIBLE
                        binding.cardLog.visibility = View.VISIBLE
                        binding.buttonSave.visibility = View.VISIBLE

                        val summary = buildString {
                            appendLine("\n=== 检测完成 ===")
                            appendLine("总计节点: ${viewModel.totalCount}")
                            appendLine("存活节点: ${binding.textAlive.text}")
                            appendLine("最终节点: ${progress.results.size}")
                            appendLine("OpenAI: ${progress.results.count { it.openai == "yes" }}")
                            appendLine("YouTube: ${progress.results.count { it.youtube == "yes" }}")
                            appendLine("Netflix: ${progress.results.count { it.netflix == "yes" }}")
                            appendLine("Disney+: ${progress.results.count { it.disney }}")
                            appendLine("Gemini: ${progress.results.count { it.gemini == "yes" }}")
                            appendLine("Claude: ${progress.results.count { it.claude == "yes" }}")
                            appendLine("Spotify: ${progress.results.count { it.spotify == "yes" }}")
                            appendLine("TikTok: ${progress.results.count { it.tiktok == "yes" }}")
                        }
                        appendLog(summary)

                        if (progress.results.isEmpty()) {
                            showWarning("没有可用节点")
                        } else {
                            showSuccess("检测完成，共 ${progress.results.size} 个可用节点")
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.logFlow.collectLatest { message ->
                appendLog(message)
            }
        }

        lifecycleScope.launch {
            viewModel.errorFlow.collectLatest { message ->
                showError(message)
                showProgress(false)
            }
        }

        lifecycleScope.launch {
            viewModel.saveResultFlow.collectLatest { result ->
                if (result != null) {
                    showSuccess("文件已保存到: $result")
                } else {
                    showError("保存文件失败")
                }
            }
        }
    }

    private fun appendLog(message: String) {
        val current = binding.textLog.text.toString()
        binding.textLog.text = if (current.isEmpty()) message else "$current\n$message"
        binding.scrollViewLog.post {
            binding.scrollViewLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun showProgress(show: Boolean) {
        binding.layoutProgress.visibility = if (show) View.VISIBLE else View.GONE
        binding.buttonCheck.isEnabled = !show
        binding.editTextUrl.isEnabled = !show
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showWarning(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showSettingsDialog() {
        val context = this
        val configToEdit = config

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val concurrentInput = TextInputEditText(context).apply {
            setText(configToEdit.concurrent.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        addInputField(container, "并发数 (测活)", concurrentInput)

        val mediaConcurrentInput = TextInputEditText(context).apply {
            setText(configToEdit.mediaConcurrent.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        addInputField(container, "并发数 (流媒体)", mediaConcurrentInput)

        val timeoutInput = TextInputEditText(context).apply {
            setText(configToEdit.timeoutMs.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        addInputField(container, "超时时间 (ms)", timeoutInput)

        val downloadMBInput = TextInputEditText(context).apply {
            setText(configToEdit.downloadMB.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        addInputField(container, "测速下载量 (MB)", downloadMBInput)

        val speedUrlInput = TextInputEditText(context).apply {
            setText(configToEdit.speedTestUrl)
        }
        addInputField(container, "测速地址 (留空则不测速)", speedUrlInput)

        val filterInput = TextInputEditText(context).apply {
            setText(configToEdit.filterRegex)
        }
        addInputField(container, "过滤正则表达式", filterInput)

        val fileNameInput = TextInputEditText(context).apply {
            setText(configToEdit.fileName)
            hint = "留空自动生成"
        }
        addInputField(container, "输出文件名", fileNameInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("高级设置")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val newConfig = AppConfig(
                    subscriptionUrls = configToEdit.subscriptionUrls,
                    concurrent = concurrentInput.text?.toString()?.toIntOrNull() ?: 20,
                    speedConcurrent = configToEdit.speedConcurrent,
                    mediaConcurrent = mediaConcurrentInput.text?.toString()?.toIntOrNull() ?: 10,
                    shuffleTestOrder = configToEdit.shuffleTestOrder,
                    aliveTestUrl = configToEdit.aliveTestUrl,
                    speedTestUrl = speedUrlInput.text?.toString()?.trim() ?: "",
                    timeoutMs = timeoutInput.text?.toString()?.toLongOrNull() ?: 5000,
                    mediaTimeoutMs = configToEdit.mediaTimeoutMs,
                    speedTimeoutMs = configToEdit.speedTimeoutMs,
                    downloadMB = downloadMBInput.text?.toString()?.toIntOrNull() ?: 10,
                    minSpeed = configToEdit.minSpeed,
                    filterRegex = filterInput.text?.toString()?.trim() ?: "",
                    mediaCheck = configToEdit.mediaCheck,
                    fileName = fileNameInput.text?.toString()?.trim() ?: ""
                )
                config = newConfig
                AppConfig.save(context, config)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addInputField(container: LinearLayout, label: String, input: TextInputEditText) {
        val textView = android.widget.TextView(container.context).apply {
            text = label
            setPadding(0, 16, 0, 4)
            textSize = 14f
        }
        container.addView(textView)
        container.addView(input)
    }
}
