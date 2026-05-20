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
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.beathouse.adapters.SalesAdapter;
import com.example.beathouse.databinding.FragmentSalesBinding;
import com.example.beathouse.models.Order;
import com.example.beathouse.utils.FirestoreHelper;
import com.example.beathouse.utils.LocaleHelper;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SellerSalesFragment extends Fragment {

    private FragmentSalesBinding binding;
    private FirestoreHelper firestoreHelper;
    private SalesAdapter adapter;
    private List<Order> salesList;
    private List<Order> filteredList;
    private String currentUserId;
    private boolean isSelectionMode = false;
    private List<String> selectedOrderIds = new ArrayList<>();
    private MaterialButton btnDeleteSelected;
    private ListenerRegistration salesListener;
    private static final String TAG = "SellerSalesFragment";

    @Override
    public void onAttach(@NonNull Context context) {
        // ✅ Применяем язык перед созданием фрагмента
        LocaleHelper.applyLanguage(context);
        super.onAttach(context);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup c, @Nullable Bundle s) {
        binding = FragmentSalesBinding.inflate(i, c, false);
        setHasOptionsMenu(true);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        firestoreHelper = new FirestoreHelper();
        salesList = new ArrayList<>();
        filteredList = new ArrayList<>();

        setupRecyclerView();
        setupDeleteButton();
        loadSales();
    }

    private void setupRecyclerView() {
        adapter = new SalesAdapter(salesList, requireContext(), new SalesAdapter.OnSaleActionListener() {
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

        binding.recyclerViewOrders.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewOrders.setAdapter(adapter);
    }

    private void setupDeleteButton() {
        btnDeleteSelected = new MaterialButton(requireContext());
        btnDeleteSelected.setText(getString(R.string.delete_selected));
        btnDeleteSelected.setVisibility(View.GONE);
        btnDeleteSelected.setOnClickListener(v -> deleteSelectedSales());

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
        // Только фильтр, без поиска и select all
        inflater.inflate(R.menu.menu_sales_filter, menu);
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

    private void deleteSelectedSales() {
        if (selectedOrderIds.isEmpty()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_sales))
                .setMessage(getString(R.string.sure_delete_sales) + " " + selectedOrderIds.size() + " " + getString(R.string.sale_records))
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
        salesList.removeIf(order -> selectedOrderIds.contains(order.getId()));
        filteredList.removeIf(order -> selectedOrderIds.contains(order.getId()));

        adapter.updateSales(filteredList.isEmpty() ? salesList : filteredList);
        disableSelectionMode();

        Toast.makeText(requireContext(), getString(R.string.deleted) + " " + selectedOrderIds.size() + " " + getString(R.string.sales), Toast.LENGTH_SHORT).show();
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
                        case 4: showAllSales(); break;
                    }
                })
                .show();
    }

    private void filterByDays(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        long fromDate = cal.getTimeInMillis();

        List<Order> filtered = new ArrayList<>();
        for (Order order : salesList) {
            if (order.getCreatedAt() >= fromDate) {
                filtered.add(order);
            }
        }

        filteredList.clear();
        filteredList.addAll(filtered);
        adapter.updateSales(filtered);
        Toast.makeText(requireContext(), getString(R.string.showing_last) + " " + days + " " + getString(R.string.days) + ": " + filtered.size() + " " + getString(R.string.sales), Toast.LENGTH_SHORT).show();
    }

    private void showAllSales() {
        filteredList.clear();
        filteredList.addAll(salesList);
        adapter.updateSales(salesList);
        Toast.makeText(requireContext(), getString(R.string.showing_all) + " " + salesList.size() + " " + getString(R.string.sales), Toast.LENGTH_SHORT).show();
    }

    private void loadSales() {
        if (binding == null || currentUserId == null) return;

        if (salesListener != null) salesListener.remove();

        binding.progressBar.setVisibility(View.VISIBLE);
        Log.d(TAG, "📊 Listening for sales for seller: " + currentUserId);

        salesListener = firestoreHelper.getSellerSalesRealtime(currentUserId, new FirestoreHelper.FirestoreCallback() {
            public void onSuccess(Object r) {
                List<Order> orders = (List<Order>) r;
                Log.d(TAG, "✅ Realtime sales updated: " + (orders != null ? orders.size() : 0));

                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    salesList.clear();
                    if (orders != null && !orders.isEmpty()) {
                        salesList.addAll(orders);
                        filteredList.clear();
                        filteredList.addAll(orders);
                        adapter.updateSales(salesList);
                        Log.d(TAG, "📋 Displaying " + salesList.size() + " sales");
                    } else {
                        adapter.updateSales(new ArrayList<>());
                    }
                    updateEmpty();
                });
            }
            public void onError(String e) {
                Log.e(TAG, "❌ Error loading sales: " + e);
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    updateEmpty();
                });
            }
        });
    }

    private void updateEmpty() {
        boolean empty = salesList.isEmpty();
        if (binding.emptyStateInclude != null) {
            binding.emptyStateInclude.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        binding.recyclerViewOrders.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        // ✅ Применяем язык при возобновлении фрагмента
        if (getContext() != null) {
            LocaleHelper.applyLanguage(getContext());
        }
        loadSales();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (salesListener != null) {
            salesListener.remove();
            salesListener = null;
        }
        binding = null;
    }
}