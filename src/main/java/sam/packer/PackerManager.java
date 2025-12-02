package sam.packer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

// This class is responsible for managing the resource pack. As well as authentication
public class PackerManager {
    private final MinecraftServer server;

    public PackerManager(MinecraftServer server) {
        this.server = server;
    }

    public ServerPlayer getPlayersOrOffline(String address) {
        List<ServerPlayer> players = server.getPlayerList().getPlayersWithAddress(address);
        if(players.size() != 1) {
            return null;
        }
        return  players.getFirst();
    }
}
