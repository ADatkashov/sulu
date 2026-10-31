package com.sulu.engagement;

/** Provider seam for email / SMS / push. Real providers implement this and report configured()==true. */
public interface ExternalChannel {
    String name();
    boolean configured();
    void send(long userId, String title, String body);
}
