package com.sulu.identity;

public record UserDto(long id, String email, String fullName, String phone, String role, String language, String city,
                      boolean notifyEmail, boolean notifyPush) {}
