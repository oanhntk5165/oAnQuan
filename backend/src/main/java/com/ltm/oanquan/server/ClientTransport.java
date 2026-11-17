package com.ltm.oanquan.server;

import java.io.IOException;

/** Transport-specific operations used by the shared request handler. */
interface ClientTransport {

    boolean isOpen();

    void sendText(String message) throws IOException;
}
