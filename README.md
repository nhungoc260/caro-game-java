# Cờ Caro Online (Client - Server)

Game Caro (Gomoku) 2 người chơi trực tuyến, đồng bộ nước đi qua server
trung gian — server đóng vai trò trọng tài, xác định thắng/thua/hòa theo
thời gian thực.

**Công nghệ:** Java · TCP Socket · Multithreading · Object Serialization · Swing

## Kỹ năng thể hiện qua project
- Thiết kế kiến trúc Client-Server qua giao thức TCP tự định nghĩa
- Xử lý đa luồng (mỗi client 1 thread riêng) và đồng bộ hóa dữ liệu dùng
  `synchronized` để tránh race condition
- Truyền dữ liệu qua mạng bằng Java Object Serialization
- Thuật toán kiểm tra thắng tối ưu (quét 4 hướng từ nước đi cuối, O(1)
  thay vì quét toàn bàn cờ O(n²))
- Xây dựng giao diện Swing tùy chỉnh: tự vẽ đồ họa bằng `Graphics2D`
  (không phụ thuộc font hệ thống), custom component (nút bo góc, status
  pill động)

## Tính năng
- Nhập tên người chơi, hiển thị tên đối thủ ngay trên tiêu đề cửa sổ
- Giao diện tông màu nâu gỗ, mô phỏng bàn cờ thật
- Tự động tô sáng ô vừa đánh và đường 5 quân thắng
- Bảng điểm Thắng / Thua / Hòa theo phiên chơi
- Chức năng "Chơi lại" — không cần khởi động lại chương trình
- Server tự phát hiện và hiển thị địa chỉ IP LAN khi khởi động

---
Built with by Nguyễn Trần Như Ngọc 🐨 – 2026
