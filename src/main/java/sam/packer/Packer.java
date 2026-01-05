package sam.packer;

import net.fabricmc.api.DedicatedServerModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sam.packer.web.PackerServer;


public class Packer implements DedicatedServerModInitializer {

	public static final String MOD_ID = "packer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static PackerServer packer_server;

    protected static PackerManager packer_manger;

    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PackerManager.register_command(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PackerConfig.load();
            packer_manger = new PackerManager((DedicatedServer) server);
            packer_server = new PackerServer(packer_manger, PackerConfig.port);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> packer_server.serverClosing());

        ServerPlayerEvents.JOIN.register(player -> packer_manger.player_joining(player));
        ServerPlayerEvents.LEAVE.register(player -> {
            packer_manger.player_leaving(player);
            packer_server.player_leaving(player);
        });
    }
}

