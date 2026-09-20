package caro.server;

import caro.common.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * GameRoom đại diện cho 1 ván cờ giữa 2 người chơi.
 * Server là "trọng tài": giữ bàn cờ, kiểm tra lượt đi và xác định thắng/thua/hòa.
 * Client KHÔNG tự xử lý thắng thua để tránh lệch trạng thái giữa 2 bên.
 */
public class GameRoom {

    public static final int SIZE = 15; // bàn cờ 15x15

    private final int[][] board = new int[SIZE][SIZE]; // 0 = trống, 1 = player1, 2 = player2
    private ClientHandler player1;
    private ClientHandler player2;
    private int currentTurn = 1; // player1 (X) luôn đi trước
    private boolean gameOver = false;

    private boolean replayRequested1 = false;
    private boolean replayRequested2 = false;

    // Danh sách toạ độ 5 (hoặc nhiều hơn) ô tạo thành đường thắng, để tô sáng bên client
    private List<int[]> lastWinCells;

    public void setPlayer1(ClientHandler p) { this.player1 = p; }
    public void setPlayer2(ClientHandler p) { this.player2 = p; }

    public synchronized void startGame() {
        gameOver = false;
        currentTurn = 1;
        replayRequested1 = false;
        replayRequested2 = false;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                board[i][j] = 0;
            }
        }

        Message m1 = new Message(Message.Type.START);
        m1.playerId = 1;
        player1.sendMessage(m1);

        Message m2 = new Message(Message.Type.START);
        m2.playerId = 2;
        player2.sendMessage(m2);
    }

    /**
     * Gọi khi 1 ClientHandler nhận được tên người chơi (Message.Type.NAME),
     * chuyển tiếp tên đó cho đối thủ để hiển thị lên GUI.
     */
    public synchronized void notifyNameUpdated(ClientHandler who) {
        ClientHandler other = (who == player1) ? player2 : player1;
        if (other == null) return;
        Message m = new Message(Message.Type.NAME);
        m.note = who.getPlayerName();
        other.sendMessage(m);
    }

    /**
     * Xử lý 1 nước đi do ClientHandler chuyển lên.
     */
    public synchronized void handleMove(int playerId, int x, int y) {
        if (gameOver) return;
        if (playerId != currentTurn) return;              // không đúng lượt -> bỏ qua
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        if (board[x][y] != 0) return;                      // ô đã có quân

        board[x][y] = playerId;
        currentTurn = (playerId == 1) ? 2 : 1;

        Message move = new Message(Message.Type.MOVE);
        move.playerId = playerId;
        move.x = x;
        move.y = y;
        move.nextTurn = currentTurn;
        broadcast(move);

        if (checkWin(x, y, playerId)) {
            gameOver = true;
            Message win = new Message(Message.Type.WIN);
            win.winnerId = playerId;
            if (lastWinCells != null) {
                int n = lastWinCells.size();
                win.winLineX = new int[n];
                win.winLineY = new int[n];
                for (int i = 0; i < n; i++) {
                    win.winLineX[i] = lastWinCells.get(i)[0];
                    win.winLineY[i] = lastWinCells.get(i)[1];
                }
            }
            broadcast(win);
            return;
        }

        if (isBoardFull()) {
            gameOver = true;
            broadcast(new Message(Message.Type.DRAW));
        }
    }

    private boolean isBoardFull() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == 0) return false;
            }
        }
        return true;
    }

    /**
     * Kiểm tra 5 quân liên tiếp tính từ ô (x,y) vừa đánh,
     * theo 4 hướng: ngang, dọc, chéo xuống, chéo lên.
     * Nếu thắng, lưu lại toạ độ các ô thắng vào lastWinCells.
     */
    private boolean checkWin(int x, int y, int playerId) {
        int[][] directions = { {1, 0}, {0, 1}, {1, 1}, {1, -1} };
        for (int[] d : directions) {
            List<int[]> line = new ArrayList<int[]>();
            line.add(new int[]{x, y});
            collectDirection(x, y, d[0], d[1], playerId, line);
            collectDirection(x, y, -d[0], -d[1], playerId, line);
            if (line.size() >= 5) {
                lastWinCells = line;
                return true;
            }
        }
        return false;
    }

    private void collectDirection(int x, int y, int dx, int dy, int playerId, List<int[]> line) {
        int nx = x + dx, ny = y + dy;
        while (nx >= 0 && ny >= 0 && nx < SIZE && ny < SIZE && board[nx][ny] == playerId) {
            line.add(new int[]{nx, ny});
            nx += dx;
            ny += dy;
        }
    }

    private void broadcast(Message m) {
        player1.sendMessage(m);
        player2.sendMessage(m);
    }

    /**
     * Gọi khi 1 người chơi bấm nút "Chơi lại".
     * Chỉ khi CẢ HAI người cùng bấm thì ván mới mới bắt đầu.
     */
    public synchronized void requestReplay(int playerId) {
        if (!gameOver) return; // chỉ cho xin chơi lại khi ván trước đã kết thúc

        if (playerId == 1) {
            replayRequested1 = true;
        } else {
            replayRequested2 = true;
        }

        if (replayRequested1 && replayRequested2) {
            startGame();
        } else {
            // Báo cho người còn lại biết đối thủ đã muốn chơi lại
            ClientHandler waitingFor = (playerId == 1) ? player2 : player1;
            Message note = new Message(Message.Type.REPLAY);
            note.note = "Đối thủ muốn chơi lại! Bấm \"Chơi lại\" để bắt đầu ván mới.";
            if (waitingFor != null) {
                waitingFor.sendMessage(note);
            }
        }
    }

    /**
     * Gọi khi 1 trong 2 client ngắt kết nối giữa ván.
     */
    public synchronized void notifyOpponentLeft(ClientHandler leaver) {
        if (gameOver) return;
        gameOver = true;
        ClientHandler other = (leaver == player1) ? player2 : player1;
        if (other != null) {
            other.sendMessage(new Message(Message.Type.OPPONENT_LEFT));
        }
    }
}
