package com.example.beathouse;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.adapters.OrderHistoryAdapter;
import com.example.beathouse.models.Order;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class OrderHistoryActivity extends BaseActivity {

    private static final String TAG = "OrderHistoryActivity";
    private RecyclerView recyclerView;
    private OrderHistoryAdapter adapter;
    private List<Order> orderList;
    private TextView tvEmpty;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        setupToolbar();
        initViews();
        setupRecyclerView();
        loadOrderHistory();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Order History");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_orders);
        tvEmpty = findViewById(R.id.tv_empty);
        firestore = FirebaseFirestore.getInstance();
        orderList = new ArrayList<>();
    }

    private void setupRecyclerView() {
        // ✅ Исправлено: передаем все 3 параметра (list, context, listener)
        adapter = new OrderHistoryAdapter(orderList, this, new OrderHistoryAdapter.OnOrderActionListener() {
            @Override
            public void onLongClick(Order order, int position) {
                // Можно добавить функционал, но пока ничего
            }

            @Override
            public void onSelectClick(Order order, boolean selected) {
                // Можно добавить функционал, но пока ничего
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        showEmptyState(true);
    }

    private void loadOrderHistory() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Log.d(TAG, "📋 Loading order history for user: " + userId);

        firestore.collection("orders")
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    orderList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Order order = Order.fromMap(document.getData());
                        if (order != null) {
                            order.setId(document.getId());
                            orderList.add(order);
                            Log.d(TAG, "📦 Loaded order: " + order.getId() + " - " + order.getFormattedTotal());
                        }
                    }

                    Log.d(TAG, "✅ Loaded " + orderList.size() + " orders");

                    if (orderList.isEmpty()) {
                        showEmptyState(true);
                        tvEmpty.setText("No orders yet");
                    } else {
                        showEmptyState(false);
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Error loading order history: " + e.getMessage());
                    tvEmpty.setText("Error loading orders");
                    showEmptyState(true);
                });
    }

    private void showEmptyState(boolean show) {
        if (show) {
            recyclerView.setVisibility(RecyclerView.GONE);
            tvEmpty.setVisibility(TextView.VISIBLE);
        } else {
            recyclerView.setVisibility(RecyclerView.VISIBLE);
            tvEmpty.setVisibility(TextView.GONE);
        }
    }
}