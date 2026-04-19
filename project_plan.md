# Where's That Photo

*2-day POC build plan · Android · offline · AI-powered*

## 1. Project summary

A native Android app that indexes the user's photo library and makes it searchable with natural-language queries like "dog on a beach," "receipt from restaurant," or "moody sunset." Everything runs on-device. No server, no cloud, no data leaves the phone.

The POC proves three capabilities end-to-end: visual semantic search via MobileCLIP, AI-generated captions via Gemma 4 E4B, and fuzzy keyword search via embedded caption text. Search combines all three signals for best-in-class results.

### Target device

Pixel 9 Pro (12 GB RAM, Tensor G4 with TPU). This device has the headroom to run MobileCLIP, Gemma 4 E4B, and a text embedder simultaneously without thermal or memory issues. No other devices need to be supported for the POC.

### POC scope

- **In scope:** indexing, searching, caption generation, viewing results.
- **Out of scope:** face recognition, location/map view, albums, sharing, upload to any backend, Play Store packaging, multi-device support, low-RAM device handling.
- **Dev setting:** Gemma captioning is always-on (no opt-in UI needed for POC).
- **Library size:** test against a few hundred to a few thousand photos. Don't optimize for 30k+.

## 2. Architecture overview

Three models, one SQLite database, one app. The models each solve a different part of the search problem, and their outputs combine at query time.

### The three signals

| Signal | Produced by | Stored as | Catches queries like |
| --- | --- | --- | --- |
| Visual vector | MobileCLIP image encoder | 512-dim vector per photo | "moody", "minimalist", "sunset palette" |
| Caption text | Gemma 4 E4B | Short English paragraph per photo | (not queried directly; feeds next signal) |
| Caption vector | Sentence-transformers text encoder | 384-dim vector per caption | "my daughter at graduation", "airport baggage claim" |

At search time, the user's query is embedded by both MobileCLIP's text encoder and the sentence-transformers encoder. Two vector searches run in parallel against their respective tables. Results fuse via reciprocal rank fusion. Top photos surface to the UI.

### Why this architecture

- MobileCLIP catches abstract visual qualities that captions tend to miss (mood, style, palette, composition).
- Caption vectors catch specific nouns and semantic variations of captions ("commencement" matching a caption that says "graduation").
- Together they cover queries that either one alone would miss, with minimal overlap in failure modes.
- All searches are vector operations; no full-text index or keyword matching needed in the POC.

## 3. The five libraries you'll use

| Name | What it is | What it does for you | Size |
| --- | --- | --- | --- |
| MediaStore | Android system API | Lists photos on the device; gives you URIs and EXIF metadata | Built-in |
| MobileCLIP | Apple's mobile-optimized CLIP | Embeds photos and queries into a shared vector space | ~30 MB |
| MediaPipe | Google's on-device ML framework | Runs Gemma and handles NPU/GPU delegation | ~15 MB SDK |
| Gemma 4 E4B | Google's small multimodal LLM | Reads each photo and writes a short description | ~2.5 GB |
| sentence-transformers (MiniLM) | Small text-embedding model | Turns captions and queries into vectors for fuzzy matching | ~90 MB |

ML Kit (face detection) and thermal/scheduling machinery are explicitly omitted from the POC. Add them in a follow-up milestone.

## 4. Database schema

Single SQLite database accessed via Room. Three tables, two of them vector-indexed via the sqlite-vec extension.

### `photos`

```sql
id TEXT PRIMARY KEY          -- MediaStore ID
uri TEXT NOT NULL            -- content:// URI
taken_at INTEGER             -- unix millis from EXIF
lat REAL, lng REAL           -- EXIF GPS (nullable)
width INTEGER, height INTEGER
indexed_at INTEGER           -- when CLIP ran
captioned_at INTEGER         -- when Gemma ran, nullable
```

### `clip_embeddings` (sqlite-vec virtual table)

```sql
photo_id TEXT PRIMARY KEY
embedding FLOAT[512]         -- MobileCLIP image vector
```

### `captions`

```sql
photo_id TEXT PRIMARY KEY
text TEXT NOT NULL           -- Gemma's description
model_version TEXT
```

### `caption_embeddings` (sqlite-vec virtual table)

```sql
photo_id TEXT PRIMARY KEY
embedding FLOAT[384]         -- text encoder vector
```

Foreign key relationships: all three secondary tables reference `photos.id`. A photo can exist without a caption (indexed but not yet captioned). A caption always has a `caption_embedding` — they're generated together.

## 5. Two-day schedule

### Day 1: visual search end-to-end

**Goal:** by end of day, type a query and see photos ranked by MobileCLIP similarity.

**Morning (4 hours)**

1. Create Android Studio project: empty Compose Activity, Kotlin, min SDK 31.
2. Add dependencies: Room, ONNX Runtime Mobile, sqlite-vec JNI library, CameraX (for preview scaling utilities), Coil (for image loading in Compose).
3. Request `READ_MEDIA_IMAGES` permission. Verify with a test that lists 10 photo URIs in logcat.
4. Set up Room database with `photos` and `clip_embeddings` tables. Load the sqlite-vec native library on app start.

**Afternoon (4 hours)**

