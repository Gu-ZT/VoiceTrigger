# VoiceTrigger [中文](./README.md)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.209-orange)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-LGPL%203.0-blue)](./LICENSE)

VoiceTrigger is a Minecraft NeoForge mod that allows players to control game keys via voice commands. It utilizes the Wav2Vec2 deep learning
model combined with custom-trained models for speech recognition and matching, delivering an immersive voice control experience for
Minecraft.

## Features

- **Voice Key Binding**: Bind custom voice commands to any in-game key
- **Deep Learning Speech Recognition**: High-precision voice matching based on the Wav2Vec2 model
- **Real-time Voice Monitoring**: Supports both continuous monitoring and key-triggered modes
- **Silence Detection**: Intelligent detection of speech start/end points, automatically filtering silent segments
- **Configurable Parameters**: Adjustable thresholds for similarity, silence, and other parameters

## System Requirements

- Minecraft 1.21.1
- NeoForge 21.1.209+
- Java 21+
- Microphone device

## Installation

1. Ensure NeoForge 1.21.1 is installed
2. Download the latest VoiceTrigger mod JAR file
3. Place the JAR file in `.minecraft/mods` directory
4. Launch the game

## Usage

### Opening Voice Binding Interface

Press `B` (default) in-game to open the voice binding settings interface.

### Recording Voice Templates

1. In the voice binding interface, locate the key to bind
2. Click the "Record" button to start recording
3. Speak your desired voice command into the microphone (recommended duration: 2-3 seconds)
4. Click "Stop" to end recording
5. The voice template will be automatically saved and registered

### Voice Trigger Modes

The mod supports two voice monitoring modes (configurable):

| Mode                    | Description                                                                      |
|-------------------------|----------------------------------------------------------------------------------|
| **Continuous (ON)**     | Automatically monitors microphone when in-game world, pauses when game is paused |
| **Key-triggered (OFF)** | Only listens when `V` key is held                                                |

## Configuration

Configuration file path: `.minecraft/config/voice_trigger-client.toml`

```toml
# Similarity threshold (requires exceeding this value for match)
# Range: 0.0 ~ 1.0, default: 0.75
similarityThreshold = 0.75

# Silence detection threshold (dB)
# Default: -43.0
silenceThreshold = -43.0

# Audio buffer size (bytes)
# Range: 1024 ~ 16384, default: 4096
bufferSize = 4096

# Minimum frames required for match
# Range: 1 ~ 16, default: 3
minFramesForMatch = 3

# Continuous monitoring mode
# ON: Continuous microphone monitoring
# OFF: Listens only when V key is held
continuousMonitoring = "ON"
```

### Configuration Tips

- **Low recognition rate**: Decrease `similarityThreshold` (e.g., 0.65)
- **Frequent false triggers**: Increase `similarityThreshold` (e.g., 0.85)
- **Short recordings**: Increase `minFramesForMatch`
- **Noisy environment**: Raise `silenceThreshold` (e.g., -35.0)

## Development

### Environment Requirements

- JDK 21+
- Gradle 8.8+

### Clone Project

```bash
git clone https://github.com/your-repo/VoiceTrigger.git
cd VoiceTrigger
```

### Build Project

```bash
# Windows
gradlew.bat build

# Linux/macOS
./gradlew build
```

Build artifacts are located in `build/libs/`.

### Run Development Environment

```bash
# Launch client
gradlew.bat runClient

# Launch server
gradlew.bat runServer
```

### Generate Language Files

```bash
gradlew.bat runData
```

## Technical Architecture

### Core Components

| Component             | Description                                                            |
|-----------------------|------------------------------------------------------------------------|
| `VoiceListener`       | Real-time voice listener handling microphone input and speech matching |
| `VoiceRecorder`       | Voice recorder for capturing user voice templates                      |
| `VoiceProfileManager` | Manages storage and loading of WAV files                               |
| `AudioSimilarityDL`   | Deep learning audio similarity calculation based on Wav2Vec2 model     |

### Dependencies

- **TarsosDSP**: Audio processing and microphone input
- **DJL (Deep Java Library)**: Deep learning inference framework
- **PyTorch Engine**: Wav2Vec2 model runtime

### Voice Profile Storage

Voice templates are stored in: `.minecraft/config/voice_trigger/*.wav`

File naming format: `{key_name}.wav`

## Key Bindings

| Key | Function                                              |
|-----|-------------------------------------------------------|
| `B` | Opens voice binding interface                         |
| `V` | Voice monitoring (only when continuousMonitoring=OFF) |

## FAQ

### Q: Voice recognition not working?

1. Verify microphone functionality
2. Confirm microphone is enabled in game sound settings
3. Check `logs/latest.log` for error messages

### Q: Low recognition rate?

1. Re-record clearer voice templates
2. Recommended recording duration: 2-3 seconds
3. Lower `similarityThreshold` value
4. Ensure quiet recording environment

### Q: Too many false triggers?

1. Increase `similarityThreshold` value
2. Use more distinctive voice commands
3. Raise `silenceThreshold` to filter background noise

## License

Licensed under [GNU Lesser General Public License v3.0](./LICENSE).

## Author

- **Gugle** - Lead Developer

## Acknowledgments

- [NeoForge](https://neoforged.net/) - Minecraft mod loader
- [TarsosDSP](https://github.com/JorenSix/TarsosDSP) - Audio processing library
- [DJL](https://djl.ai/) - Deep learning framework
- [Wav2Vec2](https://huggingface.co/facebook/wav2vec2-base) - Speech recognition model