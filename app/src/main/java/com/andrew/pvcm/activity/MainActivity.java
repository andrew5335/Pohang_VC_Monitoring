package com.andrew.pvcm.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.andrew.pvcm.R;
import com.andrew.pvcm.api.InsertLocation;
import com.andrew.pvcm.api.LocationJson;
import com.andrew.pvcm.handler.BackPressCloseHandler;
import com.andrew.pvcm.service.ApiService;

import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapReverseGeoCoder;
import net.daum.mf.map.api.MapView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements MapView.CurrentLocationEventListener, MapView.MapViewEventListener, MapReverseGeoCoder.ReverseGeoCodingResultListener, SensorEventListener {

    private static final String LOG_TAG = "MainActivity";

    // 앱 권한
    private static final int GPS_ENABLE_REQUEST_CODE = 2001;
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    String[] REQUIRED_PERMISSIONS  = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

    // 위치 정보
    LocationManager locationManager;
    GPSListener gpsListener;
    Timer timer;
    TimerTask timerTask;
    private Location location;
    private MapView mapView;
    private ViewGroup mapViewContainer;
    private MapReverseGeoCoder mReverseGeoCoder = null;
    private boolean isUsingCustomLocationMarker = false;

    // gyro & accelero sensor 정보
    private double RAD2DGR = 180 / Math.PI;
    private static final float NS2S = 1.0f/1000000000.0f;
    private SensorManager mSensorManager = null;
    private float speed;
    private long curTime;
    private long lastTime;
    private long gabTime;

    //private SensorEventListener mGyroLis;
    private Sensor mGyroSensor = null;
    private Sensor mAccSensor = null;
    private Sensor mLinearAccSensor = null;
    private double pitch, pitch2;
    private double roll, roll2;
    private double yaw, yaw2;
    private double timestamp;
    private double dt;
    private float gAccX, gAccY, gAccZ, accX, accY, accZ, lAccX, lAccY, lAccZ;
    private float tmpX, tmpY, tmpZ;
    private double gTotal, total, lTotal;
    private float alpha = 0.8f;

    // api 연동
    private ApiService apiService;
    private InsertLocation apiResponse;
    private List<LocationJson> locatoinJsonList;
    private String deviceId;

    // 뒤로가기 처리
    private BackPressCloseHandler backPressCloseHandler;

    // 갱신 주기 설정 (10초/3m)
    long minTime = 10000;    // 10초마다 갱신
    float minDistance = 5;    // 5m마다 갱신

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        gpsListener = new GPSListener();

        apiService = new ApiService();
        apiResponse = new InsertLocation();
        locatoinJsonList = new ArrayList<LocationJson>();
        deviceId = "12345";

        this.backPressCloseHandler = new BackPressCloseHandler(this);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mLinearAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        //mGyroLis = new GyroscopeListener();

        mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        mSensorManager.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        mSensorManager.registerListener(this, mLinearAccSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
    }

    public void onViewPathBtnClick(View v) {
        Toast.makeText(getApplicationContext(), "경로보기 버튼 클릭", Toast.LENGTH_LONG).show();
        Intent viewPathIntent = new Intent(MainActivity.this, ViewPathActivity.class);
        mapViewContainer.removeView(mapView);
        startActivity(viewPathIntent);

        close();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mapView = new MapView(this);
        mapView.setMapType(MapView.MapType.Standard);
        mapViewContainer = (ViewGroup) findViewById(R.id.map_view);
        mapViewContainer.addView(mapView);
        mapView.setMapViewEventListener(this);
        mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithHeading);

        if (!checkLocationServicesStatus()) {
            showDialogForLocationServiceSetting();
        } else {
            checkRunTimePermission();
        }

        Map<String, Object> curLoc = new HashMap<String, Object>();
        curLoc = getGpsInfo();

        if(null != curLoc && 0 < curLoc.size()) {
            double lng = (Double)curLoc.get("mapX");
            double lat = (Double)curLoc.get("mapY");

            mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(lat, lng), true);
            Toast.makeText(getApplicationContext(), "cur location : " + lat + "/" + lng, Toast.LENGTH_LONG).show();
        }

        mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        mSensorManager.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        mSensorManager.registerListener(this, mLinearAccSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);

        /**
        Map<String, Object> curLoc = new HashMap<String, Object>();
        curLoc = getGpsInfo();

        if(null != curLoc && 0 < curLoc.size()) {
            double lng = (Double)curLoc.get("mapX");
            double lat = (Double)curLoc.get("mapY");

            mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(lat, lng), true);
            Toast.makeText(getApplicationContext(), "cur location : " + lat + "/" + lng, Toast.LENGTH_LONG).show();


            // 현재 위치 DB 저장 처리
            new Thread(new Runnable() {
                @Override
                public void run() {
                    apiResponse = apiService.insertLocation(deviceId, Double.toString(lat), Double.toString(lng));

                    if(null != apiResponse) {
                        Log.i("Info", "insert result : " + apiResponse.getResultCode() + "/" + apiResponse.getResult());
                    }
                }
            }).start();
        }
         **/
    }

    public void close() {
        mapViewContainer.removeAllViews();
        //mSensorManager.unregisterListener(mGyroLis);
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void finish() {
        super.finish();
        mapViewContainer.removeView(mapView);
        //mSensorManager.unregisterListener(mGyroLis);
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //mSensorManager.unregisterListener(mGyroLis);
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //mSensorManager.unregisterListener(mGyroLis);
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onReverseGeoCoderFoundAddress(MapReverseGeoCoder mapReverseGeoCoder, String s) {

    }

    @Override
    public void onReverseGeoCoderFailedToFindAddress(MapReverseGeoCoder mapReverseGeoCoder) {

    }

    @Override
    public void onCurrentLocationUpdate(MapView mapView, MapPoint mapPoint, float v) {
        MapPoint.GeoCoordinate gps = mapPoint.getMapPointGeoCoord();
        Log.i(LOG_TAG, String.format("MapView onCurrentLocationUpdate (%f,%f) accuracy (%f)", gps.latitude, gps.longitude, v));
        mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(gps.latitude, gps.longitude), true);
        //Toast.makeText(getApplicationContext(), "===current location : " + gps.latitude + "/" + gps.longitude, Toast.LENGTH_LONG).show();

        // DB 입력 처리 - 현재 위치가 변경될때마다 변경된 위치의 좌표값을 DB에 저장
        /**
        new Thread(new Runnable() {
            @Override
            public void run() {
                apiResponse = apiService.insertLocation(deviceId, Double.toString(gps.latitude), Double.toString(gps.longitude));

                if(null != apiResponse) {
                    Log.i("Info", "insert result2 : " + apiResponse.getResultCode() + "/" + apiResponse.getResult());
                }
            }
        }).start();
         **/
    }

    @Override
    public void onCurrentLocationDeviceHeadingUpdate(MapView mapView, float v) {

    }

    @Override
    public void onCurrentLocationUpdateFailed(MapView mapView) {
        Toast.makeText(getApplicationContext(), "current location update fail", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCurrentLocationUpdateCancelled(MapView mapView) {

    }

    @Override
    public void onMapViewInitialized(MapView mapView) {

    }

    @Override
    public void onMapViewCenterPointMoved(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewZoomLevelChanged(MapView mapView, int i) {

    }

    @Override
    public void onMapViewSingleTapped(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDoubleTapped(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewLongPressed(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragStarted(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragEnded(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewMoveFinished(MapView mapView, MapPoint mapPoint) {

    }

    public void checkRunTimePermission() {

        int hasFineLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_COARSE_LOCATION);

        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED && hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED) {

        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, REQUIRED_PERMISSIONS[0])) {
                Toast.makeText(MainActivity.this, "이 앱을 실행하려면 위치 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS,
                        PERMISSIONS_REQUEST_CODE);
            } else if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, REQUIRED_PERMISSIONS[1])) {
                Toast.makeText(MainActivity.this, "required coarse locaiton permission", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS,
                        PERMISSIONS_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int permsRequestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grandResults) {

        if ( permsRequestCode == PERMISSIONS_REQUEST_CODE && grandResults.length == REQUIRED_PERMISSIONS.length) {

            boolean check_result = true;

            for (int result : grandResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    check_result = false;
                    break;
                }
            }

            if ( check_result ) {
                Log.d("@@@", "start");
                //위치 값을 가져올 수 있음

            }
            else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])) {
                    Toast.makeText(MainActivity.this, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요.", Toast.LENGTH_LONG).show();
                    finish();
                }else {
                    Toast.makeText(MainActivity.this, "퍼미션이 거부되었습니다. 설정(앱 정보)에서 퍼미션을 허용해야 합니다. ", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void showDialogForLocationServiceSetting() {

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("위치 서비스 비활성화");
        builder.setMessage("앱을 사용하기 위해서는 위치 서비스가 필요합니다.\n"
                + "위치 설정을 수정하시겠습니까?");
        builder.setCancelable(true);
        builder.setPositiveButton("설정", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Intent callGPSSettingIntent
                        = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(callGPSSettingIntent, GPS_ENABLE_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case GPS_ENABLE_REQUEST_CODE:
                if (checkLocationServicesStatus()) {
                    if (checkLocationServicesStatus()) {
                        Log.d("===debug===", "onActivityResult : GPS 활성화 되있음");
                        checkRunTimePermission();
                        return;
                    }
                }
                break;
        }
    }

    public boolean checkLocationServicesStatus() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public Map<String, Object> getGpsInfo() {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        boolean isGPSEnabled;
        boolean isNetworkEnabled;

        double lat = 0;
        double lng = 0;

        //LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        int hasFineLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_COARSE_LOCATION);

        if(hasFineLocationPermission != PackageManager.PERMISSION_GRANTED && hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) {

        } else {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, minTime, minDistance, gpsListener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, gpsListener);

            Location lastKnownLocation;
            if (isGPSEnabled) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } else {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (null != lastKnownLocation) {
                lng = lastKnownLocation.getLongitude();
                lat = lastKnownLocation.getLatitude();
                //Toast.makeText(getApplicationContext(), "latitude2 : " + lat + ", longtitude2 : " + lng, Toast.LENGTH_LONG).show();
            }

            resultMap.put("mapX", lng);
            resultMap.put("mapY", lat);
        }

        return resultMap;
    }

    class GPSListener implements LocationListener {

        @Override
        public void onLocationChanged(@NonNull Location location) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            Log.i("Info", "changed location - latitude : " + latitude + " / longitude : " + longitude);
            insertLocation(latitude, longitude);
        }
    }

    public void insertLocation(double latitude, double longitude) {
        apiResponse = apiService.insertLocation(deviceId, Double.toString(latitude), Double.toString(longitude));
        //Toast.makeText(getApplicationContext(), "cur location2 : " + latitude + "/" + longitude, Toast.LENGTH_LONG).show();
        Log.i("Info", "cur location2 : " + latitude + "/" + longitude);

        if(null != apiResponse) {
            Log.i("Info", "insert result 2 : " + apiResponse.getResultCode() + "/" + apiResponse.getResult());
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if(event.sensor == mGyroSensor) {
            /* 각 축의 각속도 성분을 받는다. */
            double gyroX = event.values[0];
            double gyroY = event.values[1];
            double gyroZ = event.values[2];

            /* 각속도를 적분하여 회전각을 추출하기 위해 적분 간격(dt)을 구한다.
             * dt : 센서가 현재 상태를 감지하는 시간 간격
             * NS2S : nano second -> second */
            dt = (event.timestamp - timestamp) * NS2S;
            timestamp = event.timestamp;

            /* 맨 센서 인식을 활성화 하여 처음 timestamp가 0일때는 dt값이 올바르지 않으므로 넘어간다. */
            if (dt - timestamp * NS2S != 0) {

                /* 각속도 성분을 적분 -> 회전각(pitch, roll)으로 변환.
                 * 여기까지의 pitch, roll의 단위는 '라디안'이다.
                 * SO 아래 로그 출력부분에서 멤버변수 'RAD2DGR'를 곱해주어 degree로 변환해줌.  */
                pitch = pitch + gyroY * dt;
                roll = roll + gyroX * dt;
                yaw = yaw + gyroZ * dt;

                pitch2 = Math.toDegrees(pitch);
                roll2 = Math.toDegrees(roll);
                yaw2 = Math.toDegrees(yaw);

                TextView pitchView = (TextView) findViewById(R.id.gyro_pitch);
                TextView rollView = (TextView) findViewById(R.id.gyro_roll);
                TextView yawView = (TextView) findViewById(R.id.gyro_yaw);

                pitchView.setText("PITCH : " + String.format("%.1f", pitch * RAD2DGR) + "/" + String.format("%.1f", pitch2));
                rollView.setText("ROLL : " + String.format("%.1f", roll * RAD2DGR) + "/" + String.format("%.1f", roll2));
                yawView.setText("YAW : " + String.format("%.1f", yaw * RAD2DGR) + "/" + String.format("%.1f", yaw2));

                Log.e("LOG", "GYROSCOPE           [X]:" + String.format("%.4f", event.values[0])
                        + "           [Y]:" + String.format("%.4f", event.values[1])
                        + "           [Z]:" + String.format("%.4f", event.values[2])
                        + "           [Pitch]: " + String.format("%.1f", pitch * RAD2DGR)
                        + "           [Roll]: " + String.format("%.1f", roll * RAD2DGR)
                        + "           [Yaw]: " + String.format("%.1f", yaw * RAD2DGR)
                        + "           [dt]: " + String.format("%.4f", dt));

            }
        } else if(event.sensor == mAccSensor) {
            gAccX = event.values[0];
            gAccY = event.values[1];
            gAccZ = event.values[2];

            tmpX = alpha * tmpX + (1-alpha) * event.values[0];
            tmpY = alpha * tmpY + (1-alpha) * event.values[0];
            tmpZ = alpha * tmpZ + (1-alpha) * event.values[0];

            accX = event.values[0] - tmpX;
            accY = event.values[1] - tmpY;
            accZ = event.values[2] - tmpZ;

            gTotal = Math.sqrt(Math.pow(gAccX, 2) + Math.pow(gAccY, 2) + Math.pow(gAccZ, 2)) * 3.6;
            total = Math.sqrt(Math.pow(accX, 2) + Math.pow(accY, 2) + Math.pow(accZ, 2)) * 3.6;

            TextView gTotalView = (TextView) findViewById(R.id.accel_speed);
            TextView totalView = (TextView) findViewById(R.id.accel_no_speed);

            gTotalView.setText("Total Gravity 1 : " + String.format("%.2f", gTotal) + " km/h");
            totalView.setText("Total Gravity 2 : " + String.format("%.2f", total) + " km/h");

            Log.e("LOG", "Total Gravity 1 : " + String.format("%.2f", gTotal) + " km/h");
            Log.e("LOG", "Total Gravity 2 : " + String.format("%.2f", total) + " km/h");

        } else if(event.sensor == mLinearAccSensor) {
            lAccX = event.values[0];
            lAccY = event.values[1];
            lAccZ = event.values[2];

            lTotal = Math.sqrt(Math.pow(lAccX, 2) + Math.pow(lAccY, 2) + Math.pow(lAccZ, 2)) * 3.6;

            if(lTotal < 0.10) {
                lTotal = 0;
            }

            TextView linearView = (TextView) findViewById(R.id.accel_linear_speed);

            linearView.setText("Total Gravity 3 : " + String.format("%.2f", lTotal) + " km/h");

            Log.e("LOG", "Total Gravity 3 : " + String.format("%.2f", lTotal) + " km/h");    // 중력 가속도를 제거한 속도
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void onBackPressed() {
        this.backPressCloseHandler.onBackPressed();
    }
}