package com.example.beathouse.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.R;
import com.example.beathouse.models.Beat;
import com.example.beathouse.utils.FirestoreHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SellerBeatsAdapter extends RecyclerView.Adapter<SellerBeatsAdapter.ViewHolder> {

    private List<Beat> beats;
    private Context context;
    private FirestoreHelper firestoreHelper;
    private FirebaseFirestore db;
    private OnBeatDeletedListener deleteListener;
    private OnEditBeatListener editBeatListener;

    private static final int PICK_IMAGE_REQUEST = 1002;
    private String pendingCoverBase64 = null;
    private String pendingBeatId = null;
    private int pendingPosition = -1;
    private AlertDialog currentEditDialog = null;
    private ImageView currentCoverImageView = null;
    private Beat currentEditingBeat = null;

    public interface OnEditBeatListener {
        void onEditBeat(Beat beat);
    }

    public interface OnBeatDeletedListener {
        void onBeatDeleted();
    }

    public SellerBeatsAdapter(List<Beat> beats, Context context, OnBeatDeletedListener listener) {
        this.beats = beats;
        this.context = context;
        this.deleteListener = listener;
        this.firestoreHelper = new FirestoreHelper();
        this.db = FirebaseFirestore.getInstance();
    }

    public void setOnEditBeatListener(OnEditBeatListener listener) {
        this.editBeatListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_seller_beat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        onBindViewHolder(holder, position, new ArrayList<>());
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        Beat beat = beats.get(position);

        if (payloads.contains("cover")) {
            Log.d("SellerBeatsAdapter", "Updating only cover for position: " + position);
            loadCoverImage(holder.ivCover, beat);
        } else {
            holder.tvTitle.setText(beat.getTitle());
            holder.tvBpm.setText(beat.getBpm() + " BPM");
            holder.tvKey.setText(beat.getKey());
            holder.tvGenre.setText(beat.getGenre());
            holder.tvPrice.setText(beat.getFormattedPrice());

            loadCoverImage(holder.ivCover, beat);

            holder.btnEdit.setOnClickListener(v -> {
                if (editBeatListener != null) {
                    editBeatListener.onEditBeat(beat);
                } else {
                    showEditDialog(beat, position);
                }
            });

            holder.btnDelete.setOnClickListener(v -> showDeleteConfirmDialog(beat, position));
        }
    }

    private void loadCoverImage(ImageView imageView, Beat beat) {
        Log.d("SellerBeatsAdapter", "loadCoverImage for: " + beat.getTitle() + " (ID: " + beat.getId() + ")");

        imageView.setImageResource(R.drawable.ic_music_note);

        FirestoreHelper helper = new FirestoreHelper();
        helper.loadBeatCover(beat.getId(), new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                String coverData = (String) result;
                if (coverData != null && !coverData.isEmpty() && coverData.length() > 100) {
                    beat.setCoverImage(coverData);
                    try {
                        byte[] decodedBytes = Base64.decode(coverData, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap);
                        }
                    } catch (Exception e) {
                        Log.e("SellerBeatsAdapter", "Error decoding cover: " + e.getMessage());
                    }
                }
            }
            @Override
            public void onError(String error) {
                Log.e("SellerBeatsAdapter", "Error loading cover: " + error);
            }
        });
    }

    private void showEditDialog(Beat beat, int position) {
        pendingCoverBase64 = null;
        pendingBeatId = null;
        pendingPosition = -1;
        currentEditingBeat = beat;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_beat, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        currentEditDialog = builder.create();
        currentCoverImageView = dialogView.findViewById(R.id.ivCurrentCover);

        TextInputEditText etTitle = dialogView.findViewById(R.id.etEditTitle);
        AutoCompleteTextView etGenre = dialogView.findViewById(R.id.etEditGenre);
        AutoCompleteTextView etKey = dialogView.findViewById(R.id.etEditKey);
        TextInputEditText etBpm = dialogView.findViewById(R.id.etEditBpm);

        TextInputEditText etPriceMp3Wav = dialogView.findViewById(R.id.etEditPriceMp3Wav);
        TextInputEditText etPriceTrackOut = dialogView.findViewById(R.id.etEditPriceTrackOut);
        TextInputEditText etPriceExclusive = dialogView.findViewById(R.id.etEditPriceExclusive);
        View licensePricesLayout = dialogView.findViewById(R.id.licensePricesLayout);

        SwitchMaterial switchFree = dialogView.findViewById(R.id.switchEditFree);
        TextInputEditText etDescription = dialogView.findViewById(R.id.etEditDescription);
        View layoutDescription = dialogView.findViewById(R.id.layoutDescription);
        TextView tvLicensePricesHeader = dialogView.findViewById(R.id.tvLicensePricesHeader);
        MaterialButton btnUpdateCover = dialogView.findViewById(R.id.btnUpdateCover);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSaveBeat);
        ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);

        etTitle.setText(beat.getTitle());
        loadCoverImageToDialog(beat);

        String[] genres = {
                "Hip-Hop", "Trap", "R&B", "Drill", "Pop", "Electronic", "Lo-Fi",
                "Rock", "Jazz", "Classical", "Country", "Blues", "Reggae", "Funk",
                "Soul", "Disco", "Techno", "House", "Ambient", "Dubstep", "Grime"
        };
        ArrayAdapter<String> genreAdapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, genres);
        etGenre.setAdapter(genreAdapter);
        etGenre.setText(beat.getGenre(), false);

        String[] keys = {
                "Cmin", "Cmaj", "C#min", "C#maj", "Dmin", "Dmaj", "D#min", "D#maj",
                "Emin", "Emaj", "Fmin", "Fmaj", "F#min", "F#maj", "Gmin", "Gmaj",
                "G#min", "G#maj", "Amin", "Amaj", "A#min", "A#maj", "Bmin", "Bmaj"
        };
        ArrayAdapter<String> keyAdapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, keys);
        etKey.setAdapter(keyAdapter);
        etKey.setText(beat.getKey(), false);

        etBpm.setText(String.valueOf(beat.getBpm()));

        // ✅ Заполняем цены лицензий
        if (beat.getPriceMp3Wav() > 0) {
            etPriceMp3Wav.setText(String.valueOf((int) beat.getPriceMp3Wav()));
        }
        if (beat.getPriceTrackOut() > 0) {
            etPriceTrackOut.setText(String.valueOf((int) beat.getPriceTrackOut()));
        }
        if (beat.getPriceExclusive() > 0) {
            etPriceExclusive.setText(String.valueOf((int) beat.getPriceExclusive()));
        }

        switchFree.setChecked(beat.isFree());
        etDescription.setText(beat.getDescription() != null ? beat.getDescription() : "");

        // ✅ Показываем/скрываем секцию с ценами лицензий
        if (switchFree.isChecked()) {
            licensePricesLayout.setVisibility(View.VISIBLE); // Keep it visible but hide prices
            tvLicensePricesHeader.setVisibility(View.GONE);
            dialogView.findViewById(R.id.layoutPriceMp3Wav).setVisibility(View.GONE);
            dialogView.findViewById(R.id.layoutPriceTrackOut).setVisibility(View.GONE);
            dialogView.findViewById(R.id.layoutPriceExclusive).setVisibility(View.GONE);
        } else {
            licensePricesLayout.setVisibility(View.VISIBLE);
            tvLicensePricesHeader.setVisibility(View.VISIBLE);
            dialogView.findViewById(R.id.layoutPriceMp3Wav).setVisibility(View.VISIBLE);
            dialogView.findViewById(R.id.layoutPriceTrackOut).setVisibility(View.VISIBLE);
            dialogView.findViewById(R.id.layoutPriceExclusive).setVisibility(View.VISIBLE);
        }

        switchFree.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                tvLicensePricesHeader.setVisibility(View.GONE);
                dialogView.findViewById(R.id.layoutPriceMp3Wav).setVisibility(View.GONE);
                dialogView.findViewById(R.id.layoutPriceTrackOut).setVisibility(View.GONE);
                dialogView.findViewById(R.id.layoutPriceExclusive).setVisibility(View.GONE);
            } else {
                tvLicensePricesHeader.setVisibility(View.VISIBLE);
                dialogView.findViewById(R.id.layoutPriceMp3Wav).setVisibility(View.VISIBLE);
                dialogView.findViewById(R.id.layoutPriceTrackOut).setVisibility(View.VISIBLE);
                dialogView.findViewById(R.id.layoutPriceExclusive).setVisibility(View.VISIBLE);
            }
        });

        btnUpdateCover.setOnClickListener(v -> {
            pendingBeatId = beat.getId();
            pendingPosition = position;
            openImagePicker();
        });

        btnSave.setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            btnSave.setEnabled(false);

            Map<String, Object> updates = new HashMap<>();
            updates.put("title", etTitle.getText().toString().trim());
            updates.put("genre", etGenre.getText().toString().trim());
            updates.put("key", etKey.getText().toString().trim());

            try {
                int bpm = Integer.parseInt(etBpm.getText().toString());
                updates.put("bpm", bpm);
            } catch (NumberFormatException e) {
                Toast.makeText(context, "Invalid BPM", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                btnSave.setEnabled(true);
                return;
            }

            updates.put("isFree", switchFree.isChecked());
            updates.put("description", etDescription.getText().toString().trim());

            if (!switchFree.isChecked()) {
                try {
                    // ✅ Сохраняем цены трех лицензий
                    double priceMp3Wav = Double.parseDouble(etPriceMp3Wav.getText().toString().trim());
                    double priceTrackOut = Double.parseDouble(etPriceTrackOut.getText().toString().trim());
                    double priceExclusive = Double.parseDouble(etPriceExclusive.getText().toString().trim());

                    if (priceMp3Wav <= 0 || priceTrackOut <= 0 || priceExclusive <= 0) {
                        Toast.makeText(context, "All license prices must be greater than 0", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                        btnSave.setEnabled(true);
                        return;
                    }

                    updates.put("priceMp3Wav", priceMp3Wav);
                    updates.put("priceTrackOut", priceTrackOut);
                    updates.put("priceExclusive", priceExclusive);
                    updates.put("price", priceMp3Wav); // Для обратной совместимости

                } catch (NumberFormatException e) {
                    Toast.makeText(context, "Invalid price format", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    return;
                }
            } else {
                updates.put("price", 0);
                updates.put("priceMp3Wav", 0);
                updates.put("priceTrackOut", 0);
                updates.put("priceExclusive", 0);
            }

            final String newCoverBase64 = pendingCoverBase64;
            if (newCoverBase64 != null && newCoverBase64.length() > 100) {
                updates.put("coverImage", newCoverBase64);
            }

            db.collection("beats").document(beat.getId())
                    .update(updates)
                    .addOnSuccessListener(a -> {
                        // Обновляем локальный объект
                        if (updates.containsKey("title")) beat.setTitle((String) updates.get("title"));
                        if (updates.containsKey("genre")) beat.setGenre((String) updates.get("genre"));
                        if (updates.containsKey("key")) beat.setKey((String) updates.get("key"));
                        if (updates.containsKey("bpm")) beat.setBpm((Integer) updates.get("bpm"));
                        if (updates.containsKey("isFree")) beat.setFree((Boolean) updates.get("isFree"));
                        if (updates.containsKey("description")) beat.setDescription((String) updates.get("description"));
                        if (updates.containsKey("coverImage")) beat.setCoverImage((String) updates.get("coverImage"));

                        if (updates.containsKey("priceMp3Wav")) beat.setPriceMp3Wav((Double) updates.get("priceMp3Wav"));
                        if (updates.containsKey("priceTrackOut")) beat.setPriceTrackOut((Double) updates.get("priceTrackOut"));
                        if (updates.containsKey("priceExclusive")) beat.setPriceExclusive((Double) updates.get("priceExclusive"));

                        if (position >= 0 && position < beats.size()) {
                            if (updates.containsKey("coverImage")) {
                                beats.get(position).setCoverImage((String) updates.get("coverImage"));
                            }
                            notifyItemChanged(position);
                        }

                        pendingCoverBase64 = null;
                        progressBar.setVisibility(View.GONE);
                        if (currentEditDialog != null) {
                            currentEditDialog.dismiss();
                            currentEditDialog = null;
                        }
                        currentCoverImageView = null;
                        currentEditingBeat = null;
                        Toast.makeText(context, "Beat updated!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e("SellerBeatsAdapter", "Update failed: " + e.getMessage());
                        progressBar.setVisibility(View.GONE);
                        btnSave.setEnabled(true);
                        Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        progressBar.setVisibility(View.GONE);
        currentEditDialog.show();
    }

    private void loadCoverImageToDialog(Beat beat) {
        if (beat.hasCover() && beat.getCoverImage() != null && !beat.getCoverImage().isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(beat.getCoverImage(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                if (bitmap != null) {
                    currentCoverImageView.setImageBitmap(bitmap);
                    return;
                }
            } catch (Exception e) {
                Log.e("SellerBeatsAdapter", "Error loading cover to dialog: " + e.getMessage());
            }
        }
        currentCoverImageView.setImageResource(R.drawable.ic_music_note);
    }

    private void openImagePicker() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        ((android.app.Activity) context).startActivityForResult(
                android.content.Intent.createChooser(intent, "Select Cover"), PICK_IMAGE_REQUEST);
    }

    public void handleActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == android.app.Activity.RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                Toast.makeText(context, "Loading cover...", Toast.LENGTH_SHORT).show();
                compressAndConvertCover(imageUri);
            }
        }
    }

    private void compressAndConvertCover(Uri imageUri) {
        new Thread(() -> {
            try {
                InputStream is = context.getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();

                if (bitmap != null) {
                    Bitmap compressed = Bitmap.createScaledBitmap(bitmap, 300, 300, true);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    compressed.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    pendingCoverBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

                    ((android.app.Activity) context).runOnUiThread(() -> {
                        if (currentCoverImageView != null) {
                            currentCoverImageView.setImageBitmap(compressed);
                            Toast.makeText(context, "New cover selected! Click Save to apply.", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("SellerBeatsAdapter", "Error: " + e.getMessage());
            }
        }).start();
    }

    private void showDeleteConfirmDialog(Beat beat, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Beat")
                .setMessage("Are you sure you want to delete \"" + beat.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteBeat(beat, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteBeat(Beat beat, int position) {
        db.collection("beats").document(beat.getId())
                .update("status", "deleted")
                .addOnSuccessListener(a -> {
                    beats.remove(position);
                    notifyItemRemoved(position);
                    Toast.makeText(context, "Beat deleted", Toast.LENGTH_SHORT).show();
                    if (deleteListener != null) deleteListener.onBeatDeleted();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public int getItemCount() {
        return beats != null ? beats.size() : 0;
    }

    public void updateBeatsList(List<Beat> newBeats) {
        this.beats = newBeats;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvTitle, tvBpm, tvKey, tvGenre, tvPrice;
        ImageButton btnEdit, btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivBeatCover);
            tvTitle = itemView.findViewById(R.id.tvBeatTitle);
            tvBpm = itemView.findViewById(R.id.tvBeatBpm);
            tvKey = itemView.findViewById(R.id.tvBeatKey);
            tvGenre = itemView.findViewById(R.id.tvBeatGenre);
            tvPrice = itemView.findViewById(R.id.tvBeatPrice);
            btnEdit = itemView.findViewById(R.id.btnEditBeat);
            btnDelete = itemView.findViewById(R.id.btnDeleteBeat);
        }
    }
}