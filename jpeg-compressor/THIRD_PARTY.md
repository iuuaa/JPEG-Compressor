# 第三方代码与修改说明

## libjpeg-turbo

本库的 JPEG 编解码基于 [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo)，并在此基础上进行了修改与增补。

- **上游仓库**：<https://github.com/libjpeg-turbo/libjpeg-turbo>
- **许可证**：IJG (Independent JPEG Group) License + Modified (3-clause) BSD License，详见上游 [LICENSE.md](https://github.com/libjpeg-turbo/libjpeg-turbo/blob/main/LICENSE.md)。
- **本仓库中的关联方式**：通过拷贝或子模块引入 libjpeg-turbo 源码至 `jpeg-compressor/src/main/cpp/libjpeg-turbo`（或当前 CMake 所指向的路径），并在本仓库内对部分文件做了修改、对部分头文件做了新增，以便在 Android NDK 下编译与集成。

### 在本仓库中修改过的 libjpeg-turbo 相关文件

（以下路径相对于 libjpeg-turbo 源码根目录或本工程 `jpeg-compressor/src/main/cpp`，请根据你实际放置 libjpeg-turbo 的位置对应）

- `simd/neon-compat.h` — 修改
- `simd/jsimd.h` — 修改
- `simd/jsimd_none.c` — 修改

### 在本仓库中新增的文件（与 libjpeg-turbo 配置/构建相关）

- `jpeg-compressor/src/main/cpp/include/jconfig.h`
- `jpeg-compressor/src/main/cpp/include/jconfigint.h`
- `jpeg-compressor/src/main/cpp/include/jversion.h`

上述文件用于本工程的构建与头文件搜索路径，未提交到 libjpeg-turbo 上游，仅在本仓库 [iuuaa/JPEG-Compressor](https://github.com/iuuaa/JPEG-Compressor) 中维护。