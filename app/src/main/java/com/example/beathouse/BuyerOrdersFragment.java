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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beathouse.adapters.OrderHistoryAdapter;
import com.example.beathouse.databinding.FragmentBuyerOrdersBinding;
import com.example.beathouse.models.Order;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.utils.LocaleHelper;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import android.util.Log;

public class BuyerOrdersFragment extends Fragment {

    private FragmentBuyerOrdersBinding binding;
    private FirestoreHelper firestoreHelper;
    private OrderHistoryAdapter adapter;
    private List<Order> ordersList;
    private List<Order> filteredList;
    private String currentUserId;
    private boolean isSelectionMode = false;
    private List<String> selectedOrderIds = new ArrayList<>();
    private MaterialButton btnDeleteSelected;
    private static final String TAG = "BuyerOrdersFragment";

    @Override
    public void onAttach(@NonNull Context context) {
        // ✅ Применяем язык перед созданием фрагмента
        LocaleHelper.applyLanguage(context);
        super.onAttach(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBuyerOrdersBinding.inflate(inflater, container, false);
        setHasOptionsMenu(true);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firestoreHelper = new FirestoreHelper();
        ordersList = new ArrayList<>();
        filteredList = new ArrayList<>();

        setupRecyclerView();
        setupDeleteButton();
        loadOrders();
    }

    private void setupRecyclerView() {
        adapter = new OrderHistoryAdapter(ordersList, requireContext(), new OrderHistoryAdapter.OnOrderActionListener() {
            @Override
            public void onLongClick(Order order, int position) {
                enableSelectionMode();
                toggleSelection(order.getId());
            }

            @Override
            public void onSelectClick(Order order, boolean selected) {
                if (selected) {
                    if (!selectedOrderIds.contains(order.getId())) {
                        selectedOrderIds.add(order.getId());
                    }
                } else {
                    selectedOrderIds.remove(order.getId());
                }
                updateSelectionMode();
            }
        });

        // ✅ Использование правильного ID из XML
        binding.recyclerViewOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewOrders.setAdapter(adapter);
    }

    private void setupDeleteButton() {
        btnDeleteSelected = new MaterialButton(requireContext());
        btnDeleteSelected.setText(getString(R.string.delete_selected));
        btnDeleteSelected.setVisibility(View.GONE);
        btnDeleteSelected.setOnClickListener(v -> deleteSelectedOrders());

        // Добавляем кнопку в корневой layout
        if (binding.getRoot() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) binding.getRoot();
            parent.addView(btnDeleteSelected, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_orders, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            showFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void enableSelectionMode() {
        isSelectionMode = true;
        selectedOrderIds.clear();
        btnDeleteSelected.setVisibility(View.VISIBLE);
        adapter.setSelectionMode(true);
        requireActivity().invalidateOptionsMenu();
    }

    private void disableSelectionMode() {
        isSelectionMode = false;
        selectedOrderIds.clear();
        btnDeleteSelected.setVisibility(View.GONE);
        adapter.setSelectionMode(false);
        requireActivity().invalidateOptionsMenu();
    }

    private void toggleSelection(String orderId) {
        if (selectedOrderIds.contains(orderId)) {
            selectedOrderIds.remove(orderId);
        } else {
            selectedOrderIds.add(orderId);
        }
        updateSelectionMode();
    }

    private void updateSelectionMode() {
        if (selectedOrderIds.isEmpty()) {
            disableSelectionMode();
        } else {
            btnDeleteSelected.setText(getString(R.string.delete_selected) + " (" + selectedOrderIds.size() + ")");
        }
    }

    private void deleteSelectedOrders() {
        if (selectedOrderIds.isEmpty()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_orders))
                .setMessage(getString(R.string.sure_delete_orders) + " " + selectedOrderIds.size() + " " + getString(R.string.order_records))
                .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                    deleteOrdersFromFirestore();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void deleteOrdersFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        for (String orderId : selectedOrderIds) {
            db.collection("orders").document(orderId)
                    .delete()
                    .addOnSuccessListener(a -> {
                        Log.d(TAG, "Deleted order: " + orderId);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to delete: " + orderId, e);
                    });
        }

        // Обновляем локальный список
        ordersList.removeIf(order -> selectedOrderIds.contains(order.getId()));
        filteredList.removeIf(order -> selectedOrderIds.contains(order.getId()));

        adapter.updateOrders(filteredList.isEmpty() ? ordersList : filteredList);
        disableSelectionMode();

        Toast.makeText(requireContext(), getString(R.string.deleted) + " " + selectedOrderIds.size() + " " + getString(R.string.orders), Toast.LENGTH_SHORT).show();

        if (ordersList.isEmpty()) {
            showEmptyState(true);
        }
    }

    private void showFilterDialog() {
        String[] periods = {
                getString(R.string.last_7_days),
                getString(R.string.last_30_days),
                getString(R.string.last_3_months),
                getString(R.string.last_year),
                getString(R.string.all_time)
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.filter_by_period))
                .setItems(periods, (dialog, which) -> {
                    switch (which) {
                        case 0: filterByDays(7); break;
                        case 1: filterByDays(30); break;
                        case 2: filterByDays(90); break;
                        case 3: filterByDays(365); break;
                        case 4: showAllOrders(); break;
                    }
                })
                .show();
    }

    private void filterByDays(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        long fromDate = cal.getTimeInMillis();

        List<Order> filtered = new ArrayList<>();
        for (Order order : ordersList) {
            if (order.getCreatedAt() >= fromDate) {
                filtered.add(order);
            }
        }

        filteredList.clear();
        filteredList.addAll(filtered);
        adapter.updateOrders(filtered);
        Toast.makeText(requireContext(), getString(R.string.showing_last) + " " + days + " " + getString(R.string.days) + ": " + filtered.size() + " " + getString(R.string.orders), Toast.LENGTH_SHORT).show();
    }

    private void showAllOrders() {
        filteredList.clear();
        filteredList.addAll(ordersList);
        adapter.updateOrders(ordersList);
        Toast.makeText(requireContext(), getString(R.string.showing_all) + " " + ordersList.size() + " " + getString(R.string.orders), Toast.LENGTH_SHORT).show();
    }

    private void loadOrders() {
        binding.progressBar.setVisibility(View.VISIBLE);

        firestoreHelper.getUserOrders(currentUserId, new FirestoreHelper.FirestoreCallback() {
            @Override
            public void onSuccess(Object result) {
                List<Order> orders = (List<Order>) result;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);

                        if (orders != null && !orders.isEmpty()) {
                            ordersList.clear();
                            ordersList.addAll(orders);
                            filteredList.clear();
                            filteredList.addAll(orders);
                            adapter.updateOrders(orders);
                            showEmptyState(false);
                        } else {
                            showEmptyState(true);
                        }
                    });
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        showEmptyState(true);
                    });
                }
            }
        });
    }

    private void showEmptyState(boolean show) {
        if (binding.emptyState != null) {
            binding.emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        // ✅ Использование правильного ID из XML
        binding.recyclerViewOrders.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        // ✅ Применяем язык при возобновлении фрагмента
        if (getContext() != null) {
            LocaleHelper.applyLanguage(getContext());
        }
        loadOrders();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}