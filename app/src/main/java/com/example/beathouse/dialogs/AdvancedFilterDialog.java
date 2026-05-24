package com.example.beathouse.dialogs;
import com.example.beathouse.R;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import com.example.beathouse.R;

public class AdvancedFilterDialog extends Dialog {

    private Context context;
    private String currentSort;
    private String currentTag;
    private int currentMinBpm;
    private int currentMaxBpm;
    private OnFilterApplyListener listener;

    private RadioGroup rgSort;
    private RadioButton rbDefault, rbPriceAsc, rbPriceDesc, rkBpmAsc, rkBpmDesc;
    private EditText etTagSearch;
    private EditText etBpmMin, etBpmMax;
    private Button btnApply, btnClear, btnCancel;

    public interface OnFilterApplyListener {
        void onSortSelected(String sort);
        void onTagSearch(String tag);
        void onBpmRange(int minBpm, int maxBpm);
        void onClearFilters();
    }

    // ✅ Новый конструктор с BPM параметрами
    public AdvancedFilterDialog(Context context, String currentSort, String currentTag,
                                int currentMinBpm, int currentMaxBpm, OnFilterApplyListener listener) {
        super(context);
        this.context = context;
        this.currentSort = currentSort;
        this.currentTag = currentTag;
        this.currentMinBpm = currentMinBpm;
        this.currentMaxBpm = currentMaxBpm;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_advanced_filter);

        initViews();
        setupCurrentValues();
        setupListeners();
    }

    private void initViews() {
        rgSort = findViewById(R.id.rg_sort);
        rbDefault = findViewById(R.id.rb_default);
        rbPriceAsc = findViewById(R.id.rb_price_asc);
        rbPriceDesc = findViewById(R.id.rb_price_desc);
        rkBpmAsc = findViewById(R.id.rb_bpm_asc);
        rkBpmDesc = findViewById(R.id.rb_bpm_desc);
        etTagSearch = findViewById(R.id.et_tag_search);
        etBpmMin = findViewById(R.id.et_bpm_min);
        etBpmMax = findViewById(R.id.et_bpm_max);
        btnApply = findViewById(R.id.btn_apply);
        btnClear = findViewById(R.id.btn_clear);
        btnCancel = findViewById(R.id.btn_cancel);
    }

    private void setupCurrentValues() {
        switch (currentSort) {
            case "price_asc":
                rbPriceAsc.setChecked(true);
                break;
            case "price_desc":
                rbPriceDesc.setChecked(true);
                break;
            case "bpm_asc":
                rkBpmAsc.setChecked(true);
                break;
            case "bpm_desc":
                rkBpmDesc.setChecked(true);
                break;
            default:
                rbDefault.setChecked(true);
                break;
        }

        if (currentTag != null && !currentTag.isEmpty()) {
            etTagSearch.setText(currentTag);
        }

        if (currentMinBpm > 0) {
            etBpmMin.setText(String.valueOf(currentMinBpm));
        }
        if (currentMaxBpm > 0) {
            etBpmMax.setText(String.valueOf(currentMaxBpm));
        }
    }

    private void setupListeners() {
        btnApply.setOnClickListener(v -> {
            String selectedSort = getSelectedSort();
            String tag = etTagSearch.getText().toString().trim().toLowerCase();

            int minBpm = -1;
            int maxBpm = -1;

            String bpmMinStr = etBpmMin.getText().toString().trim();
            String bpmMaxStr = etBpmMax.getText().toString().trim();

            if (!bpmMinStr.isEmpty()) {
                try {
                    minBpm = Integer.parseInt(bpmMinStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(context, "Invalid minimum BPM", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (!bpmMaxStr.isEmpty()) {
                try {
                    maxBpm = Integer.parseInt(bpmMaxStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(context, "Invalid maximum BPM", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            if (minBpm > 0 && maxBpm > 0 && minBpm > maxBpm) {
                Toast.makeText(context, "Min BPM cannot be greater than Max BPM", Toast.LENGTH_SHORT).show();
                return;
            }

            if (listener != null) {
                listener.onSortSelected(selectedSort);
                listener.onTagSearch(tag);
                listener.onBpmRange(minBpm, maxBpm);
            }

            Toast.makeText(context, "Filters applied", Toast.LENGTH_SHORT).show();
            dismiss();
        });

        btnClear.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClearFilters();
            }
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());
    }

    private String getSelectedSort() {
        int checkedId = rgSort.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_price_asc) return "price_asc";
        if (checkedId == R.id.rb_price_desc) return "price_desc";
        if (checkedId == R.id.rb_bpm_asc) return "bpm_asc";
        if (checkedId == R.id.rb_bpm_desc) return "bpm_desc";
        return "default";
    }
}