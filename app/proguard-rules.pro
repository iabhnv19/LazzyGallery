# Keep Rust JNI bridge methods and signatures
-keep class com.lazzy.gallery.data.RustBridge {
    native <methods>;
    *;
}

# Keep Room entities, DAOs, and projection classes
-keep class com.lazzy.gallery.data.local.** { *; }

# Keep Parcelable/Immutable models if referenced reflectively
-keep class com.lazzy.gallery.data.MediaModel { *; }
-keep class com.lazzy.gallery.data.Album { *; }
-keep class com.lazzy.gallery.data.AlbumInfo { *; }
-keep class com.lazzy.gallery.data.ExifMetadata { *; }

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
