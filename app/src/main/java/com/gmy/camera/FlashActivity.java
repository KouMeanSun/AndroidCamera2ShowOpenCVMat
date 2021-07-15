package com.gmy.camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class FlashActivity extends AppCompatActivity implements View.OnClickListener{
    private static  final String TAG = "FlashActivity";

    private static final int PERMISSIONS_REQUEST_CODE = 12345;
    private Context mContext;
    private Button mStartBtn;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash);
        commonInit();
    }
    private void commonInit(){
        this.mContext = this;
        // first make sure the necessary permissions are given
        checkPermissionsIfNeccessary();

        this.initViews();
    }
    private void initViews(){
        this.mStartBtn = this.findViewById(R.id.btn_start_camera);
        this.mStartBtn.setOnClickListener(this);
    }
    /**
     * @return true if permissions where given
     */
    private boolean checkPermissionsIfNeccessary() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions != null) {
                List<String> permissionsNotGrantedYet = new ArrayList<>(info.requestedPermissions.length);
                for (String p : info.requestedPermissions) {
                    if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNotGrantedYet.add(p);
                    }
                }
                if (permissionsNotGrantedYet.size() > 0) {
                    ActivityCompat.requestPermissions(this, permissionsNotGrantedYet.toArray(new String[permissionsNotGrantedYet.size()]),
                            PERMISSIONS_REQUEST_CODE);
                    return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean hasAllPermissions = true;
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length == 0)
                hasAllPermissions = false;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    hasAllPermissions = false;
            }

            if (!hasAllPermissions) {
                Log.e(TAG,"没有足够权限!");
//                finish();
            }
        }
    }

    @Override
    public void onClick(View view) {
        Log.e(TAG,"view onClick ... ");
        Toast.makeText(mContext,"点击了按钮!",Toast.LENGTH_SHORT).show();
        if(view.getId() == R.id.btn_start_camera){
            startActivity(new Intent(this,MainActivity.class));
        }
    }
}
