package sam.packer.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class WebUtils {
    public static Map<String, String> parse_form_data(String formData) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                map.put(key, value);
            }
        }
        return map;
    }
    public static void redirect(HttpExchange exchange, String path) throws IOException {
        exchange.getResponseHeaders().add("Location", path);
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_MOVED_TEMP, -1);
        exchange.close();
    }

    public static void send_html(HttpExchange exchange, String path) throws IOException {
        InputStream is = WebUtils.class.getResourceAsStream(path);
        if (is == null) {
            WebUtils.send_response(exchange, 404, "HTML resource not found.");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream os = exchange.getResponseBody()) {
            is.transferTo(os);
        }
        is.close();
    }

    public static void send_response(HttpExchange exchange, int statusCode, String response) {
        try {
            exchange.sendResponseHeaders(statusCode, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        } catch (Exception e) { // We don't care if this errors
        }
    }

    public static UUID parse_session_id(String cookie_header) {
        if (cookie_header == null) return null;
        String[] cookies = cookie_header.split(";");
        for (String cookie : cookies) {
            cookie = cookie.trim();
            if (cookie.startsWith("session_id=")) {
                return UUID.fromString(cookie.substring("session_id=".length()));
            }
        }
        return null;
    }

}
