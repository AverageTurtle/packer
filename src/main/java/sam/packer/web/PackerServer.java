package sam.packer.web;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.level.ServerPlayer;
import sam.packer.Packer;
import sam.packer.PackerManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.UUID;

// This class is responsible for the web facing actions
public class PackerServer {
    //private final PackerManager manager;
    private HttpServer httpserver;
    private final BiMap<UUID, UUID> sessions; // Session UUID to Player UUID


    public PackerServer(PackerManager manager, int port) {
        //this.manager = manager;
        sessions = HashBiMap.create();

        try {
            httpserver = HttpServer.create(new InetSocketAddress(port), 0);
            httpserver.createContext("/", new LanderHandler(this));
            httpserver.createContext("/login", new LoginHandler(this, manager));
            httpserver.createContext("/files", new FilesHandler(this, manager));
            httpserver.createContext("/api/upload", new UploadHandler(this, manager));
            httpserver.createContext("/api/pack.zip", new DownloadHandler(manager));
            httpserver.createContext("/api/delete", new DeleteHandler(this, manager));

            httpserver.createContext("/api/get/models", new AssetBrowseHandler(Path.of("models"), this, manager));
            httpserver.createContext("/api/get/item-definitions", new AssetBrowseHandler(Path.of("items"), this, manager));
            httpserver.createContext("/api/get/textures", new AssetBrowseHandler(Path.of("textures", "item"), this, manager));

            httpserver.setExecutor(null);
            httpserver.start();

            Packer.LOGGER.info("Packer server running on port {}", port);
        } catch (IOException e) {
            Packer.LOGGER.error("Failed to create HTTP server: {}", e.getMessage());
        }
    }

    // Returns the UUID of the session owner or null
    public UUID get_session(HttpExchange exchange) {
        String cookie_header = exchange.getRequestHeaders().getFirst("Cookie");
        UUID session_id = WebUtils.parse_session_id(cookie_header);

        if(session_id != null) {
            return sessions.get(session_id);
        } else {
            return null;
        }
    }

    public UUID new_session(UUID player_uuid) {
        sessions.inverse().remove(player_uuid);
        UUID new_session = UUID.randomUUID();
        sessions.put(new_session, player_uuid);
        return new_session;
    }

    public void player_leaving(ServerPlayer player) {
        sessions.inverse().remove(player.getUUID());
    }

    public void serverClosing() {
        if(httpserver != null) {
            httpserver.stop(0);
        }
    }



}
