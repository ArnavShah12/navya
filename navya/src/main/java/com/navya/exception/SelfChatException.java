package com.navya.exception;

public class SelfChatException extends NavyaException {
    public SelfChatException(String message) { super(409, "SELF_CHAT", message); }
}
