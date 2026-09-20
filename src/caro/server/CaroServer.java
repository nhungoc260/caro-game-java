package caro.server;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Main class của Server.
 * Chạy file này TRƯỚC, sau đó mới chạy CaroClient (2 lần) để vào chơi.
 */
public class CaroServer {

    public static final int PORT = 12345;

    public static void main(String[] args) {
        System.out.println("=== CARO SERVER ===");
        printLocalIpAddresses();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server đang lắng nghe tại port " + PORT + " ...");

            while (true) {
                // Mỗi vòng lặp: chờ đủ 2 client rồi ghép thành 1 phòng (GameRoom)
                System.out.println("Đang chờ người chơi 1 ...");
                Socket socket1 = serverSocket.accept();
                ClientHandler player1 = new ClientHandler(socket1);
                player1.setPlayerId(1);
                new Thread(player1).start();
                System.out.println("Người chơi 1 đã vào (" + socket1.getInetAddress() + ")");

                System.out.println("Đang chờ người chơi 2 ...");
                Socket socket2 = serverSocket.accept();
                ClientHandler player2 = new ClientHandler(socket2);
                player2.setPlayerId(2);
                new Thread(player2).start();
                System.out.println("Người chơi 2 đã vào (" + socket2.getInetAddress() + ")");

                GameRoom room = new GameRoom();
                room.setPlayer1(player1);
                room.setPlayer2(player2);
                player1.setRoom(room);
                player2.setRoom(room);

                System.out.println("Đủ 2 người chơi -> bắt đầu ván mới!\n");
                room.startGame();
            }
        } catch (BindException e) {
            System.out.println();
            System.out.println("!!! KHÔNG THỂ KHỞI ĐỘNG SERVER !!!");
            System.out.println("Port " + PORT + " đang bị 1 tiến trình Server khác chiếm giữ");
            System.out.println("(có thể bạn đã Run File CaroServer.java từ trước đó và quên tắt).");
            System.out.println();
            System.out.println("Cách khắc phục:");
            System.out.println("  1. Nhìn xuống khung Output, tìm tab Server cũ đang chạy,");
            System.out.println("     bấm nút vuông đỏ (Stop) để tắt nó đi.");
            System.out.println("  2. Nếu không tìm thấy, đóng và mở lại NetBeans rồi thử lại.");
            System.out.println();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * In ra các địa chỉ IP nội bộ (LAN) của máy đang chạy server,
     * để dễ đọc và nhập vào Client khi demo qua LAN/hotspot thật,
     * khỏi phải tự gõ lệnh ipconfig / ifconfig.
     */
    private static void printLocalIpAddresses() {
        try {
            System.out.println("Địa chỉ IP của máy này (dùng IP nào để bạn cùng team nhập vào Client):");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                for (InetAddress addr : Collections.list(addresses)) {
                    // Chỉ in IPv4 cho dễ đọc (VD: 192.168.x.x)
                    if (addr.getHostAddress().indexOf(':') == -1) {
                        System.out.println("   - " + ni.getDisplayName() + " : " + addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("   (Không lấy được danh sách IP, dùng lệnh ipconfig/ifconfig để xem thủ công)");
        }
        System.out.println();
    }
}
