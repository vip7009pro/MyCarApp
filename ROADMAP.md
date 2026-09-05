# ROADMAP - MyCarApp

## Giai đoạn 1: Chuẩn hóa Môi trường & Nâng cấp Target Android 16 (API 36)
- [x] Thiết lập biến môi trường hệ thống Windows (`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, PATH).
- [x] Tạo file cấu hình `local.properties` cho dự án.
- [x] Nâng cấp Gradle Wrapper lên 8.11.1 và AGP lên 8.10.1.
- [x] Nâng cấp Kotlin lên 2.0.0 và áp dụng plugin Compose compiler chính thức (`kotlin-compose`).
- [x] Cập nhật `compileSdk = 36` và `targetSdk = 36` cho module `:mobile`.
- [x] Cập nhật `compileSdk = 36` và `targetSdk = 36` cho module `:automotive`.
- [x] Cập nhật `compileSdkVersion 36` và `targetSdkVersion 36` cho module `:shared`.
- [x] Biên dịch kiểm thử thành công APK debug cho các module.

---

## Giai đoạn 2: Tương thích Runtime & Tối ưu hóa tính năng trên Android 16
- [ ] Rà soát và cập nhật các API bị deprecated (ví dụ: Activity Result API thay thế cho `onRequestPermissionsResult`).
- [ ] Kiểm tra các Foreground Service types (`location`, `mediaProjection`) theo chính sách bảo mật của Android 15/16.
- [ ] Kiểm tra tính tương thích của hệ thống thông báo (`POST_NOTIFICATIONS`) trên Android 16.

---

## Giai đoạn 3: Hoàn thiện tính năng trên Xe hơi & Bản đồ
- [ ] Kiểm tra tích hợp Android Auto trên xe (`androidx.car.app`).
- [ ] Kiểm tra tính năng dẫn đường & xem lại hành trình (`TripReplayActivity` & Google Maps).
- [ ] Tối ưu hóa hiệu năng xuất video hành trình (`TripReplayVideoExporter`).

---

## Giai đoạn 4: Đóng gói & Phát hành Google Play Store
- [x] Cấu hình signing keystore cho build release (với `speedtracker.jks`).
- [ ] Kiểm tra Proguard / R8 minification.
- [x] Build file Android App Bundle (`.aab`) qua `bundleRelease` (`versionCode = 2`, target SDK 36).
- [ ] Upload và xác thực trên Google Play Console với Target API 36.
