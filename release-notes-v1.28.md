# v1.28 正则崩溃修复

- 修复解析抖音页面时出现 `Syntax error in regexp pattern near index 39` 的问题。
- 问题来源是 `__INITIAL_STATE__` 提取正则在 Android/Java Regex 中对花括号转义不兼容。
- 改为 `[\s\S]*?` 非花括号依赖写法，并自动去掉脚本 JSON 末尾分号。
- 已通过 `assembleDebug` 和本地 Java Pattern 编译检查。
