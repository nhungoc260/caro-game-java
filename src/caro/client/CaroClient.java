package caro.client;

import caro.common.Message;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Main class của Client - giao diện Swing.
 * Chạy file này để mở 1 cửa sổ người chơi. Muốn có 2 người chơi trên
 * cùng 1 máy thì chạy (Run File) class này 2 lần.
 */
public class CaroClient extends JFrame {

    private static final int SIZE = 15;

    // ==== Bảng màu giao diện (tông nâu gỗ, giống bàn cờ thật) ====
    private static final Color COLOR_HEADER_TOP = new Color(62, 39, 35);    // brown-900
    private static final Color COLOR_HEADER_BOTTOM = new Color(93, 64, 55); // brown-700
    private static final Color COLOR_ACCENT = new Color(212, 165, 116);     // vàng gỗ nhạt (viền nhấn)

    private static final Color COLOR_PILL_MY_TURN = new Color(56, 118, 29);   // xanh lá rêu
    private static final Color COLOR_PILL_WAIT    = new Color(109, 91, 74);   // nâu xám
    private static final Color COLOR_PILL_WIN     = new Color(56, 118, 29);
    private static final Color COLOR_PILL_LOSE    = new Color(150, 40, 27);   // đỏ đất nung
    private static final Color COLOR_PILL_DRAW    = new Color(168, 111, 26);  // vàng cam đất

    private static final Color COLOR_CELL_LIGHT     = new Color(245, 222, 179); // wheat - vân gỗ sáng
    private static final Color COLOR_CELL_DARK      = new Color(222, 191, 145); // burlywood - vân gỗ tối
    private static final Color COLOR_CELL_LAST_MOVE = new Color(255, 213, 79);  // vàng hổ phách
    private static final Color COLOR_CELL_WIN_LINE  = new Color(129, 199, 132); // xanh lá highlight thắng
    private static final Color COLOR_GRID_LINE      = new Color(141, 110, 99);  // nâu đậm - đường kẻ

    private static final Color COLOR_X = new Color(62, 39, 35);    // nâu đen đậm
    private static final Color COLOR_O = new Color(150, 40, 27);   // đỏ đất nung

    private final CaroCell[][] cells = new CaroCell[SIZE][SIZE];
    private RoundedPanel statusPill;
    private JLabel statusLabel;
    private JLabel scoreLabel;
    private RoundedButton replayButton;
    private JTextArea chatArea;
    private JTextField chatInput;
    private RoundedButton sendChatButton;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private String myName = "Người chơi";
    private String opponentName = "Đối thủ";
    private int myId = 0;
    private int currentTurn = 1;
    private boolean gameStarted = false;

    private int lastMoveX = -1, lastMoveY = -1;
    private int wins = 0, losses = 0, draws = 0;

    public CaroClient(String myName) {
        this.myName = (myName == null || myName.trim().isEmpty()) ? "Người chơi" : myName.trim();

        setTitle("Cờ Caro Online - " + this.myName);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        add(buildHeaderPanel(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(Color.WHITE);
        centerPanel.add(buildBoardPanel(), BorderLayout.CENTER);
        centerPanel.add(buildChatPanel(), BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        add(buildBottomPanel(), BorderLayout.SOUTH);

        setSize(1020, 900);
        setMinimumSize(new Dimension(760, 700));
        setLocationRelativeTo(null);
        setEnabledBoard(false);
    }

    // ---------- Xây dựng giao diện ----------

    private JPanel buildHeaderPanel() {
        // Panel nền có gradient nhẹ trên xuống dưới cho hiện đại
        JPanel header = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                GradientPaint gp = new GradientPaint(0, 0, COLOR_HEADER_TOP, 0, getHeight(), COLOR_HEADER_BOTTOM);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(COLOR_ACCENT);
                g2.fillRect(0, getHeight() - 3, getWidth(), 3);
                g2.dispose();
            }
        };
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel titleLabel = new JLabel("CỜ CARO ONLINE", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel = new JLabel("Đang kết nối tới server...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setOpaque(false);
        statusLabel.setBorder(new EmptyBorder(6, 18, 6, 18));

        statusPill = new RoundedPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        statusPill.setBg(COLOR_PILL_WAIT);
        statusPill.add(statusLabel);
        statusPill.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusPill.setBorder(new EmptyBorder(10, 0, 0, 0));

        scoreLabel = new JLabel(buildScoreText(), SwingConstants.CENTER);
        scoreLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        scoreLabel.setForeground(new Color(203, 213, 225));
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        scoreLabel.setBorder(new EmptyBorder(10, 0, 0, 0));

        header.add(titleLabel);
        header.add(statusPill);
        header.add(scoreLabel);
        return header;
    }

    private JPanel buildBoardPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel boardPanel = new JPanel(new GridLayout(SIZE, SIZE, 1, 1));
        boardPanel.setBackground(COLOR_GRID_LINE);
        boardPanel.setBorder(BorderFactory.createLineBorder(COLOR_GRID_LINE, 1));

        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                CaroCell cell = new CaroCell(i, j);
                cell.setBackground(baseCellColor(i, j));
                final int x = i, y = j;
                cell.addActionListener(e -> onCellClick(x, y));
                cells[i][j] = cell;
                boardPanel.add(cell);
            }
        }
        wrapper.add(boardPanel, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildChatPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(new EmptyBorder(14, 0, 14, 14));
        wrapper.setPreferredSize(new Dimension(240, 0));

        JLabel chatTitle = new JLabel("TRÒ CHUYỆN", SwingConstants.CENTER);
        chatTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        chatTitle.setForeground(new Color(93, 64, 55));
        chatTitle.setBorder(new EmptyBorder(0, 0, 6, 0));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        chatArea.setBackground(new Color(250, 244, 234));
        chatArea.setForeground(new Color(62, 39, 35));
        chatArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_GRID_LINE, 1));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setBackground(Color.WHITE);

