package dacs1.v2;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

public class ClientSocketManager {

    private static Object executeRequest(String request) throws IOException, ClassNotFoundException {
        int maxRetries = 3;
        int retryDelay = 1000;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Socket socket = new Socket("localhost", 8888);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                socket.setSoTimeout(5000);
                System.out.println("Thử " + attempt + ": Gửi yêu cầu: " + request);
                out.writeObject(request);
                out.flush();
                Object response = in.readObject();
                System.out.println("Nhận phản hồi: " + response);
                return response;
            } catch (IOException e) {
                System.err.println("Lỗi thử " + attempt + ": " + e.getMessage());
                if (attempt == maxRetries) throw e;
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new IOException("Không thể kết nối server sau " + maxRetries + " lần thử");
    }

    public static List<FileRecord> getFilesForUser(String username, String role) {
        List<FileRecord> result = new ArrayList<>();
        try {
            String request = "GET_FILES:" + username + ":" + role;
            Object response = executeRequest(request);
            if (response instanceof List<?>) {
                for (Object obj : (List<?>) response) {
                    if (obj instanceof FileRecord) {
                        result.add((FileRecord) obj);
                    }
                }
            } else if (response instanceof String && ((String) response).startsWith("ERROR")) {
                JOptionPane.showMessageDialog(null, "Lỗi server: " + response);
            } else {
                JOptionPane.showMessageDialog(null, "Dữ liệu không hợp lệ từ server");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Lỗi lấy danh sách file: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public static String login(String user, String pass) {
        try {
            String request = "LOGIN:" + user + ":" + pass;
            String response = (String) executeRequest(request);
            return response;
        } catch (Exception e) {
            return "LOGIN_FAIL:" + e.getMessage();
        }
    }

    public static String register(String user, String pass) {
        try {
            String request = "REGISTER:" + user + ":" + pass;
            String response = (String) executeRequest(request);
            return response;
        } catch (Exception e) {
            return "REGISTER_FAIL:" + e.getMessage();
        }
    }

    public static String sendFile(File file, String sender, String receiver) {
        if (receiver.equalsIgnoreCase(sender)) {
            return "ERROR:Không thể gửi file cho chính mình";
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String request = "SEND_FILE:" + sender + ":" + receiver + ":" + file.getName() + ":" + data.length;
            try (Socket socket = new Socket("localhost", 8888);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                out.writeObject(request);
                out.flush();
                out.write(data);
                out.flush();
                String response = (String) in.readObject();
                return response;
            }
        } catch (Exception e) {
            return "ERROR:" + e.getMessage();
        }
    }

    public static void downloadFile(String filename, String sender, String receiver) {
        try {
            String request = "DOWNLOAD:" + filename + ":" + sender + ":" + receiver;
            Object response = executeRequest(request);
            if (response instanceof byte[]) {
                byte[] data = (byte[]) response;
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File(filename));
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    Files.write(chooser.getSelectedFile().toPath(), data);
                    JOptionPane.showMessageDialog(null, "Tải file thành công!");
                }
            } else if (response instanceof String && ((String) response).startsWith("ERROR")) {
                JOptionPane.showMessageDialog(null, "Lỗi: " + response);
            } else {
                JOptionPane.showMessageDialog(null, "Dữ liệu không hợp lệ");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Lỗi tải file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}