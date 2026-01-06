# OverCode
OverCode 是一款深度集成于 IntelliJ IDEA 的插件，旨在赋予开发者完全的 AI 自主权。它不仅支持使用个人的 API Key，更能无缝衔接本地运行的 Ollama 模型。

"Don't just write code, OverCode it." —— 摆脱云端束缚，通过自定义 Prompt 和本地算力，打造最懂你的编程助手。

## 详情
<!-- Plugin description -->
本地私有化 (Ollama Integrated): 完美支持本地运行的 Ollama 模型（如 Llama 3, DeepSeek, CodeQwen 等）。代码不离机，隐私零风险。

Prompt 自由调配 (Prompt Playground): * 深度自定义： 内置 Prompt 编辑器，支持针对不同编程任务（重构、注释、找 Bug）编写专用提示词。

参数微调： 实时调整 Temperature、Context Window 等模型参数，直到压榨出最优输出。

多源 API 支持: 支持 OpenAI 标准协议。无论是个人的 API Key 还是本地中转服务，都能一键接入。

沉浸式体验:

上下文感知： 自动提取选中的代码块或当前文件上下文。

无缝注入： 支持将 AI 生成的代码一键覆盖或插入当前编辑器。
<!-- Plugin description end -->
## 安装

1. 准备环境
   确保本地已安装并启动 Ollama，或拥有可用的 API Key。

2. 安装插件
   Marketplace: 在 IDEA 插件市场搜索 OverCode。

Manual: 下载 OverCode.zip 并在 IDEA 中选择 Install Plugin from Disk...。

3. 设置模型
   进入 Settings/Preferences -> Tools -> OverCode:

Endpoint: 设置为 http://localhost:11434 (Ollama 默认)。

Model Selection: 输入你想使用的模型名称（如 llama3）。

Prompt Template: 在模板库中预设你的常用指令。

---
