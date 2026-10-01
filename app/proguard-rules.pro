# 各模块的 ModuleEntry 由 ServiceLoader 在运行时发现，必须保留
-keep class * implements com.sentinel.data.module.ModuleEntry { *; }
-keepnames class com.sentinel.data.module.ModuleEntry
