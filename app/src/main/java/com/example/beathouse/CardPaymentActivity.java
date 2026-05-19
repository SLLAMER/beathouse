package com.example.beathouse;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import com.example.beathouse.databinding.ActivityCardPaymentBinding;

public class CardPaymentActivity extends BaseActivity {

    private ActivityCardPaymentBinding binding;
    private double totalAmount;
    private boolean isFormatting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCardPaymentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        totalAmount = getIntent().getDoubleExtra("total_amount", 0);
        // ✅ Убираем центы - форматируем без копеек
        binding.tvAmount.setText(String.format("$%.0f", totalAmount));

        setupToolbar();
        setupListeners();
        setupCardNumberFormatting();
        setupExpiryDateFormatting();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.card_payment));
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupListeners() {
        binding.btnPay.setOnClickListener(v -> validateAndPay());
    }

    // Форматирование номера карты (XXXX XXXX XXXX XXXX)
    private void setupCardNumberFormatting() {
        binding.etCardNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;

                isFormatting = true;
                String input = s.toString().replaceAll("\\s", "");

                if (input.length() > 16) {
                    input = input.substring(0, 16);
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < input.length(); i++) {
                    if (i > 0 && i % 4 == 0) {
                        formatted.append(" ");
                    }
                    formatted.append(input.charAt(i));
                }

                binding.etCardNumber.removeTextChangedListener(this);
                binding.etCardNumber.setText(formatted.toString());
                binding.etCardNumber.setSelection(formatted.length());
                binding.etCardNumber.addTextChangedListener(this);

                isFormatting = false;
            }
        });
    }

    // Автоформат даты MM/YY
    private void setupExpiryDateFormatting() {
        binding.etExpiryDate.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;

                isFormatting = true;
                String input = s.toString().replaceAll("/", "");

                if (input.length() > 4) {
                    input = input.substring(0, 4);
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < input.length(); i++) {
                    if (i == 2) {
                        formatted.append("/");
                    }
                    formatted.append(input.charAt(i));
                }

                // Ограничение месяца (не больше 12)
                if (formatted.length() >= 2) {
                    String monthStr = formatted.substring(0, 2);
                    int month = Integer.parseInt(monthStr);
                    if (month > 12) {
                        formatted = new StringBuilder("12" + formatted.substring(2));
                    }
                    if (month < 1 && formatted.length() >= 2) {
                        formatted = new StringBuilder("01" + formatted.substring(2));
                    }
                }

                binding.etExpiryDate.removeTextChangedListener(this);
                binding.etExpiryDate.setText(formatted.toString());
                binding.etExpiryDate.setSelection(formatted.length());
                binding.etExpiryDate.addTextChangedListener(this);

                isFormatting = false;
            }
        });
    }

    private void validateAndPay() {
        String cardNumber = binding.etCardNumber.getText().toString().trim().replaceAll("\\s", "");
        String expiryDate = binding.etExpiryDate.getText().toString().trim();
        String cvv = binding.etCvv.getText().toString().trim();
        String cardHolder = binding.etCardHolder.getText().toString().trim();

        if (TextUtils.isEmpty(cardNumber)) {
            binding.etCardNumber.setError(getString(R.string.card_number_required));
            return;
        }
        if (cardNumber.length() < 16) {
            binding.etCardNumber.setError(getString(R.string.invalid_card_number));
            return;
        }
        if (TextUtils.isEmpty(expiryDate)) {
            binding.etExpiryDate.setError(getString(R.string.expiry_required));
            return;
        }
        if (expiryDate.length() < 5) {
            binding.etExpiryDate.setError(getString(R.string.invalid_expiry));
            return;
        }

        // Проверка что дата не просрочена
        String[] parts = expiryDate.split("/");
        if (parts.length == 2) {
            int month = Integer.parseInt(parts[0]);
            int year = Integer.parseInt(parts[1]);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int currentMonth = cal.get(java.util.Calendar.MONTH) + 1;
            int currentYear = cal.get(java.util.Calendar.YEAR) % 100;

            if (year < currentYear || (year == currentYear && month < currentMonth)) {
                binding.etExpiryDate.setError(getString(R.string.card_expired));
                return;
            }
        }

        if (TextUtils.isEmpty(cvv)) {
            binding.etCvv.setError(getString(R.string.cvv_required));
            return;
        }
        if (cvv.length() < 3) {
            binding.etCvv.setError(getString(R.string.invalid_cvv));
            return;
        }
        if (TextUtils.isEmpty(cardHolder)) {
            binding.etCardHolder.setError(getString(R.string.card_holder_required));
            return;
        }

        // Имитация успешной оплаты
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

        new com.example.beathouse.utils.OrderManager(this).processOrderCreation(cartItems, "CARD", new com.example.beathouse.utils.OrderManager.OrderCallback() {
            @Override
            public void onAllOrdersCreated(java.util.List<com.example.beathouse.models.Order> createdOrders) {
                Intent intent = new Intent(CardPaymentActivity.this, BuyerOrderCompleteActivity.class);
                intent.putExtra("total", totalAmount);
                intent.putExtra("order_id", orderId);
                intent.putExtra("item_count", cartItems.size());
                intent.putExtra("order_items", itemsJson);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(CardPaymentActivity.this, error, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}