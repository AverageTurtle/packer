package sam.packer.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import sam.packer.PackerManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

class LoginHandler implements HttpHandler {
    private final PackerManager manager;
    private final PackerServer server;

    public LoginHandler(PackerServer server, PackerManager manager) {
        this.manager = manager;
        this.server = server;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if(method.equals("GET")) {
            WebUtils.send_html(exchange, "/html/login.html");
        } else if(method.equals("POST")) {
            String raw_body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form_data = WebUtils.parse_form_data(raw_body);
            String submitted_code = form_data.get("authcode");
            UUID player_uuid = manager.authenticate_link_code(submitted_code);
            if(player_uuid != null) {
                UUID session_id = server.new_session(player_uuid);
                exchange.getResponseHeaders().add("Set-Cookie", "session_id=" + session_id + "; Path=/; HttpOnly");

                WebUtils.redirect(exchange, "/files");
            } else {
                WebUtils.send_response(exchange, 401, "<h1>Invalid Code. Please try again.</h1>");
            }
        }

    }


}
