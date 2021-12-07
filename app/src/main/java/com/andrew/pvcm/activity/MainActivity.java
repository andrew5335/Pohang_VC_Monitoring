package com.andrew.pvcm.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.andrew.pvcm.R;
import com.andrew.pvcm.api.InsertLocation;
import com.andrew.pvcm.api.InsertSensor;
import com.andrew.pvcm.api.LocationJson;
import com.andrew.pvcm.client.BluetoothSerialClient;
import com.andrew.pvcm.handler.BackPressCloseHandler;
import com.andrew.pvcm.service.ApiService;
import com.harrysoft.androidbluetoothserial.BluetoothManager;
import com.harrysoft.androidbluetoothserial.BluetoothSerialDevice;
import com.harrysoft.androidbluetoothserial.SimpleBluetoothDeviceInterface;

import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapReverseGeoCoder;
import net.daum.mf.map.api.MapView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

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
    private float mDegree;

    // api 연동
    private ApiService apiService;
    private InsertLocation apiResponse;
    private InsertSensor sensorResponse;
    private List<LocationJson> locatoinJsonList;
    private String deviceId;

    // 뒤로가기 처리
    private BackPressCloseHandler backPressCloseHandler;

    // 텍스트 저장
    private SharedPreferences preferences;

    // 갱신 주기 설정 (60초/5m)
    long minTime = 60000;    // 1분마다 갱신 - 시간은 조절 가능 (1초 - 1000 으로 세팅)
    float minDistance = 5;    // 5m마다 갱신

    private BluetoothManager bluetoothManager;
    private SimpleBluetoothDeviceInterface deviceInterface;
    private StringBuilder messages = new StringBuilder();
    private MutableLiveData<String> messagesData = new MutableLiveData<>();

    private BluetoothSerialClient mClient;
    private ProgressDialog mLoadingDialog;
    private AlertDialog mDeviceListDialog;
    private LinkedList<BluetoothDevice> mBluetoothDevices = new LinkedList<BluetoothDevice>();
    private ArrayAdapter<String> mDeviceArrayAdapter;

    private TextView pitchView;
    private TextView rollView;
    private TextView yawView;
    private TextView linearView;
    private TextView vcStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        pitchView = (TextView) findViewById(R.id.gyro_pitch);
        rollView = (TextView) findViewById(R.id.gyro_roll);
        yawView = (TextView) findViewById(R.id.gyro_yaw);
        linearView = (TextView) findViewById(R.id.accel_linear_speed);
        vcStatus = (TextView) findViewById(R.id.vc_status);

        bluetoothManager = BluetoothManager.getInstance();

        if (bluetoothManager == null) {
            Toast.makeText(getApplicationContext(), "블루투스가 연결되지 않았습니다.", Toast.LENGTH_LONG).show();
            finish();
        } else {
            getBleDevice();
        }

        mClient = BluetoothSerialClient.getInstance();

        if(mClient == null) {
            Toast.makeText(getApplicationContext(), "블루투스 기기를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }

        preferences = getSharedPreferences("UserInfo", MODE_PRIVATE);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        gpsListener = new GPSListener();

        apiService = new ApiService();
        apiResponse = new InsertLocation();
        sensorResponse = new InsertSensor();

        locatoinJsonList = new ArrayList<LocationJson>();

        // sharedpreference에 uuid 값이 있으면 해당 값 사용하고 없으면 새로 생성하여 사용
        deviceId = preferences.getString("uuid", "");

        if(null != deviceId && !"".equals(deviceId)) {

        } else {
            deviceId = getDeviceUUID();
        }

        this.backPressCloseHandler = new BackPressCloseHandler(this);

        mDegree = 0;

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mLinearAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        mSensorManager.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        mSensorManager.registerListener(this, mLinearAccSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
    }

    public void getBleDevice() {

        Collection<BluetoothDevice> pairedDevices = bluetoothManager.getPairedDevicesList();

        for (BluetoothDevice device : pairedDevices) {
            if(device.getName().contains("VSPACE")) {

                // 블루투스 기기명에 VSPACE가 포함되면 연결 후 데이터 수신
                bluetoothManager.openSerialDevice(device.getAddress())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::onConnected, this::onError);
            }
        }
    }

    private void onConnected(BluetoothSerialDevice connectedDevice) {

        deviceInterface = connectedDevice.toSimpleDeviceInterface();
        deviceInterface.setListeners(this::onMessageReceived, this::onMessageSent, this::onError);
    }

    private void onMessageSent(String message) {

        Toast.makeText(getApplicationContext(), "Message : " + message, Toast.LENGTH_LONG).show();
    }

    private void onMessageReceived(String message) {

        int msgLen = 0;
        msgLen = message.length();

        if(msgLen < 26) {

        } else {
            String reverseStr = "";
            TextView soc = (TextView) findViewById(R.id.vc_soc);
            TextView voltage = (TextView) findViewById(R.id.vc_voltage);
            TextView temp = (TextView) findViewById(R.id.vc_temp);

            try {

                if (message.contains("01340201")) {
                    String msg1 = "";
                    String msg2 = "";
                    msg1 = message.substring(10, 12);
                    msg2 = message.substring(12, 14);

                    reverseStr = msg2 + msg1;
                    int socVal = Integer.parseInt(reverseStr, 16);
                    soc.setText((socVal*0.1) + "%");
                } else if (message.contains("01340203")) {
                    message = message.substring(10, 12);

                    int voltageVal = Integer.parseInt(message, 16);
                    voltage.setText((voltageVal*0.1) + "V");
                } else if (message.contains("01340207")) {
                    message = message.substring(16, 18);

                    int tempVal = Integer.parseInt(message, 16);
                    temp.setText((tempVal*0.1) + "\u2103");
                } else if(message.contains("|")) {
                    String[] msgArr = message.split("\\|");
                    int x = 0 - (int) Double.parseDouble(msgArr[0]);
                    int y = 0 - (int) Double.parseDouble(msgArr[1]);
                    int z = 0 - (int) Double.parseDouble(msgArr[2]);

                    pitchView.setText("X : " + msgArr[0]);
                    rollView.setText("Y : " + msgArr[1]);
                    yawView.setText("Z : " + msgArr[2]);

                    if(x < -50 || x > 50
                            || y < -50 || y > 50
                            || z < -50 || z > 50) {
                        vcStatus.setText("사고");
                    } else {
                        vcStatus.setText("정상");
                    }
                }
            } catch(Exception e) {
                Log.e("ERROR", "Error : " + e.toString());
            }
        }

    }

    private void onError(Throwable error) {
        // Handle the error
    }

    public LiveData<String> getMessages() { return messagesData; }

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
            double gSpeed = (Double) curLoc.get("gpsSpeed");

            mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(lat, lng), true);
            Toast.makeText(getApplicationContext(), "현재 위치 : " + lat + "/" + lng, Toast.LENGTH_LONG).show();
        }

        mSensorManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        mSensorManager.registerListener(this, mAccSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        mSensorManager.registerListener(this, mLinearAccSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
    }

    public void close() {

        mapViewContainer.removeAllViews();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void finish() {

        super.finish();
        mapViewContainer.removeView(mapView);
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
        mSensorManager.unregisterListener(this);
        bluetoothManager.close();
    }

    @Override
    protected void onPause() {

        mSensorManager.unregisterListener(this);
        bluetoothManager.close();
        super.onPause();
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
        mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(gps.latitude, gps.longitude), true);
    }

    @Override
    public void onCurrentLocationDeviceHeadingUpdate(MapView mapView, float v) {

    }

    @Override
    public void onCurrentLocationUpdateFailed(MapView mapView) {

        Toast.makeText(getApplicationContext(), "현재 위치 정보 업데이트 실패", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCurrentLocationUpdateCancelled(MapView mapView) {

        Toast.makeText(getApplicationContext(), "현재 위치 정보 업데이트 취소", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMapViewInitialized(MapView mapView) {

        Log.i("INFO", "MapView 초기화");
    }

    @Override
    public void onMapViewCenterPointMoved(MapView mapView, MapPoint mapPoint) {

        Log.i("INFO", "MapView 중심점 이동");
    }

    @Override
    public void onMapViewZoomLevelChanged(MapView mapView, int i) {

        Log.i("INFO", "MapView Zoom 레벨 변경");
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

    @SuppressLint("MissingSuperCall")
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

            if (check_result) {

            } else {

                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])) {
                    Toast.makeText(MainActivity.this, "권한 요청이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요.", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    Toast.makeText(MainActivity.this, "권한 요청이 거부되었습니다. 설정(앱 정보)에서 퍼미션을 허용해야 합니다. ", Toast.LENGTH_LONG).show();
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
        double gpsSpeed = 0;

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
                gpsSpeed = Double.parseDouble(String.format("%.3f", lastKnownLocation.getSpeed()));
            }

            resultMap.put("mapX", lng);
            resultMap.put("mapY", lat);
            resultMap.put("gpsSpeed", gpsSpeed);
        }

        return resultMap;
    }

    class GPSListener implements LocationListener {

        @Override
        public void onLocationChanged(@NonNull Location location) {
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            Location lastKnownLocation;
            double curSpeed;
            double lastSpeed;
            double chkSpeed;
            double chkTime;

            boolean isGPSEnabled;
            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            int hasFineLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION);
            int hasCoarseLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION);

            if(hasFineLocationPermission != PackageManager.PERMISSION_GRANTED && hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED) {

            } else {
                if (isGPSEnabled) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                } else {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if(lastKnownLocation != null) {
                    curSpeed = Double.parseDouble(String.format("%.3f", location.getSpeed()));
                    lastSpeed = Double.parseDouble(String.format("%.3f", lastKnownLocation.getSpeed()));

                    chkTime = (location.getTime() - lastKnownLocation.getTime()) / 1000.0;
                    chkSpeed = Double.parseDouble(String.format("%.3f", (lastKnownLocation.distanceTo(location) / chkTime)));
                    linearView.setText(curSpeed + "km/h");

                }

                lastKnownLocation = location;
            }

            insertLocation(latitude, longitude);    // 위치 정보 변경 시 데이터 저장 처리
        }
    }

    public void insertLocation(double latitude, double longitude) {

        apiResponse = apiService.insertLocation(deviceId, Double.toString(latitude), Double.toString(longitude));

        if(null != apiResponse) {
            Log.i("INFO", "insert result 2 : " + apiResponse.getResultCode() + "/" + apiResponse.getResult());
        } else {
            Log.e("ERROR", "insert fail");
        }
    }

    public void insertSensor(String acx, String acy, String acz, String gyx, String gyy, String gyz) {

        sensorResponse = apiService.insertSensor(deviceId, acx, acy, acz, gyx, gyy, gyz);

        if(null != sensorResponse) {
            Log.i("INFO", "insert result 2 : " + sensorResponse.getResultCode() + "/" + sensorResponse.getResult());
        } else {
            Log.e("ERROR", "insert fail");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        pitchView = (TextView) findViewById(R.id.gyro_pitch);
        rollView = (TextView) findViewById(R.id.gyro_roll);
        yawView = (TextView) findViewById(R.id.gyro_yaw);
        linearView = (TextView) findViewById(R.id.accel_linear_speed);
        vcStatus = (TextView) findViewById(R.id.vc_status);

        double gyroX, gyroY, gyroZ;

        if(event.sensor == mGyroSensor) {
            // 각 축의 각속도 확인
            gyroX = event.values[0];
            gyroY = event.values[1];
            gyroZ = event.values[2];

            dt = (event.timestamp - timestamp) * NS2S;
            timestamp = event.timestamp;

            if (dt - timestamp * NS2S != 0) {

                pitch = pitch + gyroY * dt;   // gyy
                roll = roll + gyroX * dt;    // gyx
                yaw = yaw + gyroZ * dt;    //gyz

                pitch2 = Math.toDegrees(pitch);
                roll2 = Math.toDegrees(roll);
                yaw2 = Math.toDegrees(yaw);

            }
        } else if(event.sensor == mAccSensor) {
            timestamp = event.timestamp;

            // 중력 가속도 제거 전 속도
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

        } else if(event.sensor == mLinearAccSensor) {
            timestamp = event.timestamp;

            // 중력 가속도 제거 후 속도
            lAccX = event.values[0];
            lAccY = event.values[1];
            lAccZ = event.values[2];

            lTotal = Math.sqrt(Math.pow(lAccX, 2) + Math.pow(lAccY, 2) + Math.pow(lAccZ, 2)) * 3.6;

            if(lTotal < 0.10) {
                lTotal = 0;
            }
        }

        try {
            String mAccx, mAccy, mAccz, mGyx, mGyy, mGyz;
            mAccx = String.format("%.2f", lAccX * RAD2DGR);
            mAccy = String.format("%.2f", lAccY * RAD2DGR);
            mAccz = String.format("%.2f", lAccZ * RAD2DGR);
            mGyx = String.format("%.2f", roll * RAD2DGR);
            mGyy = String.format("%.2f", pitch * RAD2DGR);
            mGyz = String.format("%.2f", yaw * RAD2DGR);

            insertSensor(mAccx, mAccy, mAccz, mGyx, mGyy, mGyz);

        } catch(Exception e) {
            Log.e("insertSensorError", e.toString());
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void onBackPressed() {

        mSensorManager.unregisterListener(this);
        this.backPressCloseHandler.onBackPressed();
    }

    /**
     * 기기 고유 아이디 값 생성 - unique값으로 36자리 숫자+영문 코드
     * @return
     */
    private String getDeviceUUID() {

        String result = "";

        result = UUID.randomUUID().toString();
        SharedPreferences.Editor editor = preferences.edit();

        editor.putString("uuid", result);
        editor.commit();

        return result;
    }

    private void initProgressDialog() {

        mLoadingDialog = new ProgressDialog(this);
        mLoadingDialog.setCancelable(false);
    }

    private void addDeviceToArrayAdapter(BluetoothDevice device) {

        if(mBluetoothDevices.contains(device)) {
            mBluetoothDevices.remove(device);
            mDeviceArrayAdapter.remove(device.getName() + "\n" + device.getAddress());
        }

        mBluetoothDevices.add(device);
        mDeviceArrayAdapter.add(device.getName() + "\n" + device.getAddress() );
        mDeviceArrayAdapter.notifyDataSetChanged();

    }

    private void enableBluetooth() {

        BluetoothSerialClient btSet =  mClient;

        btSet.enableBluetooth(this, new BluetoothSerialClient.OnBluetoothEnabledListener() {

            @Override
            public void onBluetoothEnabled(boolean success) {

                if(success) {
                    getPairedDevices();
                } else {
                    finish();
                }
            }
        });
    }

    private void getPairedDevices() {

        Set<BluetoothDevice> devices =  mClient.getPairedDevices();

        for(BluetoothDevice device: devices) {
            //여기서 VSPACE가 포함된 기기와 연결 처리
            if(device.getName().contains("VSPACE")) {
                connect(device);
            }
        }
    }

    private void scanDevices() {

        BluetoothSerialClient btSet = mClient;

        btSet.scanDevices(getApplicationContext(), new BluetoothSerialClient.OnScanListener() {

            String message ="";

            @Override
            public void onStart() {

                mLoadingDialog.show();
                message = "Scanning....";
                mLoadingDialog.setMessage("Scanning....");
                mLoadingDialog.setCancelable(true);
                mLoadingDialog.setCanceledOnTouchOutside(false);
                mLoadingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        BluetoothSerialClient btSet = mClient;
                        btSet.cancelScan(getApplicationContext());
                    }
                });
            }

            @Override
            public void onFoundDevice(BluetoothDevice bluetoothDevice) {

                addDeviceToArrayAdapter(bluetoothDevice);
                message += "\n" + bluetoothDevice.getName() + "\n" + bluetoothDevice.getAddress();
                mLoadingDialog.setMessage(message);
            }

            @Override
            public void onFinish() {

                message = "";
                mLoadingDialog.cancel();
                mLoadingDialog.setCancelable(false);
                mLoadingDialog.setOnCancelListener(null);
                mDeviceListDialog.show();
            }
        });
    }

    private void connect(BluetoothDevice device) {

        BluetoothSerialClient btSet =  mClient;
        btSet.connect(getApplicationContext(), device, mBTHandler);
    }

    private BluetoothSerialClient.BluetoothStreamingHandler mBTHandler = new BluetoothSerialClient.BluetoothStreamingHandler() {

        ByteBuffer mmByteBuffer = ByteBuffer.allocate(256);

        @Override
        public void onError(Exception e) {
            Log.e("ERROR", "Error - " + e.toString());
        }

        @Override
        public void onDisconnected() {
            Log.i("INFO", "Device disconnect");
        }
        @Override
        public void onData(byte[] buffer, int length) {

            mmByteBuffer.put(buffer, 0, 26);
            mmByteBuffer.clear();
        }

        @Override
        public void onConnected() {
            Toast.makeText(getApplicationContext(), "블루투스 기기 연결 : " + mClient.getConnectedDevice().getName(), Toast.LENGTH_LONG).show();
        }
    };
}
