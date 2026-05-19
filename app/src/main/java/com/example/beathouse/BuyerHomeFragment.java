package com.example.beathouse;
import androidx.fragment.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.widget.NestedScrollView;
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

    // ✅ Поля для поиска
    private EditText etSearch;
    private String currentSearchQuery = "";

    @Override
    public void onAttach(@NonNull Context context) {
        LocaleHelper.applyLanguage(context);
        super.onAttach(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBuyerHomeBinding.inflate(inflater, container, false);
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firestoreHelper = new FirestoreHelper();

        // ✅ Получаем общий адаптер из Activity
        if (getActivity() instanceof BuyerMainActivity) {
            beatsAdapter = ((BuyerMainActivity) getActivity()).getBeatsAdapter();
            Log.d(TAG, "Shared BeatsAdapter retrieved from BuyerMainActivity");
        } else {
            beatsAdapter = new BeatsAdapter(filteredBeats, requireContext());
        }

        // ✅ Настройка RecyclerView
        binding.recyclerViewBeats.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewBeats.setAdapter(beatsAdapter);

        // ✅ Настройка поиска
        setupSearchView();

        // ✅ Настройка чипов жанров
        setupGenreChips();

        // ✅ Настройка Pull-to-Refresh
        binding.swipeRefresh.setOnRefreshListener(() -> loadBeats());

        // ✅ Загрузка битов
        loadBeats();
    }

    // ✅ НАСТРОЙКА ПОИСКА
    private void setupSearchView() {
        // Ищем EditText внутри binding (если он добавлен в layout)
        etSearch = getView().findViewById(R.id.etSearch);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    currentSearchQuery = s.toString();
                    applyAllFilters();
                }
            });

            etSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    applyAllFilters();
                    // Скрываем клавиатуру
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                    return true;
                }
                return false;
            });
        }

    }

    // ✅ НАСТРОЙКА ЧИПОВ ЖАНРОВ
    private void setupGenreChips() {
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

        binding.genreChipGroup.removeAllViews();
        for (int i = 0; i < genres.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.layout_genre_chip, binding.genreChipGroup, false);
            chip.setText(genres[i]);
            chip.setTag(genreKeys[i]);

            if (genreKeys[i].equals(selectedGenre)) {
                chip.setChecked(true);
            }

            final String genreKey = genreKeys[i];
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedGenre = genreKey;
                    applyAllFilters();
                }
            });
            binding.genreChipGroup.addView(chip);
        }
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

    // ✅ ПРИМЕНЕНИЕ ВСЕХ ФИЛЬТРОВ
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

        // 2. ✅ Фильтр по поисковому запросу (название или продюсер)
        if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
            List<Beat> searchFiltered = new ArrayList<>();
            String query = currentSearchQuery.toLowerCase();
            for (Beat beat : result) {
                if (beat.getTitle().toLowerCase().contains(query) ||
                        beat.getUserName().toLowerCase().contains(query)) {
                    searchFiltered.add(beat);
                }
            }
            result = searchFiltered;
        }

        // 3. Фильтр по тегам
        if (searchTag != null && !searchTag.isEmpty()) {
            List<Beat> tagFiltered = new ArrayList<>();
            for (Beat beat : result) {
                if (containsTag(beat, searchTag)) {
                    tagFiltered.add(beat);
                }
            }
            result = tagFiltered;
        }

        // 4. Фильтр по BPM диапазону
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

        // 5. Сортировка
        sortBeats(result);

        filteredBeats.clear();
        filteredBeats.addAll(result);

        // ✅ Обновляем адаптер с поддержкой поиска
        if (beatsAdapter != null) {
            beatsAdapter.updateBeatsList(filteredBeats);
        }

        updateEmpty();
        updateSkeletonVisibility();

        Log.d(TAG, "Filter applied: genre=" + selectedGenre +
                ", search=" + currentSearchQuery +
                ", sort=" + sortType +
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
                Collections.sort(beats, (a, b) -> Double.compare(a.getPriceMp3Wav(), b.getPriceMp3Wav()));
                break;
            case "price_desc":
                Collections.sort(beats, (a, b) -> Double.compare(b.getPriceMp3Wav(), a.getPriceMp3Wav()));
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
        if (allBeats.isEmpty()) {
            showProgress(true);
        }

        if (beatsListener != null) {
            beatsListener.remove();
        }

        beatsListener = firestoreHelper.getBeatsRealtime(new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                List<Beat> beats = (List<Beat>) result;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        showProgress(false);
                        binding.swipeRefresh.setRefreshing(false);

                        allBeats.clear();
                        if (beats != null) {
                            allBeats.addAll(beats);
                            Log.d(TAG, "Loaded " + beats.size() + " beats from realtime listener");
                        }

                        applyAllFilters();
                        updateEmpty();
                        updateSkeletonVisibility();
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        showProgress(false);
                        binding.swipeRefresh.setRefreshing(false);
                        allBeats.clear();
                        applyAllFilters();
                        updateEmpty();
                        updateSkeletonVisibility();
                    });
                }
            }
        });
    }

    private void showProgress(boolean show) {
        if (binding.progressBar != null) {
            binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (binding.skeletonLayout != null) {
            binding.skeletonLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateSkeletonVisibility() {
        if (binding.skeletonLayout != null) {
            if (binding.progressBar != null && binding.progressBar.getVisibility() == View.VISIBLE) {
                binding.skeletonLayout.setVisibility(View.VISIBLE);
            } else if (allBeats.isEmpty() && binding.swipeRefresh.isRefreshing()) {
                binding.skeletonLayout.setVisibility(View.VISIBLE);
            } else {
                binding.skeletonLayout.setVisibility(View.GONE);
            }
        }
    }

    public void searchBeats(String query) {
        currentSearchQuery = query;
        applyAllFilters();
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
        boolean isLoading = (binding.progressBar != null && binding.progressBar.getVisibility() == View.VISIBLE)
                || binding.swipeRefresh.isRefreshing();

        boolean empty = filteredBeats.isEmpty();

        if (binding.emptyState != null) {
            // Показываем empty state только если данных реально нет И мы не в процессе загрузки
            binding.emptyState.setVisibility(empty && !isLoading ? View.VISIBLE : View.GONE);
        }

        if (binding.recyclerViewBeats != null) {
            // Скрываем список только если он пуст И мы не показываем скелетоны
            boolean showSkeletons = binding.skeletonLayout != null && binding.skeletonLayout.getVisibility() == View.VISIBLE;
            binding.recyclerViewBeats.setVisibility(empty || showSkeletons ? View.GONE : View.VISIBLE);
        }
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
        // Мы НЕ вызываем releaseMediaPlayer(), чтобы музыка продолжала играть
        binding = null;
    }
}