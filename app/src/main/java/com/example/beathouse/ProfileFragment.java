package com.example.beathouse;

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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.beathouse.databinding.FragmentProfileBinding;
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

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private FirestoreHelper firestoreHelper;
    private FollowManager followManager;
    private User currentUser;
    private ListenerRegistration userListener;
    private SwipeRefreshLayout swipeRefreshLayout;
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int MAX_IMAGE_SIZE = 500;
    private static final String TAG = "ProfileFragment";

    @Override
    public void onAttach(@NonNull Context context) {
        LocaleHelper.applyLanguage(context);
        super.onAttach(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
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
        swipeRefreshLayout.setOnRefreshListener(() -> {
            refreshProfileData();
        });
        // Устанавливаем цвета для индикатора обновления
        swipeRefreshLayout.setColorSchemeColors(
                getResources().getColor(R.color.primary),
                getResources().getColor(R.color.success),
                getResources().getColor(R.color.warning)
        );
    }

    private void refreshProfileData() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            loadUserProfileRealtime(firebaseUser.getUid());
        } else {
            swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(requireContext(), getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
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
            logoutUser();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeUserData() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            loadUserProfileRealtime(firebaseUser.getUid());
        } else {
            Toast.makeText(requireContext(), getString(R.string.user_not_authenticated), Toast.LENGTH_SHORT).show();
            redirectToLogin();
        }
    }

    private void loadUserProfileRealtime(String userId) {
        if (userListener != null) userListener.remove();

        userListener = FirebaseFirestore.getInstance().collection("users").document(userId)
                .addSnapshotListener((doc, err) -> {
                    if (err != null || doc == null || !doc.exists()) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                                    swipeRefreshLayout.setRefreshing(false);
                                }
                            });
                        }
                        return;
                    }
                    currentUser = User.fromMap(doc.getData());

                    if (isAdded() && binding != null) {
                        Long followers = doc.getLong("followers");
                        Long following = doc.getLong("following");
                        getActivity().runOnUiThread(() -> {
                            binding.tvFollowersCount.setText(String.valueOf(followers != null ? Math.max(0, followers) : 0));
                            binding.tvFollowingCount.setText(String.valueOf(following != null ? Math.max(0, following) : 0));
                            updateUI();

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

        binding.etUsername.setText(currentUser.getUsername());
        binding.tvEmail.setText(currentUser.getEmail());

        String roleText = currentUser.isSeller() ?
                currentUser.getUsername() + " (" + getString(R.string.seller) + ")" :
                currentUser.getUsername() + " (" + getString(R.string.buyer) + ")";
        binding.tvUsername.setText(roleText);

        if (currentUser.getBio() != null && !currentUser.getBio().isEmpty()) {
            binding.etBio.setText(currentUser.getBio());
            binding.tvBioDisplay.setText(currentUser.getBio());
            binding.tvBioDisplay.setVisibility(View.VISIBLE);
        } else {
            binding.etBio.setText("");
            binding.tvBioDisplay.setVisibility(View.GONE);
        }

        binding.etInstagram.setText(currentUser.getSocialInstagram() != null ? currentUser.getSocialInstagram() : "");
        binding.etTelegram.setText(currentUser.getSocialTelegram() != null ? currentUser.getSocialTelegram() : "");
        binding.etVk.setText(currentUser.getSocialVk() != null ? currentUser.getSocialVk() : "");

        if (currentUser.isSeller()) {
            binding.tvBeatsCount.setText(String.valueOf(currentUser.getStats().getBeatsSold()));
            binding.tvTotalSpent.setText(currentUser.getStats().getFormattedTotalEarned());
            updateLabel(R.id.tvBeatsLabel, getString(R.string.beats_sold));
            updateLabel(R.id.tvTotalLabel, getString(R.string.earned));

            showBuyerButtons(false);
        } else {
            binding.tvBeatsCount.setText(String.valueOf(currentUser.getStats().getBeatsPurchased()));
            binding.tvTotalSpent.setText(currentUser.getStats().getFormattedTotalSpent());
            updateLabel(R.id.tvBeatsLabel, getString(R.string.bought));
            updateLabel(R.id.tvTotalLabel, getString(R.string.spent));

            showBuyerButtons(true);
        }

        updateSocialIcons();

        if (currentUser.getProfileImage() != null && !currentUser.getProfileImage().isEmpty()) {
            loadBase64Image(currentUser.getProfileImage());
        } else {
            binding.profileImage.setImageResource(R.drawable.ic_profile_placeholder);
        }

        setupSwitchRoleButton();
        setupFollowersFollowingClickListeners();
    }

    private void updateLabel(int labelId, String text) {
        try {
            View label = binding.getRoot().findViewById(labelId);
            if (label instanceof android.widget.TextView) {
                ((android.widget.TextView) label).setText(text);
            }
        } catch (Exception e) {}
    }

    private void updateSocialIcons() {
        if (currentUser == null) return;
        try {
            LinearLayout socialLayout = binding.getRoot().findViewById(R.id.socialLinksLayout);
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

    private void showBuyerButtons(boolean show) {
        // Эти кнопки закомментированы, так как их нет в layout
    }

    private void setupSwitchRoleButton() {
        if (binding.btnSwitchRole == null) return;
        binding.btnSwitchRole.setVisibility(View.VISIBLE);
        if (currentUser.isSeller()) {
            binding.btnSwitchRole.setText(getString(R.string.switch_to_buyer));
            binding.btnSwitchRole.setOnClickListener(v -> showSwitchRoleDialog("buyer"));
        } else {
            binding.btnSwitchRole.setText(getString(R.string.switch_to_seller));
            binding.btnSwitchRole.setOnClickListener(v -> showSwitchRoleDialog("seller"));
        }
    }

    private void setupFollowersFollowingClickListeners() {
        if (binding.tvFollowersCount != null && binding.tvFollowersCount.getParent() != null) {
            View followersContainer = (View) binding.tvFollowersCount.getParent();
            followersContainer.setOnClickListener(v -> {
                if (currentUser != null) {
                    Intent intent = new Intent(getActivity(), FollowersFollowingActivity.class);
                    intent.putExtra("user_id", currentUser.getId());
                    intent.putExtra("user_name", currentUser.getUsername());
                    startActivity(intent);
                    if (getActivity() != null) {
                    }
                }
            });
        }

        if (binding.tvFollowingCount != null && binding.tvFollowingCount.getParent() != null) {
            View followingContainer = (View) binding.tvFollowingCount.getParent();
            followingContainer.setOnClickListener(v -> {
                if (currentUser != null) {
                    Intent intent = new Intent(getActivity(), FollowersFollowingActivity.class);
                    intent.putExtra("user_id", currentUser.getId());
                    intent.putExtra("user_name", currentUser.getUsername());
                    startActivity(intent);
                    if (getActivity() != null) {
                    }
                }
            });
        }
    }

    private void showSwitchRoleDialog(String newRole) {
        String roleName = newRole.equals("seller") ? getString(R.string.seller) : getString(R.string.buyer);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.switch_to) + " " + roleName + "?")
                .setMessage(getString(R.string.your_data_will_be_preserved))
                .setPositiveButton(getString(R.string.switch_role), (dialog, which) -> performRoleSwitch(newRole))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void performRoleSwitch(String newRole) {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        ProgressDialog loadingDialog = new ProgressDialog(requireContext());
        loadingDialog.setMessage(getString(R.string.switching_role));
        loadingDialog.setCancelable(false);
        loadingDialog.show();

        firestoreHelper.switchUserRole(userId, newRole, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                loadingDialog.dismiss();

                if (getActivity() != null) {
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
    }

    private void setupClickListeners() {
        binding.fabEditPhoto.setOnClickListener(v -> openImagePicker());
        if (binding.btnSaveProfile != null) binding.btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        if (currentUser == null || !isAdded()) return;

        String username = binding.etUsername.getText().toString().trim();
        String bio = binding.etBio.getText().toString().trim();
        String instagram = binding.etInstagram.getText().toString().trim();
        String telegram = binding.etTelegram.getText().toString().trim();
        String vk = binding.etVk.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            binding.usernameLayout.setError(getString(R.string.username_required));
            return;
        }

        String userId = currentUser.getId();
        if (userId == null || userId.isEmpty()) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            currentUser.setId(userId);
        }

        currentUser.setUsername(username);
        currentUser.setBio(bio);
        currentUser.setSocialInstagram(instagram);
        currentUser.setSocialTelegram(telegram);
        currentUser.setSocialVk(vk);

        firestoreHelper.updateUser(currentUser, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), getString(R.string.profile_saved), Toast.LENGTH_SHORT).show();
                    binding.usernameLayout.setError(null);
                }
            }
            @Override
            public void onError(String error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), getString(R.string.error_saving_profile) + error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadBase64Image(String base64Image) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
            binding.profileImage.setImageBitmap(bitmap != null ? bitmap :
                    BitmapFactory.decodeResource(getResources(), R.drawable.ic_profile_placeholder));
        } catch (Exception e) {
            binding.profileImage.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }

    private void logoutUser() {
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
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        if (getActivity() != null) getActivity().finish();
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
            if (selectedImage != null) processSelectedImage(selectedImage);
        }
    }

    private void processSelectedImage(Uri imageUri) {
        new Thread(() -> {
            try (InputStream is = requireContext().getContentResolver().openInputStream(imageUri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    Bitmap compressed = compressBitmap(bitmap, 300, 300);
                    String base64 = bitmapToBase64(compressed);
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (currentUser != null && base64 != null && isAdded()) {
                                currentUser.setProfileImage(base64);
                                firestoreHelper.updateUser(currentUser, new FirestoreHelper.FirestoreCallback() {
                                    @Override
                                    public void onSuccess(Object r) {
                                        if (isAdded()) {
                                            binding.profileImage.setImageBitmap(compressed);
                                            Toast.makeText(requireContext(), getString(R.string.photo_saved), Toast.LENGTH_SHORT).show();
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

    private Bitmap compressBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        float ratio = (float) w / h;
        if (w > maxWidth || h > maxHeight) {
            if (ratio > 1) {
                w = maxWidth;
                h = (int) (w / ratio);
            } else {
                h = maxHeight;
                w = (int) (h * ratio);
            }
        }
        return Bitmap.createScaledBitmap(bitmap, w, h, true);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int quality = 80;
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] bytes = baos.toByteArray();
        while (bytes.length > MAX_IMAGE_SIZE * 1024 && quality > 20) {
            baos.reset();
            quality -= 10;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            bytes = baos.toByteArray();
        }
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            LocaleHelper.applyLanguage(getContext());
        }
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            loadUserProfileRealtime(FirebaseAuth.getInstance().getCurrentUser().getUid());
        }
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