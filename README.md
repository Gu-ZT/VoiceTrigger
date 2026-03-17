# 声随形动 [English](./README.en.md)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.209-orange)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-LGPL%203.0-blue)](./LICENSE)

声随形动 是一个 Minecraft NeoForge 模组，允许玩家通过语音控制游戏按键。它使用 Wav2Vec2 深度学习模型混合自训练模型进行语音识别和匹配，为 Minecraft
带来沉浸式的语音控制体验。

## 功能特点

- **语音绑定按键**：为任意游戏按键绑定自定义语音指令
- **深度学习语音识别**：基于 Wav2Vec2 模型进行高精度语音匹配
- **实时语音监听**：支持持续监听或按键触发两种模式
- **静音检测**：智能检测语音起止，自动过滤静音片段
- **可配置参数**：相似度阈值、静音阈值等多项参数可调

## 系统要求

- Minecraft 1.21.1
- NeoForge 21.1.209+
- Java 21+
- 麦克风设备

## 安装

1. 确保已安装 NeoForge 1.21.1
2. 下载最新版本的 声随形动 模组 JAR 文件
3. 将 JAR 文件放入 `.minecraft/mods` 目录
4. 启动游戏

## 使用方法

### 打开语音绑定界面

在游戏中按下 `B` 键（默认）打开语音绑定设置界面。

### 录制语音模板

1. 在语音绑定界面中，找到要绑定语音的按键
2. 点击"录制"按钮开始录音
3. 对着麦克风说出你想要的语音指令（建议 2-3 秒）
4. 点击"停止"按钮结束录制
5. 语音模板将自动保存并注册

### 语音触发模式

模组支持两种语音监听模式（可在配置文件中设置）：

| 模式             | 说明                       |
|----------------|--------------------------|
| **持续监听 (ON)**  | 进入游戏世界后自动开始监听麦克风，暂停游戏时停止 |
| **按键触发 (OFF)** | 按住 `V` 键时才进行语音监听         |

## 配置

配置文件位置：`.minecraft/config/voice_trigger-client.toml`

```toml
# 相似度阈值（超过此值判定为匹配）
# 取值范围: 0.0 ~ 1.0，默认 0.75
similarityThreshold = 0.75

# 静音检测阈值（dB）
# 默认 -43.0
silenceThreshold = -43.0

# 音频缓冲区大小（字节）
# 取值范围: 1024 ~ 16384，默认 4096
bufferSize = 4096

# 匹配所需的最小帧数
# 取值范围: 1 ~ 16，默认 3
minFramesForMatch = 3

# 持续监听模式
# ON: 持续监听麦克风
# OFF: 按住 V 键时监听
continuousMonitoring = "ON"
```

### 配置建议

- **识别率低**：降低 `similarityThreshold`（如 0.65）
- **误触发频繁**：提高 `similarityThreshold`（如 0.85）
- **录音太短**：增加 `minFramesForMatch`
- **环境噪音大**：提高 `silenceThreshold`（如 -35.0）

## 开发

### 环境要求

- JDK 21+
- Gradle 8.8+

### 克隆项目

```bash
git clone https://github.com/Gu-ZT/VoiceTrigger.git
cd 声随形动
```

### 构建项目

```bash
# Windows
gradlew.bat build

# Linux/macOS
./gradlew build
```

构建产物位于 `build/libs/` 目录。

### 运行开发环境

```bash
# 启动客户端
gradlew.bat runClient

# 启动服务端
gradlew.bat runServer
```

### 生成语言文件

```bash
gradlew.bat runData
```

## 技术架构

### 核心组件

| 组件                    | 说明                         |
|-----------------------|----------------------------|
| `VoiceListener`       | 实时语音监听器，负责麦克风输入和语音匹配       |
| `VoiceRecorder`       | 语音录制器，用于录制用户的语音模板          |
| `VoiceProfileManager` | 语音配置管理器，管理 WAV 文件的存储和加载    |
| `AudioSimilarityDL`   | 深度学习音频相似度计算，基于 Wav2Vec2 模型 |

### 依赖库

- **TarsosDSP**: 音频处理和麦克风输入
- **DJL (Deep Java Library)**: 深度学习推理框架
- **PyTorch Engine**: Wav2Vec2 模型运行时

### 语音配置存储

语音模板文件存储在：`.minecraft/config/voice_trigger/*.wav`

文件命名格式：`{按键名称}.wav`

## 快捷键

| 按键  | 功能                                    |
|-----|---------------------------------------|
| `B` | 打开语音绑定界面                              |
| `V` | 语音监听（仅在 continuousMonitoring=OFF 时生效） |

## 常见问题

### Q: 语音识别不工作？

1. 检查麦克风是否正常工作
2. 确认游戏声音设置中麦克风已启用
3. 查看日志文件 `logs/latest.log` 获取错误信息

### Q: 识别率很低？

1. 尝试重新录制更清晰的语音模板
2. 录音时长建议 2-3 秒
3. 降低 `similarityThreshold` 配置值
4. 确保录音环境安静

### Q: 误触发太多？

1. 提高 `similarityThreshold` 配置值
2. 使用更独特的语音指令
3. 提高 `silenceThreshold` 过滤背景噪音

## 许可证

本项目采用 [GNU Lesser General Public License v3.0](./LICENSE) 许可证。

## 作者

- **Gugle** - 主要开发者

## 致谢

- [NeoForge](https://neoforged.net/) - Minecraft 模组加载器
- [TarsosDSP](https://github.com/JorenSix/TarsosDSP) - 音频处理库
- [DJL](https://djl.ai/) - 深度学习框架
- [Wav2Vec2](https://huggingface.co/facebook/wav2vec2-base) - 语音识别模型
