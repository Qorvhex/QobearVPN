# Keep JNI OpenVPN 3 bridge and VpnService session callbacks intact under R8
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.qorvhex.qobearvpn.core.OpenVpnNative { *; }
-keepclassmembers class com.qorvhex.qobearvpn.core.OpenVpnNative { *; }

-keepclassmembers class com.qorvhex.qobearvpn.service.QobearVpnService$Session {
    @androidx.annotation.Keep *;
    boolean configureTun(java.lang.String, int);
    boolean addAddress(java.lang.String, int);
    boolean addRoute(java.lang.String, int);
    boolean addDns(java.lang.String, int);
    boolean addSearchDomain(java.lang.String, int);
    int establishTun();
    void onCoreEvent(java.lang.String, boolean);
    void onCoreTraffic(long, long);
    void onCoreLog(java.lang.String);
}

-keepattributes *Annotation*
