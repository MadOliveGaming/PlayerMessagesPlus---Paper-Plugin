package com.madolivegaming.playermessagesplus;

public record ValidationIssue(String file, String location, String detail) {
    @Override public String toString() {
        return file + (location == null || location.isBlank() ? "" : " [" + location + "]") + ": " + detail;
    }
}
