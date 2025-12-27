package sam.packer;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


// This class is responsible for in-game facing actions
public class PackerManager {
    public enum AssetType {
        texture {
            @Override
            public String subdir() {
                return "textures";
            }

            @Override
            public boolean is_valid_extension(String extension) {
                return extension.equals("png") || extension.equals("mcmeta");
            }
        },
        model{
            @Override
            public String subdir() {
                return "models";
            }

            @Override
            public boolean is_valid_extension(String extension) {
                return extension.equals("json");
            }
        },
        item_definition {
            @Override
            public String subdir() {
                return "items";
            }

            @Override
            public boolean is_valid_extension(String extension) {
                return extension.equals("json");
            }
        };

        public abstract String subdir();
        public abstract boolean is_valid_extension(String extension);

        public static AssetType from_string(String type) {
            return switch (type) {
                case "item-definition" -> AssetType.item_definition;
                case "model" -> AssetType.model;
                case "texture" -> AssetType.texture;
                default -> null;
            };
        }
    }

    public final DedicatedServer server;
    private final BiMap<String, UUID> pending_codes;
    protected static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public final Path packer_path;
    public final Path pack_path;
    public final Path assets_path;

    private MinecraftServer.ServerResourcePackInfo packinfo;
    public boolean pack_has_changed = true;
    public PackerManager(DedicatedServer server) {

        pending_codes = HashBiMap.create();
        this.server = server;
        packer_path = Paths.get("packer");
        if(!Files.exists(packer_path)) {
            try { Files.createDirectory(packer_path); } catch (Exception ignored) {}
        }
        pack_path = Paths.get("packer/pack");
        if(!Files.exists(pack_path)) {
            try { Files.createDirectory(pack_path); } catch (Exception ignored) {}
        }
        Path meta_path = pack_path.resolve("pack.mcmeta");

        assets_path = pack_path.resolve("assets");
        if(!Files.exists(assets_path)) {
            try { Files.createDirectory(assets_path); } catch (Exception ignored) {}
        }

        try {
            if(Files.exists(meta_path)) {
                Files.delete(meta_path);
            }

            Files.createFile(meta_path);
            Files.write(meta_path, (
                    """
                    {
                      "pack": {
                        "description": "Packer generated pack",
                        "min_format": 69,
                        "max_format": 75
                      }
                    }
                    """).getBytes());
            rezip_pack();
        } catch (Exception ignored) {}
    }

    public void upload_file(UUID uploader, AssetType type, String name, byte[] contents) throws IOException {
        // Check contents
        if(contents.length > PackerConfig.max_upload_bytes) throw new IOException("File too big. Limit: "+PackerConfig.max_upload_bytes+" Bytes");

        var split_name = name.split("\\.");
        var ext = split_name[split_name.length-1];
        if(!type.is_valid_extension(ext)) throw new IOException("Invalid extension for upload type!");

        if(ext.equals("png")) {
            if(!png_is_mipmap_safe(contents)) {
                throw new IOException("Image resolution must be a power of 2!");
            }
        }

        // Replace packer_id
        if(ext.equals("json")) {
            var str = new String(contents, StandardCharsets.UTF_8);
            str = str.replaceAll("packer_id", uploader.toString());
            contents = str.getBytes(StandardCharsets.UTF_8);
        }

        // Preform upload
        Path user_path = assets_path.resolve(uploader.toString());
        if(!Files.exists(user_path)) { Files.createDirectory(user_path); }
        Path asset_path = user_path.resolve(type.subdir());
        if(!Files.exists(asset_path)) { Files.createDirectory(asset_path); }
        // TODO band-aid fix.
        if(type == AssetType.texture) {
            asset_path = asset_path.resolve("item");
            if(!Files.exists(asset_path)) { Files.createDirectory(asset_path); }
        }
        Path file_path =  asset_path.resolve(name);
        if(Files.exists(file_path)) {
            throw new IOException("File already exist");
        }
        Files.createFile(file_path);
        Files.write(file_path, contents);
        pack_has_changed = true;
    }
    public void delete_file(UUID deleter, AssetType type, String name) throws IOException {
        Path user_path = assets_path.resolve(deleter.toString());
        Path asset_path = user_path.resolve(type.subdir());
        // TODO band-aid fix.
        if(type == AssetType.texture) {
            asset_path = asset_path.resolve("item");
            if(!Files.exists(asset_path)) { Files.createDirectory(asset_path); }
        }
        Path file_path =  asset_path.resolve(name);
        Files.delete(file_path);
        pack_has_changed = true;
    }

