package sam.packer;

import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public class PackerConfig {
    private static final Path CONFIG_PATH = Path.of("config", "packer.properties");
    public static String server_address = "localhost";
    public static int port = 8081;

    public static void load() {
        File configFile = CONFIG_PATH.toFile();
        Properties props = new Properties();

        if (configFile.exists()) {
            try (InputStream is = new FileInputStream(configFile)) {
                props.load(is);
                server_address = props.getProperty("server-address", "localhost");
                port = Integer.parseInt(props.getProperty("port", "8081"));
            } catch (IOException e) {
                Packer.LOGGER.error("Failed to load config: {}", e.getMessage());
            }
        } else {
            save(); // Create default file
        }
    }

    public static void save() {
        try (OutputStream os = new FileOutputStream(CONFIG_PATH.toFile())) {
            Properties props = new Properties();
            props.setProperty("server-address", server_address);
            props.setProperty("port", String.valueOf(port));
            props.store(os, "Packer Configuration\nSet server-address to your Public IP or Domain.\n");
        } catch (IOException e) {
            Packer.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

}
