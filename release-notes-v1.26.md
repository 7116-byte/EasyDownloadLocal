# v1.26 抖音结构化解析与噪音过滤

- 修复抖音仍然只解析出音频的问题：不再靠 URL 附近文本猜类型，改为优先解析结构化 JSON。
- 支持从 `RENDER_DATA`、`__UNIVERSAL_DATA_FOR_REHYDRATION__`、`__INITIAL_STATE__` 和抖音详情接口中提取媒体。
- 只把 `video.play_addr/download_addr/bit_rate.play_addr` 作为视频来源。
- `music.play_url/download_url` 只作为音频来源，不再伪装成视频。
- `images`、`cover/origin_cover/dynamic_cover` 作为图片和视频封面来源。
- 抖音页面不再混入网页、接口、脚本、头像、统计请求等低置信候选。
- 任意门嗅探列表也收紧为真实媒体类型，减少杂项。

如果某条抖音链接被平台风控，只返回音乐和封面、不返回视频流，应用现在会避免把音乐误报成视频；这种情况需要进入任意门播放页面后再嗅探真实视频请求。