    public void rezip_pack() throws IOException{
        pack_has_changed = false;
        Path zip_path = packer_path.resolve("pack.zip");
        if(Files.exists(zip_path)) {
            Files.delete(zip_path);
        }
        Files.createFile(zip_path);
        FileOutputStream fos = new FileOutputStream(zip_path.toFile());
        ZipOutputStream zip_out = new ZipOutputStream(fos);

        File[] children = pack_path.toFile().listFiles();
        assert children != null;
        for(File child : children) {
            zip_file(child, child.getName(), zip_out);
        }
        zip_out.close();
        fos.close();

        String url = "http://"+PackerConfig.server_address + ":" + PackerConfig.port + "/api/pack.zip";
        String hash = getSHA1(zip_path.toFile());
        packinfo = new MinecraftServer.ServerResourcePackInfo(UUID.randomUUID(), url,
                hash, true, Component.literal("Please accept the Packer resource pack!."));
    }

    public void send_pack(ServerPlayer player) {
        ClientboundResourcePackPushPacket packet = new ClientboundResourcePackPushPacket(packinfo.id(), packinfo.url(), packinfo.hash(), packinfo.isRequired(), Optional.ofNullable(packinfo.prompt()));
        player.connection.send(packet);
    }

    public void player_joining(ServerPlayer player) {
        send_pack(player);
    }
    public void player_leaving(ServerPlayer player) {
        pending_codes.inverse().remove(player.getUUID());
    }

    // Return a UUID if that link code is valid or null
    public UUID authenticate_link_code(String code) {
        return pending_codes.get(code);
    }

    public String new_link_code(ServerPlayer player) {
        int number = SECURE_RANDOM.nextInt(1_000_000);
        String code = String.format("%06d", number);
        pending_codes.forcePut(code, player.getUUID());
        return code;
    }

