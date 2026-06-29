# CodBenchmarker 📱

An Android application for camouflage target detection using a custom YOLOv8 model built with LiteRT (TensorFlow Lite) and OpenCV.

---

## Setup Instructions 🚀

Since the OpenCV SDK is too large to host on GitHub, you need to download it separately before building the project.

### 1. Clone the Project
Clone the repository to your local machine:

```bash
git clone https://github.com/Vinush911/COD-vinush.git
```

### 2. Download the OpenCV Module
Download the pre-configured `opencv` folder from this Google Drive link:

👉 **[https://drive.google.com/file/d/17qFpAsKsoqhSOzzGiFv4hpbRiV37viSs/view?usp=drive_link]**

### 3. Place the Folder
Extract the downloaded folder and place the **`opencv/`** folder directly into the root directory of the cloned project (right next to the `app/` folder).

Your folder structure should look like this:

```text
CodBenchmarker/
├── app/
├── opencv/  <-- Place the downloaded folder here
├── build.gradle.kts
├── settings.gradle.kts
└── ...
```

### 4. Build and Run
1. Open the project in **Android Studio**.
2. Click **File ➔ Sync Project with Gradle Files**.
3. Connect your Android device and click **Run**.