        chatInput = new JTextField();
        chatInput.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        chatInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_GRID_LINE, 1),
                new EmptyBorder(6, 8, 6, 8)));
        chatInput.addActionListener(e -> onSendChat()); // gửi khi bấm Enter

        sendChatButton = new RoundedButton("Gửi");
        sendChatButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendChatButton.setBackground(new Color(93, 64, 55));
        sendChatButton.setForeground(Color.WHITE);
        sendChatButton.setBorder(new EmptyBorder(6, 14, 6, 14));
        sendChatButton.addActionListener(e -> onSendChat());

        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendChatButton, BorderLayout.EAST);

        wrapper.add(chatTitle, BorderLayout.NORTH);
        wrapper.add(scrollPane, BorderLayout.CENTER);
        wrapper.add(inputPanel, BorderLayout.SOUTH);
        return wrapper;
    }

    private void onSendChat() {
        String text = chatInput.getText();
        if (text == null || text.trim().isEmpty()) return;
        Message chat = new Message(Message.Type.CHAT);
        chat.playerId = myId;
        chat.note = text.trim();
        sendMessage(chat);
        chatInput.setText("");
    }

    private JPanel buildBottomPanel() {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(new EmptyBorder(0, 0, 16, 0));

        replayButton = new RoundedButton("CHƠI LẠI");
        replayButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        replayButton.setBackground(new Color(56, 118, 29)); // xanh lá rêu, hợp tông gỗ
        replayButton.setForeground(Color.WHITE);
        replayButton.setBorder(new EmptyBorder(12, 34, 12, 34));
        replayButton.setVisible(false); // chỉ hiện sau khi ván kết thúc
        replayButton.addActionListener(e -> onReplayClick());

        bottomPanel.add(replayButton);
        return bottomPanel;
    }

    private Color baseCellColor(int i, int j) {
        return ((i + j) % 2 == 0) ? COLOR_CELL_LIGHT : COLOR_CELL_DARK;
    }

    private String buildScoreText() {
        return "THẮNG " + wins + "   -   THUA " + losses + "   -   HÒA " + draws;
    }

    private void setStatus(String text, Color bgColor) {
        statusLabel.setText(text);
        statusPill.setBg(bgColor);
    }

    // ---------- Xử lý sự kiện ----------

    private void onCellClick(int x, int y) {
        if (!gameStarted) return;
        if (myId != currentTurn) {
            setStatus("Chưa tới lượt của bạn!", COLOR_PILL_WAIT);
            return;
        }
        if (cells[x][y].getMark() != 0) return;

        Message move = new Message(Message.Type.MOVE);
        move.playerId = myId;
        move.x = x;
        move.y = y;
        sendMessage(move);
    }

    private void onReplayClick() {
        replayButton.setEnabled(false);
        setStatus("Đã gửi yêu cầu chơi lại, đang chờ đối thủ...", COLOR_PILL_WAIT);
        Message replay = new Message(Message.Type.REPLAY);
        replay.playerId = myId;
        sendMessage(replay);
    }

    private void connectToServer(String ip, int port) {
        try {
            Socket socket = new Socket(ip, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Gửi tên của mình lên server ngay sau khi kết nối
            Message nameMsg = new Message(Message.Type.NAME);
            nameMsg.note = myName;
            sendMessage(nameMsg);

            setStatus("Đã kết nối. Đang chờ đối thủ...", COLOR_PILL_WAIT);

            Thread listenThread = new Thread(this::listenServer);
            listenThread.setDaemon(true);
            listenThread.start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không thể kết nối tới server: " + e.getMessage());
            System.exit(0);
        }
    }

    /**
     * Luồng riêng để LIÊN TỤC lắng nghe message từ server,
     * tách biệt với luồng UI chính (Swing Event Dispatch Thread).
     */
    private void listenServer() {
        try {
            Message msg;
            while ((msg = (Message) in.readObject()) != null) {
                Message finalMsg = msg;
                SwingUtilities.invokeLater(() -> handleMessage(finalMsg));
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> setStatus("Mất kết nối tới server.", COLOR_PILL_LOSE));
        }
    }

    private void handleMessage(Message msg) {
        switch (msg.type) {
            case NAME:
                if (msg.note != null && !msg.note.trim().isEmpty()) {
                    opponentName = msg.note.trim();
                }
                updateTitleWithNames();
                break;

            case START:
                myId = msg.playerId;
                gameStarted = true;
                currentTurn = 1;
                lastMoveX = -1;
                lastMoveY = -1;
                resetBoard();
                setEnabledBoard(true);
                replayButton.setVisible(false);
                replayButton.setEnabled(true);
                updateTitleWithNames();
                updateStatus();
                break;

            case REPLAY:
                setStatus(msg.note != null ? msg.note : "Đối thủ muốn chơi lại...", COLOR_PILL_WAIT);
                break;

            case CHAT:
                if (msg.note != null) {
                    chatArea.append(msg.note + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                }
                break;

            case MOVE: {
                int mark = (msg.playerId == 1) ? 1 : 2;
                CaroCell cell = cells[msg.x][msg.y];
                cell.setMark(mark);

                // Bỏ highlight nước đi trước, highlight nước đi mới nhất
                if (lastMoveX != -1) {
                    cells[lastMoveX][lastMoveY].setBackground(baseCellColor(lastMoveX, lastMoveY));
                }
                cell.setBackground(COLOR_CELL_LAST_MOVE);
                lastMoveX = msg.x;
                lastMoveY = msg.y;

                currentTurn = msg.nextTurn;
                updateStatus();
                break;
            }

            case WIN:
                gameStarted = false;
                setEnabledBoard(false);
                highlightWinLine(msg.winLineX, msg.winLineY);
                if (msg.winnerId == myId) {
                    wins++;
                    setStatus("BẠN THẮNG!", COLOR_PILL_WIN);
                } else {
                    losses++;
                    setStatus("BẠN THUA!", COLOR_PILL_LOSE);
                }
                scoreLabel.setText(buildScoreText());
                replayButton.setEnabled(true);
                replayButton.setVisible(true);
                break;

            case DRAW:
                gameStarted = false;
                setEnabledBoard(false);
                draws++;
                setStatus("HÒA!", COLOR_PILL_DRAW);
                scoreLabel.setText(buildScoreText());
                replayButton.setEnabled(true);
                replayButton.setVisible(true);
                break;

            case OPPONENT_LEFT:
                gameStarted = false;
                setEnabledBoard(false);
                replayButton.setVisible(false);
                setStatus("Đối thủ đã thoát khỏi ván chơi!", COLOR_PILL_LOSE);
                break;

            default:
                break;
        }
    }

    private void highlightWinLine(int[] winX, int[] winY) {
        if (winX == null || winY == null) return;
        for (int i = 0; i < winX.length; i++) {
            cells[winX[i]][winY[i]].setBackground(COLOR_CELL_WIN_LINE);
        }
    }

    private void updateTitleWithNames() {
        String mySymbol = (myId == 1) ? "X" : (myId == 2 ? "O" : "?");
        setTitle("Cờ Caro Online - " + myName + " (" + mySymbol + ")  vs  " + opponentName);
    }

    private void updateStatus() {
        String mySymbol = (myId == 1) ? "X" : "O";
        if (currentTurn == myId) {
            setStatus("Bạn (" + mySymbol + ")  -  ĐẾN LƯỢT BẠN", COLOR_PILL_MY_TURN);
        } else {
            setStatus("Bạn (" + mySymbol + ")  -  đang chờ " + opponentName + " đánh...", COLOR_PILL_WAIT);
        }
    }

    private void resetBoard() {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                cells[i][j].setMark(0);
                cells[i][j].setBackground(baseCellColor(i, j));
            }
        }
    }

    private void setEnabledBoard(boolean enabled) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                cells[i][j].setEnabled(enabled);
            }
        }
    }

    private void sendMessage(Message m) {
        try {
            out.writeObject(m);
            out.flush();
            out.reset();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------- Ô cờ tự vẽ - trông giống bàn cờ Caro/Gomoku thật ----------

    /**
     * 1 ô trên bàn cờ. Thay vì hiển thị chữ "X"/"O" bằng font (dễ vỡ hình
     * nếu thiếu font), ô này tự vẽ nét X (2 đường chéo) và vòng tròn O
     * bằng Graphics2D, luôn sắc nét trên mọi máy.
     */
    private class CaroCell extends JButton {
        int row, col; // giữ lại toạ độ ô (không dùng để trang trí nữa, chỉ để tham chiếu nếu cần)
        private int mark = 0; // 0 = trống, 1 = X, 2 = O

        CaroCell(int row, int col) {
            this.row = row;
            this.col = col;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(true);
        }

        void setMark(int m) {
            this.mark = m;
            repaint();
        }

        int getMark() {
            return mark;
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Nền ô
            g2.setColor(getBackground());
            g2.fillRect(0, 0, w, h);

            if (mark == 1) {
                // Vẽ quân X - nét mảnh, gọn trong ô (không chiếm hết ô cho thanh thoát)
                int pad = Math.max(10, Math.min(w, h) * 3 / 10);
                g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(COLOR_X);
                g2.drawLine(pad, pad, w - pad, h - pad);
                g2.drawLine(w - pad, pad, pad, h - pad);
            } else if (mark == 2) {
                // Vẽ quân O - vòng tròn mảnh, gọn trong ô
                int pad = Math.max(10, Math.min(w, h) * 3 / 10);
                g2.setStroke(new BasicStroke(2.4f));
                g2.setColor(COLOR_O);
                g2.drawOval(pad, pad, w - 2 * pad, h - 2 * pad);
            }

            g2.dispose();
        }
    }

    // ---------- Nút bo góc, có hiệu ứng khi rê chuột / bấm ----------

    private static class RoundedButton extends JButton {
        RoundedButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color base = getBackground();
            Color fill;
            if (!isEnabled()) {
                fill = new Color(158, 158, 158);
            } else if (getModel().isPressed()) {
                fill = base.darker();
            } else if (getModel().isRollover()) {
                fill = base.brighter();
            } else {
                fill = base;
            }

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            g2.dispose();

            super.paintComponent(g);
        }
    }

    // ---------- Panel bo góc dùng cho nhãn trạng thái kiểu "viên thuốc" ----------

    private static class RoundedPanel extends JPanel {
        private Color bg = Color.GRAY;

        RoundedPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        void setBg(Color c) {
            this.bg = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------- Hộp thoại kết nối lúc khởi động ----------

    /**
     * Hộp thoại gọn: hỏi tên người chơi + IP server trong cùng 1 lần.
     * Trả về null nếu người dùng bấm Cancel.
     */
    private static String[] askConnectionInfo() {
        JTextField nameField = new JTextField("Người chơi");
        JTextField ipField = new JTextField("localhost");

        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Tên của bạn:"));
        panel.add(nameField);
        panel.add(new JLabel("IP Server (để trống = localhost):"));
        panel.add(ipField);

        int result = JOptionPane.showConfirmDialog(
                null, panel, "Kết nối - Cờ Caro Online",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) return null;

        String name = nameField.getText().trim();
        String ip = ipField.getText().trim();
        if (name.isEmpty()) name = "Người chơi";
        if (ip.isEmpty()) ip = "localhost";
        return new String[]{name, ip};
    }

    public static void main(String[] args) {
        String[] info = askConnectionInfo();
        if (info == null) System.exit(0);

        final String finalName = info[0];
        final String finalIp = info[1];

        SwingUtilities.invokeLater(() -> {
            CaroClient client = new CaroClient(finalName);
            client.setVisible(true);
            client.connectToServer(finalIp, 12345);
        });
    }
}
