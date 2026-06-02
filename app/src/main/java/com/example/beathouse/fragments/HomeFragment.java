package com.example.beathouse.fragments;
import com.example.beathouse.R;
import com.example.beathouse.App;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beathouse.adapters.SellerBeatsAdapter;
import com.example.beathouse.databinding.FragmentHomeBinding;
import com.example.beathouse.models.Beat;
import com.example.beathouse.dialogs.AdvancedFilterDialog;
import com.example.beathouse.utils.BeatFilterHelper;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.List;
import android.content.Context;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private FirestoreHelper firestoreHelper;
    private SellerBeatsAdapter sellerBeatsAdapter;
    private List<Beat> beatsList = new ArrayList<>();
    private List<Beat> filteredList = new ArrayList<>();
    private String currentUserId;
    private ListenerRegistration beatsListener;
    private static final String TAG = "HomeFragment";

    // Поля для фильтрации
    private String sortType = "default";
    private String searchTag = null;
    private int minBpm = -1;
    private int maxBpm = -1;
    private String currentSearchQuery = "";
    private androidx.activity.result.ActivityResultLauncher<String> pickImageLauncher;

    @Override
    public void onAttach(@NonNull Context context) {
        LocaleHelper.applyLanguage(context);
        super.onAttach(context);
        initActivityResultLaunchers();
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentHomeBinding.inflate(i, c, false);
        setHasOptionsMenu(true);
        return binding.getRoot();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_home_seller, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            showAdvancedFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firestoreHelper = new FirestoreHelper();

        sellerBeatsAdapter = new SellerBeatsAdapter(beatsList, requireContext(), () -> {
            Log.d(TAG, "Beat deleted, refreshing list");
            loadMyBeats();
        });
        sellerBeatsAdapter.setImagePickerLauncher(() -> pickImageLauncher.launch("image/*"));

        binding.recyclerViewBeats.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewBeats.setAdapter(sellerBeatsAdapter);
        binding.swipeRefresh.setOnRefreshListener(() -> loadMyBeats());

        setupSearchView();

        if (binding.fabUpload != null) {
            binding.fabUpload.setVisibility(View.GONE);
        }

        loadMyBeats();
        Log.d(TAG, "HomeFragment created for user: " + currentUserId);
    }

    private void initActivityResultLaunchers() {
        pickImageLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && sellerBeatsAdapter != null) {
                        sellerBeatsAdapter.handleImageSelected(uri);
                    }
                }
        );
    }

    private void setupSearchView() {
        if (binding.etSearch != null) {
            binding.etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    currentSearchQuery = s.toString();
                    applyAllFilters();
                }
            });

            binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    applyAllFilters();
                    // Скрываем клавиатуру
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);
                    return true;
                }
                return false;
            });
        }
    }

    private void loadMyBeats() {
        if (binding == null || currentUserId == null) return;

        if (beatsListener != null) beatsListener.remove();

        binding.progressBar.setVisibility(View.VISIBLE);
        Log.d(TAG, "Listening for my beats: " + currentUserId);

        beatsListener = firestoreHelper.getProducerBeatsRealtime(currentUserId, new FirestoreHelper.FirestoreCallback() {
            public void onSuccess(Object r) {
                List<Beat> b = (List<Beat>) r;
                Log.d(TAG, "Beats updated: " + (b != null ? b.size() : 0));

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding == null) return;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);
                        beatsList.clear();
                        if (b != null && !b.isEmpty()) {
                            beatsList.addAll(b);
                            applyAllFilters();
                        } else {
                            filteredList.clear();
                            sellerBeatsAdapter.updateBeatsList(filteredList);
                        }
                        updateEmpty();
                    });
                }
            }
            public void onError(String e) {
                Log.e(TAG, "Error loading beats: " + e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding == null) return;
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);
                        updateEmpty();
                    });
                }
            }
        });
    }

    private void showAdvancedFilterDialog() {
        AdvancedFilterDialog dialog = new AdvancedFilterDialog(requireContext(),
                sortType, searchTag, minBpm, maxBpm,
                new AdvancedFilterDialog.OnFilterApplyListener() {
                    @Override
                    public void onSortSelected(String sort) {
                        sortType = sort;
                        applyAllFilters();
                    }

                    @Override
                    public void onTagSearch(String tag) {
                        searchTag = tag != null && !tag.isEmpty() ? tag.toLowerCase() : null;
                        applyAllFilters();
                    }

                    @Override
                    public void onBpmRange(int min, int max) {
                        minBpm = min;
                        maxBpm = max;
                        applyAllFilters();
                    }

                    @Override
                    public void onClearFilters() {
                        sortType = "default";
                        searchTag = null;
                        minBpm = -1;
                        maxBpm = -1;
                        applyAllFilters();
                    }
                });
        dialog.show();
    }

    private void applyAllFilters() {
        List<Beat> result = BeatFilterHelper.applyFilters(
                beatsList,
                currentSearchQuery,
                "All",
                searchTag,
                minBpm,
                maxBpm,
                sortType,
                false
        );

        filteredList.clear();
        filteredList.addAll(result);
        sellerBeatsAdapter.updateBeatsList(filteredList);
        updateEmpty();

        Log.d(TAG, "Filter applied: sort=" + sortType +
                ", tag=" + searchTag +
                ", bpmRange=" + minBpm + "-" + maxBpm +
                ", results=" + result.size());
    }

    private void updateEmpty() {
        boolean empty = filteredList.isEmpty();
        if (binding.emptyState != null) {
            binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        binding.recyclerViewBeats.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    public void refreshBeatsList() {
        Log.d(TAG, "Refreshing beats list");
        loadMyBeats();
    }


    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            LocaleHelper.applyLanguage(getContext());
        }
        Log.d(TAG, "onResume - refreshing beats list");
        loadMyBeats();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (beatsListener != null) {
            beatsListener.remove();
            beatsListener = null;
        }
        binding = null;
        Log.d(TAG, "HomeFragment destroyed");
    }
}