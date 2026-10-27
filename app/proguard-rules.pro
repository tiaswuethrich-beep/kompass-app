# Room-Entitäten und Receiver werden über das Manifest bzw. Reflection gefunden.
-keep class ch.akh.kompass.data.** { *; }

# Anthropic-SDK (KI): Jackson liest und schreibt die Modellklassen per Reflection.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,MethodParameters
-dontwarn com.anthropic.**
-dontwarn com.fasterxml.jackson.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn kotlin.reflect.jvm.internal.**
-dontwarn io.opentelemetry.**
-dontwarn com.sun.net.httpserver.**
-dontwarn java.lang.management.**
# JSON-Schema-Generator des SDK (nur für strukturierte Ausgaben, hier ungenutzt) nutzt Java-APIs, die Android nicht hat.
-dontwarn com.github.victools.**
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedArrayType
-dontwarn java.lang.reflect.AnnotatedWildcardType
-dontwarn java.lang.reflect.AnnotatedTypeVariable
