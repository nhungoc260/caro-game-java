package caro.common;

import java.io.Serializable;

/**
 * Lớp Message dùng để đóng gói dữ liệu trao đổi giữa Client và Server
 * qua ObjectOutputStream / ObjectInputStream (Serialization).
 *
 * Class này PHẢI giống hệt nhau ở cả phía server và client,
 * nếu không sẽ bị lỗi ClassNotFoundException / InvalidClassException.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        NAME,           // Client -> Server: gửi tên người chơi ngay sau khi kết nối
                        // Server -> Client: chuyển tiếp tên của đối thủ
        WAITING,        // Server -> Client: đang chờ đối thủ vào phòng
        START,          // Server -> Client: đủ 2 người, bắt đầu ván mới
        MOVE,           // Client -> Server: người chơi đánh 1 nước
                        // Server -> Client: broadcast lại nước đi cho cả 2 bên
        WIN,            // Server -> Client: có người thắng
        DRAW,           // Server -> Client: hòa (hết ô trống)
        OPPONENT_LEFT,  // Server -> Client: đối thủ đã ngắt kết nối
        REPLAY,         // Client -> Server: xin chơi lại
                        // Server -> Client: báo đối thủ đang chờ chơi lại
        ERROR
    }

    public Type type;
    public int playerId;    // 1 hoặc 2 - id người chơi
    public int x, y;        // toạ độ ô cờ (dùng cho MOVE)
    public int nextTurn;    // playerId của người được đánh tiếp theo (dùng cho MOVE)
    public int winnerId;    // playerId người thắng (dùng cho WIN)
    public String note;     // tên người chơi / thông điệp phụ, hiển thị lên GUI
    public int[] winLineX;  // toạ độ x của các ô nằm trên đường thắng (để tô sáng)
    public int[] winLineY;  // toạ độ y của các ô nằm trên đường thắng (để tô sáng)

    public Message(Type type) {
        this.type = type;
    }
}