5. Download MobileCLIP-S0 ONNX model (image + text encoders, two files) and bundle in `app/src/main/assets/`.
6. Write `ClipEncoder.kt`: loads the ONNX session, exposes `embedImage(bitmap)` and `embedText(query)` returning `FloatArray(512)`.
7. Write `Indexer.kt`: iterates MediaStore, loads each image at 224×224, runs `embedImage`, writes row to DB. Plain coroutine, not a service yet.
8. Test manually: hit "Index" button, watch logs, verify DB row count matches photo count.

**Evening (2 hours)**

9. Build search screen with Jetpack Compose: `TextField` at top, `LazyVerticalGrid` below.
10. On query submit: `embedText(query)`, SQL query against sqlite-vec with cosine distance, return top 50 `photo_ids`.
11. Load thumbnails via Coil using the stored URIs. Ship day 1.

**End-of-day-1 demo:** "dog on a beach" shows your actual dog photos. This alone justifies the project.

### Day 2: captions and fuzzy search

**Goal:** by end of day, every photo has a Gemma-generated caption, and queries also match against caption-embedding vectors.

**Morning (4 hours)**

12. Add MediaPipe LLM Inference dependency. Download Gemma 4 E4B `.task` file (~2.5 GB) to the app's internal storage via `adb push` for POC — skip the real download UI.
13. Write `GemmaCaptioner.kt`: initializes `LlmInference` with the `.task` file, exposes `caption(bitmap)` returning a short `String`. Use the prompt template below.
14. Prompt template: *"Describe this image in 2-3 short sentences. Include the main subject, setting, and mood. Mention visible people, animals, or objects."*
15. Test manually: caption one photo, print to log, sanity-check the output.

**Afternoon (4 hours)**

16. Add sentence-transformers MiniLM model (ONNX, ~90 MB) to assets.
17. Write `TextEncoder.kt`: loads ONNX session, exposes `embed(text)` returning `FloatArray(384)`.
18. Extend `Indexer.kt` with a second pass: for each photo without a caption, run Gemma, embed the caption, write rows to `captions` and `caption_embeddings` tables.
19. This is the slow phase — let it run while you eat.

**Evening (2 hours)**

20. Update search: on query, embed with both MobileCLIP text encoder and MiniLM. Two parallel sqlite-vec queries, top 50 each.
21. Fuse with reciprocal rank fusion: `score = sum over both lists of 1 / (60 + rank)`. Sort. Return top 50.
22. Add a photo detail screen: tap photo → show the caption Gemma wrote. Good for demoing the LLM layer.
23. Ship day 2.

**End-of-day-2 demo:** query "receipt" finds captioned receipts that CLIP alone missed. Query "moody sunset" finds atmospheric photos that keyword-search alone would miss. Every photo has a caption visible on tap.

## 6. Reciprocal rank fusion (the search merge)

RRF combines two ranked lists into one. The formula is simple and tunable.

```
rrf_score(photo) = sum over ranked lists of:
    1.0 / (k + rank_in_list)    // k = 60 by default
```

Photos appearing in both lists score higher than photos in just one. A photo ranked #1 by CLIP and #3 by caption vectors gets: `1/(60+1) + 1/(60+3) = 0.0164 + 0.0159 = 0.0323`. A photo ranked #1 by CLIP only gets `0.0164`. The #1-in-both photo wins.

`k = 60` is the standard default from the original RRF paper. Tune it if you notice one signal dominating: larger `k` flattens ranks (both signals contribute more evenly), smaller `k` steepens them (top-ranked items dominate).

## 7. Known unknowns and how to handle them

### Will Gemma hallucinate names and identities?

Yes, sometimes. The prompt explicitly says "do not guess at identities." But expect occasional weird outputs like inventing names. Not a POC blocker; note it and move on.

### How long does captioning take?

On a Pixel 9 Pro, budget 8–12 seconds per photo for Gemma E4B captions. For a 500-photo test library, that's ~1–2 hours. Plan your afternoon test run accordingly. Don't try to caption 10,000 photos for the POC — pick a subset.

### What if sqlite-vec misbehaves?

Fallback: do cosine similarity in Kotlin. Load all 500 vectors from SQLite into a `FloatArray` in memory, do the dot products manually. This is fine for POC scale and still completes in <100 ms. Switch to sqlite-vec once the rest is working.

### What if MobileCLIP's text encoder gives weaker results than expected?

Upgrade to MobileCLIP-S2 (larger, more accurate, still mobile-viable). Same API, just a bigger model file.

## 8. After the POC

These are explicitly not for this 2-day build, but worth tracking for follow-up milestones:

- Face detection and clustering via ML Kit
- Location-based search (reverse-geocode EXIF GPS)
- Proper Gemma model download flow with progress UI
- Background WorkManager worker with thermal/idle constraints
- Opt-in onboarding for low-RAM device handling
- Conversational agent: "show me my best hiking photos from last year"
- Image-to-image similarity ("more like this one")
- Pet-specific recognition as a unique differentiator
- Play Store submission

## 9. Success criteria for the POC

1. App launches on the Pixel 9 Pro without crashing.
2. Indexing runs to completion on at least 200 test photos.
3. Every photo gets a CLIP vector, a Gemma caption, and a caption vector.
4. Search returns plausible results within 500 ms for typical queries.
5. Search quality noticeably improves with captions enabled vs CLIP-only.
6. Tapping a photo shows its caption, and the caption is coherent.

If all six check out, the POC is done and the architecture is validated. The rest is product development.
