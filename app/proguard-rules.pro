# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Mantiene numeros de linea en los stack traces; el mapping.txt los traduce en Play Console.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Modelos serializados a JSON (kotlinx.serialization trae sus propias reglas;
# esto conserva ademas los nombres de campo legibles en el informe exportado).
-keep,includedescriptorclasses class com.admin.siliconpulse.domain.model.**$$serializer { *; }
-keepclassmembers class com.admin.siliconpulse.domain.model.** { *** Companion; }
