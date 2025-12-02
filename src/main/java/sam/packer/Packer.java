package sam.packer;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sam.packer.web.PackerServer;

public class Packer implements ModInitializer {

	public static final String MOD_ID = "packer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static PackerServer packer_server;
    private static PackerManager packer_manger;

	@Override
	public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            packer_manger = new PackerManager(server);
            packer_server = new PackerServer(packer_manger, 8000);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            packer_server.serverClosing();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("packer")
                .executes(context -> {
                    context.getSource().sendSystemMessage(Component.literal("Welcome to packer!"));
                    return 1;
                }
                )));

        ServerPlayerEvents.LEAVE.register(player -> {

        });


	}


}

