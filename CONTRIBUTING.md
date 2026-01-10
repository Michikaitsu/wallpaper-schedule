# Contributing to Wallpaper Scheduler

Thank you for your interest in contributing! Here's how you can help.

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/KaiTooast/wallpaper-schedule/issues)
2. If not, create a new issue with:
   - A clear title
   - Steps to reproduce
   - Expected vs actual behavior
   - Your Android version and device model

### Suggesting Features

1. Open a new issue with the "Feature Request" label
2. Describe the feature and why it would be useful
3. Include mockups or examples if possible

### Code Contributions

1. Fork the repository
2. Create a new branch: `git checkout -b feature/your-feature-name`
3. Make your changes
4. Test thoroughly on a real device
5. Commit with clear messages: `git commit -m "Add: description of change"`
6. Push to your fork: `git push origin feature/your-feature-name`
7. Open a Pull Request

## Development Setup

### Requirements
- Android Studio Arctic Fox or newer
- JDK 17+
- Android SDK 34

### Building
```bash
# Clone the repo
git clone https://github.com/KaiTooast/wallpaper-schedule.git
cd wallpaper-schedule

# Build debug version
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

## Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions small and focused

## Translations

Want to add a new language? 

1. Copy `app/src/main/res/values/strings.xml`
2. Create a new folder: `app/src/main/res/values-XX/` (XX = language code)
3. Translate all strings
4. Submit a Pull Request

## Questions?

Feel free to open an issue or reach out!
