package caro.server;

import caro.common.Message;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * Mỗi client kết nối vào server sẽ được gán 1 ClientHandler,
 * chạy trên 1 Thread riêng (đáp ứng yêu cầu "TCP + Thread").
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private int playerId;
    private GameRoom room;
    private String playerName = "Người chơi";

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void setPlayerId(int id) {
        this.playerId = id;
        this.playerName = "Người chơi " + id;
    }

    public void setRoom(GameRoom room) { this.room = room; }
    public String getPlayerName() { return playerName; }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            System.out.println("[Server] Player " + playerId + " đã kết nối: " + socket.getInetAddress());

            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                switch (msg.type) {
                    case NAME:
                        if (msg.note != null && !msg.note.trim().isEmpty()) {
                            playerName = msg.note.trim();
                        }
                        System.out.println("[Server] Player " + playerId + " đặt tên: " + playerName);
                        if (room != null) room.notifyNameUpdated(this);
                        break;

                    case MOVE:
                        System.out.println("[Server] Player " + playerId + " đánh (" + msg.x + "," + msg.y + ")");
                        room.handleMove(playerId, msg.x, msg.y);
                        break;

                    case REPLAY:
                        System.out.println("[Server] Player " + playerId + " xin chơi lại.");
                        room.requestReplay(playerId);
                        break;

                    default:
                        break;
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println("[Server] Player " + playerId + " đã ngắt kết nối.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (room != null) room.notifyOpponentLeft(this);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Gửi message xuống client của người chơi này.
     * synchronized để tránh 2 luồng ghi cùng lúc lên 1 stream.
     */
    public synchronized void sendMessage(Message m) {
        try {
            out.writeObject(m);
            out.flush();
            out.reset(); // tránh ObjectOutputStream cache lại object cũ khi gửi nhiều lần
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
