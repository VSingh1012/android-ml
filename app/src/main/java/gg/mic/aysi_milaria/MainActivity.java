package gg.mic.aysi_milaria;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.soundcloud.android.crop.Crop;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import gg.mic.aysi_milaria.ml.NewModel;


public class MainActivity extends AppCompatActivity {

    PopupWindow popUp;
    public Button execute_button;
    public ImageButton capture_button;
    private Uri imageUri;
    private ImageView imageView;
    private Bitmap img;

    public static final int REQUEST_IMAGE = 100;
    public static final int REQUEST_CAMERA_ROLL = 3645;
    public static final int REQUEST_PERMISSION = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getSupportActionBar().hide();

        init();
        execute_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCameraIntent();

            }
        });

        capture_button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                openStorageIntent();
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

    private void init(){

        imageView = (ImageView) findViewById(R.id.imageView);
        execute_button = (Button) findViewById(R.id.predict_button);
        execute_button.setText("Predict");

        capture_button = (ImageButton) findViewById(R.id.capture_image);

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

    private void openStorageIntent(){
        Intent intent = new Intent((Intent.ACTION_GET_CONTENT));
        intent.setType("image/*");

        startActivityForResult(intent, REQUEST_CAMERA_ROLL);


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){

        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == REQUEST_IMAGE && resultCode == RESULT_OK && imageUri != null) {
            try {
                Uri source_uri = imageUri;
                Uri dest_uri = Uri.fromFile(new File(getCacheDir(), "cropped"));
                Crop.of(source_uri, dest_uri).asSquare().start(this);
                img = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if(requestCode == REQUEST_CAMERA_ROLL && resultCode == RESULT_OK){


            imageView.setImageURI(data.getData());
            Uri source_uri = data.getData();
            Uri dest_uri = Uri.fromFile(new File(getCacheDir(), "cropped"));
            Crop.of(source_uri, dest_uri).asSquare().start(this);

            try {
                img = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);

            } catch (Exception e) {
                e.printStackTrace();
            }


        }


        if(requestCode == Crop.REQUEST_CROP && resultCode == RESULT_OK){
            imageView.setImageURI(Crop.getOutput(data));
            classifyImage(Crop.getOutput(data));
        }
    }

    private void classifyImage(Uri uri) {
        TensorBuffer outputFeature0 = null;

        try {
            img = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
            img = Bitmap.createScaledBitmap(img, 224, 224, true);

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            NewModel model = NewModel.newInstance(getApplicationContext());
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);

            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
            tensorImage.load(img);
            ByteBuffer byteBuffer = tensorImage.getBuffer();

            inputFeature0.loadBuffer(byteBuffer);
            NewModel.Outputs outputs = model.process(inputFeature0);
            outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            model.close();

            System.out.println(outputFeature0.getFloatArray()[0] + " | " + outputFeature0.getFloatArray()[1]);


        } catch (IOException e) {
            System.out.println(e);
        }

        onButtonShowPopupWindowClick(findViewById(android.R.id.content).getRootView(), outputFeature0.getFloatArray()[0], outputFeature0.getFloatArray()[1]);

    }
    public void onButtonShowPopupWindowClick(View view, float uninfected_val, float infected_val) {


        LayoutInflater inflater = (LayoutInflater)
        getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.result, null);

        int width = view.getWidth();
        int height = view.getHeight();
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

        Button back_button = (Button) popupView.findViewById(R.id.back_button);

        TextView uninfected = (TextView) popupView.findViewById(R.id.textView3);
        TextView infected = (TextView) popupView.findViewById(R.id.textView2);

        TextView label = (TextView) popupView.findViewById(R.id.res);
        label.setTextColor((uninfected_val > infected_val) ? Color.parseColor("#5AC934") : Color.parseColor("#C54C32"));

        Snackbar snackbar = Snackbar.make(view, "Malaria Infection Results", Snackbar.LENGTH_LONG);
        snackbar.show();

        uninfected.setText(String.valueOf(uninfected_val * 100) + "%");
        infected.setText(String.valueOf(infected_val * 100) + "%");

        back_button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();

            }
        });
    }



}