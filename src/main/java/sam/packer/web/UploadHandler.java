package sam.packer.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import sam.packer.PackerManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class UploadHandler implements HttpHandler {
    private final PackerServer server;
    private final PackerManager manager;

    public UploadHandler(PackerServer server, PackerManager manager) {
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
        // Verify we are dealing with multipart form data
        String content_type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (content_type == null || !content_type.contains("multipart/form-data")) {
            WebUtils.send_response(exchange, 400, "Content-Type must be multipart/form-data");
            return;
        }

        String boundary = content_type.substring(content_type.indexOf("boundary=")+9);
        var multipart = MultipartParser.parse(exchange.getRequestBody(), boundary);
        String asset_type_string = multipart.get("asset-type").toString();

        PackerManager.AssetType asset_type = switch (asset_type_string) {
            case "item-definition" -> PackerManager.AssetType.item_definition;
            case "model" -> PackerManager.AssetType.model;
            case "texture" -> PackerManager.AssetType.texture;
            default -> null;
        };

        if(asset_type == null) {
            WebUtils.send_response(exchange, 400, "Invalid asset type provided!");
            return;
        }

        MultipartParser.FilePart filepart = (MultipartParser.FilePart) multipart.get("asset-file");
        try {
            manager.upload_file(player_uuid, asset_type, filepart.filename, filepart.content);
        } catch (IOException e) {
            WebUtils.send_response(exchange, 400, e.getMessage());
            return;
        }

        WebUtils.send_response(exchange, 200, "File has been uploaded!");
    }

    // based on https://gist.github.com/JensWalter/0f19780d131d903879a2
    private static class MultipartParser {

        static class FilePart {
            String filename;
            byte[] content;

            FilePart(String filename, byte[] content) {
                this.filename = filename;
                this.content = content;
            }
        }

        public static Map<String, Object> parse(InputStream input, String boundary) throws IOException {
            Map<String, Object> result = new HashMap<>();
            byte[] boundaryBytes = ("\r\n--" + boundary).getBytes(StandardCharsets.UTF_8);
            byte[] payload = get_input_as_binary(input);

            List<Integer> offsets = search_bytes(payload, boundaryBytes, 0, payload.length - 1);
            offsets.addFirst(0);
            for(int idx=0;idx<offsets.size();idx++){
                int start_part = offsets.get(idx);
                int end_part = payload.length;

                if(idx<offsets.size()-1){
                    end_part = offsets.get(idx+1);
                }
                byte[] part = Arrays.copyOfRange(payload, start_part, end_part);

                int header_end = index_of(part,"\r\n\r\n".getBytes(StandardCharsets.UTF_8),0,part.length-1);
                if(header_end>0) {
                    byte[] head = Arrays.copyOfRange(part, 0, header_end);
                    String header = new String(head);
                    // extract name from header
                    int name_index = header.indexOf("\r\nContent-Disposition: form-data; name=");
                    if (name_index >= 0) {
                        int start_marker = name_index + 39;
                        //check for extra filename field
                        int file_name_start = header.indexOf("; filename=");

                        var body = Arrays.copyOfRange(part, header_end+4, part.length);
                        // file
                        if (file_name_start >= 0) {
                            String filename = header.substring(file_name_start + 11, header.indexOf("\r\n", file_name_start));
                            var filepart = new FilePart(filename.replace('"', ' ').replace('\'', ' ').trim(), body);
                            var name = header.substring(start_marker, file_name_start).replace('"', ' ').replace('\'', ' ').trim();
                            result.put(name, filepart);
                        }
                        // string
                        else {
                            int end_marker = header.indexOf("\r\n", start_marker);
                            if (end_marker == -1)
                                end_marker = header.length();
                            var name = header.substring(start_marker, end_marker).replace('"', ' ').replace('\'', ' ').trim();
                            result.put(name, new String(body));
                        }
                    }
                }

            }

            return result;
        }


        public static byte[] get_input_as_binary(InputStream requestStream) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[100000];
            int bytesRead;
            while ((bytesRead = requestStream.read(buffer)) != -1){
                bos.write(buffer, 0, bytesRead);
            }
            requestStream.close();
            bos.close();

            return bos.toByteArray();
        }


        //TODO better understand and maybe modify these
        /**
         * Search bytes in byte array returns indexes within this byte-array of all
         * occurrences of the specified(search bytes) byte array in the specified
         * range
         * borrowed from <a href="https://github.com/riversun/finbin/blob/master/src/main/java/org/riversun/finbin/BinarySearcher.java">...</a>
         *
         * @return result index list
         */
        public static List<Integer> search_bytes(byte[] srcBytes, byte[] searchBytes, int searchStartIndex, int searchEndIndex) {
            final int destSize = searchBytes.length;
            final List<Integer> positionIndexList = new ArrayList<>();
            int cursor = searchStartIndex;
            while (cursor < searchEndIndex + 1) {
                int index = index_of(srcBytes, searchBytes, cursor, searchEndIndex);
                if (index >= 0) {
                    positionIndexList.add(index);
                    cursor = index + destSize;
                } else {
                    cursor++;
                }
            }
            return positionIndexList;
        }
        /**
         * Returns the index within this byte-array of the first occurrence of the
         * specified(search bytes) byte array.<br>
         * Starting the search at the specified index, and end at the specified
         * index.
         * borrowed from <a href="https://github.com/riversun/finbin/blob/master/src/main/java/org/riversun/finbin/BinarySearcher.java">...</a>
         *
         */
        public static int index_of(byte[] srcBytes, byte[] searchBytes, int startIndex, int endIndex) {
            if (searchBytes.length == 0 || (endIndex - startIndex + 1) < searchBytes.length) {
                return -1;
            }
            int maxScanStartPosIdx = srcBytes.length - searchBytes.length;
            final int loopEndIdx = Math.min(endIndex, maxScanStartPosIdx);
            int lastScanIdx = -1;
            label: // goto label
            for (int i = startIndex; i <= loopEndIdx; i++) {
                for (int j = 0; j < searchBytes.length; j++) {
                    if (srcBytes[i + j] != searchBytes[j]) {
                        continue label;
                    }
                    lastScanIdx = i + j;
                }
                if (endIndex < lastScanIdx || lastScanIdx - i + 1 < searchBytes.length) {
                    // it becomes more than the last index
                    // or less than the number of search bytes
                    return -1;
                }
                return i;
            }
            return -1;
        }
    }
}
