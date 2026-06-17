# 便捷下载本地版

一个从零重写的 Android 本地版下载工具。项目不基于原加固 APK 重打包，不包含广告 SDK、登录系统或付费/订阅入口。

## 功能

- 分享文本/网页链接解析
- 短链跳转解析
- 页面标题、描述、候选资源链接提取
- 调用系统下载器保存资源
- 下载任务列表查看
- 文件 MD5 / SHA-256 校验
- 下载目录快捷入口
- 本地隐私说明与分享说明

## 构建

```powershell
$env:JAVA_HOME='C:\Users\25021\Documents\app2\tools\jdk17\jdk-17.0.19+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleDebug
```

APK 输出位置：

```text
app\build\outputs\apk\debug\app-debug.apk
```

## 说明

原 APK 使用加固壳，重打包后会闪退，因此本项目采用 clean-room 方式重新实现可见功能和本地工作流。涉及平台内容下载时，请只处理你有权保存或使用的内容。
