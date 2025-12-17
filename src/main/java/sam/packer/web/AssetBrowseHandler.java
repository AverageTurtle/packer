package sam.packer.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import sam.packer.Packer;
import sam.packer.PackerManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public class AssetBrowseHandler implements HttpHandler {
    private final Path sub_path;
    private final PackerServer server;
    private final PackerManager manager;

    public AssetBrowseHandler(Path sub_path, PackerServer server, PackerManager manager) {
        this.sub_path = sub_path;
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

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        Path path = manager.assets_path.resolve(player_uuid.toString()).resolve(sub_path);

        JsonArray json_array = new JsonArray();

        File[] children = path.toFile().listFiles();
        if(children != null) {
            for(File child : children) {
                JsonObject file = new JsonObject();
                file.addProperty("name", child.getName());
                json_array.add(file);
            }
        }

        WebUtils.send_response(exchange, 200, json_array.toString());
    }
}
