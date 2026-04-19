# 📷 Where's That Photo

**On-device natural-language search for your photo library.**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![ONNX Runtime](https://img.shields.io/badge/ONNX%20Runtime-005CED?style=for-the-badge&logo=onnx&logoColor=white)](https://onnxruntime.ai/)
[![MediaPipe](https://img.shields.io/badge/MediaPipe-018786?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/mediapipe)
[![Room](https://img.shields.io/badge/Room-SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![License](https://img.shields.io/badge/License-TBD-94a3b8?style=for-the-badge)](#license)

[**Features**](#features) &nbsp;·&nbsp; [**Get started**](#get-started) &nbsp;·&nbsp; [Architecture](#architecture) &nbsp;·&nbsp; [Roadmap](#roadmap) &nbsp;·&nbsp; [Contributing](#contributing)

---

### 📦 Releases

> **[2026.4.18]** **v0.1.0** — Initial POC: Jetpack Compose shell, MediaStore indexing, MobileCLIP + MiniLM embeddings, Gemma 3n captions via MediaPipe, and RRF search.

**Past releases**

> *No earlier tagged releases.*



---

### 📰 News

> **[2026.4.18]** First public README with logo, architecture banner, and consolidated setup.

---



## ✨ Key features

- **Visual semantic search** — [MobileCLIP](https://huggingface.co/plhery/mobileclip2-onnx) image and text encoders map photos and queries into a shared 512-dimensional space for “mood,” composition, and scene-level matches.
- **Caption-aware search** — **Gemma 3n E4B** (MediaPipe) generates short English captions per photo; a **MiniLM** text encoder embeds captions and queries (384-dim) so specific phrases (“graduation,” “receipt”) match even when CLIP is weak.
- **Fused ranking** — Parallel CLIP and caption-vector hit lists merge with **reciprocal rank fusion** (RRF, `k = 60`) so results benefit from both signals.
- **Privacy-first POC** — Indexing, inference, and the SQLite database stay on the phone. No cloud API for search. *(Gemma’s `.task` file is sideloaded via `adb` for this POC.)*

---



## 🚀 Get started

### Clone and open

1. Clone the repo and open it in **Android Studio** (AGP 8.5 / Kotlin 2.0).
2. Let **Gradle sync** finish.
3. Add the **ONNX assets** and **Gemma** file as in [Model setup](#model-setup) below.
4. Run the `**app`** configuration on a **physical device** with enough RAM for ONNX + Gemma (~3 GB on disk for the `.task`).
5. Grant **Photos / media** (`READ_MEDIA_IMAGES`). Tap **Index CLIP**, then **Index Captions** (slow), then **Search**.

### Command-line build

```bash
git clone https://github.com/<your-org>/wheres-that-photo.git
cd wheres-that-photo
./gradlew :app:assembleDebug   # use gradlew.bat on Windows
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Requirements


| Requirement      | Notes                                                                                                       |
| ---------------- | ----------------------------------------------------------------------------------------------------------- |
| **JDK 17**       | Matches `app/build.gradle.kts`.                                                                             |
| **minSdk 31**    | Android 12+.                                                                                                |
| **Hugging Face** | Account; accept the **Gemma** license on the model card before downloading. Use `HF_TOKEN` in CI if needed. |


---



### Model setup

Runtime dependencies are already declared in `app/build.gradle.kts` (**ONNX Runtime** for MobileCLIP + MiniLM, **MediaPipe Tasks GenAI** for Gemma).

**Weights are not stored in this repository** (GitHub rejects blobs over 100MB). After cloning, run the `hf download` commands in sections 2 and 3 and copy the files into the layout below.

**1. Asset layout** — create under `app/src/main/assets/`:

```
models/
  mobileclip/
    vision_model.onnx
    text_model.onnx
    tokenizer.json          # CLIP BPE (not MiniLM’s tokenizer)
  minilm/
    model.onnx
    tokenizer.json
    vocab.txt
    config.json
    tokenizer_config.json
    special_tokens_map.json
```

Do **not** put the Gemma `.task` in `assets/` (~3 GB; use device storage below).

**2. MobileCLIP (s0)** — source: [plhery/mobileclip2-onnx](https://huggingface.co/plhery/mobileclip2-onnx). You also need `tokenizer.json` from [openai/clip-vit-base-patch32](https://huggingface.co/openai/clip-vit-base-patch32).

```bash
pip install -U "huggingface_hub[cli]"
hf download plhery/mobileclip2-onnx onnx/s0/vision_model.onnx --local-dir temp_models
hf download plhery/mobileclip2-onnx onnx/s0/text_model.onnx --local-dir temp_models
hf download openai/clip-vit-base-patch32 tokenizer.json --local-dir temp_models/clip_tok
```

Copy into `app/src/main/assets/models/mobileclip/` (`vision_model.onnx`, `text_model.onnx`, `tokenizer.json` from `clip_tok/`).

**3. MiniLM** — [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2):

```bash
hf download sentence-transformers/all-MiniLM-L6-v2 onnx/model.onnx --local-dir temp_models
hf download sentence-transformers/all-MiniLM-L6-v2 tokenizer.json --local-dir temp_models
hf download sentence-transformers/all-MiniLM-L6-v2 config.json --local-dir temp_models
hf download sentence-transformers/all-MiniLM-L6-v2 tokenizer_config.json --local-dir temp_models
hf download sentence-transformers/all-MiniLM-L6-v2 special_tokens_map.json --local-dir temp_models
hf download sentence-transformers/all-MiniLM-L6-v2 vocab.txt --local-dir temp_models
```

Copy those files into `app/src/main/assets/models/minilm/`.

**4. Gemma 3n E4B** — prebuilt MediaPipe bundle: [google/gemma-3n-E4B-it-litert-preview](https://huggingface.co/google/gemma-3n-E4B-it-litert-preview). Download the `.task` file, then push to the path the app uses:

```bash
hf download google/gemma-3n-E4B-it-litert-preview <filename>.task --local-dir temp_models
adb shell mkdir -p /data/local/tmp/llm
adb push temp_models/<filename>.task /data/local/tmp/llm/model.task
```

Captioning requires **vision modality** enabled in code (`GemmaCaptioner.kt`): `setMaxNumImages(1)`, `setEnableVisionModality(true)`, and `**addImage` before `addQueryChunk`**. If captions ignore the image, vision is not active.

**5. Quick checks**


| Check      | Pass criterion                                                                                      |
| ---------- | --------------------------------------------------------------------------------------------------- |
| CLIP image | `embedImage` → 512 floats, L2 norm ≈ 1, values not all ~0.                                          |
| CLIP text  | Similarity of “a photo of a dog” vs “a photo of a cat” in ~0.6–0.9; same string twice ≈ 1.0.        |
| MiniLM     | Similarity of “commencement ceremony” vs “graduation” > ~0.7 (else pooling/normalization is wrong). |
| Gemma      | After push, caption a real photo; output should describe the scene, not generic filler.             |


---



## 📖 Architecture




| Stage              | What runs                                                                                                                                                                              |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Ingest**         | [MediaStore](https://developer.android.com/training/data-storage/shared/media) lists images; Room stores ids, URIs, and timestamps.                                                    |
| **Vision vectors** | ONNX Runtime runs MobileCLIP `vision_model.onnx` → L2-normalized 512-d embeddings.                                                                                                     |
| **Captions**       | MediaPipe **LlmInference** runs Gemma 3n E4B with vision modality; captions stored in Room.                                                                                            |
| **Text vectors**   | ONNX MiniLM embeds caption text (mean-pool with attention mask, then L2-normalize) → 384-d vectors.                                                                                    |
| **Query**          | Query embedded with CLIP text + MiniLM; cosine similarity rankings fused with **RRF** ([SearchRepository.kt](app/src/main/java/com/srini/wheresthatphoto/search/SearchRepository.kt)). |


Schema, RRF detail, and future **sqlite-vec** notes: **[project_plan.md](project_plan.md)**.

---



## 🗺️ Roadmap


| Status         | Milestone                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Done (POC)** | Compose UI, Room, CLIP + caption indexing, RRF search in Kotlin.                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **Next**       | **Per-photo progress UI** — replace the thin `LinearProgressIndicator` with a card that shows *"indexing N of M"*, the current photo's thumbnail, and the Gemma caption as it is generated. Requires `Indexer.kt` to emit a `Flow<IndexProgress>` instead of returning an `Int`, and (optionally) streaming token output from `GemmaCaptioner.kt`.                                                                                                                                                                |
| **Next**       | **UI polish** — compact header + primary/secondary action card, a dismissible permission banner, empty states for "no photos picked / no search run / no results," a photo **detail sheet** (full image + Gemma caption + match breakdown showing CLIP vs caption-embedding score + re-caption button), and a Material 3 seed color with dynamic color on Android 12+.                                                                                                                                            |
| **Next**       | sqlite-vec or similar for large libraries; Gemma in app-internal storage with download UI.                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Next**       | Background indexing (WorkManager) with thermal/charging constraints.                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **Next**       | **Dev setup script** — one command (e.g. `scripts/setup-models.ps1` / `setup-models.sh`) that installs or checks `huggingface_hub` CLI, runs all `hf download` steps from [Model setup](#model-setup) for MobileCLIP, CLIP tokenizer, and MiniLM, copies outputs into `app/src/main/assets/models/{mobileclip,minilm}/`, verifies expected filenames exist, and prints the `adb push` path for the Gemma `.task` (device-specific; optional `--push-gemma` if a device is connected). Document the script in **Get started** so a fresh clone can fetch weights without hand-copying. |
| **Later**      | **Person-specific search** — face detection (MLKit / MediaPipe Face Detector) + on-device face embeddings (e.g. MobileFaceNet) + clustering, plus a **People** tab to name each cluster. Face matches fuse into RRF as a third ranking signal alongside CLIP and caption embeddings, so queries like *”grandma's birthday”* find your specific grandmother rather than any elderly adult. Requires a new entity (`PersonEntity`, `FaceEmbeddingEntity`), a face-indexing pass in `Indexer.kt`, and a labeling UI. |
| **Later**      | Play Store, broader devices, optional “more like this” search.                                                                                                                                                                                                                                                                                                                                                                                                                                                    |


---



## 🤝 Contributing

Issues and PRs are welcome. If you change inference or tokenization, re-run the [quick checks](#model-setup) above. Keep diffs focused.

---



## 📄 License

Application source in this repository does not yet include a `LICENSE` file—add one when you open-source the project.

**Model weights** (Gemma, MobileCLIP ports, MiniLM from Hugging Face) are governed by **their respective licenses**; review and comply before redistribution.

---



**Where's That Photo** — find the shot without sending it to the cloud.

