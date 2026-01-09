package com.github.bboygf.over_code.ui.prompt_config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.bboygf.over_code.services.ChatDatabaseService
import com.github.bboygf.over_code.vo.PromptInfo

class PromptConfigViewModel(private val dbService: ChatDatabaseService) {
    
    var prompts by mutableStateOf(listOf<PromptInfo>())
        private set
    
    var selectedIndex by mutableStateOf<Int?>(null)
        private set
    
    var showAddDialog by mutableStateOf(false)
        private set
    
    var editingPrompt by mutableStateOf<PromptInfo?>(null)
        private set
    
    init {
        loadPrompts()
    }
    
    fun loadPrompts() {
        prompts = dbService.getAllPromptTemplates()
    }
    
    fun selectPrompt(index: Int) {
        selectedIndex = index
    }
    
    fun showAddDialog() {
        showAddDialog = true
    }
    
    fun hideAddDialog() {
        showAddDialog = false
    }
    
    fun startEditPrompt() {
        selectedIndex?.let { index ->
            editingPrompt = prompts[index]
        }
    }
    
    fun cancelEdit() {
        editingPrompt = null
    }
    
    fun addPrompt(prompt: PromptInfo) {
        dbService.addPromptTemplate(prompt)
        loadPrompts()
        hideAddDialog()
    }
    
    fun updatePrompt(promptId: String, prompt: PromptInfo) {
        dbService.updatePromptTemplate(promptId, prompt)
        loadPrompts()
        cancelEdit()
    }
    
    fun deleteSelectedPrompt() {
        selectedIndex?.let { index ->
            val prompt = prompts[index]
            if (!prompt.isBuiltIn) {
                dbService.deletePromptTemplate(prompt.promptId)
                loadPrompts()
                selectedIndex = null
            }
        }
    }
}
