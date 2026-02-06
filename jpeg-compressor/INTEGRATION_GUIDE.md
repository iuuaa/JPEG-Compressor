# libjpeg-turbo 集成指南

## 步骤1：下载libjpeg-turbo源码

```bash
cd jpeg-compressor/src/main/cpp
git clone https://github.com/libjpeg-turbo/libjpeg-turbo.git
cd libjpeg-turbo
git checkout 3.1.3  # 使用最新稳定版本
```

或者手动下载：
1. 访问 https://github.com/libjpeg-turbo/libjpeg-turbo/releases
2. 下载最新版本的源码压缩包
3. 解压到 `jpeg-compressor/src/main/cpp/libjpeg-turbo/` 目录

## 步骤2：更新CMakeLists.txt

CMakeLists.txt已经配置好，会自动编译libjpeg-turbo。

## 步骤3：完善jpeg_compressor.cpp

需要实现完整的libjpeg-turbo压缩逻辑。参考libjpeg-turbo的示例代码。

## 步骤4：编译

```bash
./gradlew :jpeg-compressor:assembleDebug
```

## 注意事项

- 确保NDK版本 >= 21
- 确保CMake版本 >= 3.22.1
- 编译时间可能较长，请耐心等待
