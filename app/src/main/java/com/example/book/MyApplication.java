package com.example.book;


import static com.example.book.Constants.MAX_BYTES_PDF;

import android.app.Activity;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnErrorListener;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MyApplication extends Application {
    public static  final  String TAG_DOWNLOAD ="MY";

    @Override
    public void onCreate() {
        super.onCreate();
    }
    //created a static method to convert timestamp to proper date format,so we can use it everywhere in project, no need to rewirte
    public  static final String formatTimestamp(long timestamp){
        Calendar calendar = Calendar.getInstance(Locale.ENGLISH);
        calendar.setTimeInMillis(timestamp);

        //format timestamp to dd/MM/yyyy
        String date = DateFormat.format("dd/MM/yyyy",calendar).toString();
        return date;
    }

    //xóa sách
    public static void deleteBook(Context context, String bookId, String bookUrl, String bookTitle) {
        String TAG = "DELETE_BOOK_TAG";

        Log.d(TAG, "deleteBook: Xóa...");
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("Vui lòng đợi");
        progressDialog.setMessage("Xóa "+bookTitle+"...");//e.g. Deleting Book
        progressDialog.show();

        Log.d(TAG, "deleteBook: Xóa khỏi bộ nhớ lưu trữ...");
        StorageReference storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(bookUrl);
        storageReference.delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG, "onSuccess: Xóa khỏi bộ nhớ");
                        Log.d(TAG, "onSuccess: Hiện đang xóa cơ sở dữ liệu biểu mẫu thông tin");
                        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Books");
                        reference.child(bookId)
                                .removeValue()
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        Log.d(TAG, "onSuccess: Xóa database sách thành công");
                                        progressDialog.dismiss();
                                        Toast.makeText(context,"Xóa sách thành công",Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.d(TAG, "onSuccess: Xóa database sách không thành công");
                                        progressDialog.dismiss();
                                        Toast.makeText(context,""+e.getMessage(),Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG, "onSuccess: Xóa khỏi bộ nhớ không thành công do"+e.getMessage());
                        progressDialog.dismiss();
                        Toast.makeText(context,""+e.getMessage(),Toast.LENGTH_SHORT).show();
                    }
                });

    }

    //xóa báo cáo sách
    public static void deleteReportBook(Context context, String reportId, String name, String email, String bookTitle, String reason) {
        String TAG = "DELETE_REPORT_TAG";

        Log.d(TAG, "deleteBook: Xóa...");
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("Vui lòng đợi");
        progressDialog.setMessage("Xóa "+reason+"...");//e.g. Deleting Book
        progressDialog.show();

        Log.d(TAG, "deleteBook: Xóa khỏi bộ nhớ lưu trữ...");
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("ReportBook");
        reference.child(reportId).removeValue()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.d(TAG, "onSuccess: Xóa thành công");
                        progressDialog.dismiss();
                        Toast.makeText(context, "Xóa thành công", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG, "onFailure: Lỗi "+e.getMessage());
                        progressDialog.dismiss();
                        Toast.makeText(context, "Lỗi: "+e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });

    }

    //size pdf
    public static void loadPdfSize(String pdfUrl, String pdfTitle, TextView sizeTv) {
        String TAG = "PDF_SIZE_CLOUDINARY";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(pdfUrl)
                .head() // chỉ lấy header, không tải file
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Lỗi khi lấy size: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Phản hồi lỗi: " + response.code());
                    return;
                }

                long bytes = 0;
                String contentLength = response.header("Content-Length");
                if (contentLength != null) {
                    try {
                        bytes = Long.parseLong(contentLength);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Parse Content-Length lỗi: " + e.getMessage());
                    }
                }

                long finalBytes = bytes;
                ((Activity) sizeTv.getContext()).runOnUiThread(() -> {
                    double kb = finalBytes / 1024.0;
                    double mb = kb / 1024.0;

                    if (mb >= 1) {
                        sizeTv.setText(String.format("%.2f MB", mb));
                    } else if (kb >= 1) {
                        sizeTv.setText(String.format("%.2f KB", kb));
                    } else {
                        sizeTv.setText(finalBytes + " bytes");
                    }

                    Log.d(TAG, "onSuccess: " + pdfTitle + " " + finalBytes + " bytes");
                });
            }
        });
    }


    //load url pdf
    public static void loadPdfFromUrlSinglePage(String pdfUrl, String pdfTitle,
                                             PDFView pdfView, ProgressBar progressBar, TextView pagesTv) {
        String TAG = "PDF_LOAD_CLOUDINARY";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(pdfUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                ((Activity) pdfView.getContext()).runOnUiThread(() -> {
                    progressBar.setVisibility(View.INVISIBLE);
                    Toast.makeText(pdfView.getContext(), "Tải PDF thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                Log.e(TAG, "Download thất bại: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    ((Activity) pdfView.getContext()).runOnUiThread(() -> {
                        progressBar.setVisibility(View.INVISIBLE);
                        Toast.makeText(pdfView.getContext(), "Lỗi khi tải PDF (code " + response.code() + ")", Toast.LENGTH_SHORT).show();
                    });
                    Log.e(TAG, "Phản hồi lỗi: " + response.code());
                    return;
                }

                byte[] bytes = response.body().bytes();

                ((Activity) pdfView.getContext()).runOnUiThread(() -> {
                    pdfView.fromBytes(bytes)
                            .pages(0) // chỉ trang đầu
                            .spacing(0)
                            .swipeHorizontal(false)
                            .enableSwipe(false)
                            .onError(t -> {
                                progressBar.setVisibility(View.INVISIBLE);
                                Log.e(TAG, "onError: " + t.getMessage());
                            })
                            .onPageError((page, t) -> {
                                progressBar.setVisibility(View.INVISIBLE);
                                Log.e(TAG, "onPageError: " + t.getMessage());
                            })
                            .onLoad(nbPages -> {
                                progressBar.setVisibility(View.INVISIBLE);
                                Log.d(TAG, "PDF load thành công từ Cloudinary");

                                if (pagesTv != null) {
                                    pagesTv.setText("" + nbPages);
                                }
                            })
                            .load();
                });
            }
        });
    }


    //hiển thị danh mục sách
    public static void loadCategory(String categoryId, TextView categoryTv) {
        //get category using categoryId

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Categories");
        ref.child(categoryId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                //get category
                String category = ""+snapshot.child("category").getValue();

                //set category text view
                categoryTv.setText(category);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    //view sách
    public static void incrementBookViewCount(String bookId){
        //1) Get book view count
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Books");
        reference.child(bookId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        //get views count
                        String viewsCount = ""+snapshot.child("viewsCount").getValue();
                        //in case of null replace with 0
                        if (viewsCount.equals("")|| viewsCount.equals("null")){
                            viewsCount = "0";
                        }

                        //2) Increment views count
                        long newViewsCount = Long.parseLong(viewsCount) + 1;

                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put("viewsCount", newViewsCount);

                        DatabaseReference reference1 = FirebaseDatabase.getInstance().getReference("Books");
                        reference1.child(bookId)
                                .updateChildren(hashMap);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
    }



    // Download PDF từ Cloudinary URL hoặc bất kỳ URL nào
    public static void downloadBook(Context context, String bookId, String bookTitle, String bookUrl) {
        Log.d(TAG_DOWNLOAD, "downloadBookFromUrl: tải xuống sách từ URL: " + bookUrl);

        String nameWithExtension = bookTitle + ".pdf";

        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle("Vui lòng đợi");
        progressDialog.setMessage("Đang tải xuống " + nameWithExtension + "...");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        new AsyncTask<String, Void, byte[]>() {
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
                    Log.e(TAG_DOWNLOAD, "doInBackground: Lỗi tải file -> " + e.getMessage());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(byte[] bytes) {
                super.onPostExecute(bytes);

                if (bytes != null) {
                    saveDownloadedBook(context, progressDialog, bytes, nameWithExtension, bookId);
                } else {
                    progressDialog.dismiss();
                    Toast.makeText(context, "Không tải được file", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(bookUrl);
    }

    //lưu book
    private static void saveDownloadedBook(Context context, ProgressDialog progressDialog, byte[] bytes, String nameWithExtension, String bookId) {
        Log.d(TAG_DOWNLOAD, "onSuccess: Lưu sách đã tải xuống");

        try {
            OutputStream outputStream;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ dùng MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, nameWithExtension);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                outputStream = context.getContentResolver().openOutputStream(uri);
            } else {
                // Android 9 trở xuống
                File downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsFolder.exists()) {
                    downloadsFolder.mkdirs();
                }
                File file = new File(downloadsFolder, nameWithExtension);
                outputStream = new FileOutputStream(file);
            }

            // Ghi dữ liệu ra file
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();

            Log.d(TAG_DOWNLOAD, "saveDownloadedBook: Lưu thành công -> " + nameWithExtension);
            Toast.makeText(context, "Đã lưu vào thư mục Download", Toast.LENGTH_SHORT).show();

            progressDialog.dismiss();

            incrementBookDownloadCount(bookId);
        } catch (Exception e) {
            Log.e(TAG_DOWNLOAD, "saveDownloadedBook: Lỗi lưu file -> " + e.getMessage());
            Toast.makeText(context, "Lỗi lưu file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            progressDialog.dismiss();
        }
    }


    //tăng số lượng tải sách
    private static void incrementBookDownloadCount(String bookId) {
        Log.d(TAG_DOWNLOAD, "incrementBookDownloadCount: Tăng số lượng tải sách");

        //step 1) get previous download count
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("Books");
        reference.child(bookId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String downloadsCount = ""+snapshot.child("downloadsCount").getValue();
                        Log.d(TAG_DOWNLOAD, "onDataChange: Số lượng tải xuống file: "+downloadsCount);

                        if (downloadsCount.equals("") || downloadsCount.equals("null")){
                            downloadsCount = "0";
                        }

                        //convert to long and increment 1
                        long newDownloadsCount = Long.parseLong(downloadsCount) + 1;
                        Log.d(TAG_DOWNLOAD, "onDataChange: New Download Count: "+newDownloadsCount);

                        //setup data to update
                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put("downloadsCount", newDownloadsCount);

                        //step 2) update new incremented downloads count to db
                        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Books");
                        ref.child(bookId).updateChildren(hashMap)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void unused) {
                                        Log.d(TAG_DOWNLOAD, "onSuccess: Đã cập nhật số lượng tải xuống");

                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.d(TAG_DOWNLOAD, "onFailure: Cập nhật số lượng tải xuống thất bại do: "+e.getMessage());
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                    }
                });
    }


    // thêm yêu thích
    public static void addFavorite(Context context, String bookId){
        //we can add only if user is logger in
        //1) check if user is logged in
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        if (firebaseAuth.getCurrentUser() == null){
            //not logged in, cant add to fav
            Toast.makeText(context, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
        }
        else {
            long timestamp = System.currentTimeMillis();

            //setup data to in firebase db of current user for favorite book
            HashMap<String, Object> hashMap = new HashMap<>();
            hashMap.put("bookId",""+bookId);
            hashMap.put("timestamp",""+timestamp);

            //save to db
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users");
            ref.child(firebaseAuth.getUid()).child("Favorites").child(bookId)
                    .setValue(hashMap)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            Toast.makeText(context, "Thêm sách vào mục yêu thích thành công", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(context, "Lỗi thêm vào mục yêu thích "+e.getMessage(), Toast.LENGTH_SHORT).show();

                        }
                    });
        }
    }

    //xóa khỏi mục yêu thích
    public static void removeFavorite(Context context, String bookId){
        //we can add only if user is logger in
        //1) check if user is logged in
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        if (firebaseAuth.getCurrentUser() == null){
            //not logged in, cant remove from  fav
            Toast.makeText(context, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
        }
        else {
            //remove from db
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users");
            ref.child(firebaseAuth.getUid()).child("Favorites").child(bookId)
                    .removeValue()
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            Toast.makeText(context, "Xóa sách ở mục yêu thích thành công", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(context, "Xóa không được khỏi mục yêu thích "+e.getMessage(), Toast.LENGTH_SHORT).show();

                        }
                    });
        }
    }
}
