package dacs1.v2;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(8888);
        System.out.println("Server đang chạy...");
        while (true) {
            Socket socket = ss.accept();
            new Thread(new ClientHandler(socket)).start();
        }
    }
}