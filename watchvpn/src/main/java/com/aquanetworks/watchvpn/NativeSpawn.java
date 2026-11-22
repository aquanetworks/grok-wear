package com.aquanetworks.watchvpn;

/** JNI helper: fork+exec libmihomo.so with the VpnService TUN fd inherited as fd 3. */
final class NativeSpawn {
    static { System.loadLibrary("watchvpn-spawn"); }
    /**
     * @param tunFd TUN fd from {@code ParcelFileDescriptor.detachFd()}, or -1 for API-only mode
     * @return child pid, or a negative errno
     */
    static native int start(String binary,String home,String configPath,int tunFd);
    /** @return -1 if still running, else exit/signal status */
    static native int waitFor(int pid,boolean nohang);
    static native void terminate(int pid);
    static native void killNow(int pid);
}
