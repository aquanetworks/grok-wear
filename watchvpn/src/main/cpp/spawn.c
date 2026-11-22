#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/wait.h>
#include <android/log.h>

#define TAG "watchvpn-spawn"
#define TUN_KEEP 3

JNIEXPORT jint JNICALL
Java_com_aquanetworks_watchvpn_NativeSpawn_start(JNIEnv *env, jclass clazz,
        jstring jBinary, jstring jHome, jstring jConfig, jint tunFd) {
    if (jBinary == NULL || jHome == NULL || jConfig == NULL) return -EINVAL;
    const char *binary = (*env)->GetStringUTFChars(env, jBinary, NULL);
    const char *home = (*env)->GetStringUTFChars(env, jHome, NULL);
    const char *config = (*env)->GetStringUTFChars(env, jConfig, NULL);
    if (binary == NULL || home == NULL || config == NULL) {
        if (binary) (*env)->ReleaseStringUTFChars(env, jBinary, binary);
        if (home) (*env)->ReleaseStringUTFChars(env, jHome, home);
        if (config) (*env)->ReleaseStringUTFChars(env, jConfig, config);
        return -ENOMEM;
    }

    pid_t pid = fork();
    if (pid == 0) {
        /* Child: Java UTF strings are still valid in this address space until exec. */
        signal(SIGPIPE, SIG_IGN);
        if (setsid() < 0) { /* keep going */ }
        if (chdir(home) != 0) _exit(126);

        char logpath[512];
        snprintf(logpath, sizeof(logpath), "%s/mihomo.log", home);
        int logfd = open(logpath, O_WRONLY | O_CREAT | O_TRUNC, 0600);
        if (logfd >= 0) {
            dup2(logfd, STDOUT_FILENO);
            dup2(logfd, STDERR_FILENO);
            if (logfd != STDOUT_FILENO && logfd != STDERR_FILENO) close(logfd);
        }

        if (tunFd >= 0) {
            if (tunFd != TUN_KEEP) {
                if (dup2(tunFd, TUN_KEEP) < 0) _exit(125);
                if (tunFd != TUN_KEEP) close(tunFd);
            }
            fcntl(TUN_KEEP, F_SETFD, 0); /* inherit across exec */
        }

        long maxfd = sysconf(_SC_OPEN_MAX);
        if (maxfd < 0 || maxfd > 1024) maxfd = 256;
        for (int fd = 0; fd < (int) maxfd; fd++) {
            if (fd == STDIN_FILENO || fd == STDOUT_FILENO || fd == STDERR_FILENO) continue;
            if (tunFd >= 0 && fd == TUN_KEEP) continue;
            close(fd);
        }

        char *argv[] = {(char *) binary, "-d", (char *) home, "-f", (char *) config, NULL};
        execv(binary, argv);
        _exit(127);
    }

    (*env)->ReleaseStringUTFChars(env, jBinary, binary);
    (*env)->ReleaseStringUTFChars(env, jHome, home);
    (*env)->ReleaseStringUTFChars(env, jConfig, config);
    if (pid < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "fork failed: %s", strerror(errno));
        return -errno;
    }
    return pid;
}

JNIEXPORT jint JNICALL
Java_com_aquanetworks_watchvpn_NativeSpawn_waitFor(JNIEnv *env, jclass clazz, jint pid, jboolean nohang) {
    if (pid <= 0) return -EINVAL;
    int status = 0;
    pid_t r = waitpid((pid_t) pid, &status, nohang ? WNOHANG : 0);
    if (r == 0) return -1; /* still running */
    if (r < 0) return -errno;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return status;
}

JNIEXPORT void JNICALL
Java_com_aquanetworks_watchvpn_NativeSpawn_terminate(JNIEnv *env, jclass clazz, jint pid) {
    if (pid > 0) kill((pid_t) pid, SIGTERM);
}

JNIEXPORT void JNICALL
Java_com_aquanetworks_watchvpn_NativeSpawn_killNow(JNIEnv *env, jclass clazz, jint pid) {
    if (pid > 0) kill((pid_t) pid, SIGKILL);
}
