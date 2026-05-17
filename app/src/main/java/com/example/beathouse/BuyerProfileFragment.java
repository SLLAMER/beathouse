package com.example.beathouse;  // ✅ Правильный пакет

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.beathouse.databinding.FragmentBuyerProfileBinding;
import com.example.beathouse.models.User;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.utils.FollowManager;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.app.ProgressDialog;
import android.os.Process;

public class BuyerProfileFragment extends Fragment {

    private FragmentBuyerProfileBinding binding;
    private FirestoreHelper firestoreHelper;
    private FollowManager followManager;
    private User currentUser;
    private ListenerRegistration userListener;
    private SwipeRefreshLayout swipeRefreshLayout;
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final String TAG = "BuyerProfileFragment";

    @Override
    public void onAttach(@NonNull Context context) {
        LocaleHelper.applyLanguage(context);
        super.onAttach(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBuyerProfileBinding.inflate(inflater, container, false);
        setHasOptionsMenu(true);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        firestoreHelper = new FirestoreHelper();
        followManager = new FollowManager();

        setupSwipeRefresh();
        initializeUserData();
        setupClickListeners();
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout = binding.swipeRefreshLayout;
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                refreshProfileData();
            });
            swipeRefreshLayout.setColorSchemeColors(
                    getResources().getColor(R.color.primary),
                    getResources().getColor(R.color.success),
                    getResources().getColor(R.color.warning)
            );
        }
    }

    private void refreshProfileData() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            loadUserProfileRealtime(firebaseUser.getUid());
        } else {
            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
            redirectToLogin();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull android.view.MenuInflater inflater) {
        inflater.inflate(R.menu.menu_profile, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeUserData() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            loadUserProfileRealtime(firebaseUser.getUid());
        } else {
            redirectToLogin();
        }
    }

    private void loadUserProfileRealtime(String userId) {
        if (userListener != null) userListener.remove();

        userListener = FirebaseFirestore.getInstance().collection("users").document(userId)
                .addSnapshotListener((doc, err) -> {
                    if (err != null || doc == null || !doc.exists()) {
                        if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                        return;
                    }
                    currentUser = User.fromMap(doc.getData());

                    if (isAdded() && binding != null && getActivity() != null) {
                        Long followers = doc.getLong("followers");
                        Long following = doc.getLong("following");
                        getActivity().runOnUiThread(() -> {
                            try {
                                TextView tvFollowers = binding.getRoot().findViewById(R.id.tvFollowers);
                                if (tvFollowers != null)
                                    tvFollowers.setText(String.valueOf(followers != null ? Math.max(0, followers) : 0));
                                TextView tvFollowing = binding.getRoot().findViewById(R.id.tvFollowing);
                                if (tvFollowing != null)
                                    tvFollowing.setText(String.valueOf(following != null ? Math.max(0, following) : 0));
                            } catch (Exception e) {}
                            updateUI();
                            setupFollowersFollowingClickListeners();

                            // Останавливаем анимацию обновления
                            if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                        });
                    }
                });
    }

    private void updateUI() {
        if (currentUser == null || getActivity() == null || !isAdded()) return;

        binding.tvUsername.setText(currentUser.getUsername());
        binding.tvEmail.setText(currentUser.getEmail());

        String bio = currentUser.getBio();
        if (bio != null && !bio.isEmpty()) {
            binding.tvBioDisplay.setText(bio);
            binding.tvBioDisplay.setVisibility(View.VISIBLE);
        } else {
            binding.tvBioDisplay.setVisibility(View.GONE);
        }

        binding.tvTotalSpent.setText(currentUser.getStats().getFormattedTotalSpent());
        binding.tvBeatsPurchased.setText(String.valueOf(currentUser.getStats().getBeatsPurchased()));

        try {
            com.google.android.material.textfield.TextInputEditText etUsername = binding.getRoot().findViewById(R.id.etUsername);
            if (etUsername != null) etUsername.setText(currentUser.getUsername());
            com.google.android.material.textfield.TextInputEditText etBio = binding.getRoot().findViewById(R.id.etBio);
            if (etBio != null) etBio.setText(currentUser.getBio() != null ? currentUser.getBio() : "");
            com.google.android.material.textfield.TextInputEditText etInstagram = binding.getRoot().findViewById(R.id.etInstagram);
            if (etInstagram != null) etInstagram.setText(currentUser.getSocialInstagram() != null ? currentUser.getSocialInstagram() : "");
            com.google.android.material.textfield.TextInputEditText etTelegram = binding.getRoot().findViewById(R.id.etTelegram);
            if (etTelegram != null) etTelegram.setText(currentUser.getSocialTelegram() != null ? currentUser.getSocialTelegram() : "");
            com.google.android.material.textfield.TextInputEditText etVk = binding.getRoot().findViewById(R.id.etVk);
            if (etVk != null) etVk.setText(currentUser.getSocialVk() != null ? currentUser.getSocialVk() : "");
        } catch (Exception e) {}

        updateSocialIcons();

        if (currentUser.getProfileImage() != null && !currentUser.getProfileImage().isEmpty()) {
            loadAvatar(currentUser.getProfileImage());
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }

        setupSwitchRoleButton();
    }

    private void setupFollowersFollowingClickListeners() {
        TextView tvFollowers = binding.getRoot().findViewById(R.id.tvFollowers);
        if (tvFollowers != null && tvFollowers.getParent() != null) {
            View followersContainer = (View) tvFollowers.getParent();
            followersContainer.setOnClickListener(v -> {
                if (currentUser != null) {
                    Intent intent = new Intent(getActivity(), FollowersFollowingActivity.class);
                    intent.putExtra("user_id", currentUser.getId());
                    intent.putExtra("user_name", currentUser.getUsername());
                    startActivity(intent);
                }
            });
        }

        TextView tvFollowing = binding.getRoot().findViewById(R.id.tvFollowing);
        if (tvFollowing != null && tvFollowing.getParent() != null) {
            View followingContainer = (View) tvFollowing.getParent();
            followingContainer.setOnClickListener(v -> {
                if (currentUser != null) {
                    Intent intent = new Intent(getActivity(), FollowersFollowingActivity.class);
                    intent.putExtra("user_id", currentUser.getId());
                    intent.putExtra("user_name", currentUser.getUsername());
                    startActivity(intent);
                }
            });
        }
    }

    private void updateSocialIcons() {
        if (currentUser == null) return;
        try {
            LinearLayout socialLayout = binding.getRoot().findViewById(R.id.socialIconsLayout);
            if (socialLayout == null) return;
            boolean hasAny = false;

            String ig = currentUser.getSocialInstagram();
            ImageView ivIg = binding.getRoot().findViewById(R.id.ivInstagram);
            if (ig != null && !ig.isEmpty() && ivIg != null) {
                ivIg.setVisibility(View.VISIBLE);
                ivIg.setOnClickListener(v -> openSocialUrl(ig));
                hasAny = true;
            } else if (ivIg != null) {
                ivIg.setVisibility(View.GONE);
            }

            String tg = currentUser.getSocialTelegram();
            ImageView ivTg = binding.getRoot().findViewById(R.id.ivTelegram);
            if (tg != null && !tg.isEmpty() && ivTg != null) {
                ivTg.setVisibility(View.VISIBLE);
                ivTg.setOnClickListener(v -> openSocialUrl(tg));
                hasAny = true;
            } else if (ivTg != null) {
                ivTg.setVisibility(View.GONE);
            }

            String vk = currentUser.getSocialVk();
            ImageView ivVk = binding.getRoot().findViewById(R.id.ivVk);
            if (vk != null && !vk.isEmpty() && ivVk != null) {
                ivVk.setVisibility(View.VISIBLE);
                ivVk.setOnClickListener(v -> openSocialUrl(vk));
                hasAny = true;
            } else if (ivVk != null) {
                ivVk.setVisibility(View.GONE);
            }

            socialLayout.setVisibility(hasAny ? View.VISIBLE : View.GONE);
        } catch (Exception e) {}
    }

    private void openSocialUrl(String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.cannot_open_link), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSwitchRoleButton() {
        if (binding.btnSwitchRole != null) {
            binding.btnSwitchRole.setVisibility(View.VISIBLE);
            binding.btnSwitchRole.setText(getString(R.string.become_seller));
            binding.btnSwitchRole.setOnClickListener(v -> showSwitchToSellerDialog());
        }
    }

    private void showSwitchToSellerDialog() {
        if (!isAdded()) return;

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.become_seller))
                .setMessage(getString(R.string.become_seller_message))
                .setPositiveButton(getString(R.string.switch_to_seller), (dialog, which) -> {
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    ProgressDialog loadingDialog = new ProgressDialog(requireContext());
                    loadingDialog.setMessage(getString(R.string.switching_to_seller));
                    loadingDialog.setCancelable(false);
                    loadingDialog.show();

                    firestoreHelper.switchUserRole(userId, "seller", new FirestoreHelper.FirestoreCallback() {
                        @Override
                        public void onSuccess(Object result) {
                            loadingDialog.dismiss();
                            if (isAdded()) {
                                Toast.makeText(requireContext(), getString(R.string.you_are_now_seller), Toast.LENGTH_LONG).show();

                                FirebaseFirestore.getInstance().clearPersistence();
                                FirebaseAuth.getInstance().signOut();

                                Intent intent = new Intent(getActivity(), LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);

                                android.os.Process.killProcess(android.os.Process.myPid());
                                System.exit(0);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            loadingDialog.dismiss();
                            if (isAdded()) {
                                Toast.makeText(requireContext(), getString(R.string.error_prefix) + error, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void loadAvatar(String base64Image) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            binding.ivAvatar.setImageBitmap(bitmap != null ? bitmap :
                    BitmapFactory.decodeResource(getResources(), R.drawable.ic_profile_placeholder));
        } catch (Exception e) {
            binding.ivAvatar.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }

    private void setupClickListeners() {
        binding.fabEditAvatar.setOnClickListener(v -> openImagePicker());
        binding.ivAvatar.setOnClickListener(v -> openImagePicker());

        try {
            com.google.android.material.button.MaterialButton btnSave = binding.getRoot().findViewById(R.id.btnSaveProfile);
            if (btnSave != null) btnSave.setOnClickListener(v -> saveProfile());
        } catch (Exception e) {}
    }

    private void saveProfile() {
        if (currentUser == null || !isAdded()) return;
        try {
            com.google.android.material.textfield.TextInputEditText etUsername = binding.getRoot().findViewById(R.id.etUsername);
            com.google.android.material.textfield.TextInputEditText etBio = binding.getRoot().findViewById(R.id.etBio);
            com.google.android.material.textfield.TextInputEditText etInstagram = binding.getRoot().findViewById(R.id.etInstagram);
            com.google.android.material.textfield.TextInputEditText etTelegram = binding.getRoot().findViewById(R.id.etTelegram);
            com.google.android.material.textfield.TextInputEditText etVk = binding.getRoot().findViewById(R.id.etVk);

            String username = etUsername != null ? etUsername.getText().toString().trim() : "";
            if (TextUtils.isEmpty(username)) {
                Toast.makeText(requireContext(), getString(R.string.username_required), Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = currentUser.getId();
            if (userId == null || userId.isEmpty()) {
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                currentUser.setId(userId);
            }

            currentUser.setUsername(username);
            currentUser.setBio(etBio != null ? etBio.getText().toString().trim() : "");
            currentUser.setSocialInstagram(etInstagram != null ? etInstagram.getText().toString().trim() : "");
            currentUser.setSocialTelegram(etTelegram != null ? etTelegram.getText().toString().trim() : "");
            currentUser.setSocialVk(etVk != null ? etVk.getText().toString().trim() : "");

            firestoreHelper.updateUser(currentUser, new FirestoreHelper.FirestoreCallback() {
                @Override
                public void onSuccess(Object result) {
                    if (isAdded()) Toast.makeText(requireContext(), getString(R.string.profile_saved), Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onError(String error) {
                    if (isAdded()) Toast.makeText(requireContext(), getString(R.string.error_saving_profile) + error, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error saving profile: " + e.getMessage());
        }
    }

    private void openImagePicker() {
        startActivityForResult(Intent.createChooser(
                new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), getString(R.string.select_picture)), PICK_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == getActivity().RESULT_OK && requestCode == PICK_IMAGE_REQUEST && data != null) {
            Uri selectedImage = data.getData();
            if (selectedImage != null) uploadAvatar(selectedImage);
        }
    }

    private void uploadAvatar(Uri imageUri) {
        new Thread(() -> {
            try (InputStream is = requireContext().getContentResolver().openInputStream(imageUri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    Bitmap compressed = Bitmap.createScaledBitmap(bitmap, 300, 300, true);
                    String base64 = bitmapToBase64(compressed);
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (currentUser != null && base64 != null && isAdded()) {
                                currentUser.setProfileImage(base64);
                                firestoreHelper.updateUser(currentUser, new FirestoreHelper.FirestoreCallback() {
                                    @Override
                                    public void onSuccess(Object r) {
                                        if (isAdded()) {
                                            binding.ivAvatar.setImageBitmap(compressed);
                                            Toast.makeText(getContext(), getString(R.string.photo_saved), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                    @Override
                                    public void onError(String e) {}
                                });
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }).start();
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    private void logout() {
        if (!isAdded()) return;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.are_you_sure))
                .setPositiveButton(getString(R.string.logout), (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    redirectToLogin();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(getContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        if (getActivity() != null) getActivity().finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            LocaleHelper.applyLanguage(getContext());
        }
        if (FirebaseAuth.getInstance().getCurrentUser() != null && isAdded())
            loadUserProfileRealtime(FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
        binding = null;
    }
}