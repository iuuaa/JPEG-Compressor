# Consumer rules: 随 AAR 一起发布，在「调用方 App」做 R8/ProGuard 混淆时生效。
# 作用：告诉调用方“不要混淆/删除本库的公开 API 与 native 方法”，否则运行时可能找不到类或 JNI 符号。
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, Signature
-keep @interface *
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.iuuaa.jpegcompressor.JPEGCompressor { *; }
-keep class com.iuuaa.jpegcompressor.JPEGCompressor$* { *; }
-keep class com.iuuaa.jpegcompressor.ImagePicker { *; }
-keep class com.iuuaa.jpegcompressor.ImagePicker$* { *; }