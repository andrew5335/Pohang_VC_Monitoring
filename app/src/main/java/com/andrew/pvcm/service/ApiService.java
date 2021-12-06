package com.andrew.pvcm.service;

import android.util.Log;

import com.andrew.pvcm.api.InsertLocation;
import com.andrew.pvcm.api.InsertSensor;
import com.andrew.pvcm.api.LocationJson;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiService {

    private String eye2webHost = "http://eye2web.co.kr";
    private Retrofit client = new Retrofit.Builder().baseUrl(eye2webHost)
            .addConverterFactory(GsonConverterFactory.create()).build();

    private List<LocationJson> locationList;
    private InsertLocation apiResponse;
    private InsertSensor sensorResponse;

    public List<LocationJson> getLocationList(String deviceId) {

        locationList = new ArrayList<LocationJson>();

        try {
            LocationJson.LocationJsonInterface service = client.create(LocationJson.LocationJsonInterface.class);
            Call<List<LocationJson>> call = service.getLocationList(deviceId);

            locationList = call.execute().body();
        } catch(Exception e) {
            Log.e("Error", "Error get location list");
        }

        return locationList;
    }

    public InsertLocation insertLocation(String deviceId, String deviceLatitude, String deviceLongitude) {

        apiResponse = new InsertLocation();

        if(null != deviceId && null != deviceLatitude && !("0.0").equals(deviceLatitude) && null != deviceLongitude && !("0.0").equals(deviceLongitude)) {
            try {
                Log.i("Location insert", "insert info : " + deviceId + "/" + deviceLatitude + "/" + deviceLongitude);
                InsertLocation.InsertLocatonInterface service = client.create(InsertLocation.InsertLocatonInterface.class);
                Call<InsertLocation> call = service.insertLocation(deviceId, deviceLatitude, deviceLongitude);

                apiResponse = call.execute().body();
                Log.i("Location insert", "apiresponse : " + apiResponse);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Location insert", "Error insert location info : " + e.toString());
            }
        }

        return apiResponse;
    }

    public InsertSensor insertSensor(String deviceId, String deviceAcx, String deviceAcy, String deviceAcz, String deviceGyx, String deviceGyy, String deviceGyz) {
        sensorResponse = new InsertSensor();

        if(null != deviceId) {
            try {
                Log.i("Sensor insert", "insert info : " + deviceId + "/" + deviceAcx + "/" + deviceAcy + "/" + deviceAcz + "/" + deviceGyx + "/" + deviceGyy + "/" + deviceGyz);
                InsertSensor.InsertSensorInterface service = client.create(InsertSensor.InsertSensorInterface.class);
                Call<InsertSensor> call = service.insertSensor(deviceId, deviceAcx, deviceAcy, deviceAcz, deviceGyx, deviceGyy, deviceGyz);

                sensorResponse = call.execute().body();
                Log.i("Sensor insert", "sensorResponse : " + sensorResponse);
            } catch(Exception e) {
                Log.e("Sensor insert", "Error insert sensor info : " + e.toString());
            }

        }

        return sensorResponse;
    }
}
