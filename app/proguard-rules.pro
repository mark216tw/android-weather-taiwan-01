# Retrofit reads endpoint annotations at runtime.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-keep interface tw.app.taiwanweather.data.CwaApi
-keep interface tw.app.taiwanweather.data.MoenvApi
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
