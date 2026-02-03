# 🛠️ OverCode LLM 工具扩展开发指南

本项目采用 **“注册表模式” (Registry Pattern)** 来管理大语言模型（LLM）调用的工具。该架构实现了工具元数据（声明）与执行逻辑的解耦，新增工具时无需修改
`HomeViewModel` 的核心代码。

## 1. 核心架构说明

* **接口定义 (`LlmTool`)**: 定义在 `LlmTool.kt` 中。每个工具必须实现 `name` (工具名), `description` (描述),
  `parameters` (参数定义) 和 `execute` (执行逻辑)。
* **集中注册 (`ToolRegistry`)**: 维护一个工具对象列表。`HomeViewModel` 会自动遍历此列表进行工具声明和分发。
* **自动分发**: `HomeViewModel#executeTool` 会通过 `functionCall.name` 自动在注册表中查找并运行对应的 `execute` 方法。

## 2. 新增工具步骤

若要为 LLM 增加一个新功能，请遵循以下三步：

### 第一步：实现底层业务逻辑

在 `ProjectFileUtils.kt` (或相关的 Service 类) 中增加具体的业务方法。
> **原则**：务必使用 `runReadAction` (读) 或 `WriteCommandAction` (写) 保证 IDE ���程安全。

### 第二步：定义工具对象

在 `LlmTool.kt` 中创建一个实现 `LlmTool` 接口的 `object`。

### 第三步：在注册表中登记

将新对象添加到 `ToolRegistry` 的 `allTools` 列表中。

## 3. 最佳实践与注意事项

1. **行号返回**：搜索类工具务必返回 `起始行 - 终止行`。这能让 AI 配合 `read_file_range` 实现精确的代码读取。
2. **异常捕获**：在 `execute` 方法中务必捕获 `Throwable`，确保工具返回错误字符串而不是导致崩溃。
3. **模式区分**：读取工具设置 `isWriteTool = false`，修改工具设置 `isWriteTool = true`。
