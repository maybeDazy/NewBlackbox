# BlackBox Virtual Environment - Complete User Guide

## Table of Contents
1. [Overview](#overview)
2. [Installation & Setup](#installation--setup)
3. [App Management](#app-management)
4. [WebView & Browser Support](#webview--browser-support)
5. [Google Services Integration](#google-services-integration)
6. [Background Job Management](#background-job-management)
7. [Troubleshooting](#troubleshooting)
8. [Advanced Features](#advanced-features)
9. [API Reference](#api-reference)
10. [Frequently Asked Questions](#frequently-asked-questions)
11. [Android Container Clone Blueprint (Korean)](#android-container-clone-blueprint-korean)

---

## Android Container Clone Blueprint (Korean)

> 아래 설계는 `ALEX5402/NewBlackbox` 기반 포팅을 전제로 한 **아키텍처 가이드**입니다. 실제 배포 시에는 각 앱의 약관, 개인정보보호법, 통신 관련 법규, 구글 플레이 정책을 반드시 준수하세요.

### 1) 목표 범위

- 최신 안드로이드(기준: Android 14/15 대응 코드 경로 유지)까지 가상 컨테이너 구동
- 호스트(실기기)에 설치된 앱 목록을 읽어, 선택한 앱별로 독립 컨테이너 생성
- 동일 앱 다중 인스턴스(예: `com.shop.app` x N 컨테이너) 지원
- WebView, 딥링크(Intent Filter), 파일 선택, 알림, 백그라운드 작업 호환
- 컨테이너별 디바이스 프로필(예: Android ID, 모델명 등) 오버라이드

### 2) Android Studio / SDK 권장 스펙

- **Android Studio**: Iguana 이상(Gradle 8.x + AGP 최신 안정 버전)
- **JDK**: 17
- **compileSdk**: 35 권장
- **targetSdk**: 34 이상(점진적 35 검증)
- **minSdk**: 26 권장
  - 이유: WebView/Scoped Storage/백그라운드 제약 대응 비용 대비 안정성
  - 레거시 확장이 필요하면 24까지 가능하나, 테스트 매트릭스 급증

### 3) 포팅 전략 (NewBlackbox 기반)

#### Phase A. 코어 포팅
1. `Bcore` 가상화/후킹 계층을 우선 유지
2. 앱 셸(`app`)은 UI/브랜딩만 교체(디자인 차별화)
3. Android 14+에서 막히는 PendingIntent, receiver export, foreground service 타입 점검

#### Phase B. 컨테이너 매니저 도입
1. `ContainerRegistry`(SQLite)로 컨테이너 메타 관리
2. `PackageCloneService`로 앱 import/install 파이프라인 통합
3. `ProfileSpoofService`로 컨테이너별 프로필 바인딩

#### Phase C. 안정화
1. WebView/딥링크 회귀 테스트
2. DB/파일 경로 권한 예외 전수 점검
3. e커머스 앱 중심 실사용 시나리오 테스트

### 4) 데이터 구조 (요청한 경로 반영)

요구사항 기준 루트:

`/data/user/0/daize.pro.container/databases/`

권장 레이아웃:

```text
/data/user/0/daize.pro.container/
  databases/
    container_registry.db
    containers/
      <container_uuid_1>/
        config.json
        virtual_data/
        virtual_de/<user>/
        webview/
      <container_uuid_2>/
        ...
```

- 컨테이너 ID는 `UUIDv4` 또는 `ULID` 권장
- 같은 패키지도 컨테이너 ID가 다르면 완전히 분리
- 매핑 테이블 예시:
  - `containers(container_id, package_name, display_name, created_at, state)`
  - `profiles(container_id, android_id, model, brand, device, phone_number_masked, ... )`
  - `launch_stats(container_id, last_launch_at, crash_count)`

### 5) 컨테이너 실행 플로우

1. 호스트 설치 앱 스캔 (`PackageManager`)
2. 사용자가 앱 선택
3. 컨테이너 UUID 발급 + registry 기록
4. 선택 앱을 가상 사용자/가상 UID 공간에 설치
5. 데이터 루트를 컨테이너 경로로 바인딩
6. 실행 시 후킹 계층에서 `container_id` 기준 프로필 주입

### 6) WebView / 딥링크 필수 체크

- WebView 데이터 디렉토리 suffix를 컨테이너 단위로 분리
- CookieManager, cache, service worker 저장소 충돌 방지
- 딥링크는 host ↔ virtual intent 브리지 설계
- 브라우저/쇼핑앱 결제 콜백 URL 스킴 회귀 테스트 필수

### 7) 스푸핑/후킹 설계 가이드 (안전 범위)

- 컨테이너별 프로필 값을 런타임에 주입
- `ANDROID_ID`, `Build.MODEL`, `Build.DEVICE`, `Build.MANUFACTURER`, `Build.VERSION.RELEASE` 등은 일관성 있게 세트 구성
- 전화번호/IMEI류는 앱·국가·정책·권한 상태에 따라 수집/노출 제한이 강하므로 **권한 및 법적 검토 선행**
- 값 변경 시 즉시 반영보다 "다음 실행부터 적용" 전략이 충돌이 적음

### 8) `Cannot create directory` / `SQLiteCantOpenDatabaseException` 예방

#### 디렉토리 생성 원칙
- DB 오픈 전에 부모 디렉토리 생성 + writable 검증
- 프로세스 동시 생성 경합을 막기 위해 파일 락 사용
- 경로 상수화(하드코딩 분산 금지) + 마이그레이션 유틸 제공

#### SQLite 안정화 원칙
- 앱 시작 초기에 registry DB를 선오픈(warm-up)
- WAL 모드 + busy timeout
- 스키마 버전 관리(DDL 변경 시 migration step 강제)

예시 체크 순서:

```kotlin
val root = File(context.filesDir.parentFile, "databases/containers/$containerId")
if (!root.exists() && !root.mkdirs()) {
    throw IOException("Cannot create directory: ${root.absolutePath}")
}
if (!root.canWrite()) {
    throw IOException("Directory not writable: ${root.absolutePath}")
}
```

### 9) SELinux / All Files Access 고려

- 일반 앱은 SELinux enforcing 환경에서 시스템 영역 우회 불가
- 따라서 가상화는 반드시 **앱 샌드박스 내부 경로 중심**으로 설계
- `MANAGE_EXTERNAL_STORAGE`(All Files Access)는 심사/정책 리스크가 매우 큼
- 가능하면 SAF(Storage Access Framework) + 앱 내부 저장소 우선
- 외부 저장소 접근이 필수일 때만 최소 범위 권한 요청

### 10) 모듈 구조 제안

- `app` : 런처/UI, 컨테이너 목록/생성/삭제 화면
- `Bcore` : 프로세스 가상화, 후킹, 패키지/UID 추상화
- `container-manager`(신규 권장) :
  - `ContainerRegistryDao`
  - `ContainerFilesystem`
  - `ContainerInstaller`
  - `ContainerLaunchOrchestrator`
- `profile-engine`(신규 권장) : 컨테이너별 프로필/규칙/적용 시점 관리

### 11) 화면(UX) 구성 초안

1. **홈**: 컨테이너 카드 리스트(앱 아이콘 + 컨테이너 ID + 상태)
2. **앱 추가**: 호스트 설치앱 검색/필터 후 다중 선택 생성
3. **컨테이너 상세**: 프로필 편집(Android ID, 기기명 등), 데이터 초기화, 복제
4. **로그/진단**: 최근 충돌, 권한 누락, WebView/딥링크 상태

### 12) 테스트 매트릭스 (e커머스 앱 중심)

- OS: Android 10 / 12 / 13 / 14 / 15 preview
- ABI: arm64-v8a 우선, armeabi-v7a 보조
- 시나리오:
  - 동일 앱 컨테이너 1개/3개/5개 동시 로그인
  - 딥링크 진입, 결제 리다이렉트, WebView 로그인 유지
  - 백그라운드 복귀/프로세스 kill 후 재실행
  - 컨테이너별 알림 토큰 분리 확인

### 13) 구현 우선순위 (현실적인 로드맵)

1. 컨테이너 생성/삭제 + 앱 import/install 안정화
2. 데이터 경로 분리 100% 보장
3. WebView/딥링크 호환성
4. 프로필 엔진(스푸핑 설정 UI + 적용)
5. 성능 최적화(콜드 스타트, 메모리)

---

## Overview

BlackBox is a comprehensive Android virtualization solution that creates isolated environments for running apps. The latest version includes significant improvements for:

- **App Installation & Management**: Robust app installation with cloning prevention
- **WebView Support**: Complete WebView compatibility for browsers and web apps
- **Google Services**: Enhanced Google account and GMS integration
- **Background Jobs**: WorkManager and JobScheduler compatibility
- **UID Management**: Smart UID spoofing for system compatibility
- **Crash Prevention**: Comprehensive error handling and recovery

---

## Installation & Setup

### Prerequisites
- Android 8.0+ (API 26+)
- Root access (recommended for full functionality)
- At least 2GB free storage space
- Internet connection for initial setup

### Basic Installation
1. **Download BlackBox APK** from the official source
2. **Install the APK** using your preferred method
3. **Grant Permissions** when prompted:
   - Storage access
   - System overlay (for floating features)
   - Location (for GPS spoofing)
   - Notification access (Android 12+)

### Initial Configuration
```bash
# First launch will create virtual environment
# Wait for initialization to complete
# Check logs for any setup issues
```

---

## App Management

### Installing Apps

#### Method 1: APK File Installation
```java
// Using BlackBoxCore API
BlackBoxCore.get().installPackageAsUser(apkFile, userId);

// Example with error handling
try {
    InstallResult result = BlackBoxCore.get().installPackageAsUser(apkFile, 0);
    if (result.isSuccess()) {
        Log.d("BlackBox", "App installed successfully: " + result.getPackageName());
    } else {
        Log.e("BlackBox", "Installation failed: " + result.getErrorMessage());
    }
} catch (Exception e) {
    Log.e("BlackBox", "Installation error", e);
}
```

#### Method 2: Package Name Installation
```java
// Install from existing package
BlackBoxCore.get().installPackageAsUser("com.example.app", userId);

// Check if package exists first
if (BlackBoxCore.getPackageManager().getPackageInfo("com.example.app", 0) != null) {
    BlackBoxCore.get().installPackageAsUser("com.example.app", userId);
}
```

#### Method 3: URI Installation
```java
// Install from content URI
Uri apkUri = Uri.parse("content://com.example.provider/app.apk");
BlackBoxCore.get().installPackageAsUser(apkUri, userId);
```

### App Removal

#### Uninstall Virtual App
```java
// Remove app from virtual environment
BlackBoxCore.get().uninstallPackage(packageName, userId);

// Force uninstall if needed
BlackBoxCore.get().uninstallPackage(packageName, userId, true);
```

#### Clean App Data
```java
// Clear app data without uninstalling
BlackBoxCore.get().clearAppData(packageName, userId);

// Clear specific data types
BlackBoxCore.get().clearAppData(packageName, userId, "cache");
BlackBoxCore.get().clearAppData(packageName, userId, "data");
```

### App Management Utilities

#### List Installed Apps
```java
// Get all virtual apps
List<AppInfo> virtualApps = BlackBoxCore.get().getInstalledApps(userId);

// Get specific app info
AppInfo appInfo = BlackBoxCore.get().getAppInfo(packageName, userId);

// Check if app is installed
boolean isInstalled = BlackBoxCore.get().isAppInstalled(packageName, userId);
```

#### App Configuration
```java
// Enable/disable app
BlackBoxCore.get().setAppEnabled(packageName, userId, true);

// Set app permissions
BlackBoxCore.get().setAppPermission(packageName, permission, userId, true);

// Configure app settings
BlackBoxCore.get().setAppSetting(packageName, setting, value, userId);
```

---

## WebView & Browser Support

### WebView Configuration

#### Automatic WebView Setup
The new WebView system automatically handles:
- **Unique Data Directories**: Each virtual app gets isolated WebView storage
- **Process Isolation**: WebView conflicts between apps are prevented
- **Data Persistence**: WebView data is preserved per app

#### Manual WebView Configuration
```java
// Set custom WebView data directory
WebView.setDataDirectorySuffix("custom_suffix");

// Configure WebView settings
WebView webView = new WebView(context);
WebSettings settings = webView.getSettings();
settings.setJavaScriptEnabled(true);
settings.setDomStorageEnabled(true);
settings.setDatabaseEnabled(true);
```

### Browser App Support

#### Chrome/Firefox Compatibility
```java
// Browser apps automatically get:
// - Isolated WebView instances
// - Separate cookie storage
// - Independent cache directories
// - Process isolation
```

#### Web App Support
```java
// Progressive Web Apps (PWAs) work with:
// - Service worker isolation
// - Cache storage separation
// - Background sync support
```

---

## Google Services Integration

### Google Account Management

#### Automatic Account Handling
```java
// Google accounts are automatically managed:
// - Mock Google accounts for virtual environment
// - Authentication token handling
// - Account synchronization
```

#### Custom Account Configuration
```java
// Add custom Google accounts
AccountManager accountManager = AccountManager.get(context);
Account account = new Account("user@gmail.com", "com.google");
accountManager.addAccountExplicitly(account, "password", null);

// Configure account sync
ContentResolver.setSyncAutomatically(account, "com.google", true);
```

### Google Play Services

#### GMS Compatibility
```java
// Google Play Services automatically:
// - Returns mock package info
// - Handles authentication requests
// - Provides fallback implementations
```

#### Custom GMS Configuration
```java
// Override GMS behavior if needed
GmsProxy.setCustomGmsInfo("com.example.gms", customInfo);

// Configure GMS permissions
GmsProxy.setGmsPermission("com.example.gms", permission, true);
```

---

## Background Job Management

### WorkManager Integration

#### Automatic WorkManager Handling
```java
// WorkManager automatically:
// - Handles UID validation issues
// - Provides fallback implementations
// - Prevents crashes on job scheduling
```

#### Custom Work Configuration
```java
// Configure custom work
WorkManager workManager = WorkManager.getInstance(context);

// Create work request
OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(MyWorker.class)
    .setInputData(inputData)
    .build();

// Enqueue work
workManager.enqueue(workRequest);
```

### JobScheduler Compatibility

#### Job Scheduling
```java
// Jobs are automatically handled with:
// - UID validation bypass
// - Fallback mechanisms
// - Error recovery
```

#### Custom Job Configuration
```java
// Create custom job
JobInfo.Builder builder = new JobInfo.Builder(jobId, componentName);
builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
builder.setRequiresCharging(true);

// Schedule job
JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
scheduler.schedule(builder.build());
```

---

## Advanced Features

### UID Spoofing

#### Automatic UID Management
```java
// UID spoofing automatically:
// - Detects UID validation issues
// - Selects appropriate UIDs for operations
// - Provides fallback UIDs when needed
```

#### Custom UID Configuration
```java
// Configure custom UID for specific operations
UIDSpoofingHelper.setCustomUID("operation", "package", customUID);

// Override UID selection logic
UIDSpoofingHelper.setUIDStrategy("operation", customStrategy);
```

### Process Management

#### Virtual Process Control
```java
// Control virtual processes
BlackBoxCore.get().startVirtualProcess(packageName, userId);
BlackBoxCore.get().stopVirtualProcess(packageName, userId);

// Monitor process status
ProcessInfo processInfo = BlackBoxCore.get().getProcessInfo(packageName, userId);
```

#### Memory Management
```java
// Optimize memory usage
BlackBoxCore.get().optimizeMemory(userId);

// Clear unused resources
BlackBoxCore.get().clearUnusedResources(userId);
```

---

## Troubleshooting

### Common Issues

#### App Installation Failures
```bash
# Check logs for installation errors
adb logcat | grep "BlackBox"

# Common solutions:
# 1. Ensure sufficient storage space
# 2. Check APK file integrity
# 3. Verify package compatibility
# 4. Clear BlackBox cache
```

#### WebView Issues
```bash
# WebView troubleshooting:
# 1. Check WebView data directories
# 2. Verify WebView provider status
# 3. Clear WebView cache
# 4. Restart virtual environment
```

#### Google Services Problems
```bash
# GMS troubleshooting:
# 1. Check GMS proxy status
# 2. Verify account configuration
# 3. Clear GMS cache
# 4. Reinstall GMS components
```

### Debug Mode

#### Enable Debug Logging
```java
// Enable comprehensive logging
BlackBoxCore.setDebugMode(true);

// Set log level
Slog.setLogLevel(Slog.LEVEL_DEBUG);

// Enable specific debug features
BlackBoxCore.enableDebugFeature("webview", true);
BlackBoxCore.enableDebugFeature("gms", true);
```

#### Log Analysis
```bash
# Filter BlackBox logs
adb logcat | grep "BlackBox\|WebView\|GmsProxy\|WorkManager"

# Save logs to file
adb logcat > blackbox_logs.txt

# Analyze specific components
adb logcat | grep "JobServiceStub\|WebViewProxy\|GoogleAccountManagerProxy"
```

---

## API Reference

### Core Classes

#### BlackBoxCore
```java
// Main entry point
BlackBoxCore core = BlackBoxCore.get();

// Core methods
core.installPackageAsUser(apkFile, userId);
core.uninstallPackage(packageName, userId);
core.getInstalledApps(userId);
core.isAppInstalled(packageName, userId);
```

#### BActivityThread
```java
// Activity thread management
int userId = BActivityThread.getUserId();
String packageName = BActivityThread.getAppPackageName();
String processName = BActivityThread.getAppProcessName();
```

#### UIDSpoofingHelper
```java
// UID management utilities
int systemUID = UIDSpoofingHelper.getSystemUID();
int packageUID = UIDSpoofingHelper.getPackageUID(packageName);
boolean needsSpoofing = UIDSpoofingHelper.needsUIDSpoofing(operation, packageName);
```

### Service Proxies

#### WebViewProxy
```java
// WebView management
WebViewProxy.configureWebView(webView, context);
WebViewProxy.setDataDirectorySuffix(suffix);
String dataDir = WebViewProxy.getDataDirectory();
```

#### WorkManagerProxy
```java
// WorkManager compatibility
WorkManagerProxy.enqueueWork(workRequest);
WorkManagerProxy.cancelWork(workId);
List<WorkInfo> workInfos = WorkManagerProxy.getWorkInfos();
```

#### GoogleAccountManagerProxy
```java
// Google account management
Account[] accounts = GoogleAccountManagerProxy.getAccounts();
String token = GoogleAccountManagerProxy.getAuthToken(account, authTokenType);
boolean success = GoogleAccountManagerProxy.addAccount(account, password, extras);
```

---

## Frequently Asked Questions

### Q: Why do some apps show black screens?
**A**: This is usually caused by context or resource loading issues. The new BlackBox version includes comprehensive fixes for:
- Context management
- Resource loading
- Activity lifecycle
- Service initialization

### Q: How do I fix WebView issues in browsers?
**A**: The new WebView system automatically handles:
- Data directory conflicts
- Process isolation
- Provider issues
- Cache management

### Q: Why do background jobs fail?
**A**: Background job failures are now handled by:
- WorkManager compatibility layer
- JobScheduler UID validation bypass
- Smart UID spoofing
- Graceful fallback mechanisms

### Q: How do I prevent app cloning issues?
**A**: BlackBox now includes:
- Automatic cloning prevention
- Package validation
- Security checks
- Error messages for blocked installations

### Q: What if Google services don't work?
**A**: The new GMS system provides:
- Mock Google Play Services
- Account authentication fallbacks
- Token management
- Service compatibility layers

---

## Support & Updates

### Getting Help
- **Documentation**: Check this Docs.md file
- **Logs**: Enable debug mode and analyze logs
- **Community**: Join BlackBox user forums
- **Issues**: Report bugs with detailed logs

### Version History
- **v2.0**: Complete rewrite with new architecture
- **v2.1**: WebView and browser compatibility
- **v2.2**: Google services integration
- **v2.3**: Background job management
- **Current**: UID spoofing and crash prevention

### Future Features
- **Enhanced Security**: Additional anti-detection features
- **Performance**: Memory and CPU optimization
- **Compatibility**: Support for more Android versions
- **Integration**: Additional service proxies

---

## Conclusion

The new BlackBox virtual environment provides a robust, feature-rich solution for Android app virtualization. With comprehensive WebView support, Google services integration, and background job management, it offers enterprise-grade functionality for both developers and end users.

For the best experience:
1. **Keep BlackBox updated** to the latest version
2. **Enable debug logging** when troubleshooting
3. **Monitor system resources** for optimal performance
4. **Report issues** with detailed logs for faster resolution

Happy virtualizing! 🚀✨
