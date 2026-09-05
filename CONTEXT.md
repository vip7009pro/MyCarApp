# CONTEXT - MyCarApp

## 1. Thông tin chung
- **Tên dự án**: `MyCarApp`
- **Mục tiêu**: Ứng dụng Android đa nền tảng gồm ứng dụng di động (`mobile`), ứng dụng trên ô tô (`automotive`), và thư viện dùng chung (`shared`).
- **Nền tảng**: Android (hỗ trợ Android Auto & Android Automotive OS).

---

## 2. Kiến trúc & Các module
- **`:mobile`**:
  - Namespace: `hn.page.mycarapp`
  - Chức năng: Ứng dụng di động chính với giao diện Jetpack Compose, hiển thị bản đồ (Google Maps), tracking hành trình (ForegroundTrackingService), ghi lại & phát lại hành trình (ReplayRecordingService & TripReplayActivity), quản lý chuyến đi (TripsActivity, Room Database).
  - Tích hợp Android Auto với `androidx.car.app` (`MyCarAppService`, `MainCarScreen`).
- **`:automotive`**:
  - Namespace: `hn.page.mycarapp`
  - Chức năng: Ứng dụng độc lập trên xe hơi (Android Automotive OS) xử lý tin nhắn & thông báo rảnh tay (`MyMessagingService`, `MessageReadReceiver`, `MessageReplyReceiver`).
- **`:shared`**:
  - Namespace: `hn.page.mycarapp.shared`
  - Chức năng: Thư viện phát nhạc & media dùng chung (`MyMusicService` kế thừa `MediaBrowserService`).

---

## 3. Môi trường phát triển & Cấu hình Build
- **Hệ điều hành**: Windows 11 64-bit
- **JDK**: Microsoft OpenJDK 17 (`17.0.20.1 LTS`)
  - Đường dẫn: `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`
- **Android SDK**: `C:\Users\Admin\AppData\Local\Android\Sdk`
  - Platforms: `android-36` (Android 16), `android-35`, `android-34`
  - Build-tools: `36.0.0`
- **Gradle Wrapper**: `8.11.1`
- **Android Gradle Plugin (AGP)**: `8.10.1`
- **Kotlin**: `2.0.0` (kèm plugin `org.jetbrains.kotlin.plugin.compose`)
- **Target SDK**: `36` (Android 16 - tuân thủ quy định mới nhất của Google Play Store)
- **Compile SDK**: `36`
- **Min SDK**: `28` (Android 9.0)

---

## 4. Lịch sử thay đổi gần nhất
- **2026-09-05**:
  - Thiết lập biến môi trường hệ thống Windows: `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, cập nhật `Path` với `platform-tools`, `cmdline-tools\latest\bin`.
  - Khởi tạo file cấu hình cục bộ `local.properties` trỏ đúng Android SDK.
  - Nâng cấp AGP từ `8.10.0` lên `8.10.1`.
  - Nâng cấp Kotlin từ `1.9.24` lên `2.0.0`.
  - Bổ sung plugin chính thức `kotlin-compose` cho Kotlin 2.0 và loại bỏ `composeOptions` cũ.
  - Cập nhật toàn diện `compileSdk = 36` và `targetSdk = 36` cho cả 3 module `:mobile`, `:automotive`, `:shared` đáp ứng yêu cầu Play Store (Target Android 16+).
  - Tăng `versionCode` lên `2`.
  - Cấu hình release signing với keystore `G:\NODEJS\speedtracker.jks` (alias `speedtracker`).
  - Đã build và ký thành công file Android App Bundle: `mobile/build/outputs/bundle/release/mobile-release.aab` (~20.06 MB) sẵn sàng upload Google Play Console.
