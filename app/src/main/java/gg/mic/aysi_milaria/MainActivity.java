package gg.mic.aysi_milaria;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.soundcloud.android.crop.Crop;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


public class MainActivity extends AppCompatActivity {

    private Interpreter tflite;
    private ArrayList<String> labelList = new ArrayList<String>();
    public Button execute_button, capture_button;
    private Uri imageUri;
    private ImageView imageView;

    public static final int REQUEST_IMAGE = 100;
    public static final int REQUEST_PERMISSION = 300;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getSupportActionBar().hide();

        init();
        execute_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println(tflite);
                openCameraIntent();

            }
        });

    }

    private void requestPermissions() {
        int PERMISSION_ALL = 1;
        String[] PERMISSIONS = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA
        };

        if (!Helper.hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void init(){

        labelList.add("Uninfected");
        labelList.add("Parasitized");

        try {
            tflite = new Interpreter(Helper.loadModelFile(this.getAssets()), new Interpreter.Options());
        } catch (IOException e) {
            e.printStackTrace();
        }

        imageView = (ImageView) findViewById(R.id.imageView);
        execute_button = (Button) findViewById(R.id.predict_button);
        execute_button.setText("Predict");

        requestPermissions();

    }

    private void openCameraIntent() {

        String[] PERMISSIONS = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA
        };

        if (!Helper.hasPermissions(this, PERMISSIONS)) requestPermissions();
        else{
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "New Picture");
            values.put(MediaStore.Images.Media.DESCRIPTION, "From your Camera");

            imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            startActivityForResult(intent, REQUEST_IMAGE);
        }

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        System.out.println("Picture Taken");

        if(requestCode == REQUEST_IMAGE && resultCode == RESULT_OK) {
            try {
                Uri source_uri = imageUri;
                Uri dest_uri = Uri.fromFile(new File(getCacheDir(), "cropped"));
                Crop.of(source_uri, dest_uri).asSquare().start(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        else if(requestCode == Crop.REQUEST_CROP && resultCode == RESULT_OK){
            classifyImage(Crop.getOutput(data));
        }
    }

    private void classifyImage(Uri uri) {
        int imageSizeX = Helper.DIM_IMG_SIZE_X;
        int imageSizeY = Helper.DIM_IMG_SIZE_Y;
        int imagePixelSize = Helper.DIM_PIXEL_SIZE;

        // initialize array that holds image data
        int[] imgArray = new int[imageSizeX * imageSizeY];
        System.out.println(imgArray.length);
        // initialize byte array.
        ByteBuffer imgData =  ByteBuffer.allocateDirect(4 * imageSizeX * imageSizeY * imagePixelSize);
        imgData.order(ByteOrder.nativeOrder());

        // initialize probabilities array.
        float[][] labelProbArray = new float[1][labelList.size()];

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            imageView.setImageBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // get current bitmap from imageView
        Bitmap bitmap_orig = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
        // resize the bitmap to the required input size to the CNN
        Bitmap bitmap = Helper.getResizedBitmap(bitmap_orig, imageSizeX, imageSizeY);
        // convert bitmap to byte array
        imgData = Helper.convertBitmapToByteBuffer(bitmap, imgData, imgArray);
        // pass byte data to the graph

        System.out.println(imgData);
        tflite.run(imgData, labelProbArray);

    }


}