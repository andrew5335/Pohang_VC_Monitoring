package com.andrew.pvcm.activity;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.andrew.pvcm.R;
import com.andrew.pvcm.api.InsertLocation;
import com.andrew.pvcm.api.LocationJson;
import com.andrew.pvcm.service.ApiService;

import net.daum.mf.map.api.CameraUpdateFactory;
import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapPointBounds;
import net.daum.mf.map.api.MapPolyline;
import net.daum.mf.map.api.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ViewPathActivity extends AppCompatActivity {

    private MapView pathView;
    private ViewGroup pathViewContainer;

    private Handler handler = null;

    private ApiService apiService;
    private InsertLocation apiResponse;
    private List<LocationJson> locatoinJsonList;
    private List<Map<String, Object>> resultList;
    private String deviceId;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_path);

        apiService = new ApiService();
        locatoinJsonList = new ArrayList<LocationJson>();
        resultList = new ArrayList<Map<String, Object>>();

        preferences = getSharedPreferences("UserInfo", MODE_PRIVATE);
        deviceId = preferences.getString("uuid", "");

        pathView = new MapView(this);
        pathView.setMapType(MapView.MapType.Standard);
        pathViewContainer = (ViewGroup) findViewById(R.id.pathView);
        pathViewContainer.addView(pathView);
        pathView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);

        MapPolyline polyLine = new MapPolyline();
        polyLine.setTag(1000);
        polyLine.setLineColor(Color.RED);

        List<Map<String, Object>> pathList = new ArrayList<Map<String, Object>>();
        pathList = getPathList(deviceId);

        if(null != pathList && 0 < pathList.size()) {

            Log.i("Info", "pathList size : " + pathList.size());
            for(int i=0; i < pathList.size(); i++) {
                double lat = (Double)pathList.get(i).get("lat");
                double lng = (Double)pathList.get(i).get("lng");
                polyLine.addPoint(MapPoint.mapPointWithGeoCoord(lat, lng));
            }

            pathView.addPolyline(polyLine);
            MapPointBounds mapPointBounds = new MapPointBounds(polyLine.getMapPoints());
            int padding = 100;
            pathView.moveCamera(CameraUpdateFactory.newMapPointBounds(mapPointBounds, padding));
        }

    }

    public List<Map<String, Object>> getPathList(String deviceId) {

        // DB??? ????????? ?????? ????????? ????????? ????????? ??????
        handler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                locatoinJsonList = apiService.getLocationList(deviceId);

                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        if(null != locatoinJsonList && 0 < locatoinJsonList.size()) {
                            Log.i("Info", "locationlist.size : " + locatoinJsonList.size());

                            MapPolyline polyLine = new MapPolyline();
                            polyLine.setTag(1000);
                            polyLine.setLineColor(Color.RED);

                            for(int i=0; i < locatoinJsonList.size(); i++) {
                                double lat = Double.parseDouble(String.valueOf(locatoinJsonList.get(i).getDeviceLatitude()));
                                double lng = Double.parseDouble(String.valueOf(locatoinJsonList.get(i).getDeviceLongitude()));
                                polyLine.addPoint(MapPoint.mapPointWithGeoCoord(lat, lng));
                            }

                            pathView.addPolyline(polyLine);
                            MapPointBounds mapPointBounds = new MapPointBounds(polyLine.getMapPoints());
                            int padding = 10;
                            pathView.moveCamera(CameraUpdateFactory.newMapPointBounds(mapPointBounds, padding));

                        } else {
                            Log.i("Info", "locationlist is null");
                        }
                    }
                });
            }
        }).start();

        Log.i("Info", "resultList size : " + resultList.size());
        return resultList;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pathViewContainer.removeView(pathView);
        pathViewContainer.removeAllViews();
    }

    @Override
    public void finish() {
        super.finish();
        pathViewContainer.removeView(pathView);
        pathViewContainer.removeAllViews();
    }

}