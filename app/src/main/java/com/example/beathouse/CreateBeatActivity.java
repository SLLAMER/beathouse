package com.example.beathouse;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.beathouse.databinding.ActivityCreateBeatBinding;
import com.example.beathouse.models.Beat;
import com.example.beathouse.models.User;
import com.example.beathouse.utils.FirebaseAuthHelper;
import com.example.beathouse.utils.FirestoreHelper;
import com.google.firebase.auth.FirebaseUser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CreateBeatActivity extends BaseActivity {

    private ActivityCreateBeatBinding binding;
    private FirestoreHelper firestoreHelper;
    private FirebaseAuthHelper authHelper;

    private String audioBase64 = "";
    private String coverBase64 = "";
    private User currentUser;
    private Beat editingBeat = null;

    private String[] genres = {
            "Hip-Hop", "Trap", "R&B", "Drill", "Pop", "Electronic", "Lo-Fi",
            "Rock", "Jazz", "Classical", "Country", "Blues", "Reggae", "Funk",
            "Soul", "Disco", "Techno", "House", "Ambient", "Dubstep", "Grime"
    };
    private String[] keys = {
            "Cmin", "Cmaj", "C#min", "C#maj", "Dmin", "Dmaj", "D#min", "D#maj",
            "Emin", "Emaj", "Fmin", "Fmaj", "F#min", "F#maj", "Gmin", "Gmaj",
            "G#min", "G#maj", "Amin", "Amaj", "A#min", "A#maj", "Bmin", "Bmaj"
    };

    private static final int PICK_AUDIO_REQUEST = 1;
    private static final int PICK_COVER_REQUEST = 2;
    private static final long MAX_AUDIO_SIZE = 10 * 1024 * 1024;
    private static final long MAX_COVER_SIZE = 2 * 1024 * 1024;
    private static final String TAG = "CreateBeatActivity";
    private boolean isActivityDestroyed = false;
    private boolean isUpdatingBeat = false;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateBeatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firestoreHelper = new FirestoreHelper();
        authHelper = new FirebaseAuthHelper(this);

        setupToolbar();
        setupDropdowns();
        setupUI();
        loadCurrentUser();

        checkForEditMode();
        setupSwipeToExit();
    }

    private void setupSwipeToExit() {
        gestureDetector = new GestureDetector(this, new com.example.beathouse.utils.SwipeGestureHelper(new com.example.beathouse.utils.SwipeGestureHelper.OnSwipeListener() {
            @Override
            public void onSwipeLeft() {
                Log.d(TAG, "onSwipeLeft detected, finishing activity");
                finish();
            }

            @Override
            public void onSwipeRight() {}
        }));

        binding.getRoot().setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        // Also apply to ScrollView content to catch swipes there
        binding.nestedScrollView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false; // Let ScrollView handle its own scrolling
        });
    }

    private void checkForEditMode() {
        editingBeat = (Beat) getIntent().getSerializableExtra("EDIT_BEAT");
        if (editingBeat != null) {
            isUpdatingBeat = true;
            binding.toolbar.setTitle(getString(R.string.edit_beat));
            binding.btnUploadBeat.setText(getString(R.string.update_beat));
            fillExistingBeatData(editingBeat);
        }
    }

    private void fillExistingBeatData(Beat beat) {
        binding.etTitle.setText(beat.getTitle());
        binding.etDescription.setText(beat.getDescription() != null ? beat.getDescription() : "");
        binding.etGenre.setText(beat.getGenre() != null ? beat.getGenre() : "Hip-Hop");
        binding.etKey.setText(beat.getKey() != null ? beat.getKey() : "Cmin");
        binding.etBpm.setText(String.valueOf(beat.getBpm()));

        // ✅ Заполняем цены лицензий
        if (beat.getPriceMp3Wav() > 0) {
            binding.etPriceMp3Wav.setText(String.valueOf((int) beat.getPriceMp3Wav()));
        }
        if (beat.getPriceTrackOut() > 0) {
            binding.etPriceTrackOut.setText(String.valueOf((int) beat.getPriceTrackOut()));
        }
        if (beat.getPriceExclusive() > 0) {
            binding.etPriceExclusive.setText(String.valueOf((int) beat.getPriceExclusive()));
        }

        if (beat.isFree()) {
            binding.switchFree.setChecked(true);
            binding.licensePricesLayout.setVisibility(View.GONE);
        } else {
            binding.switchFree.setChecked(false);
            binding.licensePricesLayout.setVisibility(View.VISIBLE);
        }

        if (beat.hasCover() && beat.getCoverImage() != null && !beat.getCoverImage().isEmpty()) {
            coverBase64 = beat.getCoverImage();
            byte[] decodedBytes = Base64.decode(coverBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            if (bitmap != null) {
                binding.ivCoverPreview.setVisibility(View.VISIBLE);
                binding.ivCoverPreview.setImageBitmap(bitmap);
            }
        }

        if (beat.hasAudio() && beat.getFullAudio() != null) {
            audioBase64 = beat.getFullAudio();
            binding.tvAudioFile.setText(getString(R.string.audio_loaded));
            binding.tvAudioFile.setTextColor(getColor(R.color.success));
        }

        updateUploadButtonState();
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setTitle(getString(R.string.upload_new_beat));
    }

    private void setupDropdowns() {
        ArrayAdapter<String> genreAdapter = new ArrayAdapter<>(this, R.layout.dropdown_menu_item, genres);
        binding.etGenre.setAdapter(genreAdapter);
        binding.etGenre.setText("Hip-Hop", false);

        ArrayAdapter<String> keyAdapter = new ArrayAdapter<>(this, R.layout.dropdown_menu_item, keys);
        binding.etKey.setAdapter(keyAdapter);
        binding.etKey.setText("Cmin", false);
    }

    private void setupUI() {
        binding.btnSelectAudio.setOnClickListener(v -> selectAudioFile());
        setupCoverSelection();

        binding.switchFree.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.licensePricesLayout.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            updateUploadButtonState();
        });

        // ✅ Отслеживаем изменения в полях цен
        View.OnFocusChangeListener priceListener = (v, hasFocus) -> updateUploadButtonState();
        binding.etPriceMp3Wav.setOnFocusChangeListener(priceListener);
        binding.etPriceTrackOut.setOnFocusChangeListener(priceListener);
        binding.etPriceExclusive.setOnFocusChangeListener(priceListener);

        binding.btnUploadBeat.setOnClickListener(v -> validateAndUploadBeat());
        binding.etBpm.setText("120");
        binding.switchFree.setChecked(true);
        binding.btnUploadBeat.setEnabled(false);
        binding.progressBar.setVisibility(View.GONE);
    }

    private void setupCoverSelection() {
        View coverSelectArea = findViewById(R.id.coverSelectArea);
        if (coverSelectArea != null) {
            coverSelectArea.setOnClickListener(v -> selectCoverFromGallery());
        }
        binding.ivCoverPreview.setOnClickListener(v -> selectCoverFromGallery());
        View coverCard = binding.getRoot().findViewById(R.id.cover_card);
        if (coverCard != null) {
            coverCard.setOnClickListener(v -> selectCoverFromGallery());
        }
    }

    private void loadCurrentUser() {
        FirebaseUser firebaseUser = authHelper.getCurrentUser();
        if (firebaseUser != null) {
            firestoreHelper.getUser(firebaseUser.getUid(), new FirestoreHelper.FirestoreCallback() {
                @Override
                public void onSuccess(Object result) {
                    currentUser = (User) result;
                    updateUploadButtonState();
                    Log.d(TAG, "User loaded: " + currentUser.getUsername());
                }
                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error loading user: " + error);
                    currentUser = new User();
                    currentUser.setId(firebaseUser.getUid());
                    currentUser.setUsername(firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "Producer");
                    updateUploadButtonState();
                }
            });
        } else {
            Toast.makeText(this, getString(R.string.login_first), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void updateUploadButtonState() {
        boolean hasAudio = !TextUtils.isEmpty(audioBase64);
        boolean hasUser = currentUser != null;

        // ✅ Проверяем цены если бит платный
        boolean hasValidPrices = true;
        if (!binding.switchFree.isChecked()) {
            hasValidPrices = hasValidPrice(binding.etPriceMp3Wav) &&
                    hasValidPrice(binding.etPriceTrackOut) &&
                    hasValidPrice(binding.etPriceExclusive);
        }

        binding.btnUploadBeat.setEnabled(hasAudio && hasUser && hasValidPrices);

        if (!hasAudio) {
            binding.btnUploadBeat.setText(getString(R.string.select_audio_first));
        } else if (!hasUser) {
            binding.btnUploadBeat.setText(getString(R.string.loading_user));
        } else if (!hasValidPrices && !binding.switchFree.isChecked()) {
            binding.btnUploadBeat.setText(getString(R.string.enter_all_prices));
        } else if (isUpdatingBeat) {
            binding.btnUploadBeat.setText(getString(R.string.update_beat));
        } else {
            binding.btnUploadBeat.setText(getString(R.string.upload_beat));
        }
    }

    private boolean hasValidPrice(EditText editText) {
        String text = editText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return false;
        try {
            double price = Double.parseDouble(text);
            return price > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void selectAudioFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_audio)), PICK_AUDIO_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_opening_selector), Toast.LENGTH_SHORT).show();
        }
    }

    private void selectCoverFromGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_cover)), PICK_COVER_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_opening_selector), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == PICK_AUDIO_REQUEST) handleAudioSelection(uri);
            else if (requestCode == PICK_COVER_REQUEST) handleCoverSelection(uri);
        }
    }

    private void handleAudioSelection(Uri uri) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvAudioFile.setText(getString(R.string.loading_audio));

        new Thread(() -> {
            try {
                // ✅ Сначала проверяем размер файла через ContentResolver, чтобы избежать OutOfMemory
                long rawFileSize = 0;
                try (android.content.res.AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(uri, "r")) {
                    if (fd != null) rawFileSize = fd.getLength();
                } catch (Exception e) {
                    Log.e(TAG, "Error getting file size: " + e.getMessage());
                }

                // Base64 увеличивает размер примерно на 33%.
                // MAX_AUDIO_SIZE (10MB) - это лимит для финального Base64.
                // Проверяем сырой размер (примерно 7.5MB лимит для сырого файла)
                if (rawFileSize > (MAX_AUDIO_SIZE * 0.75)) {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.tvAudioFile.setText(getString(R.string.file_too_large_10mb));
                        binding.tvAudioFile.setTextColor(getColor(R.color.error));
                        audioBase64 = "";
                        updateUploadButtonState();
                    });
                    return;
                }

                String base64 = convertToBase64(uri);
                long fileSize = base64.length();

                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    if (fileSize > MAX_AUDIO_SIZE) {
                        binding.tvAudioFile.setText(getString(R.string.file_too_large_10mb));
                        binding.tvAudioFile.setTextColor(getColor(R.color.error));
                        audioBase64 = "";
                    } else if (fileSize < 1000) {
                        binding.tvAudioFile.setText(getString(R.string.invalid_audio_file));
                        binding.tvAudioFile.setTextColor(getColor(R.color.error));
                        audioBase64 = "";
                    } else {
                        audioBase64 = base64;
                        String fileName = getFileName(uri);
                        binding.tvAudioFile.setText(fileName + " (" + formatFileSize(fileSize) + ")");
                        binding.tvAudioFile.setTextColor(getColor(R.color.success));
                    }
                    updateUploadButtonState();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.tvAudioFile.setText(getString(R.string.error_loading_file));
                    binding.tvAudioFile.setTextColor(getColor(R.color.error));
                });
            }
        }).start();
    }

    private void handleCoverSelection(Uri uri) {
        binding.progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream == null) throw new IOException("Cannot open input stream");
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                if (bitmap != null) {
                    Bitmap compressedBitmap = compressBitmap(bitmap, 800, 800);
                    coverBase64 = convertBitmapToBase64(compressedBitmap);
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.ivCoverPreview.setVisibility(View.VISIBLE);
                        binding.ivCoverPreview.setImageBitmap(compressedBitmap);
                        Toast.makeText(this, getString(R.string.cover_added), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, getString(R.string.error_loading_image), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private Bitmap compressBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        if (width <= maxWidth && height <= maxHeight) return bitmap;
        float ratio = Math.min((float) maxWidth / width, (float) maxHeight / height);
        return Bitmap.createScaledBitmap(bitmap, (int) (width * ratio), (int) (height * ratio), true);
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Сжимаем сильнее для обложек, чтобы они влезли в лимит документа Firestore
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    private String convertToBase64(Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (inputStream == null) throw new IOException("Cannot open file");
            byte[] data = new byte[16384];
            int bytesRead;
            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            return Base64.encodeToString(buffer.toByteArray(), Base64.DEFAULT);
        }
    }

    private String getFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
                if (nameIndex != -1) return cursor.getString(nameIndex);
            }
        } catch (Exception e) {}
        return uri.getLastPathSegment() != null ? uri.getLastPathSegment() : "audio_file";
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        else return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    private void validateAndUploadBeat() {
        String title = binding.etTitle.getText().toString().trim();
        String description = binding.etDescription.getText().toString().trim();
        String genre = binding.etGenre.getText().toString().trim();
        String key = binding.etKey.getText().toString().trim();
        String bpmText = binding.etBpm.getText().toString().trim();

        if (TextUtils.isEmpty(title)) { binding.etTitle.setError(getString(R.string.title_required)); return; }
        if (TextUtils.isEmpty(audioBase64)) { Toast.makeText(this, getString(R.string.select_audio), Toast.LENGTH_SHORT).show(); return; }
        if (currentUser == null) { Toast.makeText(this, getString(R.string.user_not_loaded), Toast.LENGTH_SHORT).show(); return; }
        if (TextUtils.isEmpty(genre)) { binding.etGenre.setError(getString(R.string.select_genre)); return; }
        if (TextUtils.isEmpty(key)) { binding.etKey.setError(getString(R.string.select_key)); return; }

        int bpm = 120;
        if (!TextUtils.isEmpty(bpmText)) {
            try { bpm = Integer.parseInt(bpmText); } catch (NumberFormatException e) { binding.etBpm.setError(getString(R.string.invalid_bpm)); return; }
            if (bpm < 60 || bpm > 300) { binding.etBpm.setError(getString(R.string.bpm_range)); return; }
        }

        boolean isFree = binding.switchFree.isChecked();
        double priceMp3Wav = 0, priceTrackOut = 0, priceExclusive = 0;

        if (!isFree) {
            try {
                priceMp3Wav = Double.parseDouble(binding.etPriceMp3Wav.getText().toString().trim());
                priceTrackOut = Double.parseDouble(binding.etPriceTrackOut.getText().toString().trim());
                priceExclusive = Double.parseDouble(binding.etPriceExclusive.getText().toString().trim());

                if (priceMp3Wav <= 0 || priceTrackOut <= 0 || priceExclusive <= 0) {
                    Toast.makeText(this, getString(R.string.enter_all_prices), Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, getString(R.string.invalid_price), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        uploadBeat(title, description, genre, key, bpm, priceMp3Wav, priceTrackOut, priceExclusive, isFree);
    }

    private void uploadBeat(String title, String description, String genre, String key,
                            int bpm, double priceMp3Wav, double priceTrackOut,
                            double priceExclusive, boolean isFree) {
        binding.btnUploadBeat.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);

        Beat beat = new Beat(title, currentUser.getUsername(), bpm, key, genre,
                priceMp3Wav, isFree, description);

        // ✅ Устанавливаем цены лицензий
        beat.setPriceMp3Wav(priceMp3Wav);
        beat.setPriceTrackOut(priceTrackOut);
        beat.setPriceExclusive(priceExclusive);
        beat.setUserId(currentUser.getId());
        beat.setProducerId(currentUser.getId());
        beat.setStatus("active");

        if (isUpdatingBeat && editingBeat != null && !TextUtils.isEmpty(editingBeat.getId())) {
            beat.setId(editingBeat.getId());
        }

        if (!TextUtils.isEmpty(coverBase64)) beat.setCoverImage(coverBase64);

        Log.d(TAG, (isUpdatingBeat ? "Updating" : "Uploading") + " beat: " + title);
        Log.d(TAG, "Prices: MP3/WAV=$" + priceMp3Wav + ", TrackOut=$" + priceTrackOut + ", Exclusive=$" + priceExclusive);

        if (isUpdatingBeat) {
            binding.btnUploadBeat.setText(getString(R.string.updating));

            firestoreHelper.updateBeat(beat, new FirestoreHelper.ProgressCallback() {
                @Override
                public void onProgress(int currentChunk, int totalChunks, String type) {}

                @Override
                public void onComplete(Object result) {
                    runOnUiThread(() -> {
                        if (isActivityDestroyed) return;
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(CreateBeatActivity.this, getString(R.string.beat_updated), Toast.LENGTH_LONG).show();
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("BEAT_UPDATED", true);
                        resultIntent.putExtra("BEAT_ID", (String) result);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        if (isActivityDestroyed) return;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnUploadBeat.setEnabled(true);
                        binding.btnUploadBeat.setText(getString(R.string.update_beat));
                        Toast.makeText(CreateBeatActivity.this, getString(R.string.error_prefix) + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            binding.btnUploadBeat.setText(getString(R.string.uploading));

            firestoreHelper.saveBeatSmart(beat, audioBase64, coverBase64, new FirestoreHelper.ProgressCallback() {
                @Override
                public void onProgress(int currentChunk, int totalChunks, String type) {
                    runOnUiThread(() -> {
                        if (isActivityDestroyed) return;
                        int progress = (int) ((currentChunk / (float) totalChunks) * 100);
                        binding.btnUploadBeat.setText(getString(R.string.uploading_percent, progress));
                    });
                }

                @Override
                public void onComplete(Object result) {
                    runOnUiThread(() -> {
                        if (isActivityDestroyed) return;
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(CreateBeatActivity.this, getString(R.string.beat_uploaded), Toast.LENGTH_LONG).show();
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("BEAT_UPLOADED", true);
                        resultIntent.putExtra("BEAT_ID", (String) result);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        if (isActivityDestroyed) return;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnUploadBeat.setEnabled(true);
                        binding.btnUploadBeat.setText(getString(R.string.upload_beat));
                        Toast.makeText(CreateBeatActivity.this, getString(R.string.error_prefix) + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;
        binding = null;
    }
}