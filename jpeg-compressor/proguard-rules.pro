# ProGuard rules: 仅在「本库」Release 构建时使用（minifyEnabled=true）。
# 作用：压缩本库时保留公开 API 与 native 方法，避免被 R8 删掉。
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.iuuaa.jpegcompressor.JPEGCompressor { *; }
-keep class com.iuuaa.jpegcompressor.JPEGCompressor$* { *; }
-keep class com.iuuaa.jpegcompressor.ImagePicker { *; }
-keep class com.iuuaa.jpegcompressor.ImagePicker$* { *; }