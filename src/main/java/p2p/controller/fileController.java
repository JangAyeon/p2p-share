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

    
}
