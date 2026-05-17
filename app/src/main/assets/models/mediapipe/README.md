# MediaPipe vision models (not in git)

Place these files in this directory before face/pet detection will run:

| File | Purpose |
|------|---------|
| `face_detection_short_range.tflite` | BlazeFace short-range — people |
| `efficientdet_lite0.tflite` | COCO object detector — dog/cat pets |

## Obtain models

From a MediaPipe Android sample checkout, copy from `app/src/main/assets/` after running the sample’s model download Gradle task, or download from [MediaPipe Face Detector](https://ai.google.dev/edge/mediapipe/solutions/vision/face_detector) and [Object Detector](https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector) model pages.

If models are missing, indexing still completes (CLIP + Gemma); face detection is skipped with a log warning.
