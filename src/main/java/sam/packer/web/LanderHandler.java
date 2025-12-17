package sam.packer.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import sam.packer.PackerManager;

import java.io.IOException;
import java.util.UUID;

class LanderHandler implements HttpHandler {
    private final PackerServer server;

    public LanderHandler(PackerServer server) {
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        UUID player_uuid = server.get_session(exchange);
        if(player_uuid == null) {
            WebUtils.redirect(exchange, "/login");
            return;
        }
        WebUtils.redirect(exchange, "/files");
    }
}
