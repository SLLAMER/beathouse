package com.example.beathouse.activities;
import com.example.beathouse.R;
import com.example.beathouse.App;
import com.example.beathouse.BaseActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.beathouse.adapters.ChatAdapter;
import com.example.beathouse.models.Message;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.util.Log;

public class ChatActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private EditText etMessage;
    private ImageButton btnSend;
    private View progressBar;

    private ChatAdapter adapter;
    private List<Message> messagesList = new ArrayList<>();
    private FirebaseFirestore db;
    private String currentUserId;
    private String otherUserId;
    private String otherUserName;
    private String chatId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        otherUserId = getIntent().getStringExtra("user_id");
        otherUserName = getIntent().getStringExtra("user_name");

        if (otherUserId == null) {
            Toast.makeText(this, "Error: User not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Создаем уникальный ID чата (сортируем ID для одинакового ключа)
        chatId = currentUserId.compareTo(otherUserId) < 0
                ? currentUserId + "_" + otherUserId
                : otherUserId + "_" + currentUserId;

        initViews();
        setupToolbar();
        setupRecyclerView();
        loadMessages();
        setupSendButton();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(otherUserName != null ? otherUserName : "Chat");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter(messagesList, currentUserId);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadMessages() {
        progressBar.setVisibility(View.VISIBLE);

        db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Error loading messages: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    messagesList.clear();
                    if (snapshots != null) {
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Message message = Message.fromMap(doc.getData());
                            if (message != null) {
                                messagesList.add(message);
                            }
                        }
                    }

                    adapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);

                    // Прокручиваем вниз
                    if (messagesList.size() > 0) {
                        recyclerView.scrollToPosition(messagesList.size() - 1);
                    }
                });
    }

    private void setupSendButton() {
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String messageText = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) return;

        etMessage.setText("");

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderId", currentUserId);
        messageData.put("receiverId", otherUserId);
        messageData.put("text", messageText);
        messageData.put("timestamp", System.currentTimeMillis());
        messageData.put("read", false);

        db.collection("chats").document(chatId)
                .collection("messages")
                .add(messageData)
                .addOnSuccessListener(docRef -> {
                    // Обновляем последнее сообщение в метаданных чата
                    updateChatMetadata(messageText);
                    // Отправляем уведомление
                    sendNotification(messageText);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error sending message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    etMessage.setText(messageText);
                });
    }

    private void updateChatMetadata(String lastMessage) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("lastMessage", lastMessage);
        metadata.put("lastMessageTime", System.currentTimeMillis());
        metadata.put("lastMessageSender", currentUserId);

        db.collection("chats").document(chatId)
                .set(metadata)
                .addOnFailureListener(e -> Log.e("Chat", "Error updating metadata: " + e.getMessage()));
    }

    private void sendNotification(String message) {
        // Получаем имя отправителя
        db.collection("users").document(currentUserId).get()
                .addOnSuccessListener(doc -> {
                    String senderName = doc.getString("username");
                    if (senderName == null) senderName = "Someone";

                    // Создаем уведомление
                    com.google.firebase.firestore.DocumentReference notifRef = db.collection("notifications").document();
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("notificationId", notifRef.getId());
                    notification.put("userId", otherUserId);
                    notification.put("type", "message");
                    notification.put("title", "New message from " + senderName);
                    notification.put("message", message.length() > 50 ? message.substring(0, 50) + "..." : message);
                    notification.put("chatId", chatId);
                    notification.put("senderId", currentUserId);
                    notification.put("senderName", senderName); // ✅ Добавляем имя отправителя
                    notification.put("read", false);
                    notification.put("createdAt", System.currentTimeMillis());

                    notifRef.set(notification);
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Отмечаем сообщения как прочитанные
        markMessagesAsRead();
    }

    private void markMessagesAsRead() {
        db.collection("chats").document(chatId)
                .collection("messages")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        doc.getReference().update("read", true);
                    }
                });
    }
}