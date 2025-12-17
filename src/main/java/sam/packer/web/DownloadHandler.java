package sam.packer.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import sam.packer.PackerManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

class DownloadHandler implements HttpHandler {
    private final PackerManager manager;

    public DownloadHandler(PackerManager manager) {
        this.manager = manager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Path to the generated pack.zip
        Path zip_path = manager.packer_path.resolve("pack.zip");

        if (!Files.exists(zip_path)) {
            WebUtils.send_response(exchange, 404, "Pack file not generated yet.");
            return;
        }

        // Set headers to tell the browser this is a file download
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"pack.zip\"");

        // Send the file
        exchange.sendResponseHeaders(200, Files.size(zip_path));
        try (OutputStream os = exchange.getResponseBody()) {
            Files.copy(zip_path, os);
        }
    }
}