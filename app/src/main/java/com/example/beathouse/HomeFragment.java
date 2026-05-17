package com.example.beathouse;

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
import com.example.beathouse.utils.AdvancedFilterDialog;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.Context;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private FirestoreHelper firestoreHelper;
    private SellerBeatsAdapter sellerBeatsAdapter;
    private List<Beat> beatsList = new ArrayList<>();
    private List<Beat> filteredList = new ArrayList<>();
    private String currentUserId;
    private static final String TAG = "HomeFragment";

    // Поля для фильтрации
    private String sortType = "default";
    private String searchTag = null;
    private int minBpm = -1;
    private int maxBpm = -1;

    @Override
    public void onAttach(@NonNull Context context) {
        LocaleHelper.applyLanguage(context);
        super.onAttach(context);
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

        binding.recyclerViewBeats.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewBeats.setAdapter(sellerBeatsAdapter);
        binding.swipeRefresh.setOnRefreshListener(() -> loadMyBeats());

        if (binding.fabUpload != null) {
            binding.fabUpload.setVisibility(View.GONE);
        }

        loadMyBeats();
        Log.d(TAG, "HomeFragment created for user: " + currentUserId);
    }

    private void loadMyBeats() {
        if (binding == null || currentUserId == null) return;

        binding.progressBar.setVisibility(View.VISIBLE);
        Log.d(TAG, "Loading my beats for producer: " + currentUserId);

        firestoreHelper.getProducerBeats(currentUserId, new FirestoreHelper.FirestoreCallback() {
            public void onSuccess(Object r) {
                List<Beat> b = (List<Beat>) r;
                Log.d(TAG, "Loaded " + (b != null ? b.size() : 0) + " beats");

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);
                        beatsList.clear();
                        if (b != null && !b.isEmpty()) {
                            beatsList.addAll(b);
                            applyAllFilters();
                            Log.d(TAG, "Displaying " + filteredList.size() + " beats");
                        } else {
                            Log.d(TAG, "No beats found - showing empty state");
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
        List<Beat> result = new ArrayList<>(beatsList);

        // 1. Фильтр по тегам
        if (searchTag != null && !searchTag.isEmpty()) {
            List<Beat> tagFiltered = new ArrayList<>();
            for (Beat beat : result) {
                if (containsTag(beat, searchTag)) {
                    tagFiltered.add(beat);
                }
            }
            result = tagFiltered;
        }

        // 2. Фильтр по BPM диапазону
        if (minBpm > 0 || maxBpm > 0) {
            List<Beat> bpmFiltered = new ArrayList<>();
            for (Beat beat : result) {
                int bpm = beat.getBpm();
                boolean bpmOk = true;
                if (minBpm > 0 && bpm < minBpm) bpmOk = false;
                if (maxBpm > 0 && bpm > maxBpm) bpmOk = false;
                if (bpmOk) bpmFiltered.add(beat);
            }
            result = bpmFiltered;
        }

        // 3. Сортировка
        sortBeats(result);

        filteredList.clear();
        filteredList.addAll(result);
        sellerBeatsAdapter.updateBeatsList(filteredList);
        updateEmpty();

        Log.d(TAG, "Filter applied: sort=" + sortType +
                ", tag=" + searchTag +
                ", bpmRange=" + minBpm + "-" + maxBpm +
                ", results=" + result.size());
    }

    private boolean containsTag(Beat beat, String tag) {
        if (beat == null || tag == null) return false;

        String description = beat.getDescription();
        if (description == null || description.isEmpty()) return false;

        Pattern pattern = Pattern.compile("#(\\w+)");
        Matcher matcher = pattern.matcher(description.toLowerCase());

        while (matcher.find()) {
            String foundTag = matcher.group(1);
            if (foundTag.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private void sortBeats(List<Beat> beats) {
        switch (sortType) {
            case "price_asc":
                Collections.sort(beats, (a, b) -> Double.compare(a.getPrice(), b.getPrice()));
                break;
            case "price_desc":
                Collections.sort(beats, (a, b) -> Double.compare(b.getPrice(), a.getPrice()));
                break;
            case "bpm_asc":
                Collections.sort(beats, (a, b) -> Integer.compare(a.getBpm(), b.getBpm()));
                break;
            case "bpm_desc":
                Collections.sort(beats, (a, b) -> Integer.compare(b.getBpm(), a.getBpm()));
                break;
            default:
                break;
        }
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
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        Log.d(TAG, "onActivityResult called - requestCode: " + requestCode + ", resultCode: " + resultCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (sellerBeatsAdapter != null) {
            sellerBeatsAdapter.handleActivityResult(requestCode, resultCode, data);
        }
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
        binding = null;
        Log.d(TAG, "HomeFragment destroyed");
    }
}