# 第三方资源说明

本项目自身代码以 MIT License 发布，见 `LICENSE`。以下是随项目分发的第三方资源及其授权：

## 离线词典数据

- 文件：`app/src/main/assets/offline_dictionary.json`
- 来源：基于 [ECDICT](https://github.com/skywind3000/ECDICT)（MIT License）的核心词库裁剪、
  清洗后，与项目手工整理的词表（`app/src/main/assets/dict_source.txt`）合并生成。
- 生成脚本：`build_dict.py`；原始数据：`data/ecdict-core.json`。
- ECDICT License: MIT License, Copyright (c) 2017 skywind3000.

## Xposed API

- 文件：`app/libs/xposed-api-82.jar`
- 来源：[XposedBridge API](https://api.xposed.info/)（Apache License 2.0）
- 仅以 `compileOnly` 方式参与编译，不会打包进 APK。

## 在线服务

应用运行时可能访问以下公开免费接口，它们不属于本项目的分发包：

- [有道词典 jsonapi](https://dict.youdao.com/jsonapi)：在线英汉释义
- [dictionaryapi.dev](https://dictionaryapi.dev/)：英文释义（有道不可用时的兜底）
- [MyMemory](https://mymemory.translated.net/)：机器翻译

这些接口的内容与可用性由各自服务提供方决定，本项目的 MIT 许可不覆盖其数据。
