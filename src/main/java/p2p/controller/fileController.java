package p2p.controller;

import p2p.service.FileSharer;

import java.io.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

import org.apache.commons.io.IOUtils;

public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException{
        this.fileSharer = new FileSharer();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.uploadDir= System.getProperty("java.io.tmpdir")+File.separator+"p2p-uploads";
        this.executorService = Executors.newFixedThreadPool(10);

        File uploadDirFile = new File(uploadDir);
        if(!uploadDirFile.exists()){
            uploadDirFile.mkdirs();
        }
        server.createContext("/upload", new UploadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/", new CORSHandler());
        server.setExecutor(executorService);
    }

    public void start(){
        server.start();
        System.out.println("API server started on port " + server.getAddress().getPort());
    }


    public void stop(){
        server.stop(0);
        executorService.shutdown();
        System.out.println("API Server Stopped");
    }

    public class CORSHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange exchange) throws IOException{
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization");

            if(exchange.getRequestMethod().equals("OPTIONS")){
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            // Get, POST는 CORS Handler가 처리하지 않음
            String response = "NOT FOUND";
            exchange.sendResponseHeaders(404, response.getBytes().length);
            try(OutputStream oos = exchange.getResponseBody()){
                oos.write(response.getBytes());
            }


        }
    }

    

    public class UploadHandler implements HttpHandler{
        @Override
        public void handle(HttpExchange exchange) throws IOException{
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin","*");
            
            if(!exchange.getRequestMethod().equalsIgnoreCase("POST")){
                String response = "Method not Allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try(OutputStream os = exchange.getResponseBody()){
                    os.write(response.getBytes());
                }
                return;
            }


            Headers requestHeaders = exchange.getRequestHeaders();
            String contentType = requestHeaders.getFirst("Content-Type");

            Boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/form-data");
            if(!isMultipart){
                String response = "Bad Request: Content-Type muse be multipart/form-data";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try(OutputStream os = exchange.getResponseBody()){ // try-with-resources → 자동 close
                    os.write(response.getBytes());
                }
                return;
            }

            try{

                String boundary = contentType.substring(contentType.indexOf("boundary=")+9);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IOUtils.copy(exchange.getRequestBody(), baos);
                byte[] requestData = baos.toByteArray();

                Multiparser parser = new Multiparser(requestData, boundary);
                Multiparser.ParseResult result = parser.parse();

                if(result == null){
                    String response = "Bad Request: Could not parse file content";
                    exchange.sendResponseHeaders(400, response.getBytes().length);
                    try(OutputStream os = exchange.getResponseBody()){
                        os.write(response.getBytes());
                    }
                    return;
                }

                String filename = result.filename;
                Boolean isUnamed = filename == null || filename.trim().isEmpty();
                if(isUnamed){
                    filename = "unamed-file";
                }
                String uniqueFilename = UUID.randomUUID().toString()+"_"+new File(filename).getName();
                String filePath = uploadDir + File.separator + uniqueFilename;

                try(FileOutputStream fos = new FileOutputStream(filePath)){
                    fos.write(result.fileContent);
                }

                int port = fileSharer.offerFile(filePath);
                new Thread(()->fileSharer.startFileServer(port)).start();
                String jsonResponse = "{\"port\":" + port + "}";
                headers.add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                try(OutputStream os = exchange.getResponseBody()){
                    os.write(jsonResponse.getBytes());
                }
            }
            catch(Exception e){
                System.err.println("Error processing upload: "+ e.getMessage());
                String response = "Server Error: "+e.getMessage();
                exchange.sendResponseHeaders(500, response.getBytes().length);
                try(OutputStream os = exchange.getResponseBody()){ // try-with-resources → 자동 close
                    os.write(response.getBytes());
                }

            }
            

        }
    }


    public static class Multiparser{

        /* HTTP Request: 브라우저에서 <input type="file">로 hello.txt 업로드
        POST /upload HTTP/1.1
        Host: localhost:8080
        Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryABC123

        ------WebKitFormBoundaryABC123
        Content-Disposition: form-data; name="file"; filename="hello.txt"
        Content-Type: text/plain

        Hello World!
        This is a test file.
        ------WebKitFormBoundaryABC123--

        */
        private final byte[] data;
        private final String boundary;

        public Multiparser(byte[] data, String boundary){
            this.data= data;
            this.boundary= boundary;
        }


        public ParseResult parse(){
            try{
                // multipart 헤더 파싱을 문자열 기반으로 하기 위함
                String dataAsString = new String(data);

                // Filename 추출
                String filenameMarker = "filename=\"";
                int filenameStart = dataAsString.indexOf(filenameMarker);
                if(filenameStart ==-1){
                    return null;
                }
                filenameStart +=filenameMarker.length();
                int filenameEnd = dataAsString.indexOf("\"", filenameStart);
                String filename = dataAsString.substring(filenameStart, filenameEnd);


                // Content-Type 추출
                String contentTypeMarker = "Content-Type: ";
                int contentTypeStart = dataAsString.indexOf(contentTypeMarker, filenameEnd);
                String contentType ="application/octet-stream";
                if(contentTypeStart != -1){
                    contentTypeStart += contentTypeMarker.length();
                    int contentTypeEnd = dataAsString.indexOf("\r\n", contentTypeStart);
                    contentType = dataAsString.substring(contentTypeStart, contentTypeEnd);
                }

                // 헤더 끝 찾기
                String headerEndMarker = "\r\n\r\n";
                int headerEnd = dataAsString.indexOf(headerEndMarker);
                if(headerEnd == -1){return null;}

                int contentStart = headerEnd + headerEndMarker.length();

                byte[] boundaryBytes = ("\r\n--"+boundary+"--").getBytes();
                int contentEnd = findSequence(data, boundaryBytes, contentStart);

                if(contentEnd ==-1){
                    boundaryBytes = ("\r\n--"+boundary).getBytes();
                    contentEnd = findSequence(data, boundaryBytes, contentStart);
                }

                if(contentEnd ==-1 || contentEnd <= contentStart){return null;}

                byte[] fileContent = new byte[contentEnd - contentStart];
                System.arraycopy(data, contentStart, fileContent, 0, fileContent.length);

                return new ParseResult(filename, contentType, fileContent);

            }
            catch(Exception e){
                System.err.println("Error parsing multipart data: "+ e.getMessage());
                return null;
            }

        }
        public static class ParseResult {
            public final String filename;
            public final String contentType;
            public final byte[] fileContent;
            
            public ParseResult(String filename, String contentType, byte[] fileContent) {
                this.filename = filename;
                this.contentType = contentType;
                this.fileContent = fileContent;
            }
        }
    
        private int findSequence(byte[] data, byte[] sequence, int startPos) {
            outer:
            for (int i = startPos; i <= data.length - sequence.length; i++) {
                for (int j = 0; j < sequence.length; j++) {
                    if (data[i + j] != sequence[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }

    private class DownloadHandler implements HttpHandler{

        /*
        브라우저는 TCP 소켓 직접 연결 못함
        - 서버가 대신 소켓 연결 -> 파일 받아서 HTTP 다운로드로 변환
        */
        @Override
        public void handle(HttpExchange exchange) throws IOException{
            // CORS 허용
            Headers headers = exchange.getResponseHeaders();
            headers.add("Access-Control-Allow-Origin","*");


            // GET 요청만 허용
            Boolean isGet = exchange.getRequestMethod().equalsIgnoreCase("GET");
            if(!isGet){
                String response = "Method not Allowed";
                exchange.sendResponseHeaders(405, response.getBytes().length);
                try(OutputStream os = exchange.getResponseBody()){
                    os.write(response.getBytes());
                }
                return;
            }

            // URL에서 PORT 번호 추출
            String path = exchange.getRequestURI().getPath();
            String portStr = path.substring(path.lastIndexOf("/")+1);

            try{ 
                int port = Integer.parseInt(portStr);
                // PORT 번호로 소켓 연결
                try(Socket socket = new Socket("localhost", port);
                    InputStream socketInput = socket.getInputStream()){
                        /* tempFile 필요성
                        HTTP 응답에는 Content-Length가 필요함
                        - 파일 크기를 미리 모르기 때문
                        - 일단 다 받고 크기 측정하고 Content-Length 설정
                        */
                        File tempFile =  File.createTempFile("download-", ".tmp");
                        String filename = "downloaded-file"; // default filename

                        // 파일 이름 받기 -> 나머지는 전부 파일 데이터임
                        /* 소켓에서 오는 데이터 구조 (내가 정한 형태임)
                            Filename: example.pdf\n
                            <binary file data>
                        */
                        try(FileOutputStream fos = new FileOutputStream(tempFile)){
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();

                            // 우선 파일 이름 먼저 읽기 (엔터(\n) 나올 때까지 한 바이트씩 읽기)
                            int b;
                            while((b = socketInput.read()) != -1){
                                if(b == '\n')break;
                                headerBaos.write(b);
                            }
                            // 헤더 해석 : "Filename: " 뒤에 있는 게 실제 다운로드될 파일 이름
                            String header = headerBaos.toString().trim();
                            if(header.startsWith("Filename: ")){
                                filename = header.substring("Filename: ".length());
                            }
                  
                            // 나머지는 파일 데이터 (소켓에서 오는 나머지 모든 바이트)
                            while((bytesRead = socketInput.read(buffer)) != -1){
                                fos.write(buffer, 0, bytesRead);
                            }
                        }

                        // 브라우저에서 파일로 인식하게 만들기
                        headers.add("Content-Disposition", "attachment; filename=\"" + filename + "\"");
                        headers.add("Content-Type", "application/octet-stream");
                        exchange.sendResponseHeaders(200, tempFile.length());
                        try(OutputStream os = exchange.getResponseBody();
                            FileInputStream fis = new FileInputStream(tempFile)){
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = fis.read(buffer)) != -1) {
                                    os.write(buffer, 0, bytesRead);
                                }
                            }
                            tempFile.delete();
                        }
                        catch(IOException e){
                            System.err.println("Error downloading file from peer: "+ e.getMessage());
                            String response = "Error downloading file: " + e.getMessage();
                            headers.add("Content-Type", "text/plain");
                            exchange.sendResponseHeaders(400, response.getBytes().length);
                            try(OutputStream os = exchange.getResponseBody()){
                                os.write(response.getBytes());
                            }
                    
                        }


                    
            

                }catch(NumberFormatException e){
                String response = "Bad Request: Invalid port number";
                exchange.sendResponseHeaders(400, response.getBytes().length);
                try(OutputStream os = exchange.getResponseBody()){
                    os.write(response.getBytes());
                }
            }
           



           


            

            // 브라우저에게 다운로드로 응답

            // 임시 파일을 브라우저로 전송


            // 끝나면 임시 파일 삭제
        }
    }

}