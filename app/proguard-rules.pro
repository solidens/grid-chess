-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
# The wire classes are found by name through their generated serializers.
-keep,includedescriptorclasses class com.gridchess.online.**$$serializer { *; }
-keepclassmembers class com.gridchess.online.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
