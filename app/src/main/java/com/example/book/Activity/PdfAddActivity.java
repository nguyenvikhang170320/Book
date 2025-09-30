package com.example.book.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.book.databinding.ActivityPdfAddBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.cloudinary.json.JSONException;
import org.cloudinary.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class PdfAddActivity extends AppCompatActivity {
    //binding
    private ActivityPdfAddBinding binding;

    //firebase auth
    private FirebaseAuth firebaseAuth;

    //progress dialog
    private ProgressDialog progressDialog;

    //arraylist pdf categories
    private ArrayList<String> categoryTitleArrayList,categoryIdArrayList;
    private ArrayList<String> majorArraylist, majorUserId;

    //uri of picked
    private Uri pdfUri = null;

    private static final int PDF_PICK_CODE = 1000;

    //tag for debugging
    private static final String TAG = "ADD_PDF_TAG";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPdfAddBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //init firebase auth
        firebaseAuth = FirebaseAuth.getInstance();
        loadPdfCategories();

        //setup progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Vui lòng đợi trong giây lát");
        progressDialog.setCanceledOnTouchOutside(false);

        //handle click, go to previous activity
        binding.backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        //handle click, attach pdf
        binding.attachBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pdfPickIntent();
            }
        });

        //handle click, pick category
        binding.categoryTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                categoryPickDialog();
            }
        });

        //handle click, upload pdf
        binding.submitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //validate data
                validateData();
            }
        });

    }


    private String title = "", decscription = "";
    private String author = "";
    private String year = "";
    private String major = "";

    private void validateData() {
        //step1: Validate data


        //get data
        title = binding.titleEt.getText().toString().trim();
        decscription = binding.decscriptionEt.getText().toString().trim();
        author = binding.authorEt.getText().toString().trim();
        year = binding.yearEt.getText().toString().trim();
        major = binding.majorEt.getText().toString().trim();



        //validate data
        if (TextUtils.isEmpty(title)){
            Toast.makeText(this, "Vui lòng nhập tiêu đề...", Toast.LENGTH_SHORT).show();
        }
        else if (TextUtils.isEmpty(decscription))
        {
            Toast.makeText(this, "Vui lòng nhập mô tả...", Toast.LENGTH_SHORT).show();
        }
        else if (TextUtils.isEmpty(author))
        {
            Toast.makeText(this, "Vui lòng nhập tác giả...", Toast.LENGTH_SHORT).show();
        }
        else if (TextUtils.isEmpty(year))
        {
            Toast.makeText(this, "Vui lòng nhập năm xuất bản...", Toast.LENGTH_SHORT).show();
        }

        else if (TextUtils.isEmpty(selectedCategoryTitle))
        {
            Toast.makeText(this, "Vui lòng chọn thể loại...", Toast.LENGTH_SHORT).show();
        }
        else if (TextUtils.isEmpty(major))
        {
            Toast.makeText(this, "Vui lòng chọn chuyên ngành - lĩnh vực...", Toast.LENGTH_SHORT).show();
        }
        else if (pdfUri==null){
            Toast.makeText(this, "Vui lòng chọn file PDF...", Toast.LENGTH_SHORT).show();
        }
        else {
            uploadPdfToStorage();
        }
    }

    private void uploadPdfToStorage() {
        if (pdfUri == null) {
            Toast.makeText(this, "Bạn chưa chọn PDF!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.setMessage("Đang upload PDF...");
        progressDialog.show();

        String cloudName = "da95oqe1j"; // Cloud name của bạn
        String uploadPreset = "ml_default2"; // Upload preset unsigned bạn đã tạo
        String url = "https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload";
        // Dùng auto để Cloudinary tự nhận diện (pdf, image, video...)

        try {
            // Đọc file thành byte[]
            InputStream inputStream = getContentResolver().openInputStream(pdfUri);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] tmp = new byte[1024];
            int n;
            while ((n = inputStream.read(tmp)) != -1) {
                buffer.write(tmp, 0, n);
            }
            byte[] fileBytes = buffer.toByteArray();
            inputStream.close();

            // Tạo multipart request
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "upload.pdf",
                            RequestBody.create(fileBytes, MediaType.parse("application/pdf")))
                    .addFormDataPart("upload_preset", uploadPreset)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            OkHttpClient client = new OkHttpClient();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(PdfAddActivity.this,
                                "Upload thất bại: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBody = response.body().string();
                    Log.d("CloudinaryUpload", "Response: " + responseBody);

                    if (response.isSuccessful()) {
                        try {
                            JSONObject json = new JSONObject(responseBody);
                            String uploadedUrl = json.getString("secure_url");

                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(PdfAddActivity.this,
                                        "Upload thành công!",
                                        Toast.LENGTH_SHORT).show();

                                // TODO: Gọi hàm lưu DB
                                uploadPdfInfoToDb(uploadedUrl, System.currentTimeMillis());
                            });
                        } catch (JSONException e) {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(PdfAddActivity.this,
                                        "Parse JSON lỗi: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(PdfAddActivity.this,
                                    "Upload lỗi: " + response.code() + " - " + responseBody,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                }
            });

        } catch (Exception e) {
            progressDialog.dismiss();
            Toast.makeText(this, "Lỗi đọc file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void uploadPdfInfoToDb(String uploadedPdfUrl, long timestamp) {
        //step3: Upload Pdf to firebase database
        Log.d(TAG,"uploadPdfToStorage: Tải thông tin pdf lên firebase database....");

        progressDialog.setMessage("Tải thông tin pdf");

        String uid = firebaseAuth.getUid();
        // id firebase tự sinh ra và ta lấy id đó xuống và lưu lại
        String id = FirebaseDatabase.getInstance().getReference().push().getKey();

        //setup data to upload
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("uid", ""+uid);
        hashMap.put("id", ""+id);
        hashMap.put("title", ""+title);
        hashMap.put("decscription", ""+decscription);
        hashMap.put("author", ""+author);
        hashMap.put("year", ""+year);
        hashMap.put("major", ""+major);
        hashMap.put("categoryId", ""+selectedCategoryId);
        hashMap.put("url", ""+uploadedPdfUrl);
        hashMap.put("timestamp", timestamp);
        hashMap.put("viewsCount", 0);
        hashMap.put("downloadsCount", 0);

        //db reference: DB > Books
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Books");
        ref.child(""+id)
                .setValue(hashMap)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        progressDialog.dismiss();
                        Log.d(TAG, "onSuccess: Upload thành công");
                        Toast.makeText(PdfAddActivity.this, "Upload thành công ", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        progressDialog.dismiss();
                        Log.d(TAG, "onFailure: Upload không thành công do "+e.getMessage());
                        Toast.makeText(PdfAddActivity.this, "Upload không thành công do "+e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadPdfCategories() {
        Log.d(TAG, "loadPdfPickIntent: Đang tải các danh mục PDF....");
        categoryTitleArrayList = new ArrayList<>();
        categoryIdArrayList = new ArrayList<>();

        //db reference to load categories... db > Categories
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Categories");
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                categoryTitleArrayList.clear();
                categoryIdArrayList.clear();
                for (DataSnapshot ds: snapshot.getChildren()){
                    //get id and title of category
                    String categoryId = ""+ds.child("id").getValue();
                    String categoryTitle = ""+ds.child("category").getValue();


                    //add to respective arraylist
                    categoryTitleArrayList.add(categoryTitle);
                    categoryIdArrayList.add(categoryId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }


    //selected category id and category title
    private String selectedCategoryId, selectedCategoryTitle;
    private void categoryPickDialog() {
        Log.d(TAG, "categoryPickDialog: Hiển thị hộp thoại chọn danh mục...");
        //get string array of categories from arraylist
        String[] categoriesArray = new String[categoryTitleArrayList.size()];
        for (int i=0; i<categoryTitleArrayList.size(); i++){
            categoriesArray[i] = categoryTitleArrayList.get(i);
        }

        //alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn thể loại")
                .setItems(categoriesArray, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //handle item click
                        //get clicked item from list
                        selectedCategoryTitle = categoryTitleArrayList.get(which);
                        selectedCategoryId = categoryIdArrayList.get(which);
                        //set to category textview
                        binding.categoryTv.setText(selectedCategoryTitle);

                        Log.d(TAG, "onClick: Danh mục đã chọn "+selectedCategoryId+" "+selectedCategoryTitle);
                    }
                })
                .show();
    }

    private void pdfPickIntent() {
        Log.d(TAG, "pdfPickIntent: Bắt đầu chọn pdf");

        Intent intent = new Intent();
        intent.setType("application/pdf");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,"Vui lòng chọn PDF"),PDF_PICK_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK){
            if (requestCode == PDF_PICK_CODE){
                Log.d(TAG, "onActivityResult: PDF được chọn");

                pdfUri = data.getData();

                Log.d(TAG, "onActivityResult: URI: "+pdfUri);
            }
        }
        else {
            Log.d(TAG, "onActivityResult: Hủy PDF đã chọn");
            Toast.makeText(this, "Hủy PDF đã chọn", Toast.LENGTH_SHORT).show();
        }
    }
}