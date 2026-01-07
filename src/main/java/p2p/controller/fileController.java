package p2p.controller;

public class FileController {

    private final FileSharer fileSharer;
    private final HttpServer server;
    private final String uploadDir;
    private final ExecutorService executorService;

    public FileController(int port) throws IOException{
        this.fileSharer = new FileSharer();
        this.server = HttpSever.create(new InetSocketAddress(port));
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

            if(exchange.getRequestMthod().equals("OPTIONS")){
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
        @override
        public void handle(HttpExchange exchange)throws IOException{
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
                IOUtils.copy(exchange.getRequestBody(), baos)
                byte[] fileBytes = baos.toByteArray();

                MultipartParser parser = new MultipartParser(requestData, boundary);
                MultipartParser.ParserResult result = parser.parse();

                if(result == null){
                    String response = "Bad Request: Could not parse file content";
                    exchange.sendResponseHeaders(400, response.getBytes().length)
                    try(OuputStream os = exchange.getResponseBody()){
                        os.write(response.getBytes());
                    }
                    return
                }

                String response = result.filename;
                Boolean isUnamed = filename.trim().isEmpty() || filename == null
                if(isUnamed){
                    filename ="unamed-file"
                }

            }
            catch(Exception e){
                System.err.println("Error processing upload: "+ e.getMessage());
                String response = "Server Error: "+e.getMessage();
                exchange.sendResponseHeaders(500, response.getBytes().length);
                try(OutputStream os = exchange.getResponseBody()){ // try-with-resources → 자동 close
                    os.write(response.getBytes())
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
    }

}