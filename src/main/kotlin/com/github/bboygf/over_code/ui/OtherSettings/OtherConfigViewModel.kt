package com.github.bboygf.over_code.ui.OtherSettings

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.PromptInfo

class OtherConfigViewModel(private val dbService: ChatDatabaseService) {

    var ipAdded by mutableStateOf("127.0.0.1")

    var port by mutableStateOf("10808")

    /**
     * 显示消息
     */
    var showMsg by mutableStateOf(false)

    init {
        loadConfig()
    }

    fun loadConfig() {
        ipAdded = dbService.getValue("host") ?: "127.0.0.1"
        port = dbService.getValue("port") ?: "10808"
    }

    fun updateConfig() {
        dbService.addOrUpdateValue("host", ipAdded)
        dbService.addOrUpdateValue("port", port)
        showMsg = true
    }

    fun onDismiss() {
        showMsg = false
    }

}
