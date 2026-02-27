# 第三方代码与修改说明

## libjpeg-turbo

本库的 JPEG 编解码基于 [libjpeg-turbo](https://github.com/libjpeg-turbo/libjpeg-turbo)，并在此基础上进行了修改与增补。

- **上游仓库**：<https://github.com/libjpeg-turbo/libjpeg-turbo>
- **许可证**：IJG (Independent JPEG Group) License + Modified (3-clause) BSD License，详见上游 [LICENSE.md](https://github.com/libjpeg-turbo/libjpeg-turbo/blob/main/LICENSE.md)。
- **本仓库中的关联方式**：通过拷贝或子模块引入 libjpeg-turbo 源码至 `jpeg-compressor/src/main/cpp/libjpeg-turbo`（或当前 CMake 所指向的路径），并在本仓库内对部分文件做了修改、对部分头文件做了新增，以便在 Android NDK 下编译与集成。

### 在本仓库中修改过的 libjpeg-turbo 相关文件

（以下路径相对于 libjpeg-turbo 源码根目录或本工程 `jpeg-compressor/src/main/cpp`，请根据你实际放置 libjpeg-turbo 的位置对应）

- `simd/arm/neon-compat.h` — 修改
- `simd/jsimd.h` — 修改
- `simd/jsimd_none.c` — 修改

### 覆盖上游 libjpeg-turbo 源码的建议方式

出于子项目/子模块隔离考虑，不直接在 libjpeg-turbo 子仓库内提交这些修改，而是在本仓库中单独维护一份补丁文件。

**推荐结构（相对于本仓库根目录）：**

- `jpeg-compressor/libjpeg-turbo-patches/simd/arm/neon-compat.h`
- `jpeg-compressor/libjpeg-turbo-patches/simd/jsimd.h`
- `jpeg-compressor/libjpeg-turbo-patches/src/jsimd_none.c`

你可以将自己在 libjpeg-turbo 中已经修改好的 3 个源码文件，拷贝到以上对应路径中进行版本管理。之后，在需要对上游 libjpeg-turbo 进行同步或重新应用补丁时，按下面步骤覆盖：

1. 确保本地存在 libjpeg-turbo 源码目录，例如：`<LIBJPEG_TURBO_ROOT>`。  
2. 将本仓库中的补丁文件覆盖到上游源码中：
   - 复制 `jpeg-compressor/libjpeg-turbo-patches/simd/arm/neon-compat.h` → `<LIBJPEG_TURBO_ROOT>/simd/arm/neon-compat.h`
   - 复制 `jpeg-compressor/libjpeg-turbo-patches/simd/jsimd.h` → `<LIBJPEG_TURBO_ROOT>/simd/jsimd.h`
   - 复制 `jpeg-compressor/libjpeg-turbo-patches/src/jsimd_none.c` → `<LIBJPEG_TURBO_ROOT>/src/jsimd_none.c`
3. 按 libjpeg-turbo 官方文档执行其构建脚本，或按本工程的 CMake 配置重新编译 native 库，即可得到带有这些补丁的 libjpeg-turbo 版本。

> 说明：本仓库内仅维护这 3 个文件的修改版本，不修改 libjpeg-turbo 上游仓库；如果未来需要从上游同步新版本，可以先更新上游源码，再按上述步骤重新覆盖并解决冲突。

### 在本仓库中新增的文件（与 libjpeg-turbo 配置/构建相关）

- `jpeg-compressor/src/main/cpp/include/jconfig.h`
- `jpeg-compressor/src/main/cpp/include/jconfigint.h`
- `jpeg-compressor/src/main/cpp/include/jversion.h`

上述文件用于本工程的构建与头文件搜索路径，未提交到 libjpeg-turbo 上游，仅在本仓库 [iuuaa/JPEG-Compressor](https://github.com/iuuaa/JPEG-Compressor) 中维护。

### 修改/新增这些文件的目的

- **`simd/arm/neon-compat.h`（修改）**
  - 统一并修正 NEON 相关的兼容层，适配 Android NDK/Clang 环境，避免旧版内联汇编或宏在新编译器下产生错误或警告。
  - 为 armeabi-v7a / arm64-v8a 提供更稳定的 NEON 支持，使 libjpeg-turbo 在这些 ABI 上可以安全启用 SIMD 加速。

- **`simd/jsimd.h`（修改）**
  - 调整 `jsimd_*` 系列函数与能力探测函数的声明，以匹配本工程实际参与编译的 SIMD 源文件集合（例如只启用 ARM NEON，禁用依赖 NASM 的 x86 SIMD）。
  - 保证在某些 ABI 未启用 SIMD 时，链接阶段仍能找到对应的回退实现（由 `jsimd_none.c` 提供），防止符号缺失。

- **`simd/jsimd_none.c` / `src/jsimd_none.c`（修改）**
  - 作为「无 SIMD」情况下的纯 C 回退实现，确保在 x86/x86_64 或其他未启用 SIMD 的 ABI 上仍然可以正常编译和运行。
  - 明确让能力探测函数在这些 ABI 上返回「不支持 SIMD」，从而强制走标量代码路径，避免运行时误用不存在的 SIMD 实现。

- **`include/jconfig.h`（新增）**
  - 提供经过配置的 `jconfig.h`，将 libjpeg-turbo 的构建选项（如是否启用某些特性、目标平台宏）固定下来，便于在 Android NDK 中直接编译，而无需在本地再次运行上游的自动配置脚本。

- **`include/jconfigint.h`（新增）**
  - 配合 `jconfig.h` 提供内部使用的配置宏，实现与上游类似的类型与平台适配，但以「静态文件」形式维护，避免依赖 autotools/CMake 生成步骤。

- **`include/jversion.h`（新增）**
  - 声明 libjpeg-turbo/JPEG 库的版本信息与版权字符串，供编译时或运行时查询。
  - 避免直接修改上游版本文件，将所需版本信息固定在本工程中，便于在更新上游源码时进行对比与合并。
