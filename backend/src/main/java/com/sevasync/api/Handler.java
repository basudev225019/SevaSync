package com.sevasync.api;

/** A route's code. May return a {@link Response}, or any other object, which is sent as a 200 JSON body. */
@FunctionalInterface
public interface Handler {
    Object handle(Request request) throws Exception;
}
