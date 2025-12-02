package sam.packer.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import sam.packer.Packer;
import sam.packer.PackerManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class PackerServer {
    private final PackerManager manager;
    private HttpServer httpserver;


    public PackerServer(PackerManager manager, int port) {
        this.manager = manager;

        try {
            httpserver = HttpServer.create(new InetSocketAddress(port), 0);
            httpserver.createContext("/", new TestHandler());

            httpserver.setExecutor(null);
            httpserver.start();

            Packer.LOGGER.info("Packer server running on port {}", port);
        } catch (IOException e) {
            Packer.LOGGER.error("Failed to create HTTP server: {}", e.getMessage());
        }
    }

    public void serverClosing() {
        if(httpserver != null) {
            httpserver.stop(0);
        }
    }

    static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                InputStream is = getClass().getResourceAsStream("/html/landing.html");
                if (is == null) {
                    sendResponse(exchange, 404, "HTML resource not found.");
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    // Transfer bytes from the HTML file to the response body
                    is.transferTo(os);
                }
                is.close();

            } catch (Exception e) {
                Packer.LOGGER.warn(e.getMessage());
                sendResponse(exchange, 500, "Packer server error during page delivery.");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) {
            try {
                exchange.sendResponseHeaders(statusCode, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (Exception e) { // We don't care if this errors
            }
        }
    }

}
