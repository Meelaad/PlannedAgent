package ca.yorku.plannedagent.ai;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** A tiny local HTTP server that records the last request and answers with a fixed reply. */
class StubServer implements AutoCloseable {

    private final HttpServer server;
    volatile int status = 200;
    volatile String reply = "{}";
    volatile String lastPath;
    volatile String lastQuery;
    volatile String lastBody;
    volatile String lastApiKeyHeader;

    StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastQuery = exchange.getRequestURI().getRawQuery();
            lastApiKeyHeader = exchange.getRequestHeaders().getFirst("x-goog-api-key");
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
