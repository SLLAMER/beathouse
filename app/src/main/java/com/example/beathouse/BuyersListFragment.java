package com.example.beathouse;

import android.content.Context;
import android.os.Bundle;
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
import com.example.beathouse.adapters.BuyersAdapter;
import com.example.beathouse.databinding.FragmentBuyersListBinding;
import com.example.beathouse.models.Order;
import com.example.beathouse.models.User;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.utils.LocaleHelper;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuyersListFragment extends Fragment {

    private FragmentBuyersListBinding binding;
    private BuyersAdapter adapter;
    private List<User> buyersList;
    private List<User> filteredList;
    private FirestoreHelper firestoreHelper;
    private String currentUserId;
    private static final String TAG = "BuyersListFragment";

    @Override
    public void onAttach(@NonNull Context context) {
        LocaleHelper.applyLanguage(context);
        super.onAttach(context);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentBuyersListBinding.inflate(i, c, false);
        setHasOptionsMenu(true);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firestoreHelper = new FirestoreHelper();
        buyersList = new ArrayList<>();
        filteredList = new ArrayList<>();
        adapter = new BuyersAdapter(buyersList, requireContext());

        binding.recyclerViewBuyers.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewBuyers.setAdapter(adapter);

        loadBuyersForSeller();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_search, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.search_by_name_email));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterBuyers(newText);
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    private void filterBuyers(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.updateList(buyersList);
            return;
        }

        String lowerQuery = query.toLowerCase().trim();
        List<User> filtered = new ArrayList<>();

        for (User user : buyersList) {
            if (user.getUsername() != null && user.getUsername().toLowerCase().contains(lowerQuery)) {
                filtered.add(user);
            } else if (user.getEmail() != null && user.getEmail().toLowerCase().contains(lowerQuery)) {
                filtered.add(user);
            }
        }

        filteredList.clear();
        filteredList.addAll(filtered);
        adapter.updateList(filtered);

        if (filtered.isEmpty()) {
            binding.emptyState.getRoot().setVisibility(View.VISIBLE);
            binding.recyclerViewBuyers.setVisibility(View.GONE);
        } else {
            binding.emptyState.getRoot().setVisibility(View.GONE);
            binding.recyclerViewBuyers.setVisibility(View.VISIBLE);
        }
    }

    private void loadBuyersForSeller() {
        binding.progressBar.setVisibility(View.VISIBLE);

        // 1. Получаем все заказы (продажи) текущего продавца
        firestoreHelper.getSellerSales(currentUserId, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                List<Order> orders = (List<Order>) result;

                if (orders == null || orders.isEmpty()) {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        updateEmpty();
                    });
                    return;
                }

                // 2. Собираем уникальные ID покупателей
                Set<String> uniqueBuyerIds = new HashSet<>();
                for (Order order : orders) {
                    String buyerId = order.getBuyerId();
                    if (buyerId != null && !buyerId.isEmpty()) {
                        uniqueBuyerIds.add(buyerId);
                    }
                }

                if (uniqueBuyerIds.isEmpty()) {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        updateEmpty();
                    });
                    return;
                }

                // 3. Загружаем данные каждого покупателя
                loadBuyerDetails(new ArrayList<>(uniqueBuyerIds));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            getString(R.string.error_prefix) + error, Toast.LENGTH_SHORT).show();
                    updateEmpty();
                });
            }
        });
    }

    private void loadBuyerDetails(List<String> buyerIds) {
        if (buyerIds.isEmpty()) {
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                updateEmpty();
            });
            return;
        }

        List<User> buyers = new ArrayList<>();
        Map<String, Integer> buyerStats = new HashMap<>();

        // Для каждого buyerId загружаем данные
        for (String buyerId : buyerIds) {
            firestoreHelper.getUser(buyerId, new FirestoreHelper.FirestoreCallback() {
                @Override
                public void onSuccess(Object result) {
                    User user = (User) result;
                    if (user != null && user.isBuyer()) {
                        synchronized (buyers) {
                            buyers.add(user);
                        }
                    }

                    // Если загрузили всех
                    synchronized (buyerIds) {
                        if (buyers.size() >= buyerIds.size()) {
                            runOnUiThread(() -> {
                                binding.progressBar.setVisibility(View.GONE);
                                buyersList.clear();
                                buyersList.addAll(buyers);
                                filteredList.clear();
                                filteredList.addAll(buyers);
                                adapter.updateList(buyersList);
                                updateEmpty();
                            });
                        }
                    }
                }

                @Override
                public void onError(String error) {
                    synchronized (buyerIds) {
                        if (buyers.size() >= buyerIds.size()) {
                            runOnUiThread(() -> {
                                binding.progressBar.setVisibility(View.GONE);
                                buyersList.clear();
                                buyersList.addAll(buyers);
                                filteredList.clear();
                                filteredList.addAll(buyers);
                                adapter.updateList(buyersList);
                                updateEmpty();
                            });
                        }
                    }
                }
            });
        }
    }

    private void updateEmpty() {
        boolean empty = buyersList.isEmpty();
        if (binding.emptyState != null && binding.emptyState.getRoot() != null) {
            binding.emptyState.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        binding.recyclerViewBuyers.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void runOnUiThread(Runnable action) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(action);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            LocaleHelper.applyLanguage(getContext());
        }
        loadBuyersForSeller();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter.removeAllListeners();
        }
        binding = null;
    }
}