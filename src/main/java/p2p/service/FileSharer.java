package p2p.service;

import java.util.HashMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import p2p.utils.UploadUtils;

public class FileSharer {
    private HashMap<Integer, String> availableFiles;

    public FileSharer(){
        availableFiles = new HashMap<>();
    }

    public int offerFile(String filePath){
        int port;
        while(true){
            port = UploadUtils.generateCode();
            if(!availableFiles.containsKey(port)){
                availableFiles.put(port, filePath);
                return port;
            }
        }
    }

    public void startFileServer(int port){
        String filePath = availableFiles.get(port)  ;
        if(filePath ==null){
            System.out.println("No File is associated with this port: " + port);
            return;
        }

        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("File server started on port: " +new File(filePath).getName() +"on port"+ port);
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connection:"+ clientSocket.getInetAddress());
            new Thread(new FileTransferHandler(clientSocket, filePath)).start();
        }
        catch(IOException err){
            System.out.println("Error starting file server: " + err.getMessage() + ", Port: " + port);
        }
    }

    private static class FileTransferHandler implements Runnable{

        private final Socket clientSocket;
        private final String filePath;


        public FileTransferHandler(Socket clientSocket, String filePath){
            this.clientSocket = clientSocket;
            this.filePath = filePath;
        }

        @Override
        public void run(){
            try( FileInputStream fileInputStream = new FileInputStream(filePath) ){
                OutputStream oos = clientSocket.getOutputStream();
                String fileName = new File(filePath).getName();
                String header = "Filename: " + fileName + "\n";
                oos.write(header.getBytes());

                byte[] buffer = new byte[4096];
                int byteRead;
                while((byteRead = fileInputStream.read(buffer)) != -1){
                    oos.write(buffer, 0, byteRead);
                }
                System.out.println("File ("+fileName+ ") transferred successfully to: " + clientSocket.getInetAddress());
            }
            catch(Exception err){
                System.out.println("Error transferring file: " + err.getMessage());
            }
            finally{
                try{
                    clientSocket.close();
                }
                catch(Exception err){
                    System.out.println("Error closing client socket: " + err.getMessage());
                }
            }

        }
    }
}
    