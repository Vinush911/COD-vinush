# 📱 Data Ingestion Module – CodBenchmarker

This module is part of the **COD project** and focuses on Android-based data ingestion and model benchmarking for camouflage detection using YOLO models.

---

## 🚀 Features

* 📷 Real-time image input
* 🧠 Camouflage detection using YOLO models
* ⚡ Model benchmarking (float16, int8 variants)
* 🎯 Overlay visualization for detections
* 📊 Performance comparison of models

---

## 📁 Project Structure

```
CodBenchmarker/
│
├── app/
│   ├── src/main/java/...        # Core Kotlin code
│   ├── src/main/assets/        # ML models + test images
│   ├── res/                    # UI resources
│
├── gradle/                     # Gradle wrapper
├── build.gradle.kts            # Project config
├── settings.gradle.kts
```

---

## 🛠️ Requirements

* Android Studio (latest recommended)
* Android SDK (API level compatible)
* Java/Kotlin environment
* Gradle (auto-managed)

---

## 📥 How to Clone

```bash
git clone https://github.com/Vinush911/COD-vinush.git
cd COD-vinush/vinush/data\ ingestion/CodBenchmarker
```

---

## ▶️ How to Run

1. Open **Android Studio**
2. Click **Open Project**
3. Select:

   ```
   CodBenchmarker/
   ```
4. Let Gradle sync complete
5. Connect your device or emulator
6. Click **Run ▶**

---

## 📦 Models Used

Located in:

```
app/src/main/assets/
```

Includes:

* `yolov8n_float16.tflite`
* `yolov8n_int8.tflite`
* `best_int8.tflite`

---

## ⚠️ Important Notes

* Ensure device permissions (Camera, Storage) are enabled
* First run may take time due to model loading
* Do NOT include build folders when contributing

---

## 🤝 Contribution

1. Fork the repository
2. Create a new branch
3. Make changes
4. Submit a Pull Request

---

## 👤 Author

**Vinush Kumar**

---

## 📌 Future Improvements

* Real-time video stream optimization
* Better model accuracy tuning
* UI enhancements
* Cloud-based model updates

---

## 📄 License

This project is part of a collaborative academic/research initiative.
