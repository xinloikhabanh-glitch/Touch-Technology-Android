# Touch Technology – Android

## Cách build (làm được trên điện thoại)
1. Tạo repo GitHub mới.
2. Upload 2 thứ: file `android-src.zip` (để nguyên, KHÔNG giải nén) và file `.github/workflows/android-build.yml`.
3. Tab Actions → "Build Android APK" → Run workflow. Chờ ~5-8 phút.
4. Tải artifact `TouchTechnology-apk`, giải nén ra file .apk và cài (cho phép cài từ nguồn không rõ).
Nếu build đỏ: mở bước "Hiện lỗi biên dịch" và gửi mình đoạn lỗi.

## Dùng Shizuku (để các nút có tác dụng thật)
1. Cài app Shizuku (github.com/RikkaApps/Shizuku/releases).
2. Android 11+: Tuỳ chọn nhà phát triển → Gỡ lỗi không dây → trong Shizuku bấm "Pairing" rồi "Start".
   Hoặc dùng adb: `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
3. Mở Touch Technology → bấm "Cấp quyền" ở dòng Shizuku. Mỗi lần khởi động lại máy cần Start lại Shizuku.

## An toàn
- Đổi độ phân giải/DPI có hộp thoại 15 giây tự hoàn lại nếu không bấm "Giữ".
- Nếu bị kẹt màn hình: adb shell `wm size reset; wm density reset`.
- Mật khẩu keystore (touchtech) để công khai trong file build, chỉ dùng cho cài cá nhân.

## Giới hạn thật
- App không làm cảm ứng nhanh hơn phần cứng; nút "Tăng độ nhạy (Samsung)" và "Ép tần số quét" phụ thuộc hãng máy.
- Bảng độ nhạy Free Fire là công thức gợi ý, không phải bảng chính thức.
