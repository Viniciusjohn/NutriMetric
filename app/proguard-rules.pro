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

# Mantém a linha/arquivo de origem nos stack traces (o plugin do Crashlytics
# faz upload automático do mapping.txt para desofuscar no console).
-keepattributes SourceFile,LineNumberTable

# ---------- kotlinx.serialization ----------
# As versões atuais da lib já trazem consumer-rules.txt, mas mantemos essas
# regras como reforço defensivo (não há como testar em dispositivo real
# aqui) para os modelos usados com Retrofit/kotlinx-serialization.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class br.com.nutrimetric.app.**$$serializer { *; }
-keepclassmembers class br.com.nutrimetric.app.** {
    *** Companion;
}
-keepclasseswithmembers class br.com.nutrimetric.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Moshi (adapter reflexivo, KotlinJsonAdapterFactory) ----------
# MealRepository serializa/desserializa Alimento via reflexão (moshi-kotlin,
# não codegen) para gravar a lista de alimentos da refeição no Room. Sem
# essas regras o R8 pode renomear/remover campos e quebrar o parsing.
-keep class kotlin.Metadata { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.FromJson <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonQualifier interface *
-keep class br.com.nutrimetric.app.data.remote.Alimento { *; }

# ---------- Retrofit / OkHttp ----------
# Reforço além do consumer-rules.txt já embutido no Retrofit 2.6+.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- TensorFlow Lite ----------
# Interpreter usa binding JNI; classes precisam sobreviver ao shrink.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ---------- RevenueCat ----------
# SDK de pagamento — mantemos por completo em vez de confiar só no
# consumer-rules.txt, já que não há como testar compra real neste ambiente.
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# ---------- kotlinx.coroutines ----------
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
