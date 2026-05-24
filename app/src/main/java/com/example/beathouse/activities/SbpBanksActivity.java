package com.example.beathouse.activities;
import com.example.beathouse.R;
import com.example.beathouse.BaseActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.adapters.BanksAdapter;
import com.example.beathouse.databinding.ActivitySbpBanksBinding;
import com.example.beathouse.models.Bank;
import java.util.ArrayList;
import java.util.List;

public class SbpBanksActivity extends BaseActivity {

    private ActivitySbpBanksBinding binding;
    private BanksAdapter adapter;
    private List<Bank> banksList;
    private double totalAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySbpBanksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        totalAmount = getIntent().getDoubleExtra("total_amount", 0);

        binding.progressBar.setVisibility(View.VISIBLE);

        // Получаем актуальный курс и конвертируем в рубли
        com.example.beathouse.utils.CurrencyUtils.getUsdToRubRate(rate -> {
            binding.progressBar.setVisibility(View.GONE);
            binding.tvAmount.setText(com.example.beathouse.utils.CurrencyUtils.formatRub(totalAmount, rate));
        });

        setupToolbar();
        setupRecyclerView();
        loadBanks();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.sbp));
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        banksList = new ArrayList<>();
        adapter = new BanksAdapter(banksList, bank -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.confirm_payment))
                    .setMessage(getString(R.string.confirm_payment_message) + " " + bank.getName() + "?")
                    .setPositiveButton(getString(R.string.pay), (dialog, which) -> {
                        processPayment();
                    })
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
        });
        binding.recyclerViewBanks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewBanks.setAdapter(adapter);
    }

    private void loadBanks() {
        banksList.add(new Bank("sberbank", "Сбербанк", R.drawable.ic_sberbank));
        banksList.add(new Bank("tinkoff", "Тинькофф", R.drawable.ic_tinkoff));
        banksList.add(new Bank("vtb", "ВТБ", R.drawable.ic_vtb));
        banksList.add(new Bank("alfa", "Альфа-Банк", R.drawable.ic_alfa));
        banksList.add(new Bank("gazprom", "Газпромбанк", R.drawable.ic_gazprom));
        banksList.add(new Bank("raiffeisen", "Райффайзенбанк", R.drawable.ic_raiffeisen));
        banksList.add(new Bank("open", "Открытие", R.drawable.ic_open));
        banksList.add(new Bank("uralsib", "Уралсиб", R.drawable.ic_uralsib));

        adapter.notifyDataSetChanged();
    }

    private void processPayment() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnPay.setEnabled(false);

        new android.os.Handler().postDelayed(() -> {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, getString(R.string.payment_successful), Toast.LENGTH_SHORT).show();

            processOrderCreation();
        }, 2000);
    }

    private void processOrderCreation() {
        String orderId = getIntent().getStringExtra("order_id");
        String itemsJson = getIntent().getStringExtra("cart_items");
        java.util.List<com.example.beathouse.models.CartItem> cartItems = new com.google.gson.Gson().fromJson(itemsJson, new com.google.gson.reflect.TypeToken<java.util.ArrayList<com.example.beathouse.models.CartItem>>() {}.getType());

        if (cartItems == null || cartItems.isEmpty()) {
            finish();
            return;
        }

        new com.example.beathouse.utils.OrderManager(this).processOrderCreation(cartItems, "SBP", new com.example.beathouse.utils.OrderManager.OrderCallback() {
            @Override
            public void onAllOrdersCreated(java.util.List<com.example.beathouse.models.Order> createdOrders) {
                Intent intent = new Intent(SbpBanksActivity.this, BuyerOrderCompleteActivity.class);
                intent.putExtra("total", totalAmount);
                if (createdOrders != null && !createdOrders.isEmpty()) {
                    intent.putExtra("order_id", createdOrders.get(0).getId());
                } else {
                    intent.putExtra("order_id", orderId);
                }
                intent.putExtra("item_count", cartItems.size());
                intent.putExtra("order_items", itemsJson);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SbpBanksActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}