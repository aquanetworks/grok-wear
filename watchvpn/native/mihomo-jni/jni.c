#include <jni.h>
#include <stdlib.h>
#include "_cgo_export.h"

static jstring jempty(JNIEnv *env) {
    return (*env)->NewStringUTF(env, "");
}

JNIEXPORT jstring JNICALL
Java_com_aquanetworks_watchvpn_MihomoJni_nativeStart(JNIEnv *env, jclass cls, jstring jHome, jstring jConfig) {
    if (jHome == NULL || jConfig == NULL) return (*env)->NewStringUTF(env, "empty path");
    const char *home = (*env)->GetStringUTFChars(env, jHome, NULL);
    const char *config = (*env)->GetStringUTFChars(env, jConfig, NULL);
    char *err = MihomoStart((char *) home, (char *) config);
    (*env)->ReleaseStringUTFChars(env, jHome, home);
    (*env)->ReleaseStringUTFChars(env, jConfig, config);
    jstring out = (*env)->NewStringUTF(env, err ? err : "");
    if (err) free(err);
    return out;
}

JNIEXPORT void JNICALL
Java_com_aquanetworks_watchvpn_MihomoJni_nativeStop(JNIEnv *env, jclass cls) {
    MihomoStop();
}

JNIEXPORT jboolean JNICALL
Java_com_aquanetworks_watchvpn_MihomoJni_nativeRunning(JNIEnv *env, jclass cls) {
    return MihomoRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_aquanetworks_watchvpn_MihomoJni_nativeVersion(JNIEnv *env, jclass cls) {
    char *v = MihomoVersion();
    jstring out = (*env)->NewStringUTF(env, v ? v : "");
    if (v) free(v);
    return out;
}

JNIEXPORT jstring JNICALL
Java_com_aquanetworks_watchvpn_MihomoJni_nativeLoadError(JNIEnv *env, jclass cls) {
    return jempty(env);
}
