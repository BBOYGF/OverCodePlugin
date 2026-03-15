<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# OverCode Plugin Changelog

## [Unreleased]

## [1.1.6]
### Added

- 增加可设置是否每次聊天将记忆摘要增加到聊天上下文中。
- 增加一键总结当前上下文到记忆库功能
- 
### Fixed

- 修复 Mac 下粘贴图片不生效问题。
- 修复 设置窗口显示不全组件挤压在一起问题。

## [1.1.5]

### Added

- 增加多窗口聊天，用户可以同时开多个回话，互不影响。
- 解决聊天内容不能很方便的复杂问题。

## [1.1.4]

### Added

- 增加读取第三方依赖源码功能。
- 增加执行命令行功能功能，可执行git、cmd、task等命令。


## [1.1.3]

### Added

- 优化 LOGO

## [1.1.2]

### Fixed

- 修复聊天后滚动条没及时更新滚动问题

## [1.1.1]

### Added

- 增加右键添加异常到聊天窗口
- 增加全局搜索工具

## [1.1.0]

### Added

- 增加修改代码后一键跳转到修改的位置功能

### Fixed

- 修复 OpenAI API 调用异常问题

## [1.0.9]

### Added

- 增加关于界面
- 增加聊天出口右侧滚动条大小

## [1.0.8]

### Added

- 增加根据变量名获取所在文件方法
- 增加根据变量或者方法找到被应用的文件方法

## [1.0.7]

### Fixed

- 修改插件 logo
- 修复插件在计划模式下也能修改代码问题

## [1.0.6]

### Added

- 增加记忆库功能，AI自动感知会将有用的内容保存到记忆库中，记忆库可以手动维护
- 去掉一次性获取所有文件功能改为获取所有目录减少上下文占用

### Fixed

- 修复新建文件后再次编辑失败问题

## [1.0.5] - 2026-02-19

### Changed

- 优化 Claude 请求数据兼容。
- 优化聊天时鼠标滚动后被模型回复劫持问题。
- 优化历史回话标题，使用首次请求前100字。

## [1.0.4]

### Fixed

- 解决请求时间太长导致主动断开连接问题
- 解决 LLM 回复没有及时更新问题

## [1.0.3]

### Fixed

- 修复在设置中添加模型后，切回来没看到模型问题
- 修复没记录上次选中的回话
- 修复检查代码格式时检查 sql 脚本问题

## [1.0.2]

### Changed

- 优化写功能，采用智能搜索方式找到替换位置替换内容

## [1.0.0]

### Added

- 首次发布

[Unreleased]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.1.3...HEAD
[1.1.3]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.9...v1.1.0
[1.0.9]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.8...v1.0.9
[1.0.8]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.0...v1.0.2
[1.0.0]: https://github.com/BBOYGF/OverCodePlugin/commits/v1.0.0
