package com.example.beathouse;

import android.content.Context;
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
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beathouse.adapters.BeatsAdapter;
import com.example.beathouse.databinding.FragmentBuyerHomeBinding;
import com.example.beathouse.models.Beat;
import com.example.beathouse.utils.AdvancedFilterDialog;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.utils.LocaleHelper;
import com.google.android.material.chip.Chip;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuyerHomeFragment extends Fragment {
    private FragmentBuyerHomeBinding binding;
    private FirestoreHelper firestoreHelper;
    private BeatsAdapter beatsAdapter;
    private MiniPlayer miniPlayer;
    private List<Beat> allBeats = new ArrayList<>();
    private List<Beat> filteredBeats = new ArrayList<>();
    private String selectedGenre = "All";
    private ListenerRegistration beatsListener;
    private static final String TAG = "BuyerHomeFragment";

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentBuyerHomeBinding.inflate(i, c, false);
        setHasOptionsMenu(true);
        return binding.getRoot();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_buyer_home, menu);
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
        firestoreHelper = new FirestoreHelper();
        beatsAdapter = new BeatsAdapter(filteredBeats, requireContext());

        if (getActivity() != null) {
            View activityRoot = getActivity().findViewById(R.id.miniPlayerCard);
            if (activityRoot != null) {
                miniPlayer = new MiniPlayer(activityRoot, beatsAdapter, requireContext());
                beatsAdapter.setMiniPlayer(miniPlayer);
                Log.d(TAG, "MiniPlayer initialized from Activity root");
            } else {
                Log.e(TAG, "miniPlayerCard not found in Activity layout");
            }
        }

        binding.recyclerViewBeats.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewBeats.setAdapter(beatsAdapter);

        String[] genres = {
                getString(R.string.genre_all),
                getString(R.string.genre_hip_hop),
                getString(R.string.genre_trap),
                getString(R.string.genre_rnb),
                getString(R.string.genre_drill),
                getString(R.string.genre_pop),
                getString(R.string.genre_electronic),
                getString(R.string.genre_lo_fi)
        };

        String[] genreKeys = {"All", "Hip-Hop", "Trap", "R&B", "Drill", "Pop", "Electronic", "Lo-Fi"};

        for (int i = 0; i < genres.length; i++) {
            Chip chip = new Chip(requireContext());
            chip.setText(genres[i]);
            chip.setTag(genreKeys[i]);
            chip.setCheckable(true);
            chip.setChecked(genreKeys[i].equals("All"));
            final String genreKey = genreKeys[i];
            chip.setOnClickListener(v1 -> {
                selectedGenre = genreKey;
                applyAllFilters();
            });
            binding.genreChipGroup.addView(chip);
        }

        binding.swipeRefresh.setOnRefreshListener(() -> loadBeats());
        loadBeats();
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
        List<Beat> result = new ArrayList<>();

        // 1. Фильтр по жанру
        if ("All".equals(selectedGenre)) {
            result.addAll(allBeats);
        } else {
            for (Beat b : allBeats) {
                if (selectedGenre.equalsIgnoreCase(b.getGenre())) {
                    result.add(b);
                }
            }
        }

        // 2. Фильтр по тегам
        if (searchTag != null && !searchTag.isEmpty()) {
            List<Beat> tagFiltered = new ArrayList<>();
            for (Beat beat : result) {
                if (containsTag(beat, searchTag)) {
                    tagFiltered.add(beat);
                }
            }
            result = tagFiltered;
        }

        // 3. Фильтр по BPM диапазону
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

        // 4. Сортировка
        sortBeats(result);

        filteredBeats.clear();
        filteredBeats.addAll(result);
        beatsAdapter.notifyDataSetChanged();
        updateEmpty();

        Log.d(TAG, "Filter applied: genre=" + selectedGenre +
                ", sort=" + sortType + ", tag=" + searchTag +
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

    private void loadBeats() {
        binding.progressBar.setVisibility(View.VISIBLE);

        if (beatsListener != null) {
            beatsListener.remove();
        }

        beatsListener = firestoreHelper.getBeatsRealtime(new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object r) {
                List<Beat> beats = (List<Beat>) r;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);
                        if (beats != null && !beats.isEmpty()) {
                            allBeats.clear();
                            allBeats.addAll(beats);
                            applyAllFilters();
                            Log.d(TAG, "Loaded " + beats.size() + " beats from realtime listener");
                        }
                        updateEmpty();
                    });
                }
            }

            @Override
            public void onError(String e) {
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

    public void searchBeats(String q) {
        binding.progressBar.setVisibility(View.VISIBLE);
        firestoreHelper.searchBeats(q, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object r) {
                List<Beat> res = (List<Beat>) r;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        filteredBeats.clear();
                        if (res != null) filteredBeats.addAll(res);
                        beatsAdapter.notifyDataSetChanged();
                        updateEmpty();
                    });
                }
            }

            @Override
            public void onError(String e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> binding.progressBar.setVisibility(View.GONE));
                }
            }
        });
    }

    public void forceRefresh() {
        Log.d(TAG, "Force refreshing beats list");
        if (beatsListener != null) {
            beatsListener.remove();
            beatsListener = null;
        }
        loadBeats();
    }

    private void updateEmpty() {
        boolean empty = filteredBeats.isEmpty();
        if (binding.emptyState != null) {
            binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        binding.recyclerViewBeats.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            LocaleHelper.applyLanguage(getContext());
        }
        Log.d(TAG, "onResume - refreshing beats list");
        forceRefresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (beatsListener != null) {
            beatsListener.remove();
            beatsListener = null;
        }
        binding = null;
    }
}