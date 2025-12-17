package sam.packer.web;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import sam.packer.Packer;
import sam.packer.PackerManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

public class DeleteHandler implements HttpHandler {
    private final PackerServer server;
    private final PackerManager manager;

    public DeleteHandler(PackerServer server, PackerManager manager) {
        this.server = server;
        this.manager = manager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        UUID player_uuid = server.get_session(exchange);
        if(player_uuid == null) {
            WebUtils.send_response(exchange, 401, "Unauthorized");
            return;
        }
        if(!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            WebUtils.send_response(exchange, 405, "Method Not Allowed");
            return;
        }

        String content_type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (content_type == null || !content_type.contains("pplication/json; charset=UTF-8")) {
            WebUtils.send_response(exchange, 400, "Content-Type must be application/json; charset=UTF-8");
            return;
        }
        var json = JsonParser.parseReader( new InputStreamReader(exchange.getRequestBody()));
        var name = json.getAsJsonObject().get("name").getAsString();
        var type = json.getAsJsonObject().get("type").getAsString();

        var asset_type = PackerManager.AssetType.from_string(type);
        if(asset_type == null) {
            WebUtils.send_response(exchange, 400, "Invalid asset type: "+type);
            return;
        }
        try { manager.delete_file(player_uuid, asset_type, name); } catch (IOException e) {
            WebUtils.send_response(exchange, 400, e.getMessage());
            return;
        }
        WebUtils.send_response(exchange, 200, "File has been deleted!");
    }
}
