package com.example.beathouse;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beathouse.adapters.ProducersAdapter;
import com.example.beathouse.databinding.FragmentProducersBinding;
import com.example.beathouse.models.Producer;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;

public class ProducersFragment extends Fragment {

    private FragmentProducersBinding binding;
    private ProducersAdapter adapter;
    private List<Producer> producersList;
    private List<Producer> filteredList;
    private String currentUserId;
    private FirebaseFirestore db;
    private ListenerRegistration producersListener;
    private static final String TAG = "ProducersFragment";

    @Override
    public void onAttach(@NonNull Context context) {
        // ✅ Применяем язык перед созданием фрагмента
        LocaleHelper.applyLanguage(context);
        super.onAttach(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProducersBinding.inflate(inflater, container, false);
        setHasOptionsMenu(true);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db = FirebaseFirestore.getInstance();
        producersList = new ArrayList<>();
        filteredList = new ArrayList<>();

        setupRecyclerView();
        loadProducersRealtime();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_search, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.search_by_name_genre));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterProducers(newText);
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    private void filterProducers(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.updateProducers(producersList);
            showEmptyState(producersList.isEmpty());
            return;
        }

        String lowerQuery = query.toLowerCase().trim();
        List<Producer> filtered = new ArrayList<>();

        for (Producer producer : producersList) {
            // Поиск по имени
            String displayName = producer.getDisplayName();
            String username = producer.getUsername();

            if ((displayName != null && displayName.toLowerCase().contains(lowerQuery)) ||
                    (username != null && username.toLowerCase().contains(lowerQuery))) {
                filtered.add(producer);
                continue;
            }

            // Поиск по жанрам
            List<String> genres = producer.getGenres();
            if (genres != null) {
                for (String genre : genres) {
                    if (genre.toLowerCase().contains(lowerQuery)) {
                        filtered.add(producer);
                        break;
                    }
                }
            }
        }

        filteredList.clear();
        filteredList.addAll(filtered);
        adapter.updateProducers(filtered);
        showEmptyState(filtered.isEmpty());
    }

    private void setupRecyclerView() {
        adapter = new ProducersAdapter(producersList, requireContext(), producer -> {
            openProducerProfile(producer);
        });

        binding.recyclerViewProducers.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewProducers.setAdapter(adapter);
    }

    private void openProducerProfile(Producer producer) {
        Intent intent = new Intent(requireContext(), ProducerProfileActivity.class);
        intent.putExtra("producer_id", producer.getProducerId());
        intent.putExtra("producer_name", producer.getDisplayName());
        startActivity(intent);
    }

    private void loadProducersRealtime() {
        binding.progressBar.setVisibility(View.VISIBLE);

        producersListener = db.collection("producers")
                .orderBy("rating", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) {
                        Log.e(TAG, "Listen failed: " + err.getMessage());
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                binding.progressBar.setVisibility(View.GONE);
                                showEmptyState(true);
                            });
                        }
                        return;
                    }

                    if (snap == null) return;

                    List<Producer> producers = new ArrayList<>();
                    for (var doc : snap) {
                        Producer p = Producer.fromMap(doc.getData());
                        if (p != null) {
                            producers.add(p);
                        }
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            producersList.clear();
                            producersList.addAll(producers);
                            filteredList.clear();
                            filteredList.addAll(producers);
                            adapter.updateProducers(producers);
                            showEmptyState(producersList.isEmpty());
                        });
                    }
                });
    }

    private void showEmptyState(boolean show) {
        binding.emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.recyclerViewProducers.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        // ✅ Применяем язык при возобновлении фрагмента
        if (getContext() != null) {
            LocaleHelper.applyLanguage(getContext());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (producersListener != null) {
            producersListener.remove();
            producersListener = null;
        }
        binding = null;
    }
}