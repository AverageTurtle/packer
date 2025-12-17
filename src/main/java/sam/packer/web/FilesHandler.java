package sam.packer.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import sam.packer.PackerManager;

import java.io.IOException;
import java.util.UUID;

class FilesHandler implements HttpHandler {
    private final PackerServer server;
    private final PackerManager manager;

    public FilesHandler(PackerServer server, PackerManager manager) {
        this.server = server;
        this.manager = manager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        UUID player_uuid = server.get_session(exchange);
        if(player_uuid == null) {
            WebUtils.redirect(exchange, "/login");
            return;
        }
        WebUtils.send_html(exchange, "/html/files.html");
    }
}