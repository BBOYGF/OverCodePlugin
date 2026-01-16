package com.github.bboygf.over_code.ui.model_config

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project

/**
 * 模型配置页面提供者
 */
class ModelConfigurableProvider(private val project: Project) : ConfigurableProvider() {
    override fun createConfigurable(): Configurable {
        return ModelConfigurable(project)
    }
}
