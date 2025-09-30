package com.example.book.Activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.book.Constants;
import com.example.book.databinding.ActivityPdfViewBinding;
import com.github.barteksc.pdfviewer.listener.OnErrorListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PdfViewActivity extends AppCompatActivity {

    private ActivityPdfViewBinding binding;

    private String bookId;

    private static final String TAG="PDF_VIEW_TAG";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPdfViewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //get bookId from intent
        Intent intent = getIntent();
        bookId = intent.getStringExtra("bookId");
        Log.d(TAG, "onCreate: BookId:"+bookId);

        loadBookDetails();

        binding.backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    private void loadBookDetails() {
        //Database Reference to get book detail
        //Step (1) get book url using book id
        Log.d(TAG, "loadBookDetails: Lấy url pdf database...");
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Books");
        ref.child(bookId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        //get book url
                        String pdfUrl = ""+snapshot.child("url").getValue();
                        Log.d(TAG, "onDataChange: PDF URL "+pdfUrl);
                        Toast.makeText(PdfViewActivity.this, "PDF URL"+pdfUrl, Toast.LENGTH_SHORT).show();

                        //step (2) load pdf using that url from firebase storage
                        loadBookFromUrl(pdfUrl);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
    }

    // Load PDF từ URL bất kỳ (Cloudinary, server custom, v.v)
    @SuppressLint("StaticFieldLeak")
    private void loadBookFromUrl(String pdfUrl) {
        Log.d(TAG, "loadBookFromUrl: File PDF từ URL Cloudinary: " + pdfUrl);

        new AsyncTask<String, Void, byte[]>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                binding.progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            protected byte[] doInBackground(String... strings) {
                try {
                    URL url = new URL(strings[0]);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] data = new byte[1024];
                    int n;
                    while ((n = inputStream.read(data)) != -1) {
                        buffer.write(data, 0, n);
                    }
                    inputStream.close();
                    return buffer.toByteArray();
                } catch (Exception e) {
                    Log.e(TAG, "doInBackground: Lỗi tải PDF -> " + e.getMessage());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(byte[] bytes) {
                super.onPostExecute(bytes);
                binding.progressBar.setVisibility(View.GONE);

                if (bytes != null) {
                    binding.pdfView.fromBytes(bytes)
                            .swipeHorizontal(false) // dọc
                            .onPageChange((page, pageCount) -> {
                                int currentPage = page + 1;
                                binding.toolbarSubtitleTv.setText(currentPage + "/" + pageCount);
                                Log.d(TAG, "onPageChanged: " + currentPage + "/" + pageCount);
                            })
                            .onError(t -> {
                                Log.e(TAG, "onError: " + t.getMessage());
                                Toast.makeText(PdfViewActivity.this, "Lỗi PDF: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            })
                            .onPageError((page, t) -> {
                                Log.e(TAG, "onPageError: " + t.getMessage());
                                Toast.makeText(PdfViewActivity.this, "Trang " + page + " lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            })
                            .load();
                } else {
                    Toast.makeText(PdfViewActivity.this, "Không tải được PDF", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(pdfUrl);
    }

}