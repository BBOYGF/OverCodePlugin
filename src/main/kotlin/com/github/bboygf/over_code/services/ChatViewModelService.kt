package com.github.bboygf.over_code.services

import com.github.bboygf.over_code.ui.home.HomeViewModel
import com.intellij.openapi.components.Service

/**
 * 用于在 Project 范围内持有 ChatViewModel 实例的服务
 */
@Service(Service.Level.PROJECT)
class ChatViewModelService {
    var viewModel: HomeViewModel? = null

    fun sendMessageToChat(message: String) {
        viewModel?.sendMessageFromExternal(message)
    }

    fun insertTextToChat(text: String) {
        viewModel?.insertTextToInput(text)
    }
}