    public static void register_command(CommandDispatcher<CommandSourceStack> dispatcher) {
        var node = Commands.literal("packer")
                .executes(context -> {
                    context.getSource().sendSystemMessage(Component.literal("Welcome to packer!"));
                    return 1;
                });
        node.then(Commands.literal("link").requires(source -> Permissions.check(source, "packer.link", 2))
                .executes(context -> {
                    var player = context.getSource().getPlayer();
                    if(player == null) return 0;
                    var link = "http://"+PackerConfig.server_address+":"+PackerConfig.port;
                    player.sendSystemMessage(Component.literal(link).withStyle(style -> style
                            .withColor(ChatFormatting.BLUE)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(link)))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy link code")))
                    ));
                    return 1;
                }));

        node.then(Commands.literal("auth").requires(source -> Permissions.check(source, "packer.auth", 2))
                        .executes(context -> {
                            var player = context.getSource().getPlayer();
                            if(player == null) return 0;
                            var code = Packer.packer_manger.new_link_code(player);
                            player.sendSystemMessage(Component.literal("Your link code is: ").append(Component.literal(code).withStyle(style -> style
                                    .withColor(ChatFormatting.BLUE)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent.CopyToClipboard(code))
                                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy link code")))
                            )));
                            return 1;
                        }));

        node.then(Commands.literal("rezip").requires(source -> Permissions.check(source, "packer.zip", 2))
                .executes(context -> {
                    var player = context.getSource().getPlayer();
                    if(player == null) return 0;
                    if(!Packer.packer_manger.pack_has_changed) {
                        player.sendSystemMessage(Component.literal("No changes have been made to the pack!").withStyle(ChatFormatting.RED));
                        return 0;
                    }
                    try {
                        Packer.packer_manger.rezip_pack();
                    } catch (IOException e) {
                        return 0;
                    }

                    var players = Packer.packer_manger.server.getPlayerList();

                    var message = Component.literal("");
                    var rezip_message = Component.literal(" has request a pack rezip!\n").withStyle(ChatFormatting.LIGHT_PURPLE);
                    var pack_command_message = Component.literal("run \"/packer pack\" to reload update your resource pack").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY);
                    players.broadcastSystemMessage(message.append(player.getName()).append(rezip_message).append(pack_command_message), false);
                    return 1;
                }));
        var model_node = Commands.literal("model").requires(source -> Permissions.check(source, "packer.modify_items.model", 2));

        model_node.then(Commands.literal("set").then(Commands.argument("model", StringArgumentType.string())
                .executes(context -> {
                    String input_model = StringArgumentType.getString(context, "model");
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ItemStack stack = player.getMainHandItem();

                    if (stack.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("You must be holding an item!"));
                        return 0;
                    }
                    stack.set(DataComponents.ITEM_MODEL, ResourceLocation.fromNamespaceAndPath(player.getStringUUID(), input_model));

                    return 1;
                })));

        model_node.then(Commands.literal("reset").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ItemStack stack = player.getMainHandItem();

                    if (stack.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("You must be holding an item!"));
                        return 0;
                    }
                    stack.set(DataComponents.ITEM_MODEL, stack.getItem().components().get(DataComponents.ITEM_MODEL));

                    return 1;
                }));
        node.then(model_node);

        node.then(Commands.literal("pack")
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("Requesting updated server pack...").withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(Style.EMPTY));
                    ClientboundResourcePackPopPacket pop_packet = new ClientboundResourcePackPopPacket(Optional.empty());
                    player.connection.send(pop_packet);


                    Packer.packer_manger.send_pack(player);
                    return 1;
                }));

        dispatcher.register(node);
    }

    private static void zip_file(File file_to_zip, String file_name, ZipOutputStream zipOut) throws IOException {
        if (file_to_zip.isDirectory()) {
            if (file_name.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(file_name));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(file_name + "/"));
                zipOut.closeEntry();
            }
            File[] children = file_to_zip.listFiles();
            if(children == null){ return; }

            for (File childFile : children) {
                zip_file(childFile, file_name + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(file_to_zip);
        ZipEntry zipEntry = new ZipEntry(file_name);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }

    public static String getSHA1(File file) throws IOException {
        MessageDigest digest = null;
        try { digest = MessageDigest.getInstance("SHA-1"); } catch(NoSuchAlgorithmException ignore) {}
        assert digest != null;

        FileInputStream fis = new FileInputStream(file);
        byte[] byteArray = new byte[1024];
        int bytesCount;

        while ((bytesCount = fis.read(byteArray)) != -1) {
            digest.update(byteArray, 0, bytesCount);
        }
        fis.close();

        StringBuilder sb = new StringBuilder();

        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean png_is_mipmap_safe(byte @NotNull [] contents) throws IOException {
        if(contents.length == 0) return false;
        var bais = new ByteArrayInputStream(contents);
        var iis = ImageIO.createImageInputStream(bais);

        var readers = ImageIO.getImageReaders(iis);
        if(!readers.hasNext()) {
            return false;
        }

        var reader = readers.next();
        try {
            reader.setInput(iis);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);

            return is_power_of_two(width) && is_power_of_two(height);
        } finally {
            reader.dispose();
        }
    }

    private boolean is_power_of_two(int n) {
        return (n > 0) && ((n & (n - 1)) == 0);
    }
}
