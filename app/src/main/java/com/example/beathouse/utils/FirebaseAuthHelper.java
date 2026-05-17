package com.example.beathouse.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class FirebaseAuthHelper {
    private static final String TAG = "FirebaseAuthHelper";
    private final FirebaseAuth mAuth;
    private final Context context;

    public FirebaseAuthHelper(Context context) {
        this.mAuth = FirebaseAuth.getInstance();
        this.context = context;
    }

    public interface AuthCallback {
        void onSuccess();
        void onError(String error);
    }

    public void registerUser(String email, String password, String username, AuthCallback callback) {
        Log.d(TAG, "Starting registration for: " + email);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener((Activity) context, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Registration successful, sending verification email");
                            // Send email verification
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                user.sendEmailVerification()
                                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if (task.isSuccessful()) {
                                                    Log.d(TAG, "Verification email sent");
                                                    callback.onSuccess();
                                                } else {
                                                    String errorMsg = "Failed to send verification email: " + task.getException().getMessage();
                                                    Log.e(TAG, errorMsg);
                                                    callback.onError(errorMsg);
                                                }
                                            }
                                        });
                            } else {
                                callback.onError("User creation failed");
                            }
                        } else {
                            String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Log.e(TAG, "Registration failed: " + error);

                            if (error.contains("email address is already in use")) {
                                callback.onError("This email is already registered");
                            } else if (error.contains("password is invalid") || error.contains("Weak password")) {
                                callback.onError("Password should be at least 6 characters");
                            } else if (error.contains("badly formatted")) {
                                callback.onError("Please enter a valid email address");
                            } else {
                                callback.onError("Registration failed: " + error);
                            }
                        }
                    }
                });
    }

    public void loginUser(String email, String password, AuthCallback callback) {
        Log.d(TAG, "Attempting login for: " + email);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener((Activity) context, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Login successful");
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                if (user.isEmailVerified()) {
                                    callback.onSuccess();
                                } else {
                                    Log.d(TAG, "Email not verified");
                                    // Don't sign out, let user resend verification
                                    callback.onError("Please verify your email first. Check your inbox.");
                                }
                            }
                        } else {
                            String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Log.e(TAG, "Login failed: " + error);

                            if (error.contains("invalid credential") || error.contains("wrong password")) {
                                callback.onError("Invalid email or password");
                            } else if (error.contains("user not found")) {
                                callback.onError("No account found with this email");
                            } else if (error.contains("badly formatted")) {
                                callback.onError("Please enter a valid email address");
                            } else {
                                callback.onError("Login failed: " + error);
                            }
                        }
                    }
                });
    }

    public void sendPasswordResetEmail(String email, AuthCallback callback) {
        Log.d(TAG, "Sending password reset email to: " + email);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Password reset email sent successfully");
                            callback.onSuccess();
                        } else {
                            String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Log.e(TAG, "Password reset failed: " + error);

                            if (error.contains("user not found")) {
                                callback.onError("No account found with this email address");
                            } else if (error.contains("badly formatted")) {
                                callback.onError("Please enter a valid email address");
                            } else {
                                callback.onError("Failed to send reset email: " + error);
                            }
                        }
                    }
                });
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public void signOut() {
        mAuth.signOut();
        Log.d(TAG, "User signed out");
    }

    public boolean isEmailVerified() {
        FirebaseUser user = mAuth.getCurrentUser();
        return user != null && user.isEmailVerified();
    }
}