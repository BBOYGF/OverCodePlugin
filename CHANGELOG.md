<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# OverCode Plugin Changelog

## [Unreleased]

## [1.0.5] - 2026-02-19

### Added

- v1.0 首次发布
- V1.0.2 优化写功能，采用智能搜索方式找到替换位置替换内容
- V1.0.3 
  - 修复在设置中添加模型后，切回来没看到模型问题
  - 修复没记录上次选中的回话
  - 修复检查代码格式时检查 sql 脚本问题
- V1.0.4
  - 解决请求时间太长导致主动断开连接问题
  - 解决 LLM 回复没有及时更新问题
- V1.0.5
  - 优化 Claude 请求数据兼容。
  - 优化聊天是鼠标滚动后被模型回复劫持问题。
  - 优化历史回话标题，使用首次请求前100字。
- V1.0.6
  - 修复新建文件后再次编辑失败问题
  - 增加记忆库功能，AI自动感知会将有用的内容保存到记忆库中，记忆库可以手动维护
  - 去掉一次性获取所有文件功能改为获取所有目录减少上下文占用

[Unreleased]: https://github.com/BBOYGF/OverCodePlugin/compare/v1.0.5...HEAD
[1.0.5]: https://github.com/BBOYGF/OverCodePlugin/commits/v1.0.5
