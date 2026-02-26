# Preserve all public classes and methods
-keep public class * {
    public protected *;
}

-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, Signature
-keep @interface *

-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.iuuaa.jpegcompressor.JPEGCompressor { *; }
-keep class com.iuuaa.jpegcompressor.JPEGCompressor$* { *; }
-keep class com.iuuaa.jpegcompressor.ImagePicker { *; }
-keep class com.iuuaa.jpegcompressor.ImagePicker$* { *; }