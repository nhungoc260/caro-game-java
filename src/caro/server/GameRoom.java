package caro.server;

import caro.common.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * GameRoom đại diện cho 1 ván cờ giữa 2 người chơi.
 * Server là "trọng tài": giữ bàn cờ, kiểm tra lượt đi, giới hạn thời gian
 * mỗi lượt, và xác định thắng/thua/hòa.
 * Client KHÔNG tự xử lý thắng thua để tránh lệch trạng thái giữa 2 bên.
 */
public class GameRoom {

    public static final int SIZE = 15; // bàn cờ 15x15

    // Mỗi lượt đánh có tối đa 20 giây; hết giờ server tự đánh thay 1 ô ngẫu nhiên.
    private static final long TURN_TIME_MS = 20_000;

    private final int[][] board = new int[SIZE][SIZE]; // 0 = trống, 1 = player1, 2 = player2
    private ClientHandler player1;
    private ClientHandler player2;
    private int currentTurn = 1; // player1 (X) luôn đi trước
    private boolean gameOver = false;

    private boolean replayRequested1 = false;
    private boolean replayRequested2 = false;

    // Danh sách toạ độ 5 (hoặc nhiều hơn) ô tạo thành đường thắng, để tô sáng bên client
    private List<int[]> lastWinCells;

    private final Random random = new Random();
    private final Timer turnTimer = new Timer(true); // daemon thread, tự tắt khi server đóng
    private TimerTask currentTurnTask;

    public void setPlayer1(ClientHandler p) { this.player1 = p; }
    public void setPlayer2(ClientHandler p) { this.player2 = p; }

    public synchronized void startGame() {
        cancelTurnTimer();
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

        scheduleTurnTimer();
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
     * Xử lý 1 nước đi do ClientHandler chuyển lên (người chơi tự bấm).
     */
    public synchronized void handleMove(int playerId, int x, int y) {
        if (gameOver) return;
        if (playerId != currentTurn) return;              // không đúng lượt -> bỏ qua
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        if (board[x][y] != 0) return;                      // ô đã có quân
        applyMove(playerId, x, y);
    }

    /**
     * Gọi khi hết giờ 1 lượt mà người chơi chưa đánh - server tự chọn
     * 1 ô trống ngẫu nhiên để đánh thay, giữ ván cờ không bị treo mãi.
     */
    private synchronized void autoMove() {
        if (gameOver) return;

        List<int[]> emptyCells = new ArrayList<int[]>();
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == 0) emptyCells.add(new int[]{i, j});
            }
        }
        if (emptyCells.isEmpty()) return; // hết ô trống thì lẽ ra đã hòa từ trước

        int[] chosen = emptyCells.get(random.nextInt(emptyCells.size()));
        System.out.println("[Server] Hết giờ! Tự động đánh thay Player " + currentTurn
                + " tại (" + chosen[0] + "," + chosen[1] + ")");
        applyMove(currentTurn, chosen[0], chosen[1]);
    }

    /**
     * Logic đặt quân dùng chung cho cả nước đi thật (handleMove) và
     * nước đi tự động khi hết giờ (autoMove).
     */
    private void applyMove(int playerId, int x, int y) {
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
            cancelTurnTimer();
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
            cancelTurnTimer();
            broadcast(new Message(Message.Type.DRAW));
            return;
        }

        // Ván vẫn tiếp tục -> bắt đầu đếm giờ cho lượt kế tiếp
        scheduleTurnTimer();
    }

    private void scheduleTurnTimer() {
        cancelTurnTimer();
        currentTurnTask = new TimerTask() {
            @Override
            public void run() {
                autoMove();
            }
        };
        turnTimer.schedule(currentTurnTask, TURN_TIME_MS);
    }

    private void cancelTurnTimer() {
        if (currentTurnTask != null) {
            currentTurnTask.cancel();
            currentTurnTask = null;
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
     * Chuyển tiếp tin nhắn chat từ 1 người chơi tới cả 2 người
     * (kể cả người gửi, để đơn giản hóa - client nhận lại đúng tin của mình
     * kèm tên, không cần tự hiển thị cục bộ trước).
     */
    public synchronized void broadcastChat(ClientHandler sender, String text) {
        if (text == null || text.trim().isEmpty()) return;
        Message chat = new Message(Message.Type.CHAT);
        chat.note = sender.getPlayerName() + ": " + text.trim();
        if (player1 != null) player1.sendMessage(chat);
        if (player2 != null) player2.sendMessage(chat);
    }

    /**
     * Gọi khi 1 trong 2 client ngắt kết nối giữa ván.
     */
    public synchronized void notifyOpponentLeft(ClientHandler leaver) {
        if (gameOver) return;
        gameOver = true;
        cancelTurnTimer();
        ClientHandler other = (leaver == player1) ? player2 : player1;
        if (other != null) {
            other.sendMessage(new Message(Message.Type.OPPONENT_LEFT));
        }
    }
}
