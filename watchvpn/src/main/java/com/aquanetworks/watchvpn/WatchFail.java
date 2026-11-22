package com.aquanetworks.watchvpn;

/** User-visible failure. Messages must never include subscription URLs or credentials. */
final class WatchFail extends Exception {
    WatchFail(String message){super(message);}
    static WatchFail of(String message){return new WatchFail(message);}
}
