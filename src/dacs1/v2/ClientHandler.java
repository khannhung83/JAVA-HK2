package dacs1.v2;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private boolean running;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            // Khởi tạo out trước in để tránh deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            running = true;
        } catch (IOException e) {
            System.err.println("Lỗi khởi tạo stream: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                Object request = in.readObject();
                if (request instanceof String) {
                    String req = (String) request;
                    System.out.println("Received request: " + req);
                    String[] parts = req.split(":");
                    switch (parts[0]) {
                        case "LOGIN":
                            handleLogin(parts[1], parts[2]);
                            break;
                        case "REGISTER":
                            handleRegister(parts[1], parts[2]);
                            break;
                        case "GET_FILES":
                            handleGetUserFiles(parts[1]);
                            break;
                        case "SEND_FILE":
                            handleSendFile(parts[1], parts[2], parts[3], new byte[Integer.parseInt(parts[4])]);
                            break;
                        case "DOWNLOAD":
                            handleDownloadFile(parts[1], parts[2], parts[3]);
                            break;
                        default:
                            sendError("Yêu cầu không hợp lệ");
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Lỗi xử lý yêu cầu: " + e.getMessage());
                e.printStackTrace();
                running = false;
            }
        }
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("Lỗi đóng socket: " + e.getMessage());
        }
    }

    private void handleLogin(String username, String password) throws IOException {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            System.out.println("Đăng nhập: " + username);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String role = rs.getString("role");
                out.writeObject("LOGIN_SUCCESS:" + role);
            } else {
                sendError("Đăng nhập thất bại: Sai tên hoặc mật khẩu");
            }
            out.flush();
        } catch (SQLException e) {
            sendError("Lỗi server: " + e.getMessage());
        }
    }

    private void handleRegister(String username, String password) throws IOException {
        String sql = "INSERT INTO users(username, password, role) VALUES (?, ?, 'USER')";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            out.writeObject("REGISTER_SUCCESS");
            out.flush();
        } catch (SQLException e) {
            sendError("Đăng ký thất bại: " + (e.getMessage().contains("Duplicate") ? "Tên đã tồn tại" : e.getMessage()));
        }
    }

    private void handleSendFile(String sender, String receiver, String filename, byte[] data) throws IOException {
        // Kiểm tra người nhận tồn tại
        if (!userExists(receiver)) {
            sendError("Người nhận không tồn tại");
            return;
        }
        String folder = "server_files/" + receiver;
        new File(folder).mkdirs();
        Path path = Paths.get(folder, filename);
        try {
            Files.write(path, data);
            saveMetadata(filename, sender, receiver, data.length);
            out.writeObject("FILE_RECEIVED");
            out.flush();
        } catch (SQLException e) {
            sendError("Lỗi lưu file: " + e.getMessage());
        }
    }

    private void saveMetadata(String filename, String sender, String receiver, int size) throws SQLException {
        String sql = "INSERT INTO files(filename, sender, receiver, size) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, filename);
            pstmt.setString(2, sender);
            pstmt.setString(3, receiver);
            pstmt.setInt(4, size);
            pstmt.executeUpdate();
            System.out.println("Lưu metadata: " + filename + " từ " + sender + " đến " + receiver);
        }
    }

    private void handleDownloadFile(String filename, String sender, String receiver) throws IOException {
        Path path = Paths.get("server_files/" + receiver, filename);
        if (Files.exists(path)) {
            byte[] data = Files.readAllBytes(path);
            out.writeObject(data);
            out.flush();
        } else {
            sendError("File không tồn tại");
        }
    }

    private void handleGetUserFiles(String username) throws IOException {
        String sql = "SELECT * FROM files WHERE sender = ? OR receiver = ?";
        List<FileRecord> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                list.add(new FileRecord(
                    rs.getString("filename"),
                    rs.getString("sender"),
                    rs.getString("receiver"),
                    rs.getLong("size"),
                    rs.getTimestamp("upload_time")
                ));
            }
            out.writeObject(list);
            out.flush();
        } catch (SQLException e) {
            sendError("Lỗi truy vấn file: " + e.getMessage());
        }
    }

    private boolean userExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra user: " + e.getMessage());
            return false;
        }
    }

    private void sendError(String message) throws IOException {
        out.writeObject("ERROR:" + message);
        out.flush();
    }
}