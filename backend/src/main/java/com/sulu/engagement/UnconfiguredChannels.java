package com.sulu.engagement;

import org.springframework.stereotype.Component;

/** Placeholders: no email/SMS/push provider is wired yet. They never claim a message was sent. */
public class UnconfiguredChannels {
    private UnconfiguredChannels() {}

    @Component public static class Email implements ExternalChannel {
        public String name() { return "email"; } public boolean configured() { return false; }
        public void send(long u, String t, String b) { throw new UnsupportedOperationException("email provider not configured"); }
    }
    @Component public static class Sms implements ExternalChannel {
        public String name() { return "sms"; } public boolean configured() { return false; }
        public void send(long u, String t, String b) { throw new UnsupportedOperationException("sms provider not configured"); }
    }
    @Component public static class Push implements ExternalChannel {
        public String name() { return "push"; } public boolean configured() { return false; }
        public void send(long u, String t, String b) { throw new UnsupportedOperationException("push provider not configured"); }
    }
}
